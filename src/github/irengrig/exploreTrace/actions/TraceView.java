package github.irengrig.exploreTrace.actions;

import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.filters.OpenFileHyperlinkInfo;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.unscramble.ThreadOperation;
import com.intellij.unscramble.ThreadState;
import github.irengrig.exploreTrace.Trace;
import github.irengrig.exploreTrace.TracesClassifier;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.*;
import java.util.List;

import static com.intellij.icons.AllIcons.Debugger.ThreadStates.*;

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
                   final List<Trace> jdkThreads, final Trace edtTrace, DefaultActionGroup defaultActionGroup) {
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
    updateDetails();
  }

  public JBList getNamesList() {
    return myNamesList;
  }

  public void initUi() {
    mySplitter = new Splitter(false, 0.3f);
    myNamesList = new JBList(new DefaultListModel());
    final TextConsoleBuilder builder = TextConsoleBuilderFactory.getInstance().createBuilder(myProject);
    myConsole = builder.getConsole();

    myNamesList.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
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

      /*final String join = StringUtil.join(t.getTrace(), "\n");
      myConsole.print(StringUtil.join(new String[]{t.getFirstLine(), join}, "\n"), ConsoleViewContentType.ERROR_OUTPUT);*/
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
      trace.setCase(TraceCase.identify(trace));
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

  private static enum TraceType {
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

      int compareCases = Integer.compare(trace1.getCase().ordinal(), trace2.getCase().ordinal());
      if (compareCases != 0) return compareCases;

      int comparePri = Integer.compare(trace2.getPriority(), trace1.getPriority());
      if (comparePri != 0) return comparePri;

      return trace1.getThreadName().compareToIgnoreCase(trace2.getThreadName());
    }
  }
}
