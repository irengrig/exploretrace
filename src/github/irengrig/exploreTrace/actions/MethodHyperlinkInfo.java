package github.irengrig.exploreTrace.actions;

import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.filters.OpenFileHyperlinkInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
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
    private final List<Pair<VirtualFile, PsiMethod>> myPsiMethods;

    public MethodHyperlinkInfo(final List<Pair<VirtualFile, PsiMethod>> psiMethods) {
        myPsiMethods = psiMethods;
        Collections.sort(myPsiMethods, new Comparator<Pair<VirtualFile, PsiMethod>>() {
            @Override
            public int compare(final Pair<VirtualFile, PsiMethod> o1, final Pair<VirtualFile, PsiMethod> o2) {
                return getMethodDescription(o1.getSecond()).compareTo(getMethodDescription(o2.getSecond()));
            }
        });
    }

    @Override
    public void navigate(final Project project) {
        final List<Pair<VirtualFile, PsiMethod>> m = new ArrayList<Pair<VirtualFile, PsiMethod>>(myPsiMethods.size());
        for (Pair<VirtualFile, PsiMethod> psiMethod : myPsiMethods) {
            if (psiMethod.getSecond().isValid()) m.add(psiMethod);
        }
        if (m.isEmpty()) return;
        if (m.size() > 1) {
            final JBList jbList = new JBList(m);
            jbList.setCellRenderer(new ColoredListCellRenderer() {
                @Override
                protected void customizeCellRenderer(final JList jList, final Object o, final int i, final boolean b, final boolean b1) {
                    if (o instanceof Pair) {
                        final Pair<VirtualFile, PsiMethod> m = (Pair<VirtualFile, PsiMethod>) o;
                        String text = getMethodDescription(m.getSecond());
                        append(text);
                    }
                }
            });
            JBPopupFactory.getInstance().createListPopupBuilder(jbList)
                    .setTitle("Choose Target Method").setItemChoosenCallback(new Runnable() {
                @Override
                public void run() {
                    final Pair<VirtualFile, PsiMethod> selectedMethod = (Pair<VirtualFile, PsiMethod>) jbList.getSelectedValue();
                    navigateToMethod(selectedMethod, project);
                }
            }).createPopup().showInFocusCenter();
        } else {
            navigateToMethod(m.get(0), project);
        }
    }

    private String getMethodDescription(final PsiMethod m) {
        return m.getPresentation().getPresentableText();
    }

    private void navigateToMethod(final Pair<VirtualFile, PsiMethod> pair, Project project) {
        pair.getSecond().navigate(true);
    }
}
