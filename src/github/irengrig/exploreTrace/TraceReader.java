package github.irengrig.exploreTrace;

import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by Irina.Chernushina on 7/8/2014.
 */
public class TraceReader {
  private final static String HEADER_START = "Full thread dump";
  private final static String JNI_LINE = "JNI global references:";
  private final static String HEAP_START = "Heap";

  private boolean myDateAwaited;
  private boolean myHeaderAwaited;
  private boolean myTraceAwaited;
  private boolean myHeapAwaited;
  private boolean myInHeap;

  private String myHeader;
  private String myHeap;
  private long myJndiNumber;
  private Date myDate;
  private final StringBuilder mySb;
  private final List<String> myTraces;

  public TraceReader() {
    myTraces = new ArrayList<String>();
    myDateAwaited = true;
    myHeaderAwaited = true;
    myTraceAwaited = true;
    myJndiNumber = -1;
    mySb = new StringBuilder();
  }

  public void read(@NotNull final InputStream is) throws IOException {
    final LineNumberReader reader = new LineNumberReader(new InputStreamReader(is));

    for (;;) {
      final String line = reader.readLine();
      if (line == null) break;  // eof possibly
      if (myDateAwaited) {
        myDate = DateParser.tryParse(line);
        if (myDate != null) {
          myDateAwaited = false;
          continue;
        }
      }
      if (myHeaderAwaited) {
        if (line.trim().startsWith(HEADER_START)) {
          recordHeader(line);
          continue;
        }
      }
      if (myTraceAwaited) {
        if (line.trim().isEmpty()) {
          flushBufferIntoTrace();
        } else {
          if (line.trim().startsWith(JNI_LINE)) {
            myTraceAwaited = false;
            myDateAwaited = false;
            myHeaderAwaited = false;
            myHeapAwaited = true;

            flushBufferIntoTrace();
            tryParseJni(line);
          } else {
            if (mySb.length() > 0) mySb.append('\n');
            // no trim!
            mySb.append(line);
          }
        }
      } else if (myHeapAwaited) {
//        if (line.trim().startsWith(HEAP_START)) {
          if (mySb.length() > 0) mySb.append('\n');
          // no trim!
          mySb.append(line);
//        }
        if (line.trim().isEmpty()) break;
      }
    }

    if (mySb.length() > 0) {
      if (myTraceAwaited) {
        flushBufferIntoTrace();
      } else if (myHeapAwaited) {
        myHeap = mySb.toString();
      }
    }
  }

  private void tryParseJni(String line) {
    String trim = line.trim();
    if (trim.length() <= JNI_LINE.length()) return;
    trim = trim.substring(JNI_LINE.length()).trim();
    try {
      myJndiNumber = Long.parseLong(trim);
    } catch (NumberFormatException e) {
      //
    }
  }

  private void flushBufferIntoTrace() {
    if (mySb.length() > 0) {
      myTraces.add(mySb.toString());
      mySb.setLength(0);
      myDateAwaited = false;
      myHeaderAwaited = false;
    }
  }

  private void recordHeader(String line) {
  /*Full thread dump Java HotSpot(TM) Server VM (24.0-b56 mixed mode):*/
    myHeader = line.length() > HEADER_START.length() ? (line.substring(HEADER_START.length()).trim()) : null;
    myHeader = myHeader != null && myHeader.endsWith(":") ? myHeader.substring(0, myHeader.length() - 1) : myHeader;

    myDateAwaited = false;
    myHeaderAwaited = false;
  }

  public String getHeader() {
    return myHeader;
  }

  public String getHeap() {
    return myHeap;
  }

  public long getJndiNumber() {
    return myJndiNumber;
  }

  public Date getDate() {
    return myDate;
  }

  public List<String> getTraces() {
    return myTraces;
  }
}
