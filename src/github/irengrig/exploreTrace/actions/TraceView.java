package github.irengrig.exploreTrace.actions;

import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.*;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBList;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.popup.PopupFactoryImpl;
import com.intellij.util.BooleanFunction;
import com.intellij.util.Processor;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.UIUtil;
import github.irengrig.exploreTrace.PoolDescriptor;
import github.irengrig.exploreTrace.Trace;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
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
  private final List<PoolDescriptor> myPools;
  private final List<PoolDescriptor> mySimilar;
  private final List<Trace> myJdkThreads;
  private final Trace myEdtTrace;
  private final DefaultActionGroup myDefaultActionGroup;
  private JBList myNamesList;
  private Splitter mySplitter;
  private ConsoleView myConsole;
  private Set<Pair<TraceType, TraceCase>> myCurrentFilter;
  private final Set<Object> myMarked;

  @NonNls
  private static final String SOCKET_GENERIC_PATTERN = "at java.net.";
  private List<TypedTrace> myTraces;

  public TraceView(final Project project, final List<Trace> notGrouped,
                   final List<PoolDescriptor> pools, final List<PoolDescriptor> similar,
                   final List<Trace> jdkThreads, final Trace edtTrace, DefaultActionGroup defaultActionGroup, final String contentId) {
    super(new BorderLayout());
    myProject = project;
    myMarked = new HashSet<Object>();
    myNotGrouped = notGrouped;
    myPools = pools;
    mySimilar = similar;
    myJdkThreads = jdkThreads;
    myEdtTrace = edtTrace;
    myDefaultActionGroup = defaultActionGroup;

    myCurrentFilter = new HashSet<Pair<TraceType, TraceCase>>();
    myCurrentFilter.add(Pair.create(TraceType.jdk, TraceCase.unknown));
    myCurrentFilter.add(Pair.create(TraceType.pool, TraceCase.unknown));

    initUi();
    processFilterResults(myCurrentFilter);
    final DefaultListModel model = (DefaultListModel) myNamesList.getModel();
    if (model.isEmpty()) {
      myCurrentFilter.clear();
      processFilterResults(myCurrentFilter);
    }
    createActions(contentId);
    updateDetails();
  }

  private void createActions(final String contentId) {
    final AnAction[] popup = {new MyEditDumpNameAction(contentId),
            new MyKeyUpAction(), new MyKeyDownAction(), new MyFilterAction(),
            new MyMarkAction(),
            new MyShowPoolThreads(),
            new ExportIntoText(),
            new MyDeleteLineAction()};
    myDefaultActionGroup.addAll(popup);

    final DefaultActionGroup popupGroup = new DefaultActionGroup();
    popupGroup.addAll(popup);
    myNamesList.addMouseListener(new PopupHandler() {
      @Override
      public void invokePopup(final Component component, final int x, final int y) {
        ActionPopupMenu popupMenu = ActionManager.getInstance()
                .createActionPopupMenu(ActionPlaces.UPDATE_POPUP, popupGroup);
        final JPopupMenu menu = popupMenu.getComponent();
        menu.show(component, x, y);
        menu.requestFocus();
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
    Disposer.register(myProject, myConsole);

    myNamesList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    fillNamesList();
    myNamesList.setCellRenderer(new MyNameListCellRenderer(myMarked));
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
        final TypedTrace previous = (TypedTrace) model.get(selectedIndices[0] - 1);
        final int anchor = myNamesList.getAnchorSelectionIndex();
        final int lead = myNamesList.getLeadSelectionIndex();

        modifyList(previous, (TypedTrace) model.get(selectedIndices[selectedIndices.length - 1]), false);

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
        final TypedTrace next = (TypedTrace) model.get(selectedIndices[selectedIndices.length - 1] + 1);
        final int anchor = myNamesList.getAnchorSelectionIndex();
        final int lead = myNamesList.getLeadSelectionIndex();

        modifyList(next, (TypedTrace) model.get(selectedIndices[0]), true);

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
      final List<String> names = new ArrayList<String>(selectedIndices.length);
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
          modifyList((TypedTrace) model.get(selectedIndice), null, false);
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
          modifyList(typedTrace, null, false);
          myNamesList.revalidate();
          myNamesList.repaint();
        }
      }
    }
    return false;
  }

  private void modifyList(@NotNull final TypedTrace delete, @Nullable final TypedTrace anchor, final boolean beforeAnchor) {
    if (anchor != null) {
      int newIdx = -1;
      for (int i = 0; i < myTraces.size(); i++) {
        final TypedTrace trace = myTraces.get(i);
        if (anchor.equals(trace)) {
          newIdx = beforeAnchor ? i : (i + 1);
          break;
        }
      }
      if (newIdx == -1) assert false;
      else {
        myTraces.add(newIdx, delete);
      }
    }
    myTraces.remove(delete);
  }

  private void precalculateDetails(final List<TypedTrace> list) {
    for (TypedTrace typedTrace : list) {
      final List<LineInfo> result = new ArrayList<LineInfo>();
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
      myFragments = new ArrayList<Pair<String, HyperlinkInfo>>();
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
      return ((PoolDescriptor) typedTrace.getT()).getTypicalTrace();
    }
  }

  private void fillNamesList() {
    final DefaultListModel model = (DefaultListModel) myNamesList.getModel();
    model.clear();

    int cnt = 0;
    myTraces = new ArrayList<TypedTrace>();
    for (Trace trace : myNotGrouped) {
      myTraces.add(new TypedTrace<Trace>(++cnt, TraceType.single, trace));
    }
    if (myEdtTrace != null) {
      myTraces.add(new TypedTrace<Trace>(++cnt, TraceType.edt, myEdtTrace));
    }
    for (PoolDescriptor pool : mySimilar) {
      myTraces.add(new TypedTrace<PoolDescriptor>(++cnt, TraceType.similar, pool));
    }
    for (PoolDescriptor pool : myPools) {
      myTraces.add(new TypedTrace<PoolDescriptor>(++cnt, TraceType.pool, pool));
    }
    for (Trace trace : myJdkThreads) {
      myTraces.add(new TypedTrace<Trace>(++cnt, TraceType.jdk, trace));
    }
    setCases(myTraces);
    precalculateDetails(myTraces);
    Collections.sort(myTraces, TypedTraceComparator.INSTANCE);

    for (TypedTrace typedTrace : myTraces) {
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
    private Set<Object> myMarked;
    private Color mySelectedMarked;
    private Color myMarkedColor;

    public MyNameListCellRenderer(final Set<Object> marked) {
      myMarked = marked;
      mySelectedMarked = new Color(0xB1C5FF);
      myMarkedColor = Color.orange;
    }

    @Override
    protected void customizeCellRenderer(final JList jList, final Object value, int index, boolean selected, boolean hasFocus) {
      if (value instanceof TypedTrace) {
        if (myMarked.contains(value)) {
          setBackground(selected ? mySelectedMarked : myMarkedColor);
        }
        final TraceType traceType = ((TypedTrace) value).getTraceType();
        if (TraceType.edt.equals(traceType)) {
          printClass("EDT");
          final Trace t = (Trace) ((TypedTrace) value).getT();
          setIcon(t.getCase().getIcon(t.isDaemon()));
          append(t.getThreadName(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
          printState(t);
        } else if (TraceType.jdk.equals(traceType)) {
          printClass("JDK");
          final Trace t = (Trace) ((TypedTrace) value).getT();
          setIcon(t.getCase().getIcon(t.isDaemon()));
          printTrace(t);
        }
        if (TraceType.pool.equals(traceType)) {
          printPool("POOL", (PoolDescriptor) ((TypedTrace) value).getT());
        }
        if (TraceType.similar.equals(traceType)) {
          printPool("SIMILAR", (PoolDescriptor) ((TypedTrace) value).getT());
        }
        if (TraceType.single.equals(traceType)) {
          final Trace t = (Trace) ((TypedTrace) value).getT();
          setIcon(t.getCase().getIcon(t.isDaemon()));
          final String clarifier = t.getCase().getClarifier();
          if (clarifier != null) {
            printClass(clarifier);
          }
          printTrace(t);
        }
      }
    }

    private void printClass(@NotNull final String name) {
      append("[" + name + "] ", SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES);
    }

    private void printPool(final String name, final PoolDescriptor d) {
      final Trace trace = d.getTypicalTrace();
      setIcon(trace.getCase().getIcon(trace.isDaemon()));
      printClass(name + ": " + d.getNumber());
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
    private boolean myIsVisible;
    private final int myId;
    private final TraceType myTraceType;
    private final T myT;
    private List<LineInfo> myDetails;

    public TypedTrace(final int id, final TraceType traceType, final T t) {
      myId = id;
      myTraceType = traceType;
      myT = t;
      myIsVisible = true;
    }

    public boolean isVisible() {
      return myIsVisible;
    }

    public void setVisible(final boolean isVisible) {
      myIsVisible = isVisible;
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

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final TypedTrace that = (TypedTrace) o;

      if (myId != that.myId) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return myId;
    }
  }

  public static enum TraceType {
    single, edt, similar, pool, jdk;
  }

  private static class TypedTraceComparator implements Comparator<TypedTrace> {
    public static TypedTraceComparator INSTANCE = new TypedTraceComparator();

    @Override
    public int compare(final TypedTrace o1, final TypedTrace o2) {
      int compareType = new Integer(o1.getTraceType().ordinal()).compareTo(o2.getTraceType().ordinal());
      if (compareType != 0 && ! TraceType.edt.equals(o1.getTraceType()) && ! TraceType.edt.equals(o2.getTraceType())) return compareType;

      final Trace trace1 = getTrace(o1);
      final Trace trace2 = getTrace(o2);

      if (TraceType.jdk.equals(o1.getTraceType()) && compareType == 0) {
        int compareOrderInGroup = 0;
        try {
          compareOrderInGroup = new Integer(trace1.getInitialOrderInGroup()).compareTo(trace2.getInitialOrderInGroup());
        } catch (Exception e) {
          e.printStackTrace();
        }
        if (compareOrderInGroup != 0) return compareOrderInGroup;
      }

      int compareCases = new Integer(trace1.getCase().ordinal()).compareTo(trace2.getCase().ordinal());
      if (compareCases != 0) return compareCases;

      int comparePri = new Integer(trace2.getPriority()).compareTo(trace1.getPriority());
      if (comparePri != 0) return comparePri;

      return trace1.getThreadName().compareToIgnoreCase(trace2.getThreadName());
    }
  }

  private static class MyEditDumpNameAction extends AnAction {
    private final String myContentId;

    public MyEditDumpNameAction(final String contentId) {
      super("Edit Dump Name", "Edit thread dump name", AllIcons.ToolbarDecorator.Edit);
      myContentId = contentId;
    }

    @Override
    public void actionPerformed(final AnActionEvent anActionEvent) {
      final Project project = PlatformDataKeys.PROJECT.getData(anActionEvent.getDataContext());
      if (project == null) return;

      final String text = Messages.showInputDialog(project, "Edit thread dump name:",
              ShowTraceViewAction.EXPLORE_STACK_TRACE,
              AllIcons.ToolbarDecorator.Edit, myContentId, new InputValidator() {
                @Override
                public boolean checkInput(final String s) {
                  return !StringUtil.isEmptyOrSpaces(s);
                }

                @Override
                public boolean canClose(final String s) {
                  return !StringUtil.isEmptyOrSpaces(s);
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
            if (myContentId.equals(content.getTabName())) {
              content.setDisplayName(text);
            }
          }
        }
      }
    }
  }

  private static class FilterSelector {
    private final JBCheckBox myJdk;
    private final JBCheckBox myPools;
    private final JBCheckBox mySocket;
//    private final JBCheckBox mySimilar;
    private final JPanel myPanel;
    private final JBCheckBox myIo;
    private final JBCheckBox myProcess;
    private final JBCheckBox myWaiting;
    private final JBCheckBox myTimedWaiting;
    private Runnable myOnOk;
    private final JButton myOk;

    public FilterSelector() {
      // todo define pools another way? also by checking it is just waiting inside scheduled executor?
      myJdk = new JBCheckBox("JDK");
      myPools = new JBCheckBox("Pools");
      mySocket = new JBCheckBox("Socket operation");
      myIo = new JBCheckBox("I/O operation");
      myProcess = new JBCheckBox("Waiting for process");

      myWaiting = new JBCheckBox("Waiting");
      myTimedWaiting = new JBCheckBox("Timed waiting");
//      mySimilar = new JBCheckBox("Similar");

      myOk = new JButton("Apply");
      myOk.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(final ActionEvent e) {
          myOnOk.run();
        }
      });
      final JPanel wrapper = new JPanel(new BorderLayout());
      wrapper.add(myOk, BorderLayout.EAST);

      final FormBuilder builder = FormBuilder.createFormBuilder()
              .addComponent(mySocket)
              .addComponent(myIo)
              .addComponent(myProcess)
              .addComponent(myWaiting)
              .addComponent(myTimedWaiting)
              .addComponent(myPools)
              .addComponent(myJdk)
              .addComponent(disabledCheckBox("Runnable"))
              .addComponent(disabledCheckBox("Blocked"))
//              .addComponent(mySimilar)
              .addLabeledComponent("", wrapper);
      myPanel = builder.getPanel();
      myPanel.addKeyListener(new KeyAdapter() {
        @Override
        public void keyReleased(final KeyEvent e) {
          if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_ENTER) {
            myOnOk.run();
          }
          super.keyReleased(e);
        }
      });
    }

    public JButton getOk() {
      return myOk;
    }

    private JBCheckBox disabledCheckBox(final String text) {
      final JBCheckBox jbCheckBox = new JBCheckBox(text);
      jbCheckBox.setEnabled(false);
      jbCheckBox.setSelected(true);
      return jbCheckBox;
    }

    public void setOnOk(@NotNull final Runnable onOk) {
      myOnOk = onOk;
    }

    public JBCheckBox getJdk() {
      return myJdk;
    }

    public JPanel getPanel() {
      return myPanel;
    }

    public void init(Set<Pair<TraceType, TraceCase>> hidden) {
      myJdk.setSelected(! hidden.contains(Pair.create(TraceType.jdk, TraceCase.unknown)));
      myPools.setSelected(! hidden.contains(Pair.create(TraceType.pool, TraceCase.unknown)));
      mySocket.setSelected(! hidden.contains(Pair.create(TraceType.single, TraceCase.socket)));
      myIo.setSelected(! hidden.contains(Pair.create(TraceType.single, TraceCase.io)));
      myProcess.setSelected(! hidden.contains(Pair.create(TraceType.single, TraceCase.waitingProcess)));
      myWaiting.setSelected(! hidden.contains(Pair.create(TraceType.single, TraceCase.paused)));
      myTimedWaiting.setSelected(! hidden.contains(Pair.create(TraceType.single, TraceCase.pausedTimed)));
    }

    public Set<Pair<TraceType, TraceCase>> getHiddenTypes() {
      final Set<Pair<TraceType, TraceCase>> set = new HashSet<Pair<TraceType, TraceCase>>();
      if (! myJdk.isSelected()) set.add(Pair.create(TraceType.jdk, TraceCase.unknown));
      if (! myPools.isSelected()) set.add(Pair.create(TraceType.pool, TraceCase.unknown));
      if (! mySocket.isSelected()) set.add(Pair.create(TraceType.single, TraceCase.socket));
      if (! myIo.isSelected()) set.add(Pair.create(TraceType.single, TraceCase.io));
      if (! myProcess.isSelected()) set.add(Pair.create(TraceType.single, TraceCase.waitingProcess));
      if (! myWaiting.isSelected()) {
        set.add(Pair.create(TraceType.single, TraceCase.paused));
        set.add(Pair.create(TraceType.similar, TraceCase.paused));
      }
      if (! myTimedWaiting.isSelected()) {
        set.add(Pair.create(TraceType.single, TraceCase.pausedTimed));
        set.add(Pair.create(TraceType.similar, TraceCase.pausedTimed));
      }
//      if (! mySimilar.isSelected()) set.add(Pair.create(TraceType.similar, TraceCase.unknown));
      return set;
    }
  }

  private class MyDeleteLineAction extends AnAction {
    public MyDeleteLineAction() {
      super("Delete", "Delete thread", com.intellij.icons.AllIcons.Actions.GC);
      registerCustomShortcutSet(new CustomShortcutSet(KeyEvent.VK_DELETE), myNamesList);
    }

    @Override
    public void update(@NotNull final AnActionEvent e) {
      final boolean enabled = myNamesList.getSelectedIndices().length > 0;
      e.getPresentation().setEnabled(enabled);
    }

    @Override
    public void actionPerformed(@NotNull final AnActionEvent anActionEvent) {
      deleteHandler();
    }
  }

  private class MyFilterAction extends AnAction {
    public MyFilterAction() {
      super("Show/Hide", "Show/hide threads or categories", AllIcons.General.Filter);
    }

    @Override
    public void update(@NotNull final AnActionEvent e) {
      // todo: tooltip
      super.update(e);
    }

    @Override
    public void actionPerformed(@NotNull final AnActionEvent anActionEvent) {
      final Project project = PlatformDataKeys.PROJECT.getData(anActionEvent.getDataContext());
      if (project == null) return;

      final FilterSelector filterSelector = new FilterSelector();
      filterSelector.init(myCurrentFilter);
      final JButton ok = filterSelector.getOk();
      final ComponentPopupBuilder builder = PopupFactoryImpl.getInstance()
              .createComponentPopupBuilder(filterSelector.getPanel(), filterSelector.getOk())
              .setTitle("Select categories to show:")
              .setModalContext(true)
              .setFocusable(true)
              .setFocusOwners(filterSelector.getPanel().getComponents())
              .setMovable(true)
              .setKeyEventHandler(new BooleanFunction<KeyEvent>() {
                @Override
                public boolean fun(final KeyEvent keyEvent) {
                  if (keyEvent.isControlDown() && keyEvent.getKeyCode() == KeyEvent.VK_ENTER) {
                    ok.doClick();
                    return true;
                  }
                  return false;
                }
              })
              .setCancelOnOtherWindowOpen(true)
              .setCancelOnClickOutside(true);
      final JBPopup popup = builder.createPopup();
      popup.setRequestFocus(true);
      filterSelector.setOnOk(new Runnable() {
        @Override
        public void run() {
          popup.closeOk(null);
          final Set<Pair<TraceType, TraceCase>> hiddenTypes = filterSelector.getHiddenTypes();
          myCurrentFilter = hiddenTypes;
          processFilterResults(hiddenTypes);
        }
      });
      popup.showInBestPositionFor(anActionEvent.getDataContext());
      popup.getContent().requestFocus();
      //popup.showInFocusCenter();
    }
  }

  private void processFilterResults(final Set<Pair<TraceType, TraceCase>> hiddenTypes) {
    for (TypedTrace trace : myTraces) {
      trace.setVisible(true);
    }
    final DefaultListModel model = (DefaultListModel) myNamesList.getModel();
    final Set selectedValuesList = new HashSet(Arrays.asList(myNamesList.getSelectedValues()));
    final Object anchorItem = selectedValuesList.isEmpty() ? null : model.get(myNamesList.getAnchorSelectionIndex());
    final Object leadItem = selectedValuesList.isEmpty() ? null : model.get(myNamesList.getLeadSelectionIndex());

    for (Pair<TraceType, TraceCase> pair : hiddenTypes) {
      for (TypedTrace trace : myTraces) {
        if (pair.getFirst().equals(trace.getTraceType())
                && (TraceCase.unknown.equals(pair.getSecond()) || pair.getSecond().equals(getTrace(trace).getCase()))) {
          trace.setVisible(false);
        }
      }
    }
    model.clear();
    int cnt = 0;
    final Set<Integer> selected = new HashSet<Integer>();
    int anchorIdx = -1;
    int leadIdx = -1;
    for (TypedTrace trace : myTraces) {
      if (trace.isVisible()) {
        model.add(cnt, trace);
        if (selectedValuesList.contains(trace)) {
          selected.add(cnt);
          if (anchorItem.equals(trace)) anchorIdx = cnt;
          if (leadItem.equals(trace)) leadIdx = cnt;
        }
        ++cnt;
      }
    }
    if (! selected.isEmpty()) {
      for (Integer idx : selected) {
        myNamesList.addSelectionInterval(idx, idx);
      }
      if (anchorIdx >= 0 && leadIdx >= 0) {
        myNamesList.addSelectionInterval(anchorIdx, leadIdx);
      }
    } else if (! model.isEmpty()) {
      myNamesList.addSelectionInterval(0, 0);
    }
    myNamesList.revalidate();
    myNamesList.repaint();
  }

  private class MyKeyUpAction extends AnAction {
    public MyKeyUpAction() {
      super("Move Up", "Move Up", AllIcons.ToolbarDecorator.MoveUp);
      registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_UP, KeyEvent.ALT_MASK | KeyEvent.SHIFT_MASK)), myNamesList);
    }

    @Override
    public void update(@NotNull final AnActionEvent e) {
      e.getPresentation().setEnabled(false);
      moveSomewhere(new Processor<int[]>() {
        @Override
        public boolean process(final int[] ints) {
          for (int anInt : ints) {
            if (anInt == 0) {
              return false;
            }
          }
          e.getPresentation().setEnabled(true);
          return false;
        }
      });
    }

    @Override
    public void actionPerformed(@NotNull final AnActionEvent anActionEvent) {
      moveUp();
    }
  }

  private class MyKeyDownAction extends AnAction {
    public MyKeyDownAction() {
      super("Move Down", "Move Down", AllIcons.ToolbarDecorator.MoveDown);
      registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, KeyEvent.ALT_MASK | KeyEvent.SHIFT_MASK)), myNamesList);
    }

    @Override
    public void update(@NotNull final AnActionEvent e) {
      e.getPresentation().setEnabled(false);
      moveSomewhere(new Processor<int[]>() {
        @Override
        public boolean process(final int[] ints) {
          for (int anInt : ints) {
            if (anInt == (myNamesList.getItemsCount() - 1)) {
              return false;
            }
          }
          e.getPresentation().setEnabled(true);
          return false;
        }
      });
    }

    @Override
    public void actionPerformed(@NotNull final AnActionEvent anActionEvent) {
      moveDown();
    }
  }

  private class MyShowPoolThreads extends AnAction {
    public MyShowPoolThreads() {
      super("Show Threads Inside", "Show grouped threads", AllIcons.Actions.Preview);
    }

    @Override
    public void update(@NotNull final AnActionEvent e) {
      final TypedTrace trace = getSingleTrace();
      e.getPresentation().setEnabled(trace != null &&
              (TraceType.pool.equals(trace.getTraceType()) || TraceType.similar.equals(trace.getTraceType())));
    }

    private TypedTrace getSingleTrace() {
      final int[] selectedIndices = myNamesList.getSelectedIndices();
      if (selectedIndices.length != 1) {
        return null;
      }
      final DefaultListModel model = (DefaultListModel) myNamesList.getModel();
      return (TypedTrace) model.get(selectedIndices[0]);
    }

    @Override
    public void actionPerformed(@NotNull final AnActionEvent anActionEvent) {
      final TypedTrace trace = getSingleTrace();
      if (trace == null) return;
      final PoolDescriptor descriptor = (PoolDescriptor) trace.getT();
      final List<String> names = descriptor.getPresentations();
      final ListPopup listPopup = JBPopupFactory.getInstance().createListPopup(
              new BaseListPopupStep<String>("Threads inside '" + descriptor.getTemplateName() + "'", names));
      listPopup.showInFocusCenter();
    }
  }

  private class MyMarkAction extends AnAction {

    private static final String MARK = "Mark";

    public MyMarkAction() {
      super(MARK, MARK, AllIcons.Ide.Rating);
      registerCustomShortcutSet(new CustomShortcutSet(KeyEvent.VK_SPACE), myNamesList);
    }

    @Override
    public void update(@NotNull final AnActionEvent e) {
      final Presentation presentation = e.getPresentation();
      presentation.setEnabled(false);
      presentation.setText(MARK);
      presentation.setDescription(MARK);
      final int[] selectedIndices = myNamesList.getSelectedIndices();
      if (selectedIndices.length != 1) {
        return;
      }
      presentation.setEnabled(true);
      final DefaultListModel model = (DefaultListModel) myNamesList.getModel();
      final Object o = model.get(selectedIndices[0]);
      final String text = myMarked.contains(o) ? "Clear Mark" : MARK;
      presentation.setText(text);
      presentation.setDescription(text);
    }

    @Override
    public void actionPerformed(@NotNull final AnActionEvent anActionEvent) {
      final int[] selectedIndices = myNamesList.getSelectedIndices();
      if (selectedIndices.length != 1) {
        return;
      }

      final DefaultListModel model = (DefaultListModel) myNamesList.getModel();
      for (int selectedIndice : selectedIndices) {
        final Object o = model.get(selectedIndice);
        if (! myMarked.remove(o)) {
          myMarked.add(o);
        }
      }
    }
  }

  // todo header, footer?
  private class ExportIntoText extends AnAction {
    public ExportIntoText() {
      super("Copy Dump To Clipboard", "Copy Dump To Clipboard", AllIcons.Actions.Export);
    }

    @Override
    public void actionPerformed(@NotNull final AnActionEvent anActionEvent) {
      final StringBuilder sb = new StringBuilder();
      final DefaultListModel model = (DefaultListModel) myNamesList.getModel();
      for (int i = 0; i < model.size(); i++) {
        final TypedTrace typed = (TypedTrace) model.get(i);
        if (! TraceType.pool.equals(typed.getTraceType()) && ! TraceType.similar.equals(typed.getTraceType())) {
          printTrace(sb, (Trace) typed.getT());
        } else {
          final PoolDescriptor poolDescriptor = (PoolDescriptor) typed.getT();
          printTrace(sb, poolDescriptor.getTypicalTrace());
          for (Trace trace : poolDescriptor.getOtherTraces()) {
            printTrace(sb, trace);
          }
        }
      }
      final Transferable content = new StringSelection(sb.toString());
      UIUtil.invokeLaterIfNeeded(new Runnable() {
        @Override
        public void run() {
          CopyPasteManager.getInstance().setContents(content);
        }
      });
    }

    private void printTrace(@NotNull final StringBuilder sb, @NotNull final Trace trace) {
      sb.append(trace.getFirstLine()).append("\n");
      if (trace.getStateWords() != null) {
        sb.append("   java.lang.Thread.State: ");
        sb.append(trace.getStateWords());
        sb.append("\n");
      }

      for (String s : trace.getTrace()) {
        sb.append("        ").append(s).append("\n");
      }
      sb.append("\n");
    }
  }
}
