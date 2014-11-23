package github.irengrig.exploreTrace;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
* Created by irengrig on 23.11.2014.
*/
public class PoolDescriptor {
  private String myTemplateName;
  private int myNumber;
  private final Trace myTypicalTrace;
  private final List<String> myNames;
  private final List<String> myPresentations;

  public PoolDescriptor(@NotNull final Trace typicalTrace, @NotNull final List<String> names,
                        @NotNull final List<String> presentations) {
    myTypicalTrace = typicalTrace;
    myNames = names;
      myPresentations = presentations;
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

  public Trace getTypicalTrace() {
    return myTypicalTrace;
  }

  public List<String> getPresentations() {
    return myPresentations;
  }
}
