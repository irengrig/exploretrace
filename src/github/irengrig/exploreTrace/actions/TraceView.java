package github.irengrig.exploreTrace.actions;

import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.Consumer;
import com.intellij.util.Processor;
import com.intellij.util.ui.UIUtil;
import github.irengrig.exploreTrace.Trace;
import github.irengrig.exploreTrace.TracesClassifier;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.List;

/**
 * Created by Irina.Chernushina on 8/16/2014.
 */
public class TraceView extends JPanel implements TypeSafeDataProvider {
  private final Project myProject;
  private final List<Trace> myNotGrouped;
  private final List<TracesClassifier.PoolDescriptor> myPools;
  private final List<TracesClassifier.PoolDescriptor> mySimilar;
  private final List<Trace> myJdkThreads;
  private final Trace myEdtTrace;
  private final DefaultActionGroup myDefaultActionGroup;
  private JBList myNamesList;
  private Splitter mySplitter;
  private ConsoleView myConsole;

  private List<Pair<TraceType, Integer>> myListMapping;
  @NonNls
  private static final String SOCKET_GENERIC_PATTERN = "at java.net.";

  public TraceView(final Project project, final List<Trace> notGrouped,
                   final List<TracesClassifier.PoolDescriptor> pools, final List<TracesClassifier.PoolDescriptor> similar,
                   final List<Trace> jdkThreads, final Trace edtTrace, DefaultActionGroup defaultActionGroup, final String contentId) {
    super(new BorderLayout());
    myProject = project;
    myNotGrouped = notGrouped;
    myPools = pools;
    mySimilar = similar;
    myJdkThreads = jdkThreads;
    myEdtTrace = edtTrace;
    myDefaultActionGroup = defaultActionGroup;
    myListMapping = new ArrayList<>();
    initUi();
    createActions(contentId);
    updateDetails();
  }

  private void createActions(final String contentId) {
    myDefaultActionGroup.add(new AnAction("Edit Dump Name", "Edit thread dump name", AllIcons.ToolbarDecorator.Edit) {
      @Override
      public void actionPerformed(final AnActionEvent anActionEvent) {
        final Project project = PlatformDataKeys.PROJECT.getData(anActionEvent.getDataContext());
        if (project == null) return;

        final String text = Messages.showInputDialog(project, "Edit thread dump name:",
                ShowTraceViewAction.EXPLORE_STACK_TRACE,
                AllIcons.ToolbarDecorator.Edit, contentId, new InputValidator() {
                  @Override
                  public boolean checkInput(final String s) {
                    return ! StringUtil.isEmptyOrSpaces(s);
                  }

                  @Override
                  public boolean canClose(final String s) {
                    return ! StringUtil.isEmptyOrSpaces(s);
                  }
                }
        );
        if (! StringUtil.isEmptyOrSpaces(text)) {
          final ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
          final ToolWindow toolWindow = toolWindowManager.getToolWindow(ShowTraceViewAction.EXPLORE_STACK_TRACE);
          if (toolWindow != null) {
            final ContentManager cm = toolWindow.getContentManager();
            final Content[] contents = cm.getContents();
            for (Content content : contents) {
              if (contentId.equals(content.getTabName())) {
                content.setDisplayName(text);
              }
            }
          }
        }
      }
    });
  }

  public JBList getNamesList() {
    return myNamesList;
  }

  public void initUi() {
    mySplitter = new Splitter(false, 0.3f);
    myNamesList = new JBList(new DefaultListModel());
    final TextConsoleBuilder builder = TextConsoleBuilderFactory.getInstance().createBuilder(myProject);
    myConsole = builder.getConsole();

    myNamesList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    fillNamesList();
    myNamesList.setCellRenderer(new MyNameListCellRenderer());
    myNamesList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(final ListSelectionEvent e) {
        updateDetails();
      }
    });
    // todo does not work in the beginning of the list when I'm in the middle
    new ListSpeedSearch(myNamesList).setComparator(new SpeedSearchComparator(false, true));

    mySplitter.setFirstComponent(ScrollPaneFactory.createScrollPane(myNamesList, SideBorder.LEFT | SideBorder.RIGHT));
    mySplitter.setSecondComponent(myConsole.getComponent());

    add(ActionManager.getInstance().createActionToolbar(ActionPlaces.MAIN_TOOLBAR, myDefaultActionGroup, false).getComponent(), BorderLayout.WEST);
    add(mySplitter, BorderLayout.CENTER);

    addDeleteHandler();
  }

  private void addDeleteHandler() {
    myNamesList.addKeyListener(new KeyAdapter() {
      @Override
      public void keyReleased(final KeyEvent e) {
        final boolean isAltDown = e.isAltDown();
        final boolean isShiftDown = e.isShiftDown();
        if (e.getKeyCode() == KeyEvent.VK_DELETE) {
          if (deleteHandler()) return;
        } else if (isAltDown && isShiftDown && e.getKeyCode() == KeyEvent.VK_UP) {
          moveUp();
          return;
        } else if (isAltDown && isShiftDown && e.getKeyCode() == KeyEvent.VK_DOWN) {
          moveDown();
          return;
        }
        super.keyReleased(e);
      }
    });
  }

  private void moveSomewhere(final Processor<int[]> processor) {
    int[] selectedIndices = myNamesList.getSelectedIndices();
    if (selectedIndices.length == myNamesList.getItemsCount()) return;
    if (selectedIndices.length > 0) {
      int current = selectedIndices[0];
      for (int i = 1; i < selectedIndices.length; i++) {
        ++ current;
        if (selectedIndices[i] != current) return;
      }
      // one block or single selection
      if(processor.process(selectedIndices)) {
        myNamesList.revalidate();
        myNamesList.repaint();
      }
    }
  }

  private void moveUp() {
    moveSomewhere(new Processor<int[]>() {
      @Override
      public boolean process(final int[] selectedIndices) {
        if (selectedIndices[0] == 0) return false;
        final DefaultListModel model = (DefaultListModel) myNamesList.getModel();
        final Object previous = model.get(selectedIndices[0] - 1);
        final int anchor = myNamesList.getAnchorSelectionIndex();
        final int lead = myNamesList.getLeadSelectionIndex();
        model.add(selectedIndices[selectedIndices.length - 1] + 1, previous);
        model.remove(selectedIndices[0] - 1);
        myNamesList.addSelectionInterval(anchor - 1, lead - 1);
        return true;
      }
    });
  }

  private void moveDown() {
    moveSomewhere(new Processor<int[]>() {
      @Override
      public boolean process(final int[] selectedIndices) {
        if (selectedIndices[selectedIndices.length - 1] == (myNamesList.getItemsCount() - 1)) return false;
        final DefaultListModel model = (DefaultListModel) myNamesList.getModel();
        final Object next = model.get(selectedIndices[selectedIndices.length - 1] + 1);
        final int anchor = myNamesList.getAnchorSelectionIndex();
        final int lead = myNamesList.getLeadSelectionIndex();

        model.add(selectedIndices[0], next);
        model.remove(selectedIndices[selectedIndices.length - 1] + 2);
        myNamesList.removeSelectionInterval(selectedIndices[0], selectedIndices[0]);
        myNamesList.addSelectionInterval(anchor + 1, lead + 1);
        return true;
      }
    });
  }

  private boolean deleteHandler() {
    int[] selectedIndices = myNamesList.getSelectedIndices();
    if (selectedIndices.length == myNamesList.getItemsCount()) return true;

    if (selectedIndices.length > 1) {
      final DefaultListModel model = (DefaultListModel) myNamesList.getModel();
      final List<String> names = new ArrayList<>(selectedIndices.length);
      for (int selectedIndice : selectedIndices) {
        final TypedTrace typedTrace = (TypedTrace) model.get(selectedIndice);
        final Trace trace = getTrace(typedTrace);
        names.add("'" + trace.getThreadName() + "'");
      }

      final int result = Messages.showYesNoDialog(myProject,
              "Do you want to delete " + selectedIndices.length + " threads " + StringUtil.join(names, ", ") + "?\nThis action can not be undone.",
              "Delete Threads from Thread Dump", Messages.getQuestionIcon());
      if (result == Messages.OK) {
//        Arrays.sort(selectedIndices);
        for (int i = selectedIndices.length - 1; i >= 0; i--) {
          int selectedIndice = selectedIndices[i];
          model.remove(selectedIndice);
        }
        myNamesList.revalidate();
        myNamesList.repaint();
      }

    } else {
      final int selectedIndex = selectedIndices[0];
      final TypedTrace typedTrace = (TypedTrace) myNamesList.getSelectedValue();
      if (typedTrace != null) {
        final Trace trace = getTrace(typedTrace);
        // todo maybe change presentation
        final int result = Messages.showYesNoDialog(myProject,
                "Do you want to delete thread '" + trace.getThreadName() + "'?\nThis action can not be undone.",
                "Delete Thread from Thread Dump", Messages.getQuestionIcon());
        if (result == Messages.OK) {
          ((DefaultListModel) myNamesList.getModel()).remove(selectedIndex);
          myNamesList.revalidate();
          myNamesList.repaint();
        }
      }
    }
    return false;
  }

  private void precalculateDetails(final List<TypedTrace> list) {
    for (TypedTrace typedTrace : list) {
      final List<LineInfo> result = new ArrayList<>();
      final Trace tr = getTrace(typedTrace);
      final List<String> trace = tr.getTrace();
      for (String s : trace) {
        final LineParser lineParser = new LineParser(s);
        lineParser.parse(myProject);
        final LineInfo lineInfo = new LineInfo();
        for (Pair<String, HyperlinkInfo> pair : lineParser.getResult()) {
          lineInfo.add(pair.getFirst(), pair.getSecond());
        }
        result.add(lineInfo);
      }
      typedTrace.setDetails(result);
    }
  }

  private void updateDetails() {
    myConsole.clear();
    final int idx = myNamesList.getSelectedIndex();
    if (idx >= 0) {
      final TypedTrace typedTrace = (TypedTrace) myNamesList.getModel().getElementAt(idx);
      final Trace t = getTrace(typedTrace);
      myConsole.print(t.getFirstLine(), ConsoleViewContentType.NORMAL_OUTPUT);
      myConsole.print("\n", ConsoleViewContentType.NORMAL_OUTPUT);
      if (t.getStateWords() != null) {
        myConsole.print("   java.lang.Thread.State: ", ConsoleViewContentType.NORMAL_OUTPUT);
        myConsole.print(t.getStateWords(), ConsoleViewContentType.NORMAL_OUTPUT);
        myConsole.print("\n", ConsoleViewContentType.NORMAL_OUTPUT);
      }

      for (Object o : typedTrace.getDetails()) {
        final LineInfo lineInfo = (LineInfo) o;
        for (Pair<String, HyperlinkInfo> pair : lineInfo.getFragments()) {
          if (pair.getSecond() == null) {
            myConsole.print(pair.getFirst(), ConsoleViewContentType.NORMAL_OUTPUT);
          } else {
            myConsole.printHyperlink(pair.getFirst(), pair.getSecond());
          }
        }
        myConsole.print("\n", ConsoleViewContentType.NORMAL_OUTPUT);
      }
    }
    myConsole.scrollTo(0);
  }

  private static class LineInfo {
    private final List<Pair<String, HyperlinkInfo>> myFragments;

    public LineInfo() {
      myFragments = new ArrayList<>();
    }

    public void add(@NotNull final String text, @Nullable HyperlinkInfo hyperlinkInfo) {
      myFragments.add(Pair.create(text, hyperlinkInfo));
    }

    public List<Pair<String, HyperlinkInfo>> getFragments() {
      return myFragments;
    }
  }

  private static Trace getTrace(final TypedTrace typedTrace) {
    if (! TraceType.pool.equals(typedTrace.getTraceType()) && ! TraceType.similar.equals(typedTrace.getTraceType())) {
      return (Trace) typedTrace.getT();
    } else {
      return ((TracesClassifier.PoolDescriptor) typedTrace.getT()).getTypicalTrace();
    }
  }

  private void fillNamesList() {
    final DefaultListModel model = (DefaultListModel) myNamesList.getModel();
    model.clear();

    final List<TypedTrace> list = new ArrayList<>();
    for (Trace trace : myNotGrouped) {
      list.add(new TypedTrace<>(TraceType.single, trace));
    }
    if (myEdtTrace != null) {
      list.add(new TypedTrace<>(TraceType.edt, myEdtTrace));
    }
    for (TracesClassifier.PoolDescriptor pool : mySimilar) {
      list.add(new TypedTrace<>(TraceType.similar, pool));
    }
    for (TracesClassifier.PoolDescriptor pool : myPools) {
      list.add(new TypedTrace<>(TraceType.pool, pool));
    }
    for (Trace trace : myJdkThreads) {
      list.add(new TypedTrace<>(TraceType.jdk, trace));
    }
    setCases(list);
    precalculateDetails(list);
    Collections.sort(list, TypedTraceComparator.INSTANCE);

    for (TypedTrace typedTrace : list) {
      model.addElement(typedTrace);
    }
    if (! model.isEmpty()) {
      myNamesList.setSelectedIndex(0);
    }
    myNamesList.revalidate();
    myNamesList.repaint();
  }

  private void setCases(final List<TypedTrace> model) {
    for (TypedTrace typedTrace : model) {
      final Trace trace = getTrace(typedTrace);
      trace.setCase(TraceCase.identify(typedTrace.getTraceType(), trace));
    }
  }

  private static class MyNameListCellRenderer extends ColoredListCellRenderer {
    @Override
    protected void customizeCellRenderer(final JList jList, final Object value, final int i, final boolean b, final boolean b1) {
      if (value instanceof TypedTrace) {
        final TraceType traceType = ((TypedTrace) value).getTraceType();
        if (TraceType.jdk.equals(traceType) || TraceType.edt.equals(traceType)) {
          append("[JDK] ", SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES);
          final Trace t = (Trace) ((TypedTrace) value).getT();
          setIcon(t.getCase().getIcon(t.isDaemon()));
          printTrace(t);
        }
        if (TraceType.pool.equals(traceType)) {
          printPool("POOL", (TracesClassifier.PoolDescriptor) ((TypedTrace) value).getT());
        }
        if (TraceType.similar.equals(traceType)) {
          printPool("SIMILAR", (TracesClassifier.PoolDescriptor) ((TypedTrace) value).getT());
        }
        if (TraceType.single.equals(traceType)) {
          final Trace t = (Trace) ((TypedTrace) value).getT();
          setIcon(t.getCase().getIcon(t.isDaemon()));
          printTrace(t);
        }
      }
    }

    private void printPool(final String name, final TracesClassifier.PoolDescriptor d) {
      final Trace trace = d.getTypicalTrace();
      setIcon(trace.getCase().getIcon(trace.isDaemon()));
      append("[" + name + ": " + d.getNumber() + "] ", SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES);
      append(d.getTemplateName());
      printState(trace);
    }

    private void printTrace(final Trace t) {
      append(t.getThreadName());
      printState(t);
    }

    private void printState(final Trace t) {
      final String stateWords = t.getStateWords();
      if (stateWords != null) {
        append(" (").append(stateWords).append(")");
      }
    }
  }

  @Override
  public void calcData(final DataKey dataKey, final DataSink dataSink) {

  }

  private static class TypedTrace<T> {
    private final TraceType myTraceType;
    private final T myT;
    private List<LineInfo> myDetails;

    public TypedTrace(final TraceType traceType, final T t) {
      myTraceType = traceType;
      myT = t;
    }

    public List<LineInfo> getDetails() {
      return myDetails;
    }

    public void setDetails(final List<LineInfo> details) {
      myDetails = details;
    }

    public TraceType getTraceType() {
      return myTraceType;
    }

    public T getT() {
      return myT;
    }
  }

  public static enum TraceType {
    single, edt, similar, pool, jdk;
  }

  private static class TypedTraceComparator implements Comparator<TypedTrace> {
    public static TypedTraceComparator INSTANCE = new TypedTraceComparator();

    @Override
    public int compare(final TypedTrace o1, final TypedTrace o2) {
      int compareType = Integer.compare(o1.getTraceType().ordinal(), o2.getTraceType().ordinal());
      if (compareType != 0) return compareType;

      final Trace trace1 = getTrace(o1);
      final Trace trace2 = getTrace(o2);

      if (TraceType.jdk.equals(o1.getTraceType())) {
        int compareOrderInGroup = Integer.compare(trace1.getInitialOrderInGroup(), trace2.getInitialOrderInGroup());
        if (compareOrderInGroup != 0) return compareOrderInGroup;
      }

      int compareCases = Integer.compare(trace1.getCase().ordinal(), trace2.getCase().ordinal());
      if (compareCases != 0) return compareCases;

      int comparePri = Integer.compare(trace2.getPriority(), trace1.getPriority());
      if (comparePri != 0) return comparePri;

      return trace1.getThreadName().compareToIgnoreCase(trace2.getThreadName());
    }
  }
}
