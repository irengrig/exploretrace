package github.irengrig.exploreTrace;

import github.irengrig.exploreTrace.actions.TraceCase;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Irina.Chernushina on 7/7/2014.
 */
public class Trace {
  private int myInitialOrderInGroup;
  private final String myFirstLine;
  private final String myThreadName;
  private final String myThreadGroup;
  private final int myPriority;
  private Thread.State myState;
  private String myStateWords;
  private final boolean myIsDaemon;
  private final String myIdentifier;
  private final List<String> myTrace;
  private TraceCase myCase;

  private Trace(final String firstLine, String myThreadName, String myThreadGroup, int myPriority, Thread.State myState, String myStateWords,
                boolean myIsDaemon, String myIdentifier, List<String> myTrace) {
    myFirstLine = firstLine;
    this.myThreadName = myThreadName;
    this.myThreadGroup = myThreadGroup;
    this.myPriority = myPriority;
    this.myState = myState;
    this.myStateWords = myStateWords;
    this.myIsDaemon = myIsDaemon;
    this.myIdentifier = myIdentifier;
    this.myTrace = myTrace;
    myInitialOrderInGroup = 0;
  }

  public void setState(final Thread.State state) {
    myState = state;
  }

  public void setStateWords(final String stateWords) {
    myStateWords = stateWords;
  }

  public String getThreadName() {
    return myThreadName;
  }

  public String getThreadGroup() {
    return myThreadGroup;
  }

  public int getPriority() {
    return myPriority;
  }

  public Thread.State getState() {
    return myState;
  }

  public String getStateWords() {
    return myStateWords;
  }

  public boolean isDaemon() {
    return myIsDaemon;
  }

  public String getIdentifier() {
    return myIdentifier;
  }

  public List<String> getTrace() {
    return myTrace;
  }

  public String getFirstLine() {
    return myFirstLine;
  }

  public int getInitialOrderInGroup() {
    return myInitialOrderInGroup;
  }

  public void setInitialOrderInGroup(final int initialOrderInGroup) {
    myInitialOrderInGroup = initialOrderInGroup;
  }

  @Override
  public String toString() {
    return "Trace{" +
            "myThreadName='" + myThreadName + '\'' +
            '}';
  }

  public TraceCase getCase() {
    return myCase;
  }

  public void setCase(final TraceCase aCase) {
    myCase = aCase;
  }

  public static class Builder {
    private String myFirstLine;
    private String myThreadName;
    @Nullable
    private String myThreadGroup;
    private int myPriority = Thread.NORM_PRIORITY;
    @Nullable
    private Thread.State myState;
    @Nullable
    private String myStateWords;
    private boolean myIsDaemon = false;
    @Nullable
    private String myIdentifier;
    private List<String> myTrace = new ArrayList<String>();
    @Nullable
    private String myHeader;

    public Trace create() {
      return new Trace(myFirstLine, myThreadName, myThreadGroup, myPriority, myState, myStateWords, myIsDaemon, myIdentifier, myTrace);
    }

    public Builder setThreadName(String threadName) {
      myThreadName = threadName;
      return this;
    }

    public Builder setThreadGroup(@Nullable String threadGroup) {
      myThreadGroup = threadGroup;
      return this;
    }

    public Builder setPriority(int priority) {
      myPriority = priority;
      return this;
    }

    public Builder setState(@Nullable Thread.State state) {
      myState = state;
      return this;
    }

    public Builder setStateWords(@Nullable String stateWords) {
      myStateWords = stateWords;
      return this;
    }

    public Builder setIsDaemon(boolean isDaemon) {
      myIsDaemon = isDaemon;
      return this;
    }

    public Builder setIdentifier(@Nullable String identifier) {
      myIdentifier = identifier;
      return this;
    }

    public Builder setTrace(final List<String> list) {
      myTrace = list;
      return this;
    }

    public Builder setHeader(@Nullable String header) {
      myHeader = header;
      return this;
    }

    public Builder setFirstLine(final String firstLine) {
      myFirstLine = firstLine;
      return this;
    }
  }
}
