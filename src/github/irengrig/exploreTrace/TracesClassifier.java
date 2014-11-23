package github.irengrig.exploreTrace;

import com.google.common.base.Strings;
import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.MultiMap;

import java.util.*;

/**
 * Created by Irina.Chernushina on 7/21/2014.
 */
public class TracesClassifier {
  private final List<Trace> myInTraces;

  private final List<Trace> myJdkThreads;
  private Trace myEdtTrace;
  private final List<PoolDescriptor> myPools;
  private final List<PoolDescriptor> mySimilar;
  private final List<Trace> myNotGrouped;

  public TracesClassifier(final List<Trace> inTraces) {
    myInTraces = inTraces;
    myJdkThreads = new ArrayList<>();
    myPools = new ArrayList<>();
    mySimilar = new ArrayList<>();
    myNotGrouped = new ArrayList<>();
  }

  public void execute() {
    filterOutJdk();
    filterOutPools();
    filterOutSimilar();
    myNotGrouped.addAll(myInTraces);
    adjustJdk();
  }

  private void adjustJdk() {
    int cnt = 0;
    for (Trace jdkThread : myJdkThreads) {
      if (jdkThread.getFirstLine() != null && jdkThread.getFirstLine().contains("waiting on condition")) {
        jdkThread.setState(Thread.State.WAITING);
        jdkThread.setStateWords("WAITING (waiting on condition)");
      }
      jdkThread.setInitialOrderInGroup(cnt);
      ++ cnt;
    }
  }

  private void filterOutPools() {
    final List<Trace> list = new ArrayList<>();
    final MultiMap<Pair<String, List<String>>, Trace> map = new MultiMap<>();
    for (Trace trace : myInTraces) {
      map.putValue(Pair.create(trace.getStateWords(), trace.getTrace()), trace);
    }

    final Set<Map.Entry<Pair<String, List<String>>, Collection<Trace>>> entries = map.entrySet();
    for (final Map.Entry<Pair<String, List<String>>, Collection<Trace>> entry : entries) {
      if (entry.getValue().size() == 1) {
        list.add(entry.getValue().iterator().next());
      } else {
        final PoolDescriptor descriptor = groupThreads(entry.getValue());
        myPools.add(descriptor);
      }
    }
    setLeftover(list);
  }

  private void filterOutSimilar() {
    final List<Trace> list = new ArrayList<>();
    final MultiMap<Pair<String, List<String>>, Trace> mapForSimilar = new MultiMap<>();
    for (Trace trace : myInTraces) {
      mapForSimilar.putValue(TracesSimilarChecker.cutRefs(trace), trace);
    }
    final Set<Map.Entry<Pair<String, List<String>>, Collection<Trace>>> set = mapForSimilar.entrySet();
    final Iterator<Map.Entry<Pair<String, List<String>>, Collection<Trace>>> entryIterator = set.iterator();
    while (entryIterator.hasNext()) {
      final Map.Entry<Pair<String, List<String>>, Collection<Trace>> entry = entryIterator.next();
      if (entry.getValue().size() > 1) {
        entryIterator.remove();
        mySimilar.add(groupThreads(entry.getValue()));
      } else {
        list.add(entry.getValue().iterator().next());
      }
    }
    setLeftover(list);
  }

  private PoolDescriptor groupThreads(final Collection<Trace> traces) {
    final List<String> names = new ArrayList<>(traces.size());
    final List<String> presentations = new ArrayList<>(traces.size());
    for (Trace trace : traces) {
      names.add(trace.getThreadName());
      presentations.add(trace.getFirstLine());
    }
    String poolName = commonStart(names);
    // todo invent another way
    poolName = poolName == null ? names.get(0) : poolName;
    final Iterator<Trace> iterator = traces.iterator();
    final Trace first = iterator.next();
    final PoolDescriptor descriptor = new PoolDescriptor(first, names, presentations);
    while (iterator.hasNext()) {
      final Trace next = iterator.next();
      descriptor.addTrace(next);
    }
    descriptor.setNumber(traces.size());
    descriptor.setTemplateName(poolName);
    return descriptor;
  }

  private void filterOutJdk() {
    final List<Trace> list = new ArrayList<>();
    for (Trace inTrace : myInTraces) {
      if (JvmSystemThreadChecker.isEdt(inTrace)) {
        myEdtTrace = inTrace;
        continue;
      }
      if (JvmSystemThreadChecker.isJvmThread(inTrace)) {
        myJdkThreads.add(inTrace);
        continue;
      }
      list.add(inTrace);
    }
    setLeftover(list);
  }

  private void setLeftover(final List<Trace> list) {
    myInTraces.clear();
    myInTraces.addAll(list);
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

  public List<Trace> getJdkThreads() {
    return myJdkThreads;
  }

  public List<PoolDescriptor> getPools() {
    return myPools;
  }

  public List<Trace> getNotGrouped() {
    return myNotGrouped;
  }

  public Trace getEdtTrace() {
    return myEdtTrace;
  }

  public List<PoolDescriptor> getSimilar() {
    return mySimilar;
  }
}
