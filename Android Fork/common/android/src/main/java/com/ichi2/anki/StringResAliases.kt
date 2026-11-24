// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

/**
 * String resources: `getString(CommonString.card_browser)`.
 *
 * A plain import (`import com.ichi2.anki.CommonString`), which the IDE auto-inserts,
 * unlike `as` alias imports. Files in package `com.ichi2.anki` need no import.
 */
typealias CommonString = com.ichi2.anki.common.android.R.string

/**
 * Non-translatable preference keys: `getString(CommonPreferenceKey.pref_new_review_reminders)`.
 *
 * Shares the generated resource type with [CommonString]; enforced by lint
 * (`CommonPreferenceKeyDetector`).
 */
typealias CommonPreferenceKey = com.ichi2.anki.common.android.R.string

/** Plural resources: `getQuantityString(CommonPlurals.widget_cards_due, n, n)`. */
typealias CommonPlurals = com.ichi2.anki.common.android.R.plurals
