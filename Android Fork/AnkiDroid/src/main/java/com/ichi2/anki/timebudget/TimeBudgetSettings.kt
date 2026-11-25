// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.timebudget

import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.DeckId
import org.json.JSONArray
import org.json.JSONObject

/**
 * RWKV-Instant time budgets.
 *
 * A deck with a time budget (and its subdecks) is studied by predicted recall instead of due
 * date. The study queue itself is built by the backend (rslib/src/scheduler/time_budget); this
 * file only reads and writes the settings, which live in the collection config under
 * [CONFIG_KEY] and so sync with the desktop app. Keep the format in step with
 * qt/aqt/time_budget.py.
 */
const val CONFIG_KEY = "rwkvTimeBudget"

const val DEFAULT_MINUTES = 30
const val DEFAULT_MAX_REVIEWS_PER_CARD = 3
const val DEFAULT_MIN_DR = 60
const val DEFAULT_MAX_DR = 95

/** Groups of cards a session draws from; see CardGroup in rslib/src/scheduler/time_budget. */
enum class CardGroup(
    val key: String,
) {
    FIRST_REVIEW("firstReview"),
    NEW_CARDS("new"),
    RE_REVIEW("reReview"),
    ;

    companion object {
        val DEFAULT_ORDER = listOf(FIRST_REVIEW, NEW_CARDS, RE_REVIEW)

        fun fromKey(key: String): CardGroup? = entries.firstOrNull { it.key == key }

        /** Each known group once: [order] first, then the rest in the default order. */
        fun completeOrder(order: List<CardGroup>): List<CardGroup> = (order + DEFAULT_ORDER).distinct()
    }
}

data class DeckBudget(
    val enabled: Boolean = true,
    val minutes: Int = DEFAULT_MINUTES,
)

/** The time per deck, and the limits shared by all enabled decks. */
data class TimeBudgetSettings(
    val decks: Map<DeckId, DeckBudget> = emptyMap(),
    val maxReviewsPerCard: Int = DEFAULT_MAX_REVIEWS_PER_CARD,
    val minDr: Int = DEFAULT_MIN_DR,
    val maxDr: Int = DEFAULT_MAX_DR,
    val order: List<CardGroup> = CardGroup.DEFAULT_ORDER,
    /** Stop new cards at the daily new card limits of the deck and its subdecks. */
    val limitNewCards: Boolean = false,
) {
    /**
     * Writes these settings into [original], so keys this version does not know about (eg.
     * added by a newer desktop version) are kept.
     */
    fun toJson(original: JSONObject = JSONObject()): JSONObject {
        val decksJson = JSONObject()
        for ((did, deck) in decks) {
            decksJson.put(
                did.toString(),
                JSONObject().put("enabled", deck.enabled).put("minutes", deck.minutes),
            )
        }
        return JSONObject(original.toString())
            .put("decks", decksJson)
            .put("maxReviewsPerCard", maxReviewsPerCard)
            .put("minDr", minDr)
            .put("maxDr", maxDr)
            .put("order", JSONArray(order.map { it.key }))
            .put("limitNewCards", limitNewCards)
    }

    companion object {
        fun fromJson(data: JSONObject): TimeBudgetSettings {
            val decks = mutableMapOf<DeckId, DeckBudget>()
            data.optJSONObject("decks")?.let { decksJson ->
                for (key in decksJson.keys()) {
                    val did = key.toLongOrNull() ?: continue
                    val deck = decksJson.optJSONObject(key) ?: continue
                    decks[did] =
                        DeckBudget(
                            enabled = deck.optBoolean("enabled", true),
                            minutes = deck.optInt("minutes", DEFAULT_MINUTES),
                        )
                }
            }
            val order =
                data.optJSONArray("order")?.let { array ->
                    (0 until array.length()).mapNotNull { CardGroup.fromKey(array.optString(it)) }
                } ?: emptyList()
            return TimeBudgetSettings(
                decks = decks,
                maxReviewsPerCard = data.optInt("maxReviewsPerCard", DEFAULT_MAX_REVIEWS_PER_CARD),
                minDr = data.optInt("minDr", DEFAULT_MIN_DR),
                maxDr = data.optInt("maxDr", DEFAULT_MAX_DR),
                order = CardGroup.completeOrder(order),
                limitNewCards = data.optBoolean("limitNewCards", false),
            )
        }
    }
}

fun Collection.timeBudgetSettingsJson(): JSONObject = config.getObject(CONFIG_KEY, JSONObject())

fun Collection.loadTimeBudgetSettings(): TimeBudgetSettings = TimeBudgetSettings.fromJson(timeBudgetSettingsJson())

fun Collection.saveTimeBudgetSettings(settings: TimeBudgetSettings) {
    config.set(CONFIG_KEY, settings.toJson(timeBudgetSettingsJson()))
}
