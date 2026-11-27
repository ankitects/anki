// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.timebudget

import com.ichi2.anki.libanki.Collection
import org.json.JSONObject

/**
 * RWKV settings that apply outside of time budgets.
 *
 * With [allDecks], a deck that no time budget covers is studied weakest-first as well: its due
 * cards are shown lowest predicted recall first, within the deck's usual daily limits. The
 * scheduling is done by the backend (rslib/src/scheduler/rwkv_options.rs); this file only reads
 * and writes the settings, which live in the collection config under [RWKV_OPTIONS_KEY] and so
 * sync with the desktop app. Keep the format in step with qt/aqt/rwkv_options.py.
 */
const val RWKV_OPTIONS_KEY = "rwkvOptions"

data class RwkvOptions(
    /** Order every deck's queue by predicted recall, not only the decks with a time budget. */
    val allDecks: Boolean = false,
) {
    /**
     * Writes these options into [original], so keys this version does not know about (eg. added
     * by a newer desktop version) are kept.
     */
    fun toJson(original: JSONObject = JSONObject()): JSONObject = JSONObject(original.toString()).put("allDecks", allDecks)

    companion object {
        fun fromJson(data: JSONObject): RwkvOptions = RwkvOptions(allDecks = data.optBoolean("allDecks", false))
    }
}

fun Collection.rwkvOptionsJson(): JSONObject = config.getObject(RWKV_OPTIONS_KEY, JSONObject())

fun Collection.loadRwkvOptions(): RwkvOptions = RwkvOptions.fromJson(rwkvOptionsJson())

fun Collection.saveRwkvOptions(options: RwkvOptions) {
    config.set(RWKV_OPTIONS_KEY, options.toJson(rwkvOptionsJson()))
}
