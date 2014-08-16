package github.irengrig.exploreTrace;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Irina.Chernushina on 7/7/2014.
 */
public class Trace {
  private final String myThreadName;
  private final String myThreadGroup;
  private final int myPriority;
  private final Thread.State myState;
  private final String myStateWords;
  private final boolean myIsDaemon;
  private final String myIdentifier;
  private final List<String> myTrace;

  private Trace(String myThreadName, String myThreadGroup, int myPriority, Thread.State myState, String myStateWords,
                boolean myIsDaemon, String myIdentifier, List<String> myTrace) {
    this.myThreadName = myThreadName;
    this.myThreadGroup = myThreadGroup;
    this.myPriority = myPriority;
    this.myState = myState;
    this.myStateWords = myStateWords;
    this.myIsDaemon = myIsDaemon;
    this.myIdentifier = myIdentifier;
    this.myTrace = myTrace;
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

  @Override
  public String toString() {
    return "Trace{" +
            "myThreadName='" + myThreadName + '\'' +
            '}';
  }

  public static class Builder {
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
    private List<String> myTrace = new ArrayList<>();
    @Nullable
    private String myHeader;

    public Trace create() {
      return new Trace(myThreadName, myThreadGroup, myPriority, myState, myStateWords, myIsDaemon, myIdentifier, myTrace);
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
  }
}
