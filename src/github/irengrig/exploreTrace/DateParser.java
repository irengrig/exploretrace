package github.irengrig.exploreTrace;

import org.jetbrains.annotations.NotNull;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by Irina.Chernushina on 7/8/2014.
 */
public class DateParser {
  private final static List<DateFormat> ourList;

  static {
    ourList = new ArrayList<DateFormat>();
    //2014-07-07 21:49:27
    ourList.add(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));
  }

  public static Date tryParse(@NotNull final String s) {
    for (DateFormat format : ourList) {
      try {
        return format.parse(s);
      } catch (ParseException e) {
        //
      }
    }
    return null;
  }
}
