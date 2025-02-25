// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.jvmDecompiler

import com.intellij.codeInsight.AttachSourcesProvider
import com.intellij.ide.highlighter.JavaClassFileType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.util.ActionCallback
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.base.util.KotlinPlatformUtils
import org.jetbrains.kotlin.psi.KtFile

class DecompileKotlinToJavaAction : AnAction(KotlinJvmDecompilerBundle.message("action.decompile.java.name")) {
    override fun actionPerformed(e: AnActionEvent) {
        val binaryFile = getBinaryKotlinFile(e) ?: return

        KotlinJvmDecompilerFacadeImpl.showDecompiledCode(binaryFile)
    }

    override fun update(e: AnActionEvent) {
        when {
            KotlinPlatformUtils.isCidr -> e.presentation.isEnabledAndVisible = false
            else -> e.presentation.isEnabled = getBinaryKotlinFile(e) != null
        }
    }

    private fun getBinaryKotlinFile(e: AnActionEvent): KtFile? {
        val file = e.getData(CommonDataKeys.PSI_FILE) as? KtFile ?: return null
        if (!file.canBeDecompiledToJava()) return null

        return file
    }

}

fun KtFile.canBeDecompiledToJava() = isCompiled && virtualFile?.fileType == JavaClassFileType.INSTANCE

// Add action to "Attach sources" notification panel
class DecompileKotlinToJavaActionProvider : AttachSourcesProvider {
    override fun getActions(
        orderEntries: MutableList<LibraryOrderEntry>,
        psiFile: PsiFile
    ): Collection<AttachSourcesProvider.AttachSourcesAction> {
        if (psiFile !is KtFile || !psiFile.canBeDecompiledToJava()) return emptyList()

        return listOf(object : AttachSourcesProvider.AttachSourcesAction {
            override fun getName() = KotlinJvmDecompilerBundle.message("action.decompile.java.name")

            override fun perform(orderEntriesContainingFile: List<LibraryOrderEntry>?): ActionCallback {
                KotlinJvmDecompilerFacadeImpl.showDecompiledCode(psiFile)
                return ActionCallback.DONE
            }

            override fun getBusyText() = KotlinJvmDecompilerBundle.message("action.decompile.busy.text")
        })
    }
}
