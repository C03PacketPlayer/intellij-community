// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.ml.features

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.internal.statistic.eventLog.events.*
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.textMatching.PrefixMatchingType
import com.intellij.textMatching.PrefixMatchingUtil
import kotlin.math.round

internal abstract class SearchEverywhereElementFeaturesProvider(private val supportedContributorIds: List<String>) {
  constructor(vararg supportedTabs: Class<out SearchEverywhereContributor<*>>) : this(supportedTabs.map { it.simpleName })

  companion object {
    val EP_NAME: ExtensionPointName<SearchEverywhereElementFeaturesProvider>
      = ExtensionPointName.create("com.intellij.searcheverywhere.ml.searchEverywhereElementFeaturesProvider")

    fun getFeatureProviders(): List<SearchEverywhereElementFeaturesProvider> {
      return EP_NAME.extensionList
    }

    fun getFeatureProvidersForContributor(contributorId: String): List<SearchEverywhereElementFeaturesProvider> {
      return EP_NAME.extensionList.filter { it.isContributorSupported(contributorId) || it.isApplicableToEveryContributor }
    }

    internal val nameFeatureToField = hashMapOf<String, EventField<*>>(
      "prefix_same_start_count" to EventFields.Int("${PrefixMatchingUtil.baseName}SameStartCount"),
      "prefix_greedy_score" to EventFields.Double("${PrefixMatchingUtil.baseName}GreedyScore"),
      "prefix_greedy_with_case_score" to EventFields.Double("${PrefixMatchingUtil.baseName}GreedyWithCaseScore"),
      "prefix_matched_words_score" to EventFields.Double("${PrefixMatchingUtil.baseName}MatchedWordsScore"),
      "prefix_matched_words_relative" to EventFields.Double("${PrefixMatchingUtil.baseName}MatchedWordsRelative"),
      "prefix_matched_words_with_case_score" to EventFields.Double("${PrefixMatchingUtil.baseName}MatchedWordsWithCaseScore"),
      "prefix_matched_words_with_case_relative" to EventFields.Double("${PrefixMatchingUtil.baseName}MatchedWordsWithCaseRelative"),
      "prefix_skipped_words" to EventFields.Int("${PrefixMatchingUtil.baseName}SkippedWords"),
      "prefix_matching_type" to EventFields.String(
        "${PrefixMatchingUtil.baseName}MatchingType", PrefixMatchingType.values().map { it.name }
      ),
      "prefix_exact" to EventFields.Boolean("${PrefixMatchingUtil.baseName}Exact"),
      "prefix_matched_last_word" to EventFields.Boolean("${PrefixMatchingUtil.baseName}MatchedLastWord"),
    )
  }

  /**
   * If a feature provider is applicable to every contributor of Search Everywhere,
   * instead of specifying each one in the constructor, this value can be overriden
   * and set to true.
   */
  open val isApplicableToEveryContributor: Boolean = false

  /**
   * Returns true if the Search Everywhere contributor is supported by the feature provider.
   */
  fun isContributorSupported(contributorId: String): Boolean {
    return supportedContributorIds.contains(contributorId)
  }

  abstract fun getFeaturesDeclarations(): List<EventField<*>>

  abstract fun getElementFeatures(element: Any,
                                  currentTime: Long,
                                  searchQuery: String,
                                  elementPriority: Int,
                                  cache: FeaturesProviderCache?): List<EventPair<*>>

  internal fun addIfTrue(result: MutableList<EventPair<*>>, key: BooleanEventField, value: Boolean) {
    if (value) {
      result.add(key.with(true))
    }
  }

  protected fun withUpperBound(value: Int): Int {
    if (value > 100) return 101
    return value
  }

  internal fun roundDouble(value: Double): Double {
    if (!value.isFinite()) return -1.0
    return round(value * 100000) / 100000
  }

  /**
   * Associates the specified key with the value, only if the value is not null.
   */
  protected fun <T> MutableList<EventPair<*>>.putIfValueNotNull(key: EventField<T>, value: T?) {
    value?.let {
      add(key.with(it))
    }
  }

  protected fun getNameMatchingFeatures(nameOfFoundElement: String, searchQuery: String): Collection<EventPair<*>> {
    val features = mutableMapOf<String, Any>()
    PrefixMatchingUtil.calculateFeatures(nameOfFoundElement, searchQuery, features)
    val result = features.mapNotNull { (key, value) ->
      val field = nameFeatureToField[key]
      if (value is Boolean && field is BooleanEventField) {
        return@mapNotNull field.with(value)
      }
      else if (value is Double && field is DoubleEventField) {
        return@mapNotNull field.with(roundDouble(value))
      }
      else if (value is Int && field is IntEventField) {
        return@mapNotNull field.with(value)
      }
      else if (value is Enum<*> && field is StringEventField) {
        return@mapNotNull field.with(value.toString())
      }
      return@mapNotNull null
    }
    return result
  }
}