package com.siyeh.ig.verbose;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.*;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class UnnecessaryBlockStatementInspection extends StatementInspection{
    private final UnnecessaryBlockFix fix = new UnnecessaryBlockFix();

    public String getID(){
        return "UnnecessaryCodeBlock";
    }

    public String getDisplayName(){
        return "Unnecessary code block";
    }

    public String getGroupDisplayName(){
        return GroupNames.VERBOSE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return "Braces around this statement are unnecessary #loc";
    }

    public BaseInspectionVisitor buildVisitor(){
        return new UnnecessaryBlockStatementVisitor();
    }

    public InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    private static class UnnecessaryBlockFix extends InspectionGadgetsFix{
        public String getName(){
            return "Unwrap block";
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException{
            final PsiElement leftBrace = descriptor.getPsiElement();
            final PsiCodeBlock block = (PsiCodeBlock) leftBrace.getParent();
            assert block != null;
            final PsiBlockStatement blockStatement =
                    (PsiBlockStatement) block.getParent();
            assert blockStatement != null;
            final PsiElement containingElement = blockStatement.getParent();
            final PsiElement[] children = block.getChildren();
            if(children.length > 2){
                assert containingElement != null;
                final PsiElement added =
                        containingElement.addRangeBefore(children[1],
                                                         children[children
                                                                 .length -
                                                                 2],
                                                         blockStatement);
                final CodeStyleManager codeStyleManager =
                        CodeStyleManager.getInstance(project);
                codeStyleManager.reformat(added);
            }
            blockStatement.delete();
        }
    }

    private static class UnnecessaryBlockStatementVisitor
            extends StatementInspectionVisitor{
        public void visitBlockStatement(PsiBlockStatement blockStatement){
            super.visitBlockStatement(blockStatement);
            final PsiElement parent = blockStatement.getParent();
            if(!(parent instanceof PsiCodeBlock)){
                return;
            }
            final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
            final PsiJavaToken brace = codeBlock.getLBrace();
            if(brace == null){
                return;
            }
            final PsiCodeBlock parentBlock = (PsiCodeBlock) parent;
            if(parentBlock.getStatements().length > 1 &&
                    containsConflictingDeclarations(codeBlock, parentBlock)){
                return;
            }
            registerError(brace);
            final PsiJavaToken rbrace = codeBlock.getRBrace();
            if(rbrace != null){
                registerError(rbrace);
            }
        }

        private static boolean containsConflictingDeclarations(
                PsiCodeBlock block,
                PsiCodeBlock parentBlock){
            final PsiStatement[] statements = block.getStatements();
            if(statements == null){
                return false;
            }
            final Set<PsiElement> declaredVars = new HashSet<PsiElement>();
            for(final PsiStatement statement : statements){
                if(statement instanceof PsiDeclarationStatement){
                    final PsiDeclarationStatement declaration =
                            (PsiDeclarationStatement) statement;
                    final PsiElement[] vars = declaration.getDeclaredElements();
                    for(PsiElement var : vars){
                        if(var instanceof PsiLocalVariable){
                            declaredVars.add(var);
                        }
                    }
                }
            }
            for(Object declaredVar : declaredVars){
                final PsiLocalVariable variable =
                        (PsiLocalVariable) declaredVar;
                final String variableName = variable.getName();
                if(conflictingDeclarationExists(variableName, parentBlock,
                                                block)){
                    return true;
                }
            }

            return false;
        }

        private static boolean conflictingDeclarationExists(String name,
                                                            PsiCodeBlock parentBlock,
                                                            PsiCodeBlock exceptBlock)
        {
            final ConflictingDeclarationVisitor visitor =
                    new ConflictingDeclarationVisitor(name, exceptBlock);
            parentBlock.accept(visitor);
            return visitor.hasConflictingDeclaration();
        }
    }

    private static class ConflictingDeclarationVisitor
            extends PsiRecursiveElementVisitor{
        private final String variableName;
        private final PsiCodeBlock exceptBlock;
        private boolean hasConflictingDeclaration = false;

        ConflictingDeclarationVisitor(String variableName,
                                      PsiCodeBlock exceptBlock){
            super();
            this.variableName = variableName;
            this.exceptBlock = exceptBlock;
        }

        public void visitElement(@NotNull PsiElement element){
            if(!hasConflictingDeclaration){
                super.visitElement(element);
            }
        }

        public void visitCodeBlock(PsiCodeBlock block){
            if(hasConflictingDeclaration){
                return;
            }
            if(block.equals(exceptBlock)){
                return;
            }
            super.visitCodeBlock(block);
        }

        public void visitVariable(@NotNull PsiVariable variable){
            if(hasConflictingDeclaration){
                return;
            }
            super.visitVariable(variable);
            if(variable.getName().equals(variableName)){
                hasConflictingDeclaration = true;
            }
        }

        public boolean hasConflictingDeclaration(){
            return hasConflictingDeclaration;
        }
    }
}
