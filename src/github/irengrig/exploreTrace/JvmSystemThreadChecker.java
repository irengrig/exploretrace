package github.irengrig.exploreTrace;

import com.google.common.collect.ImmutableSet;

import java.util.Set;

/**
 * Created by Irina.Chernushina on 8/17/2014.
 * for Oracle jdk
 */
public class JvmSystemThreadChecker {
  private final static Set<String> ourThreadNames = new ImmutableSet.Builder<String>()
          .add("AWT-Windows")
          .add("AWT-Shutdown")
          .add("Java2D Disposer")
          .add("Finalizer")
          .add("Reference Handler")
          .build();

  public static boolean isJvmThread(final Trace t) {
    if (t.getTrace().isEmpty()) return true;
    return ourThreadNames.contains(t.getThreadName());
  }

  public static boolean isEdt(final Trace t) {
    return t.getThreadName().startsWith("AWT-EventQueue");
  }
}
