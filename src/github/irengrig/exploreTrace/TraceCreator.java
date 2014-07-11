package github.irengrig.exploreTrace;

/**
 * Created by Irina.Chernushina on 7/11/2014.
 */
public class TraceCreator {
  private final TraceReader myRead;
  private int myLineWidthCorrection;

  public TraceCreator(TraceReader read) {
    myRead = read;
    myLineWidthCorrection = 0;
  }

  public void createTraces() {
    for (String s : myRead.getTraces()) {
      final String[] lines = s.split("\n");

    }
  }
}
