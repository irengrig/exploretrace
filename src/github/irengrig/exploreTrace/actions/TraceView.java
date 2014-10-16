package github.irengrig.exploreTrace.actions;

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

  private void updateDetails() {
    myConsole.clear();
    final int idx = myNamesList.getSelectedIndex();
    if (idx >= 0) {
      final TypedTrace elementAt = (TypedTrace) ((DefaultListModel) myNamesList.getModel()).getElementAt(idx);
      final Trace t;
      if (! TraceType.pool.equals(elementAt.getTraceType()) && ! TraceType.similar.equals(elementAt.getTraceType())) {
        t = (Trace) elementAt.getT();
      } else {
        t = ((TracesClassifier.PoolDescriptor) elementAt.getT()).getTypicalTrace();
      }
      final String join = StringUtil.join(t.getTrace(), "\n");
      myConsole.print(StringUtil.join(new String[]{t.getFirstLine(), join}, "\n"), ConsoleViewContentType.ERROR_OUTPUT);
    }
    myConsole.scrollTo(0);
  }

  private void fillNamesList() {
    final DefaultListModel model = (DefaultListModel) myNamesList.getModel();
    model.clear();
    // todo here should be an order defined

    for (Trace trace : myNotGrouped) {
      model.addElement(new TypedTrace<>(TraceType.single, trace));
    }
    if (myEdtTrace != null) {
      model.addElement(new TypedTrace<>(TraceType.jdk, myEdtTrace));
    }
    for (TracesClassifier.PoolDescriptor pool : mySimilar) {
      model.addElement(new TypedTrace<>(TraceType.similar, pool));
    }
    for (TracesClassifier.PoolDescriptor pool : myPools) {
      model.addElement(new TypedTrace<>(TraceType.pool, pool));
    }
    for (Trace trace : myJdkThreads) {
      model.addElement(new TypedTrace<>(TraceType.jdk, trace));
    }
    if (! model.isEmpty()) {
      myNamesList.setSelectedIndex(0);
    }
    myNamesList.revalidate();
    myNamesList.repaint();
  }

  private static class MyNameListCellRenderer extends ColoredListCellRenderer {
    @Override
    protected void customizeCellRenderer(final JList jList, final Object value, final int i, final boolean b, final boolean b1) {
      if (value instanceof TypedTrace) {
        if (TraceType.jdk.equals(((TypedTrace) value).getTraceType())) {
          append("[JDK] ", SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES);
          final Trace t = (Trace) ((TypedTrace) value).getT();
          final Thread.State state = t.getState();
          setIconByState(state, t);
          printTrace(t);
        }
        if (TraceType.pool.equals(((TypedTrace) value).getTraceType())) {
          printPool("POOL", (TracesClassifier.PoolDescriptor) ((TypedTrace) value).getT());
        }
        if (TraceType.similar.equals(((TypedTrace) value).getTraceType())) {
          printPool("SIMILAR", (TracesClassifier.PoolDescriptor) ((TypedTrace) value).getT());
        }
        if (TraceType.single.equals(((TypedTrace) value).getTraceType())) {
          final Trace t = (Trace) ((TypedTrace) value).getT();
          final Thread.State state = t.getState();
          setIconByState(state, t);
          printTrace(t);
        }
      }
    }

    private void printPool(final String name, final TracesClassifier.PoolDescriptor d) {
      final Trace trace = d.getTypicalTrace();
      setIconByState(trace.getState(), trace);
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

    // todo cache!!
    private void setIconByState(final Thread.State state, final Trace trace) {
      if (Thread.State.BLOCKED.equals(state)) {
        setIcon(Locked);
      } else if (Thread.State.RUNNABLE.equals(state)) {
        if (isSocket(state, trace)) {
          setIcon(Socket);
        } else {
          setIcon(Running);
        }
      } else if (Thread.State.WAITING.equals(state) || Thread.State.TIMED_WAITING.equals(state)) {
        setIcon(Paused);
      } else {
        setIcon(AllIcons.Debugger.KillProcess);
      }
    }
  }

  private final static Set<String> SOCKET_PATTERNS;
  static {
    final Set<String> set = new HashSet<>();
    addToSocketPattern(set, "PlainSocketImpl.socketAccept(PlainSocketImpl.java:");
    addToSocketPattern(set, "PlainDatagramSocketImpl.receive(PlainDatagramSocketImpl.java:");
    addToSocketPattern(set, "SocketInputStream.socketRead(SocketInputStream.java");
    addToSocketPattern(set, "PlainSocketImpl.socketConnect(PlainSocketImpl.java");
    addToSocketPattern(set, "ServerSocket.accept(ServerSocket.java");
    SOCKET_PATTERNS = Collections.unmodifiableSet(set);
  }
  private static final int socketPattenLen = 31;
  private static void addToSocketPattern(final Set<String> set, final String value) {
    set.add(value.substring(0, socketPattenLen));
  }
  private static boolean isSocket(final Thread.State state, final Trace trace) {
    final List<String> list = trace.getTrace();
    // nothing interesting in first 2 lines
    for (int i = 2; i < list.size(); i++) {
      final String trim = list.get(i).trim();
      if (trim.startsWith(SOCKET_GENERIC_PATTERN)) {
        final String substring = trim.substring(SOCKET_GENERIC_PATTERN.length(), Math.min(SOCKET_GENERIC_PATTERN.length() + socketPattenLen, trim.length()));
        if (SOCKET_PATTERNS.contains(substring)) {
          return true;
        }
      }
    }
    return false;
  }

  private static final Icon PAUSE_ICON_DAEMON = new LayeredIcon(Paused, Daemon_sign);
  private static final Icon LOCKED_ICON_DAEMON = new LayeredIcon(Locked, Daemon_sign);
  private static final Icon RUNNING_ICON_DAEMON = new LayeredIcon(Running, Daemon_sign);
  private static final Icon SOCKET_ICON_DAEMON = new LayeredIcon(Socket, Daemon_sign);
  private static final Icon IDLE_ICON_DAEMON = new LayeredIcon(Idle, Daemon_sign);
  private static final Icon EDT_BUSY_ICON_DAEMON = new LayeredIcon(EdtBusy, Daemon_sign);
  private static final Icon IO_ICON_DAEMON = new LayeredIcon(IO, Daemon_sign);

  private static Icon getThreadStateIcon(final ThreadState threadState) {

    final boolean daemon = threadState.isDaemon();
    if (threadState.isSleeping()) {
      return daemon ? PAUSE_ICON_DAEMON : Paused;
    }
    if (threadState.isWaiting()) {
      return daemon ? LOCKED_ICON_DAEMON : Locked;
    }
    if (threadState.getOperation() == ThreadOperation.Socket) {
      return daemon ? SOCKET_ICON_DAEMON : Socket;
    }
    if (threadState.getOperation() == ThreadOperation.IO) {
      return daemon ? IO_ICON_DAEMON : IO;
    }
    if (threadState.isEDT()) {
      if ("idle".equals(threadState.getThreadStateDetail())) {
        return daemon ? IDLE_ICON_DAEMON : Idle;
      }
      return daemon ? EDT_BUSY_ICON_DAEMON : EdtBusy;
    }
    return daemon ? RUNNING_ICON_DAEMON : Running;
  }

  /*private void updateThreadList() {
    String text = myFilterPanel.isVisible() ? myFilterField.getText() : "";
    DefaultListModel model = (DefaultListModel)myThreadList.getModel();
    model.clear();
    for (ThreadState state : myThreadDump) {
      if (StringUtil.containsIgnoreCase(state.getStackTrace(), text) || StringUtil.containsIgnoreCase(state.getName(), text)) {
        //noinspection unchecked
        model.addElement(state);
      }
    }
    if (!model.isEmpty()) {
      myThreadList.setSelectedIndex(0);
    }
    myThreadList.revalidate();
    myThreadList.repaint();
  }*/

  @Override
  public void calcData(final DataKey dataKey, final DataSink dataSink) {

  }

  private static class TypedTrace<T> {
    private final TraceType myTraceType;
    private final T myT;

    public TypedTrace(final TraceType traceType, final T t) {
      myTraceType = traceType;
      myT = t;
    }

    public TraceType getTraceType() {
      return myTraceType;
    }

    public T getT() {
      return myT;
    }
  }

  private static enum TraceType {
    single, pool, jdk, similar
  }
}
