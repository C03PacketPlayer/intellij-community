// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.nj2k.inference

import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiComment
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.util.application.runWriteAction
import org.jetbrains.kotlin.nj2k.inference.common.InferenceFacade
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import java.io.File

abstract class AbstractConstraintCollectorTest : KotlinLightCodeInsightFixtureTestCase() {
    abstract fun createInferenceFacade(resolutionFacade: ResolutionFacade): InferenceFacade

    fun doTest(path: String) {
        val file = File(path)
        val text = FileUtil.loadFile(file, true)
        val ktFile = myFixture.configureByText("converterTestFile.kt", text) as KtFile
        val resolutionFacade = ktFile.getResolutionFacade()
        CommandProcessor.getInstance().runUndoTransparentAction {
            ktFile.prepareFile()
            createInferenceFacade(resolutionFacade).runOn(listOf(ktFile))
            ktFile.afterInference()
        }
        KotlinTestUtils.assertEqualsToFile(file, ktFile.text)
    }

    open fun KtFile.prepareFile() = runWriteAction {
        deleteComments()
    }

    open fun KtFile.afterInference() = Unit

    fun KtFile.deleteComments() {
        for (comment in collectDescendantsOfType<PsiComment>()) {
            if (!comment.text.startsWith("// RUNTIME_WITH_FULL_JDK")) {
                comment.delete()
            }
        }
    }

    override fun getProjectDescriptor(): LightProjectDescriptor = KotlinWithJdkAndRuntimeLightProjectDescriptor.INSTANCE
}