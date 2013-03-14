package com.cmcmarkets.closure;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This document and its contents are protected by copyright 2011 and owned by CMC Markets UK Plc.
 * The copying and reproduction of this document and/or its content (whether wholly or partly) or any
 * incorporation of the same into any other material in any media or format of any kind is strictly prohibited.
 * All rights are reserved.
 * <p/>
 * © CMC Markets Plc 2012
 */
public class ValidateRequireStatementsInspection extends LocalInspectionTool
{
    private static Set<String> allowedKeywords;
    private static Pattern CONSTANT_PATTERN = Pattern.compile("[A-Z0-9_]+");
    private static Pattern CLASS_PATTERN = Pattern.compile("[A-Z]{1}[a-zA-Z1-9]+");

    static
    {
        allowedKeywords = new HashSet<String>();
        allowedKeywords.add("Math");
        allowedKeywords.add("Number");
        allowedKeywords.add("String");
        allowedKeywords.add("Boolean");
        allowedKeywords.add("Array");
        allowedKeywords.add("Blob");
        allowedKeywords.add("Date");
        allowedKeywords.add("Object");
        allowedKeywords.add("RegEx");
        allowedKeywords.add("Text");
        allowedKeywords.add("goog");
        allowedKeywords.add("JSON");
        allowedKeywords.add("...*");
        allowedKeywords.add("console");
    }

    private final LocalQuickFix singleQuickFix = new SingleRequireStatementFix();
    private final LocalQuickFix multipleQuickFix = new MultipleRequireStatementFix();

    private Set<String> requireSet;
    private Set<String> localVariableSet;
    private Set<PsiElement> requireElementSet;
    private Set<PsiElement> errorElementSet;
    private PsiElement lastRequireElement;
    private Set<String> provideSet;
    private boolean requireElementFound = false;

    @NotNull
    public String getDisplayName()
    {

        return "Check whether the closure types are imported correctly.";
    }

    @NotNull
    public String getGroupDisplayName()
    {
        return GroupNames.IMPORTS_GROUP_NAME;
    }

    @NotNull
    public String getShortName()
    {
        return "ValidateRequireStatements";
    }


    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly)
    {
        return new ValidateRequirementsPsiRecursiveElementVisitor(holder);
    }

    public boolean isEnabledByDefault()
    {
        return true;
    }

    private class ValidateRequirementsPsiRecursiveElementVisitor extends PsiElementVisitor
    {
        private ProblemsHolder holder;

        public ValidateRequirementsPsiRecursiveElementVisitor(final ProblemsHolder holder)
        {
            this.holder = holder;
        }

        @Override
        public void visitFile(final PsiFile file)
        {
            super.visitFile(file);

            if(!file.getFileType().getDefaultExtension().equals("js"))
            {
                return;
            }

            requireSet = new HashSet<String>();
            provideSet = new HashSet<String>();
            localVariableSet = new HashSet<String>();
            errorElementSet = new HashSet<PsiElement>();
            requireElementFound = false;
            requireElementSet = new TreeSet<PsiElement>(new Comparator<PsiElement>()
            {
                public int compare(final PsiElement o1, final PsiElement o2)
                {
                    String o1Text = o1.getText();
                    String o2Text = o2.getText();

                    int result;

                    boolean o1Starts = o1Text.startsWith("goog.require(\"goog.") || o1Text.startsWith("goog.require('goog.");
                    boolean o2Starts = o2Text.startsWith("goog.require(\"goog.") || o2Text.startsWith("goog.require('goog.");
                    if((o1Starts && o2Starts) || (!o1Starts && !o2Starts))
                    {
                        result = o2Text.compareTo(o1Text);
                    }
                    else if(o1Starts)
                    {
                        result = 1;
                    }
                    else
                    {
                        result = o2Text.compareTo(o1Text);
                    }
                    return result;
                }
            });


            processElements(file);
        }

        private void processElements(final PsiElement element)
        {
            final PsiElement[] children = element.getChildren();

            for(PsiElement child : children)
            {
                PsiElement firstChild = child.getFirstChild();

                if(child.toString().equals("JSCallExpression") && firstChild != null && firstChild.toString().equals("JSReferenceExpression"))
                {
                    processJSCallExpression(child);
                }
                else if(child.toString().equals("PsiExpressionStatement"))
                {
                    processPsiExpressionStatement(child);
                }
                else if(child.toString().equals("JSReferenceExpression"))
                {
                    processReferenceExpression(child, child.getText());
                }
                else if(child.toString().equals("JSDocTagValue"))
                {
                    if(child.getText().contains("<"))
                    {
                        final String reference = child.getText().substring(child.getText().indexOf("<") + 1, child.getText().indexOf(">"));
                        processReferenceExpression(child, reference);
                    }
                    else if(child.getText().contains("."))
                    {
                        final String reference = child.getText().replace("{", "").replace("}", "");
                        processReferenceExpression(child, reference);
                    }
                }
                else
                {
                    if(child.toString().equals("JSLocalVariable"))
                    {
                        processVarStatement(child);
                    }
                    if(child.toString().equals("JSVariable"))
                    {
                        processVarStatement(child);
                    }
                    if(child.toString().equals("JSParameter"))
                    {
                        processVarStatement(child);
                    }
                    processElements(child);
                }
            }
        }

        private void processPsiExpressionStatement(PsiElement element)
        {
            boolean requireStatement = false;

            final PsiElement firstChild = element.getFirstChild();

            if(firstChild == null)
            {
                return;
            }

            for(PsiElement child : firstChild.getChildren())
            {
                if(child.toString().startsWith("PsiReferenceExpression") && child.getText().equals("goog.require"))
                {
                    requireStatement = true;
                }

                if(requireStatement)
                {
                    if(child.toString().startsWith("PsiExpressionList"))
                    {
                        for(PsiElement argument : child.getChildren())
                        {
                            if(argument.toString().startsWith("PsiLiteralExpression"))
                            {
                                requireSet.add(removeQuotes(argument.getText()));
                                lastRequireElement = element.getParent();
                                requireElementSet.add(element.getParent());
                                requireElementFound = true;
                            }
                        }
                    }
                }
            }
        }

        private void processJSCallExpression(final PsiElement element)
        {
            boolean requireStatement = false;
            boolean provideStatement = false;

            for(PsiElement child : element.getChildren())
            {
                if(child.toString().equals("JSReferenceExpression") && child.getText().equals("goog.require"))
                {
                    requireStatement = true;
                }

                if(child.toString().equals("JSReferenceExpression") && child.getText().equals("goog.provide"))
                {
                    provideStatement = true;
                }

                if(requireStatement)
                {
                    if(child.toString().equals("JSArgumentList"))
                    {
                        for(PsiElement argument : child.getChildren())
                        {
                            if(argument.toString().equals("JSLiteralExpression"))
                            {
                                String replace = removeQuotes(argument.getText());
                                requireSet.add(replace);
                                lastRequireElement = element.getParent();
                                requireElementSet.add(element.getParent());
                                requireElementFound = true;
                            }
                        }
                    }
                }

                if(provideStatement)
                {
                    if(child.toString().equals("JSArgumentList"))
                    {
                        for(PsiElement argument : child.getChildren())
                        {
                            if(argument.toString().equals("JSLiteralExpression"))
                            {
                                provideSet.add(removeQuotes(argument.getText()));
                                if(!requireElementFound)
                                {
                                    lastRequireElement = element.getParent();
                                }
                            }
                        }
                    }
                }

                if(!requireStatement && !provideStatement)
                {
                    if(child.toString().equals("JSReferenceExpression"))
                    {
                        PsiElement firstChild = child.getFirstChild();

                        if(firstChild != null && firstChild.toString().equals("JSCallExpression"))
                        {
                            processJSCallExpression(firstChild);
                        }
                        else
                        {
                            processReferenceExpression(child, child.getText());
                        }

                    }
                    else
                    {
                        processElements(child);
                    }
                }
            }
        }

        /**
         * removes both single and double quotes
         *
         * @param text string text
         * @return
         */
        private String removeQuotes(String text)
        {
            return text.replaceAll("\"|'", "");
        }

        private void processReferenceExpression(final PsiElement element, final String reference)
        {
            boolean localVar;
            boolean allowedKeyword;
            boolean provideKeyword;
            boolean includeLastElement;

            int index = reference.lastIndexOf(".");

            if(index != -1)
            {
                String definition = reference.substring(0, index);
                final String identifier = reference.substring(index + 1);

                Matcher matcher = CONSTANT_PATTERN.matcher(identifier);
                includeLastElement = !matcher.matches();

                if(includeLastElement)
                {
                    //Don't include method calls
                    includeLastElement = !element.getParent().toString().equals("JSCallExpression");
                }

                if(includeLastElement)
                {
                    matcher = CLASS_PATTERN.matcher(definition);
                    includeLastElement = !matcher.matches();
                }

                if(includeLastElement)
                {
                    String[] elements = reference.split("\\.");

                    boolean classFound = false;

                    for (String currentElement : elements)
                    {
                        matcher = CLASS_PATTERN.matcher(currentElement);
                        classFound = matcher.matches();
                        if (classFound)
                        {
                            break;
                        }
                    }

                    if (classFound)
                    {
                        matcher = CLASS_PATTERN.matcher(identifier);
                        includeLastElement = matcher.matches();
                    }
                }

                PsiElement elementToTest = null;

                if(includeLastElement)
                {
                    definition = reference;
                    elementToTest = element;

                    if(definition.contains(".") && !definition.startsWith("this") && !definition.contains(".prototype"))
                    {
                        String firstElement = definition.substring(0, definition.indexOf("."));

                        if(!shouldHighlight(firstElement) && !allowedKeywords.contains(firstElement))
                        {
                            return;
                        }

                    }
                }
                else
                {
                    if(definition.contains(".") && !definition.startsWith("this") && !definition.contains(".prototype"))
                    {
                        PsiElement firstChild = element.getFirstChild();
                        if(firstChild != null && firstChild.toString().equals("JSReferenceExpression"))
                        {
                            processReferenceExpression(firstChild, firstChild.getText());
                        }
                    }
                    else
                    {
                        elementToTest = element.getFirstChild();
                    }
                }

                if(shouldHighlight(definition) && elementToTest != null)
                {
                    highlightElement(elementToTest);
                }
            }
        }

        private boolean shouldHighlight(final String definition)
        {
            final boolean localVar;
            final boolean allowedKeyword;
            final boolean provideKeyword;
            final boolean imported;

            localVar = localVariableSet.contains(definition);
            allowedKeyword = allowedKeywords.contains(definition);
            provideKeyword = provideSet.contains(definition);
            imported = requireSet.contains(definition);

            return !(allowedKeyword || localVar || provideKeyword || imported || definition.startsWith("this") || definition.contains(".prototype"));
        }

        private void processVarStatement(final PsiElement element)
        {
            String localVar = element.getText();
            final int index = element.getText().indexOf(" ");
            if(index != -1)
            {
                localVar = element.getText().substring(0, index);
            }

            localVariableSet.add(localVar);
        }

        private void highlightElement(@NotNull PsiElement element)
        {
            errorElementSet.add(element);
            holder.registerProblem(element, "Reference needs goog.require statement", ProblemHighlightType.ERROR, singleQuickFix);
        }

        private void dumpElement(PsiElement element, String space, boolean dumpChildren)
        {
            System.out.println(space + element + "::" + element.getText());
            if(dumpChildren)
            {
                for(PsiElement child : element.getChildren())
                {
                    dumpElement(child, space + "    ", dumpChildren);
                }
            }
        }
    }

    private class SingleRequireStatementFix implements LocalQuickFix
    {
        @NotNull
        public String getName()
        {
            return "Add goog.require statement";
        }


        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor)
        {

            PsiElement element = descriptor.getPsiElement();
            PsiElementFactory factory = PsiElementFactory.SERVICE.getInstance(element.getProject());

            StringBuilder statement = new StringBuilder("goog.require(\"");
            String text = element.getText().replace("{", "").replace("}", "");
            if(text.contains("<"))
            {
                text = text.substring(text.indexOf("<") + 1, text.indexOf(">"));
            }
            statement.append(text);
            statement.append("\");");

            final PsiElement newElement = factory.createStatementFromText(statement.toString(), null);

            requireElementSet.add(newElement);

            for(PsiElement requireElement : requireElementSet)
            {
                lastRequireElement.getParent().addAfter(requireElement, lastRequireElement);
            }

            requireElementSet.remove(newElement);

            for(PsiElement requireElement : requireElementSet)
            {
                requireElement.delete();
            }
        }

        @NotNull
        public String getFamilyName()
        {
            return getName();
        }
    }

    private class MultipleRequireStatementFix implements LocalQuickFix
    {
        @NotNull
        public String getName()
        {
            return "Fix all goog.require statements";
        }


        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor)
        {
            PsiElementFactory factory = PsiElementFactory.SERVICE.getInstance(project);

            Set<PsiElement> newElements = new HashSet<PsiElement>();

            for(PsiElement errorElement : errorElementSet)
            {
                StringBuilder statement = new StringBuilder("goog.require(\"");
                statement.append(errorElement.getText().replace("{", "").replace("}", ""));
                statement.append("\");");


                PsiElement newElement = factory.createStatementFromText(statement.toString(), null);

                newElements.add(newElement);
                requireElementSet.add(newElement);
            }

            for(PsiElement requireElement : requireElementSet)
            {
                lastRequireElement.getParent().addAfter(requireElement, lastRequireElement);
            }

            for(PsiElement newElement : newElements)
            {
                requireElementSet.remove(newElement);
            }


            for(PsiElement requireElement : requireElementSet)
            {
                requireElement.delete();
            }

        }

        @NotNull
        public String getFamilyName()
        {
            return getName();
        }
    }

}
