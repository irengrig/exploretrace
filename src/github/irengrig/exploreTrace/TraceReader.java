package github.irengrig.exploreTrace;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by Irina.Chernushina on 7/8/2014.
 */
public class TraceReader {
  private final static String HEADER_START = "Full thread dump";
  private final static String JNI_LINE = "JNI global references:";
  private final static String HEAP_START = "Heap";
  private final List<BaseParser> myParsers;
  private final MyDateParser dateParser;
  private final HeaderParser headerParser;
  private final AtomicBoolean myTracesStarted;
  private final TraceParser traceParser;
  private final JndiAndHeapParser jndiAndHeapParser;

  public TraceReader() {
    myParsers = new ArrayList<BaseParser>();
    myTracesStarted = new AtomicBoolean();
    dateParser = new MyDateParser(myTracesStarted);
    headerParser = new HeaderParser(myTracesStarted);
    traceParser = new TraceParser(myTracesStarted);
    jndiAndHeapParser = new JndiAndHeapParser(myTracesStarted);

    myParsers.add(dateParser);
    myParsers.add(headerParser);
    myParsers.add(jndiAndHeapParser);
    myParsers.add(traceParser);
  }

  public void read(@NotNull final InputStream is) throws IOException {
    final LineNumberReader reader = new LineNumberReader(new InputStreamReader(is));

    final Set<String> possiblePrefix = new HashSet<String>();
    for (;;) {
      String line = reader.readLine();
      if (line == null) break;  // eof possibly
      while (line.trim().startsWith(">")) {
        line = line.substring(1);
      }
      for (String pre : possiblePrefix) {
        if (line.startsWith(pre)) {
          line = line.substring(pre.length());
        }
      }
      boolean eaten = tryParse(line);
      if (! eaten && ! StringUtil.isEmptyOrSpaces(line)) {
        int idx = line.indexOf("\"");
        if (idx == -1) {
          idx = line.indexOf("at ");
        }
        if (idx >= 0) {
          possiblePrefix.add(line.substring(0, idx));
          line = line.substring(idx);
          tryParse(line);
        }
        // todo debug
//        System.out.println("Problem! " + line);
      }
    }
    for (BaseParser parser : myParsers) {
      parser.finish();
    }
  }

  private boolean tryParse(final String line) {
    boolean eaten = false;
    for (BaseParser parser : myParsers) {
      if (parser.eatLine(line)) {
        eaten = true;
        break;
      }
    }
    return eaten;
  }

  public String getHeader() {
    return headerParser.getHeader();
  }

  public String getHeap() {
    return jndiAndHeapParser.getHeap().toString();
  }

  public long getJndiNumber() {
    return jndiAndHeapParser.getJni();
  }

  public Date getDate() {
    return dateParser.getDate();
  }

  public List<String> getTraces() {
    return traceParser.getTraces();
  }

  public static class JndiAndHeapParser extends BaseParser {
    private final static int ourMaxLines = 20;
    private long myJni = -1;
    private final StringBuilder myHeap = new StringBuilder();
    private boolean myInHeap;
    private int myHeapCnt = 0;

    protected JndiAndHeapParser(AtomicBoolean tracesStarted) {
      super(tracesStarted);
    }

    @Override
    protected boolean eatLineImpl(String line) {
      if (myInHeap) {
        addHeapLine(line);
        ++ myHeapCnt;
        if (myHeapCnt > ourMaxLines) {
          myInHeap = false;
          return false;
        }
        return true;
      }
      if (line.trim().startsWith(JNI_LINE)) {
        tryParseJni(line);
        return true;
      }
      if (line.trim().startsWith(HEAP_START)) {
        myInHeap  = true;
        addHeapLine(line);
        return true;
      }
      return false;
    }

    private void addHeapLine(final String line) {
      if (myHeap.length() > 0) myHeap.append('\n');
      myHeap.append(line);
    }

    public long getJni() {
      return myJni;
    }

    public StringBuilder getHeap() {
      return myHeap;
    }

    private void tryParseJni(String line) {
      String trim = line.trim();
      if (trim.length() <= JNI_LINE.length()) return;
      trim = trim.substring(JNI_LINE.length()).trim();
      try {
        myJni = Long.parseLong(trim);
      } catch (NumberFormatException e) {
        //
      }
    }
  }

  public static class TraceParser extends BaseParser {
    private final StringBuilder mySb = new StringBuilder();
    private boolean myIsInside = false;
    private final List<String> myTraces = new ArrayList<String>();

    public TraceParser(final AtomicBoolean tracesStarted) {
      super(tracesStarted);
    }

    @Override
    protected boolean eatLineImpl(String line) {
      if (myIsInside) {
        if (line.trim().startsWith("\"") || line.contains("prio=") || line.contains("tid=")) {
          myIsInside = false;
          flushBufferIntoTrace();
//          return false;
        } else {
          appendLineToSb(line);
          return true;
        }
      }
      if (line.trim().startsWith("\"")) {
        myTracesStarted.set(true);
        myIsInside = true;
        appendLineToSb(line);
        return true;
      }
      return false;
    }

    private void appendLineToSb(String line) {
      if (mySb.length() > 0) mySb.append('\n');
      mySb.append(line);
    }

    private void flushBufferIntoTrace() {
      if (mySb.length() > 0) {
        myTraces.add(mySb.toString());
        mySb.setLength(0);
      }
    }

    @Override
    protected void finish() {
      if (myIsInside) {
        myIsInside = false;
        flushBufferIntoTrace();
      }
    }

    public List<String> getTraces() {
      return myTraces;
    }
  }

  public static class HeaderParser extends BaseParser {
    private String myHeader;

    public HeaderParser(AtomicBoolean tracesStarted) {
      super(tracesStarted);
    }

    @Override
    protected boolean eatLineImpl(String line) {
      if (isTracesStarted()) {
        myIsInBusiness = false;
        return false;
      }
      if (line.trim().startsWith(HEADER_START)) {
        myHeader = line.length() > HEADER_START.length() ? (line.substring(HEADER_START.length()).trim()) : null;
        myHeader = myHeader != null && myHeader.endsWith(":") ? myHeader.substring(0, myHeader.length() - 1) : myHeader;
        myIsInBusiness = false;
        return true;
      }
      return false;
    }

    public String getHeader() {
      return myHeader;
    }
  }

  public static class MyDateParser extends BaseParser {
    private Date myDate;

    public MyDateParser(AtomicBoolean tracesStarted) {
      super(tracesStarted);
    }

    @Override
    protected boolean eatLineImpl(String line) {
      if (isTracesStarted()) {
        myIsInBusiness = false;
        return false;
      }
      myDate = DateParser.tryParse(line);
      if (myDate != null) {
        myIsInBusiness = false;
        return true;
      }
      return false;
    }

    public Date getDate() {
      return myDate;
    }
  }

  private static abstract class BaseParser {
    protected boolean myIsInBusiness = true;
    protected final AtomicBoolean myTracesStarted;

    protected BaseParser(AtomicBoolean tracesStarted) {
      this.myTracesStarted = tracesStarted;
    }

    public boolean eatLine(String line) {
      if (! myIsInBusiness) return false;
      return eatLineImpl(line);
    }

    protected abstract boolean eatLineImpl(String line);

    public boolean isTracesStarted() {
      return myTracesStarted.get();
    }

    protected void finish() {}
  }
}
