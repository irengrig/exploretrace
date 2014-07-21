package github.irengrig.exploreTrace;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Created by Irina.Chernushina on 7/11/2014.
 */
public class TraceCreator {
  public static final String PRIO = "prio=";
  private final TraceReader myRead;
  private int myLineWidthCorrection;
  private final List<List<String>> myCorrectedTraces;
  private final List<Trace> myCreatedTraces;

  public TraceCreator(TraceReader read) {
    myRead = read;
    myLineWidthCorrection = 0;
    myCorrectedTraces = new ArrayList<>();
    myCreatedTraces = new ArrayList<>();
  }

  public void createTraces() {
    findCorrection();
    doCorrection();
    createTraceObjects();
  }

  public List<Trace> getCreatedTraces() {
    return myCreatedTraces;
  }

  /*
    "JobScheduler FJ pool 0/8" daemon prio=6 tid=0x4421d800 nid=0x23cc waiting on condition [0x3b11f000]
     java.lang.Thread.State: TIMED_WAITING (parking)
          at sun.misc.Unsafe.park(Native Method)
          - parking to wait for  <0x12fe8970> (a jsr166e.ForkJoinPool)
          at jsr166e.ForkJoinPool.awaitWork(ForkJoinPool.java:1756)
          at jsr166e.ForkJoinPool.scan(ForkJoinPool.java:1694)
          at jsr166e.ForkJoinPool.runWorker(ForkJoinPool.java:1642)
          at jsr166e.ForkJoinWorkerThread.run(ForkJoinWorkerThread.java:109)


          - locked <0x130a2e60> (a io.netty.channel.nio.SelectedSelectionKeySet)
  */
  private void createTraceObjects() {
    for (List<String> trace : myCorrectedTraces) {
      if (trace.isEmpty()) continue;
      final Trace.Builder builder = new Trace.Builder();
      parseFirstLine(trace.get(0), builder);
      boolean stateFound = false;
      if (trace.size() > 1) {
        stateFound = parseState(trace.get(1), builder);
      }
      if (stateFound && trace.size() > 2) {
        createTrace(trace.subList(2, trace.size()), builder);
      } else if (! stateFound) createTrace(trace.subList(1, trace.size()), builder);
      myCreatedTraces.add(builder.create());
    }
  }

  private void createTrace(List<String> strings, Trace.Builder builder) {
    builder.setTrace(strings);
  }

  private boolean parseState(String s, final Trace.Builder builder) {
    final int idx = s.indexOf(StartsFinder.ThreadState);
    if (idx == -1) return false;
    String sub = s.substring(idx + StartsFinder.ThreadState.length());
    if (! sub.startsWith(":")) return false;
    final int idxState = sub.indexOf(' ', 1);
    if (idxState > 0) {
      try {
        final Thread.State state = Thread.State.valueOf(sub.substring(1, idxState).trim());
        builder.setState(state);
        builder.setStateWords(sub.substring(1).trim());
        return true;
      } catch (IllegalArgumentException e) {
        return false;
      }
    }
    return false;
  }

  private void parseFirstLine(String s, final Trace.Builder builder) {
    s = s.trim();
    if (! s.startsWith("\"")) return;
    final int idx = s.indexOf('"', 1);
    if (idx > 0) {
      builder.setThreadName(s.substring(1, idx));
      s = s.substring(idx + 1);
    }
    // todo or cut in words
    if (s.contains("daemon")) builder.setIsDaemon(true);
    final int prioIdx = s.indexOf(PRIO);
    if (prioIdx > 0) {
      final int spaceIdx = s.indexOf(' ', prioIdx + PRIO.length());
      if (spaceIdx > 0) {
        try {
          int prio = Integer.parseInt(s.substring(prioIdx + PRIO.length(), spaceIdx));
          builder.setPriority(prio);
        } catch (NumberFormatException e) {
          //
        }
      }
    }
  }

  private void doCorrection() {
    for (String s : myRead.getTraces()) {
      final List<String> traceLines = new ArrayList<>();
      myCorrectedTraces.add(traceLines);
      final String[] lines = s.split("\n");
      int previousLineWidth = -1;
      for (String line : lines) {
        if (StartsFinder.isStart(line)) {
          traceLines.add(line);
        } else {
          if (previousLineWidth >= 0 && myLineWidthCorrection == previousLineWidth) {
            final String was = traceLines.get(traceLines.size() - 1);
            traceLines.set(traceLines.size() - 1, was + line);
          } else {
            traceLines.add(line);
          }
        }
        previousLineWidth = line.length();
      }
    }
  }

  private void findCorrection() {
    for (String s : myRead.getTraces()) {
      final String[] lines = s.split("\n");
      int previousLineWidth = -1;
      for (String line : lines) {
        if (! StartsFinder.isStart(line)) {
          if (previousLineWidth < 0) continue;
          myLineWidthCorrection = previousLineWidth;
          break;
        }
        previousLineWidth = line.length();
      }
      if (myLineWidthCorrection > 0) break;
    }
  }

  private static class StartsFinder {
    private final static String QUOTES = "\"";
    private final static String ThreadState = "java.lang.Thread.State";
    private final static String AT = "at ";
    private final static String MINUS = "- ";
    private final static Set<String> POSSIBLE_STARTS = new HashSet<>(Arrays.asList(QUOTES, ThreadState, AT, MINUS));

    public static boolean isStart(final String line) {
      final String trim = line.trim();
      for (String possibleStart : POSSIBLE_STARTS) {
        if (trim.startsWith(possibleStart)) return true;
      }
      return false;
    }
  }
}
