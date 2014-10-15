package github.irengrig;

import github.irengrig.exploreTrace.TraceCreator;
import github.irengrig.exploreTrace.TraceReader;
import github.irengrig.exploreTrace.TracesClassifier;
import org.junit.Test;

import java.io.FileInputStream;

/**
 * Created by Irina.Chernushina on 7/10/2014.
 */
public class ReadTraceTest {
  @Test
  public void testJustRead() throws Exception {
    final TraceReader traceReader = new TraceReader();
    traceReader.read(new FileInputStream("./testResources/sampleTrace1.txt"));
    System.out.println("Date: " + traceReader.getDate());
    System.out.println("header: " + traceReader.getHeader());
    System.out.println("heap: " + traceReader.getHeap());
    System.out.println("jndi: " + traceReader.getJndiNumber());
    System.out.println("Traces: " + traceReader.getTraces());
  }

  @Test
  public void testAlsoCorrect() throws Exception {
    final TraceReader traceReader = new TraceReader();
    traceReader.read(new FileInputStream("./testResources/sampleTrace1.txt"));
    final TraceCreator traceCreator = new TraceCreator(traceReader);
    traceCreator.createTraces();
    System.out.println("-");
  }

  @Test
  public void testClassify() throws Exception {
    final TraceReader traceReader = new TraceReader();
    traceReader.read(new FileInputStream("./testResources/shortSampleTrace.txt"));
    final TraceCreator traceCreator = new TraceCreator(traceReader);
    traceCreator.createTraces();
    final TracesClassifier tracesClassifier = new TracesClassifier(traceCreator.getCreatedTraces());
    tracesClassifier.execute();
    System.out.println("-");
  }

  @Test
  public void testAlarmPool() throws Exception {
    final TraceReader traceReader = new TraceReader();
    traceReader.read(new FileInputStream("./testResources/sampleAlarmPool.txt"));
    final TraceCreator traceCreator = new TraceCreator(traceReader);
    traceCreator.createTraces();
    final TracesClassifier tracesClassifier = new TracesClassifier(traceCreator.getCreatedTraces());
    tracesClassifier.execute();
    System.out.println("-");
  }
}
