// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.gradleJava.configuration.kpm

import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.kotlin.gradle.kpm.idea.IdeaKotlinFragment
import org.jetbrains.kotlin.gradle.kpm.idea.IdeaKotlinFragmentCoordinates
import org.jetbrains.kotlin.util.capitalizeDecapitalize.capitalizeAsciiOnly
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import java.io.File


private val IdeaKotlinFragmentCoordinates.fullName
    get() = fragmentName + (module.moduleName).capitalizeAsciiOnly()

private val IdeaKotlinFragment.isTestFragment
    get() = coordinates.module.moduleName == "test"

internal fun calculateKotlinFragmentModuleId(
    gradleModule: IdeaModule,
    fragment: IdeaKotlinFragmentCoordinates,
    resolverCtx: ProjectResolverContext
): String =
    GradleProjectResolverUtil.getModuleId(resolverCtx, gradleModule) + ":" + fragment.fullName

fun IdeaKotlinFragment.computeSourceType(): ExternalSystemSourceType =
    if (isTestFragment) ExternalSystemSourceType.TEST else ExternalSystemSourceType.SOURCE

fun IdeaKotlinFragment.computeResourceType(): ExternalSystemSourceType =
    if (isTestFragment) ExternalSystemSourceType.TEST_RESOURCE else ExternalSystemSourceType.RESOURCE

val IdeaKotlinFragment.sourceDirs: Collection<File>
    get() = sourceDirectories.map { it.file }
val IdeaKotlinFragment.resourceDirs: Collection<File>
    get() = resourceDirectories.map { it.file }
