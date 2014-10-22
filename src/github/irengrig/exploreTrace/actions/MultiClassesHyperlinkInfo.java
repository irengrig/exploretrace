package github.irengrig.exploreTrace.actions;

import com.intellij.codeEditor.JavaEditorFileSwapper;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.filters.OpenFileHyperlinkInfo;
import com.intellij.ide.highlighter.JavaClassFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.components.JBList;

import javax.swing.*;
import java.util.*;

/**
 * Created by ira on 22.10.2014.
 */
public class MultiClassesHyperlinkInfo implements HyperlinkInfo {
    private final ArrayList<Pair<PsiClass, VirtualFile>> myPsiMethods;
    private int myLine;

    public MultiClassesHyperlinkInfo(final List<Pair<PsiClass, VirtualFile>> psiMethods, final int line) {
        myLine = line;
        myPsiMethods = new ArrayList<>(psiMethods);
        Collections.sort(myPsiMethods, new Comparator<Pair<PsiClass, VirtualFile>>() {
            @Override
            public int compare(final Pair<PsiClass, VirtualFile> o1, final Pair<PsiClass, VirtualFile> o2) {
                return o1.getFirst().getQualifiedName().compareTo(o2.getFirst().getQualifiedName());
            }
        });
    }

    @Override
    public void navigate(final Project project) {
        final List<Pair<PsiClass, VirtualFile>> m = new ArrayList<>(myPsiMethods.size());
        for (Pair<PsiClass, VirtualFile> psiMethod : myPsiMethods) {
            if (psiMethod.getFirst().isValid()) m.add(psiMethod);
        }
        if (m.isEmpty()) return;
        if (m.size() > 1) {
            final JBList jbList = new JBList(m);
            jbList.setCellRenderer(new ColoredListCellRenderer() {
                @Override
                protected void customizeCellRenderer(final JList jList, final Object o, final int i, final boolean b, final boolean b1) {
                    if (o instanceof Pair) {
                        final Pair<PsiClass, VirtualFile> m = (Pair<PsiClass, VirtualFile>) o;
                        append(m.getFirst().getQualifiedName());
                    }
                }
            });
            JBPopupFactory.getInstance().createListPopupBuilder(jbList)
                    .setTitle("Choose Target Class").setItemChoosenCallback(new Runnable() {
                @Override
                public void run() {
                    final Pair<PsiClass, VirtualFile> selectedMethod = (Pair<PsiClass, VirtualFile>) jbList.getSelectedValue();
                    navigateToClass(selectedMethod, project);
                }
            }).createPopup().showInFocusCenter();
        } else {
            navigateToClass(m.get(0), project);
        }
    }

    private void navigateToClass(final Pair<PsiClass, VirtualFile> pair, Project project) {
        new OpenFileHyperlinkInfo(project, pair.getSecond(), myLine - 1).navigate(project);
    }
}
