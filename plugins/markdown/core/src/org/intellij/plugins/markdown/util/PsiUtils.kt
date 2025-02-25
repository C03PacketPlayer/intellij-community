// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.util

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiUtilCore
import com.intellij.psi.util.siblings

internal fun PsiElement.hasType(type: IElementType) = PsiUtilCore.getElementType(this) == type
internal fun PsiElement.hasType(type: TokenSet) =  PsiUtilCore.getElementType(this) in type

internal fun ASTNode.hasType(type: IElementType) = PsiUtilCore.getElementType(this) == type
internal fun ASTNode.hasType(type: TokenSet) = PsiUtilCore.getElementType(this) in type

internal fun PsiElement.children(): Sequence<PsiElement> {
  return childrenOrNull().orEmpty()
}

internal fun PsiElement.childrenOrNull(): Sequence<PsiElement>? {
  return firstChild?.siblings(forward = true, withSelf = true)
}
