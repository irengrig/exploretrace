package github.irengrig.exploreTrace.actions;

import com.intellij.ide.actions.CloseAction;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import github.irengrig.exploreTrace.TraceCreator;
import github.irengrig.exploreTrace.TraceReader;
import github.irengrig.exploreTrace.TracesClassifier;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Created by Irina.Chernushina on 8/16/2014.
 */
public class ShowTraceViewAction extends AnAction {
  public ShowTraceViewAction() {
    super("Explore Stack Trace");
  }

  @Override
  public void actionPerformed(final AnActionEvent anActionEvent) {
    final Project project = PlatformDataKeys.PROJECT.getData(anActionEvent.getDataContext());
    if (project == null) return;

    final String transferData;
    try {
      final Transferable contents = CopyPasteManager.getInstance().getContents();
      if (contents == null) return;
      transferData = (String) contents.getTransferData(DataFlavor.stringFlavor);
    } catch (UnsupportedFlavorException | IOException e) {
      return;
    }

    final TraceReader traceReader = new TraceReader();
    try {
      traceReader.read(new ByteArrayInputStream(transferData.getBytes()));
    } catch (IOException e) {
      // ignore
    }
    if(traceReader.getTraces().isEmpty()) return;
    final TraceCreator traceCreator = new TraceCreator(traceReader);
    traceCreator.createTraces();
    final TracesClassifier classifier = new TracesClassifier(traceCreator.getCreatedTraces());
    classifier.execute();

    final ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
    // todo multiple
    final String name = "Explore Stack Trace";
    final ToolWindow toolWindow = toolWindowManager.registerToolWindow(name, true, ToolWindowAnchor.BOTTOM, project);
    toolWindow.setToHideOnEmptyContent(true);

    final ContentManager contentManager = toolWindow.getContentManager();
    final DefaultActionGroup defaultActionGroup = new DefaultActionGroup();
    defaultActionGroup.add(new CloseAction() {
      @Override
      public void actionPerformed(final AnActionEvent e) {
        super.actionPerformed(e);
        toolWindowManager.unregisterToolWindow(name);
      }
    });
    final TraceView traceView = new TraceView(project, classifier.getNotGrouped(), classifier.getPools(), classifier.getSimilar(),
            classifier.getJdkThreads(), classifier.getEdtTrace(), defaultActionGroup);
    // todo creation parameters?
    final Content content = contentManager.getFactory().createContent(traceView, "", false);
    content.setPreferredFocusableComponent(traceView.getNamesList());
    //content.setCloseable(true);
    contentManager.addContent(content);

    toolWindow.activate(EmptyRunnable.getInstance());
  }
}
