package github.irengrig.exploreTrace.actions;

import com.intellij.codeEditor.JavaEditorFileSwapper;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.ide.highlighter.JavaClassFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packageDependencies.DefaultScopesProvider;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.impl.java.stubs.index.JavaFullClassNameIndex;
import com.intellij.psi.search.EverythingGlobalScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopes;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.util.Function;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;

import java.util.*;

/**
 * Created by ira on 22.10.2014.
 */
public class LineParser {
    private final String mySourceLine;
    private String myLine;
    private String myClassName;
    private String myMethod;
    private int myLineNum;
    private boolean mySuccessful;
    private List<Pair<String, HyperlinkInfo>> myResult;
    private int myClassBoundary;
    private int myOpen;
    private int myClose;

    public LineParser(final String line) {
        myLine = line.trim();
        mySourceLine = line;
        myResult = new ArrayList<>();
    }

    public void parse(final Project project) {
        parseImpl();
        findObjectsAndFillResult(project);
    }

    private boolean parseImpl() {
        if (! myLine.startsWith("at ")) return true;
        myLine = myLine.substring(3);

        myOpen = myLine.indexOf('(');
        if (myOpen < 0) return true;
        myClose = myLine.indexOf(')', myOpen + 1);
        if (myClose < 0) return true;
        int colon = myLine.indexOf(".java:", myOpen + 1);
        if (colon < 0 || colon > myClose) return true;

        final String number = myLine.substring(colon + 6, myClose);
        try {
            myLineNum = Integer.parseInt(number);
        } catch (NumberFormatException e) {
            return true;
        }

        myClassBoundary = myLine.lastIndexOf('.', myOpen);
        if (myClassBoundary < 0) return true;

        myClassName = myLine.substring(0, myClassBoundary).trim();
        myMethod = myLine.substring(myClassBoundary + 1, myOpen);
        mySuccessful = true;
        return false;
    }

    public List<Pair<String, HyperlinkInfo>> getResult() {
        return myResult;
    }

    private void findObjectsAndFillResult(final Project project) {
        if (! mySuccessful) {
            myResult.add(Pair.<String, HyperlinkInfo>create(mySourceLine, null));
            return;
        }
        final GlobalSearchScope scope = new EverythingGlobalScope(project);
//        final GlobalSearchScope allScope = GlobalSearchScope.allScope(project);
//        final GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
        PsiClass[] result = psiFacade.findClasses(myClassName, scope);

        List<PsiClass> psiClasses = Arrays.asList(result);
        psiClasses = ContainerUtil.filter(psiClasses, new Condition<PsiClass>() {
            @Override
            public boolean value(final PsiClass psiClass) {
                return myClassName.equals(psiClass.getQualifiedName());
            }
        });
        final List<Pair<PsiClass, VirtualFile>> convertedFiles = new ArrayList<>();
        for (PsiClass psiClass : psiClasses) {
            VirtualFile virtualFile = psiClass.getContainingFile().getVirtualFile();
            if (virtualFile.getFileType().equals(JavaClassFileType.INSTANCE)) {
                VirtualFile sourceFile = JavaEditorFileSwapper.findSourceFile(project, virtualFile);
                if (sourceFile != null) {
                    virtualFile = sourceFile;
                }
            }
            convertedFiles.add(Pair.create(psiClass, virtualFile));
        }

        final List<Pair<VirtualFile, PsiMethod>> psiMethods = new ArrayList<>();
        final Function<Pair<PsiClass, VirtualFile>, PsiMethod[]> function = getMethodsFromClass();
        final Iterator<Pair<PsiClass, VirtualFile>> iterator = convertedFiles.iterator();
        while (iterator.hasNext()) {
            final Pair<PsiClass, VirtualFile> pair = iterator.next();
            PsiMethod[] methods = function.fun(pair);
            if (methods.length == 0) {
                iterator.remove();
            }
            for (PsiMethod method : methods) {
                psiMethods.add(Pair.create(pair.getSecond(), method));
            }
        }

        if (psiClasses.isEmpty()) {
            myResult.add(Pair.<String, HyperlinkInfo>create("at " + myLine, null));
        } else {
            myResult.add(Pair.<String, HyperlinkInfo>create("at " + myLine.substring(0, myClassBoundary + 1), null));
            myResult.add(Pair.<String, HyperlinkInfo>create(myLine.substring(myClassBoundary + 1, myOpen),
                    new MethodHyperlinkInfo(psiMethods)));
            myResult.add(Pair.<String, HyperlinkInfo>create("(", null));
            myResult.add(Pair.<String, HyperlinkInfo>create(myLine.substring(myOpen + 1, myClose),
                    new MultiClassesHyperlinkInfo(convertedFiles, myLineNum)));
            myResult.add(Pair.<String, HyperlinkInfo>create(")", null));
        }
    }

    private Function<Pair<PsiClass, VirtualFile>, PsiMethod[]> getMethodsFromClass() {
        return new Function<Pair<PsiClass, VirtualFile>, PsiMethod[]>() {
            @Override
            public PsiMethod[] fun(final Pair<PsiClass, VirtualFile> pair) {
                if (myMethod.equals("<init>")) {
                    return pair.getFirst().getConstructors();
                } else {
                    final PsiMethod[] methods = pair.getFirst().getMethods();
                    final List<PsiMethod> methodList = new ArrayList<>(2);
                    for (PsiMethod psiMethod : methods) {
                        if (myMethod.equals(psiMethod.getName())) {
                            methodList.add(psiMethod);
                        }
                    }
                    return methodList.toArray(new PsiMethod[methodList.size()]);
                }
            }
        };
    }

    public String getClassName() {
        return myClassName;
    }

    public String getMethod() {
        return myMethod;
    }

    public int getLineNum() {
        return myLineNum;
    }

    public boolean isSuccessful() {
        return mySuccessful;
    }
}
