package github.irengrig.exploreTrace;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Created by Irina.Chernushina on 8/17/2014.
 */
public class TracesSimilarChecker {
  /*public static boolean compare(@NotNull final Trace t1, @NotNull final Trace t2) {
    if (! t1.getState().equals(t2.getState())) return false;
    if (! Objects.equals(t1.getStateWords(), t2.getStateWords())) return false;

    final List<String> trace1 = t1.getTrace();
    final List<String> trace2 = t2.getTrace();
    if (trace1.size() != trace2.size()) return false;

    for (int i = 0; i < trace1.size(); i++) {
      final String s = trace1.get(i);
      if (s.trim().startsWith("at")) {
        if (! s.trim().equals(trace2.get(i).trim())) return false;
      } else {
        if (! replaceRef(s.trim()).equals(replaceRef(trace2.get(i).trim()))) return false;
      }
    }
    return true;
  }*/

  public static Pair<String, List<String>> cutRefs(final Trace trace) {
    final List<String> withReplaced = new ArrayList<>();
    for (String s : trace.getTrace()) {
      if (s.trim().startsWith("at ")) {
        withReplaced.add(s);
      } else {
        withReplaced.add(replaceRef(s));
      }
    }
    return Pair.create(replaceRef(trace.getStateWords()), withReplaced);
  }

  private static String replaceRef(final String in) {
    //on java.util.concurrent.locks.AbstractQueuedSynchronizer$ConditionObject@2a4dac22
    if (in == null) return null;
    final int start = in.indexOf("<0x");
    if (start != -1) {
      final int end = in.indexOf('>', start + 1);
      if (end == -1 || (end == in.length() - 1)) return in;
      return in.substring(0, start) + in.substring(end + 1);
    }
    final int startAt = in.indexOf("@");
    if (startAt == -1) return in;
    final int end = in.indexOf(' ', startAt + 1);
    final String numStr = in.substring(startAt + 1, end == -1 ? in.length() : end);
    try {
      Integer.parseInt(numStr, 16);
      return in.substring(0, startAt) + ((end == -1) ? "" : in.substring(end + 1));
    } catch (NumberFormatException e) {
      return in;
    }
  }
}
