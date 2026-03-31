/*
 *  Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.ui.internationalization

import android.content.Context
import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import anki.i18n.GeneratedTranslations
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R

// Functions for handling a move from 'Title Case' in Anki Desktop to 'Sentence case' in AnkiDroid

/**
 * Converts a string to sentence case if it matches the provided resource in `sentence-case.xml`
 *
 * ```
 * "Toggle Suspend".toSentenceCase(R.string.sentence_toggle_suspend) // "Toggle suspend"
 * ```
 */
context(context: Context)
fun String.toSentenceCase(
    @StringRes resId: Int,
) = toSentenceCase(context, resId)

context(fragment: Fragment)
fun String.toSentenceCase(
    @StringRes resId: Int,
): String = toSentenceCase(fragment.requireContext(), resId)

fun String.toSentenceCase(
    context: Context,
    @StringRes resId: Int,
): String {
    val resString = context.getString(resId)
    // lowercase both for the comparison: sentence case doesn't mean all words are lowercase
    if (this.equals(resString, ignoreCase = true)) return resString
    return this
}

fun String.toSentenceCase(
    resources: Resources,
    @StringRes resId: Int,
): String {
    val resString = resources.getString(resId)
    // lowercase both for the comparison: sentence case doesn't mean all words are lowercase
    if (this.equals(resString, ignoreCase = true)) return resString
    return this
}

/**
 * Provides properties converting from Anki Desktop's 'Title Case' strings to AnkiDroid's
 * 'Sentence case' strings.
 *
 * Sentence case is a material design guideline
 */
// TODO: Expand for all past properties
object SentenceCase {
    context(_: Context)
    val addNoteType get() = TR.notetypesAddNoteType().toSentenceCase(R.string.sentence_add_note_type)

    context(_: Fragment)
    val changeNoteType get() = TR.browsingChangeNotetype().toSentenceCase(R.string.sentence_change_note_type)

    context(_: Context)
    val checkDatabase get() = TR.databaseCheckTitle().toSentenceCase(R.string.sentence_check_db)

    context(_: Fragment)
    val checkDatabase get() = TR.databaseCheckTitle().toSentenceCase(R.string.sentence_check_db)

    context(_: Context)
    val checkMediaTitle get() = TR.mediaCheckWindowTitle().toSentenceCase(R.string.sentence_check_media)

    context(_: Fragment)
    val checkMediaTitle get() = TR.mediaCheckWindowTitle().toSentenceCase(R.string.sentence_check_media)

    context(_: Context)
    val checkMediaAction get() = TR.mediaCheckCheckMediaAction().toSentenceCase(R.string.sentence_check_media)
    context(_: Fragment)
    val checkMediaAction get() = TR.mediaCheckCheckMediaAction().toSentenceCase(R.string.sentence_check_media)

    context(_: Fragment)
    val customStudy get() = TR.actionsCustomStudy().toSentenceCase(R.string.sentence_custom_study)

    context(_: Fragment)
    val emptyCards get() = TR.emptyCardsWindowTitle().toSentenceCase(R.string.sentence_empty_cards)
    context(_: Fragment)
    val emptyTrash get() = TR.mediaCheckEmptyTrash().toSentenceCase(R.string.sentence_empty_trash)

    context(_: Fragment)
    val gradeNow get() = TR.actionsGradeNow().toSentenceCase(R.string.sentence_grade_now)

    context(_: Context)
    val mediaSyncLog get() = TR.syncMediaLogTitle().toSentenceCase(R.string.sentence_sync_media_log)

    context(_: Fragment)
    val restoreDeleted get() = TR.mediaCheckRestoreTrash().toSentenceCase(R.string.sentence_restore_deleted)

    context(_: Fragment)
    val restoreToDefault get() = TR.cardTemplatesRestoreToDefault().toSentenceCase(R.string.sentence_restore_to_default)
    context(_: Context)
    val restoreToDefault get() = TR.cardTemplatesRestoreToDefault().toSentenceCase(R.string.sentence_restore_to_default)

    context(_: Context)
    val setDueDate get() = TR.actionsSetDueDate().toSentenceCase(R.string.sentence_set_due_date)

    context(_: Fragment)
    val setDueDate get() = TR.actionsSetDueDate().toSentenceCase(R.string.sentence_set_due_date)

    context(_: Fragment)
    val toggleBury get() = TR.browsingToggleBury().toSentenceCase(R.string.sentence_toggle_bury)

    context(_: Fragment)
    val toggleSuspend get() = TR.browsingToggleSuspend().toSentenceCase(R.string.sentence_toggle_suspend)

    context(_: Fragment)
    val findAndReplace get() = TR.browsingFindAndReplace().toSentenceCase(R.string.sentence_find_and_replace)
}

/**
 * Provides properties converting from Anki Desktop's 'Title Case' strings to AnkiDroid's
 * 'Sentence case' strings.
 *
 * Sentence case is a material design guideline
 */
@Suppress("UnusedReceiverParameter")
val GeneratedTranslations.sentenceCase get() = SentenceCase
