// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.highlighter

import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.builtins.isFunctionTypeOrSubtype
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ConstructorDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.idea.highlighter.KotlinHighlightingColors.*
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.calls.model.VariableAsFunctionResolvedCall
import org.jetbrains.kotlin.resolve.calls.tasks.isDynamic
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameOrNull
import org.jetbrains.kotlin.serialization.deserialization.KOTLIN_SUSPEND_BUILT_IN_FUNCTION_FQ_NAME

internal class FunctionsHighlightingVisitor(holder: HighlightInfoHolder, bindingContext: BindingContext) :
    AfterAnalysisHighlightingVisitor(holder, bindingContext) {

    override fun visitBinaryExpression(expression: KtBinaryExpression) {
        if (expression.operationReference.getIdentifier() != null) {
            expression.getResolvedCall(bindingContext)?.let { resolvedCall ->
                highlightCall(expression.operationReference, resolvedCall)
            }
        }
        super.visitBinaryExpression(expression)
    }

    override fun visitCallExpression(expression: KtCallExpression) {
        val callee = expression.calleeExpression
        val resolvedCall = expression.getResolvedCall(bindingContext)
        if (callee is KtReferenceExpression && callee !is KtCallExpression && resolvedCall != null) {
            highlightCall(callee, resolvedCall)
        }

        super.visitCallExpression(expression)
    }

    private fun highlightCall(callee: PsiElement, resolvedCall: ResolvedCall<out CallableDescriptor>) {
        val calleeDescriptor = resolvedCall.resultingDescriptor

        val extensions = HighlighterExtension.EP_NAME.extensionList

        (extensions.firstNotNullOfOrNull { extension ->
            extension.highlightCall(callee, resolvedCall)
        } ?: when {
            calleeDescriptor.fqNameOrNull() == KOTLIN_SUSPEND_BUILT_IN_FUNCTION_FQ_NAME -> KEYWORD
            calleeDescriptor.isDynamic() -> DYNAMIC_FUNCTION_CALL
            calleeDescriptor is FunctionDescriptor && calleeDescriptor.isSuspend -> SUSPEND_FUNCTION_CALL
            resolvedCall is VariableAsFunctionResolvedCall -> {
                val container = calleeDescriptor.containingDeclaration
                val containedInFunctionClassOrSubclass = container is ClassDescriptor && container.defaultType.isFunctionTypeOrSubtype
                if (containedInFunctionClassOrSubclass)
                    VARIABLE_AS_FUNCTION_CALL
                else
                    VARIABLE_AS_FUNCTION_LIKE_CALL
            }
            calleeDescriptor is ConstructorDescriptor -> CONSTRUCTOR_CALL
            calleeDescriptor !is FunctionDescriptor -> null
            calleeDescriptor.extensionReceiverParameter != null -> EXTENSION_FUNCTION_CALL
            DescriptorUtils.isTopLevelDeclaration(calleeDescriptor) -> PACKAGE_FUNCTION_CALL
            else -> FUNCTION_CALL
        })?.let { key ->
            highlightName(callee, key)
        }
    }
}
