package github.irengrig.exploreTrace;

import org.jetbrains.annotations.Nullable;

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
  private final StackTraceElement[] myTrace;
  private final String myHeader;

  private Trace(String myThreadName, String myThreadGroup, int myPriority, Thread.State myState, String myStateWords,
                boolean myIsDaemon, String myIdentifier, StackTraceElement[] myTrace, String myHeader) {
    this.myThreadName = myThreadName;
    this.myThreadGroup = myThreadGroup;
    this.myPriority = myPriority;
    this.myState = myState;
    this.myStateWords = myStateWords;
    this.myIsDaemon = myIsDaemon;
    this.myIdentifier = myIdentifier;
    this.myTrace = myTrace;
    this.myHeader = myHeader;
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
    private StackTraceElement[] myTrace = new StackTraceElement[0];
    @Nullable
    private String myHeader;

    public Trace create() {
      return new Trace(myThreadName, myThreadGroup, myPriority, myState, myStateWords, myIsDaemon, myIdentifier, myTrace, myHeader);
    }

    public Builder setMyThreadName(String threadName) {
      myThreadName = threadName;
      return this;
    }

    public Builder setMyThreadGroup(@Nullable String threadGroup) {
      myThreadGroup = threadGroup;
      return this;
    }

    public Builder setMyPriority(int priority) {
      myPriority = priority;
      return this;
    }

    public Builder setMyState(@Nullable Thread.State state) {
      myState = state;
      return this;
    }

    public Builder setMyStateWords(@Nullable String stateWords) {
      myStateWords = stateWords;
      return this;
    }

    public Builder setMyIsDaemon(boolean isDaemon) {
      myIsDaemon = isDaemon;
      return this;
    }

    public Builder setMyIdentifier(@Nullable String identifier) {
      myIdentifier = identifier;
      return this;
    }

    public Builder setMyTrace(StackTraceElement[] trace) {
      myTrace = trace;
      return this;
    }

    public Builder setMyHeader(@Nullable String header) {
      myHeader = header;
      return this;
    }
  }
}
