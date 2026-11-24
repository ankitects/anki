// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.utils.ext

import anki.config.ConfigKey
import com.ichi2.anki.browser.search.SavedSearch
import com.ichi2.anki.browser.search.toMap
import com.ichi2.anki.common.utils.ConfigProperty
import com.ichi2.anki.common.utils.configProperty
import com.ichi2.anki.common.utils.ext.AddingDefaultsMode.DECIDE_BY_NOTE_TYPE
import com.ichi2.anki.common.utils.ext.AddingDefaultsMode.USE_CURRENT_DECK
import com.ichi2.anki.common.utils.jsonConfigProperty
import com.ichi2.anki.common.utils.orDefault
import com.ichi2.anki.libanki.BrowserConfig
import com.ichi2.anki.libanki.Config
import com.ichi2.anki.libanki.Consts
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.Decks.Companion.CURRENT_DECK
import com.ichi2.anki.model.CardsOrNotes
import org.json.JSONObject

/**
 * How the initial deck and note type are chosen when adding a note.
 *
 * @see USE_CURRENT_DECK
 * @see DECIDE_BY_NOTE_TYPE
 */
enum class AddingDefaultsMode {
    /** Start with the selected study deck and its remembered note type. */
    USE_CURRENT_DECK,

    /** Start with the current note type and its remembered destination deck. */
    DECIDE_BY_NOTE_TYPE,
}

// TODO: Extract module-specific config to modules (when they exist).

/**
 * @see AddingDefaultsMode
 */
var Config.addingDefaultsMode by configProperty(ConfigKey.Bool.ADDING_DEFAULTS_TO_CURRENT_DECK).mapped(
    decode = { if (it) USE_CURRENT_DECK else DECIDE_BY_NOTE_TYPE },
    encode = { it == USE_CURRENT_DECK },
)

/**
 * When enabled, simple text searches automatically ignore accents.
 *
 * When enabled, both 'uber' and 'über' match `["uber", "über", "Über"]`.
 *
 * The default for `IGNORE_ACCENTS_IN_SEARCH` is `false` upstream.
 *
 * [Manual: Searching - Ignoring accents/combining characters](https://docs.ankiweb.net/searching.html#ignoring-accentscombining-characters)
 * [Added in Anki#1667](https://github.com/ankitects/anki/pull/1667)
 * [source](https://github.com/ankitects/anki/blob/main/rslib/src/config/bool.rs)
 */
var Config.ignoreAccentsInSearch by configProperty(ConfigKey.Bool.IGNORE_ACCENTS_IN_SEARCH)

/** Whether pasted images are saved as PNG. */
var Config.pasteImagesAsPng by configProperty(ConfigKey.Bool.PASTE_IMAGES_AS_PNG)

/** Whether rendered cards hide their audio play buttons. */
val Config.hideAudioPlayButtons by configProperty(ConfigKey.Bool.HIDE_AUDIO_PLAY_BUTTONS)

/**
 * The `Custom scheduling` global setting in deck options.
 *
 * The default for `CARD_STATE_CUSTOMIZER` is `""` (empty string) upstream.
 *
 * [source](https://github.com/ankitects/anki/blob/main/rslib/src/config/string.rs)
 */
var Config.cardStateCustomizer by configProperty(ConfigKey.String.CARD_STATE_CUSTOMIZER)

/** Whether the browser displays cards or notes. */
var Config.cardsOrNotes by configProperty(ConfigKey.Bool.BROWSER_TABLE_SHOW_NOTES_MODE).mapped(
    decode = { if (it) CardsOrNotes.NOTES else CardsOrNotes.CARDS },
    encode = { it == CardsOrNotes.NOTES },
)

/** The stored selected deck ID; the referenced deck may no longer exist. */
val Config.currentDeckId by jsonConfigProperty<DeckId>(CURRENT_DECK).orDefault(Consts.DEFAULT_DECK_ID)

/** Stored scheduler version, defaulting to the legacy scheduler when unavailable. */
val Config.schedulerVersion by jsonConfigProperty<Long>("schedVer").orDefault(1L)

/** Hour of day at which the scheduler starts a new day. */
val Config.rolloverHour by jsonConfigProperty<Int>("rollover").orDefault(4)

/** Whether answer buttons display the next review interval. */
val Config.showIntervalsOnButtons by jsonConfigProperty<Boolean>("estTimes").orDefault(true)

/** Whether the reviewer displays remaining card counts. */
val Config.showRemainingDueCounts by jsonConfigProperty<Boolean>("dueCounts").orDefault(true)

/** Whether the FSRS scheduler is enabled; false when absent, `null` when the stored value cannot be decoded. */
val Config.fsrsEnabled by jsonConfigProperty("fsrs", missingValue = false)

/** User-defined flag labels, keyed by the flag number as a string. */
// TODO: Strongly type FlagLabels in anki-common
var Config.flagLabels by ConfigProperty(
    // Construct a fresh object on each read, including when the stored value is invalid.
    read = { getObject("flagLabels", JSONObject()) },
    write = { set("flagLabels", it) },
)

/**
 * Saved searches in the collection. Names are case-sensitive.
 * Anki Desktop orders them by name, regardless of the order written here.
 *
 * Defaults to an empty map when the `savedFilters` collection config key is absent.
 *
 * [source](https://github.com/ankitects/anki/blob/main/qt/aqt/browser/sidebar/tree.py)
 * @see SavedSearch
 */
var Config.savedFilters by jsonConfigProperty<Map<String, String>>("savedFilters").orDefault(emptyMap()).mapped(
    decode = { values -> values.map { (name, query) -> SavedSearch(name, query) } },
    encode = { values -> values.toMap() },
)

/** Column key used to sort the browser in cards mode. */
var Config.browserSortCardsColumn by jsonConfigProperty<String>(BrowserConfig.CARDS_SORT_COLUMN_KEY).orDefault("noteFld")

/** Column key used to sort the browser in notes mode. */
var Config.browserSortNotesColumn by jsonConfigProperty<String>(BrowserConfig.NOTES_SORT_COLUMN_KEY).orDefault("noteFld")

/** Whether the cards-mode browser sort is reversed. */
var Config.browserSortCardsBackwards by jsonConfigProperty<Boolean>(BrowserConfig.CARDS_SORT_BACKWARDS_KEY).orDefault(false)

/** Whether the notes-mode browser sort is reversed. */
var Config.browserSortNotesBackwards by jsonConfigProperty<Boolean>(BrowserConfig.NOTES_SORT_BACKWARDS_KEY).orDefault(false)
