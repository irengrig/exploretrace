package github.irengrig.exploreTrace.actions;

import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.filters.OpenFileHyperlinkInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.psi.PsiMethod;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.components.JBList;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created by ira on 22.10.2014.
 */
public class MethodHyperlinkInfo implements HyperlinkInfo {
    private final List<PsiMethod> myPsiMethods;

    public MethodHyperlinkInfo(final List<PsiMethod> psiMethods) {
        myPsiMethods = psiMethods;
        Collections.sort(myPsiMethods, new Comparator<PsiMethod>() {
            @Override
            public int compare(final PsiMethod o1, final PsiMethod o2) {
                return getMethodDescription(o1).compareTo(getMethodDescription(o2));
            }
        });
    }

    @Override
    public void navigate(final Project project) {
        final List<PsiMethod> m = new ArrayList<>(myPsiMethods.size());
        for (PsiMethod psiMethod : myPsiMethods) {
            if (psiMethod.isValid()) m.add(psiMethod);
        }
        if (m.isEmpty()) return;
        if (m.size() > 0) {
            final JBList jbList = new JBList(m);
            jbList.setCellRenderer(new ColoredListCellRenderer() {
                @Override
                protected void customizeCellRenderer(final JList jList, final Object o, final int i, final boolean b, final boolean b1) {
                    if (o instanceof PsiMethod) {
                        final PsiMethod m = (PsiMethod) o;
                        String text = getMethodDescription(m);
                        append(text);
                    }
                }
            });
            JBPopupFactory.getInstance().createListPopupBuilder(jbList)
                    .setTitle("Choose Target Method").setItemChoosenCallback(new Runnable() {
                @Override
                public void run() {
                    final PsiMethod selectedMethod = (PsiMethod) jbList.getSelectedValue();
                    navigateToMethod(selectedMethod, project);
                }
            });
        } else {
            navigateToMethod(m.get(0), project);
        }
    }

    private String getMethodDescription(final PsiMethod m) {
        String text = "";
        if (m.getContainingClass() != null) {
            text += m.getContainingClass().getQualifiedName();
            text += ".";
        }
        text += m.getName();
        return text;
    }

    private void navigateToMethod(final PsiMethod selectedMethod, Project project) {
        new OpenFileHyperlinkInfo(project, selectedMethod.getContainingFile().getVirtualFile(), selectedMethod.getTextOffset()).navigate(project);
    }
}
