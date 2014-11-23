package github.irengrig.exploreTrace.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ui.LayeredIcon;
import github.irengrig.exploreTrace.JvmSystemThreadChecker;
import github.irengrig.exploreTrace.Trace;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.intellij.icons.AllIcons.Debugger.ThreadStates.*;

/**
 * Created by ira on 21.10.2014.
 */
public enum TraceCase {
    runnable(Running, null),
    blocked(Locked, null),
    edtBusy(EdtBusy, null),
    edtIdle(Idle, null),
    paused(Paused, null),
    pausedTimed(Paused, null),
    socket(Socket, "Socket"),
    io(IO, "I/O"),
    waitingProcess(AllIcons.RunConfigurations.Application, "Waiting for process"),
    unknown(AllIcons.Debugger.KillProcess, null);

    private final Icon myIcon;
    private final Icon myDaemonIcon;
    @Nullable
    private final String myClarifier;

    TraceCase(final Icon icon, final String clarifier) {
        myIcon = icon;
        myClarifier = clarifier;
        myDaemonIcon = new LayeredIcon(icon, Daemon_sign);
    }

    @Nullable
    public String getClarifier() {
        return myClarifier;
    }

    public Icon getIcon() {
        return myIcon;
    }

    public Icon getDaemonIcon() {
        return myDaemonIcon;
    }

    public Icon getIcon(final boolean isDaemon) {
        return isDaemon ? getDaemonIcon() : getIcon();
    }

    public static TraceCase identify(final TraceView.TraceType traceType, final Trace trace) {
        if (JvmSystemThreadChecker.isEdt(trace)) {
            if (trace.getStateWords().contains("idle")) {
                return edtIdle;
            }
            return edtBusy;
        }
        final Thread.State state = trace.getState();
        if (Thread.State.BLOCKED.equals(state)) {
            return blocked;
        } else if (Thread.State.RUNNABLE.equals(state)) {
            if (isSocket(trace)) {
                return socket;
            } else if (isIOOperation(trace)) {
                return io;
            } else if (isWaitingForProcess(trace)) {
                return waitingProcess;
            } else {
                return runnable;
            }
        } else if (Thread.State.WAITING.equals(state)) {
            return paused;
        } else if (Thread.State.TIMED_WAITING.equals(state)) {
            return pausedTimed;
        } else {
            if (TraceView.TraceType.jdk.equals(traceType) && trace.getFirstLine() != null && trace.getFirstLine().contains("runnable")) {
                return runnable;
            }
            return unknown;
        }
    }

    @NonNls
    private static final String SOCKET_GENERIC_PATTERN = "at java.net.";
    private final static Set<String> SOCKET_PATTERNS;
    static {
        final Set<String> set = new HashSet<>();
        addToSocketPattern(set, "PlainSocketImpl.socketAccept(PlainSocketImpl.java:");
        addToSocketPattern(set, "PlainDatagramSocketImpl.receive(PlainDatagramSocketImpl.java:");
        addToSocketPattern(set, "PlainDatagramSocketImpl.receive0(Native Method");
        addToSocketPattern(set, "SocketInputStream.socketRead(SocketInputStream.java");
        addToSocketPattern(set, "SocketInputStream.read(SocketInputStream.java");
        addToSocketPattern(set, "PlainSocketImpl.socketConnect(PlainSocketImpl.java");
        addToSocketPattern(set, "ServerSocket.accept(ServerSocket.java");
        SOCKET_PATTERNS = Collections.unmodifiableSet(set);
    }
    private static final int socketPattenLen = 31;
    private static void addToSocketPattern(final Set<String> set, final String value) {
        set.add(value.substring(0, socketPattenLen));
    }
    private static boolean isSocket(final Trace trace) {
        final List<String> list = trace.getTrace();
        for (String aList : list) {
            final String trim = aList.trim();
            if (trim.startsWith(SOCKET_GENERIC_PATTERN)) {
                final String substring = trim.substring(SOCKET_GENERIC_PATTERN.length(), Math.min(SOCKET_GENERIC_PATTERN.length() + socketPattenLen, trim.length()));
                if (SOCKET_PATTERNS.contains(substring)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isWaitingForProcess(final Trace trace) {
        final List<String> list = trace.getTrace();
        for (String aList : list) {
            final String trim = aList.trim();
            if (trim.startsWith("at java.lang.ProcessImpl.waitFor(")) {
                return true;
            } else if (trim.startsWith("at java.lang.UNIXProcess.waitForProcessExit(Native Method)")) {
                return true;
            }
        }
        return false;
    }

    private final static Set<String> IO_PATTERNS;
    static {
        final Set<String> set = new HashSet<>();
        set.add("java.nio.channels.Selector.select(");
        set.add("java.io.FileInputStream.read(");
        set.add("java.io.FileInputStream.readBytes(");
        set.add("sun.nio.ch.SelectorImpl.select(");
        IO_PATTERNS = Collections.unmodifiableSet(set);
    }
    private static boolean isIOOperation(final Trace trace) {
        final List<String> list = trace.getTrace();
        for (String aList : list) {
            final String trim = aList.trim();
            if (trim.startsWith("at ")) {
                int idxBrace = trim.indexOf("(");
                if (idxBrace > 0 && IO_PATTERNS.contains(trim.substring(3, idxBrace + 1))) {
                    return true;
                }
            }
        }
        return false;
    }
}
