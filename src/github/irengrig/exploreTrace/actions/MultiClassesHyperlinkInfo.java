package github.irengrig.exploreTrace.actions;

import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.filters.OpenFileHyperlinkInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
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
    private final List<PsiClass> myPsiMethods;
    private int myLine;

    public MultiClassesHyperlinkInfo(final Collection<PsiClass> psiMethods, final int line) {
        myLine = line;
        myPsiMethods = new ArrayList<>(psiMethods);
        Collections.sort(myPsiMethods, new Comparator<PsiClass>() {
            @Override
            public int compare(final PsiClass o1, final PsiClass o2) {
                return o1.getQualifiedName().compareTo(o2.getQualifiedName());
            }
        });
    }

    @Override
    public void navigate(final Project project) {
        final List<PsiClass> m = new ArrayList<>(myPsiMethods.size());
        for (PsiClass psiMethod : myPsiMethods) {
            if (psiMethod.isValid()) m.add(psiMethod);
        }
        if (m.isEmpty()) return;
        if (m.size() > 1) {
            final JBList jbList = new JBList(m);
            jbList.setCellRenderer(new ColoredListCellRenderer() {
                @Override
                protected void customizeCellRenderer(final JList jList, final Object o, final int i, final boolean b, final boolean b1) {
                    if (o instanceof PsiClass) {
                        final PsiClass m = (PsiClass) o;
                        append(m.getQualifiedName());
                    }
                }
            });
            JBPopupFactory.getInstance().createListPopupBuilder(jbList)
                    .setTitle("Choose Target Class").setItemChoosenCallback(new Runnable() {
                @Override
                public void run() {
                    final PsiClass selectedMethod = (PsiClass) jbList.getSelectedValue();
                    navigateToClass(selectedMethod, project);
                }
            }).createPopup().showInFocusCenter();
        } else {
            navigateToClass(m.get(0), project);
        }
    }

    private void navigateToClass(final PsiClass selectedClass, Project project) {
        new OpenFileHyperlinkInfo(project, selectedClass.getContainingFile().getVirtualFile(), myLine).navigate(project);
    }
}
