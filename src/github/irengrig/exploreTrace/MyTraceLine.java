package github.irengrig.exploreTrace;

import com.intellij.execution.stacktrace.StackTraceLine;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Created by Irina.Chernushina on 7/12/2014.
 */
public class MyTraceLine {
  private final String myLine;
  private final StackTraceLine myStackTraceLine;

  public MyTraceLine(final String line, @NotNull final Project project) {
    myLine = line;
    myStackTraceLine = new StackTraceLine(project, line);
  }
}
