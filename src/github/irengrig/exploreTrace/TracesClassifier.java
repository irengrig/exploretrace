package github.irengrig.exploreTrace;

import com.google.common.base.Strings;
import com.intellij.util.containers.MultiMap;

import java.util.*;

/**
 * Created by Irina.Chernushina on 7/21/2014.
 */
public class TracesClassifier {
  private final List<Trace> myInTraces;

  private final List<Trace> myJdkThreads;
  private final List<PoolDescriptor> myPools;
  private final List<Trace> myNotGrouped;

  public TracesClassifier(final List<Trace> inTraces) {
    myInTraces = inTraces;
    myJdkThreads = new ArrayList<>();
    myPools = new ArrayList<>();
    myNotGrouped = new ArrayList<>();
  }

  public void execute() {
    final MultiMap<Integer, Trace> map = new MultiMap<>();
    for (Trace inTrace : myInTraces) {
      final int hash = traceHash(inTrace);
      map.putValue(hash, inTrace);
    }

    final Set<Map.Entry<Integer, Collection<Trace>>> entries = map.entrySet();
    final Iterator<Map.Entry<Integer, Collection<Trace>>> iterator = entries.iterator();
    while (iterator.hasNext()) {
      final Map.Entry<Integer, Collection<Trace>> entry = iterator.next();
      if (entry.getValue().size() == 1) {
        iterator.remove();
        myNotGrouped.add(entry.getValue().iterator().next());
      } else {
        final Collection<Trace> traces = entry.getValue();
        final List<String> names = new ArrayList<>();
        for (Trace trace : traces) {
          names.add(trace.getThreadName());
        }
        String poolName = commonStart(names);
        // todo invent another way
        poolName = poolName == null ? names.get(0) : poolName;
        final Trace first = traces.iterator().next();
        final PoolDescriptor descriptor = new PoolDescriptor(first, entry.getKey(), names);
        descriptor.setNumber(traces.size());
        descriptor.setTemplateName(poolName);
        myPools.add(descriptor);
      }
    }
  }

  private String commonStart(final List<String> list) {
    assert list.size() > 1;
    String candidate = list.get(0);
    for (int i = 1; i < list.size(); i++) {
      final String s = list.get(i);
      candidate = Strings.commonPrefix(candidate, s);
      if (candidate.isEmpty()) return null;
    }
    return candidate;
  }

  // todo rewrite with equals
  private int traceHash(final Trace trace) {
    int result = trace.getStateWords() != null ? trace.getStateWords().hashCode() : 0;
    result = 31 * result + (trace.isDaemon() ? 1 : 0);
    final List<String> stacks = trace.getTrace();
    result = 31 * result + (stacks != null ? stacks.hashCode() : 0);
    return result;
  }

  public static class PoolDescriptor {
    private final int myHash;
    private String myTemplateName;
    private int myNumber;
    private final Trace myTypicalTrace;
    private final List<String> myNames;

    public PoolDescriptor(final Trace typicalTrace, final int hash, final List<String> names) {
      myTypicalTrace = typicalTrace;
      myHash = hash;
      myNames = names;
    }

    public List<String> getNames() {
      return myNames;
    }

    public String getTemplateName() {
      return myTemplateName;
    }

    public void setTemplateName(final String templateName) {
      myTemplateName = templateName;
    }

    public int getNumber() {
      return myNumber;
    }

    public void setNumber(final int number) {
      myNumber = number;
    }

    public int getHash() {
      return myHash;
    }

    public Trace getTypicalTrace() {
      return myTypicalTrace;
    }
  }

  public List<Trace> getJdkThreads() {
    return myJdkThreads;
  }

  public List<PoolDescriptor> getPools() {
    return myPools;
  }

  public List<Trace> getNotGrouped() {
    return myNotGrouped;
  }
}
