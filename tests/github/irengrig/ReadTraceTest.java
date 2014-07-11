package github.irengrig;

import github.irengrig.exploreTrace.TraceReader;
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
}
