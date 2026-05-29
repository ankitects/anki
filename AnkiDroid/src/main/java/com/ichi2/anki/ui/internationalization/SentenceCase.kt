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

    context(_: Context)
    val customStudy get() = TR.actionsCustomStudy().toSentenceCase(R.string.sentence_custom_study)

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
    val toggleCardsNotes get() = TR.browsingToggleShowingCardsNotes().toSentenceCase(R.string.sentence_toggle_cards_notes)

    context(_: Fragment)
    val toggleSuspend get() = TR.browsingToggleSuspend().toSentenceCase(R.string.sentence_toggle_suspend)

    context(_: Fragment)
    val findAndReplace get() = TR.browsingFindAndReplace().toSentenceCase(R.string.sentence_find_and_replace)

    context(_: Context)
    val frontTemplate get() = TR.cardTemplatesFrontTemplate().toSentenceCase(R.string.sentence_front_template)

    context(_: Context)
    val backTemplate get() = TR.cardTemplatesBackTemplate().toSentenceCase(R.string.sentence_back_template)

    context(_: Context)
    val renameDeck get() = TR.actionsRenameDeck().toSentenceCase(R.string.sentence_rename_deck)

    context(_: Context)
    val deckOptions get() = TR.deckConfigTitle().toSentenceCase(R.string.sentence_deck_options)

    context(_: Context)
    val deleteDeck get() = TR.decksDeleteDeck().toSentenceCase(R.string.sentence_delete_deck)

    context(_: Context)
    val logIn get() = TR.syncLogInButton().toSentenceCase(R.string.sentence_log_in)

    context(_: Fragment)
    val logIn get() = TR.syncLogInButton().toSentenceCase(R.string.sentence_log_in)

    context(_: Context)
    val logOut get() = TR.syncLogOutButton().toSentenceCase(R.string.sentence_log_out)

    context(_: Fragment)
    val logOut get() = TR.syncLogOutButton().toSentenceCase(R.string.sentence_log_out)

    context(_: Context)
    val cardInfo get() = TR.actionsCardInfo().toSentenceCase(R.string.sentence_card_info)

    context(_: Fragment)
    val cardInfo get() = TR.actionsCardInfo().toSentenceCase(R.string.sentence_card_info)

    context(_: Context)
    val buryNote get() = TR.studyingBuryNote().toSentenceCase(R.string.sentence_bury_note)

    context(_: Fragment)
    val buryNote get() = TR.studyingBuryNote().toSentenceCase(R.string.sentence_bury_note)

    context(_: Context)
    val buryCard get() = TR.studyingBuryCard().toSentenceCase(R.string.sentence_bury_card)

    context(_: Fragment)
    val buryCard get() = TR.studyingBuryCard().toSentenceCase(R.string.sentence_bury_card)

    context(_: Context)
    val suspendNote get() = TR.studyingSuspendNote().toSentenceCase(R.string.sentence_suspend_note)

    context(_: Fragment)
    val suspendNote get() = TR.studyingSuspendNote().toSentenceCase(R.string.sentence_suspend_note)

    context(_: Context)
    val suspendCard get() = TR.actionsSuspendCard().toSentenceCase(R.string.sentence_suspend_card)

    context(_: Fragment)
    val suspendCard get() = TR.actionsSuspendCard().toSentenceCase(R.string.sentence_suspend_card)

    context(_: Context)
    val markNote get() = TR.studyingMarkNote().toSentenceCase(R.string.sentence_mark_note)

    context(_: Fragment)
    val markNote get() = TR.studyingMarkNote().toSentenceCase(R.string.sentence_mark_note)

    context(_: Context)
    val deleteNote get() = TR.studyingDeleteNote().toSentenceCase(R.string.sentence_delete_note)

    context(_: Fragment)
    val deleteNote get() = TR.studyingDeleteNote().toSentenceCase(R.string.sentence_delete_note)

    context(_: Context)
    val previousCardInfo get() = TR.actionsPreviousCardInfo().toSentenceCase(R.string.sentence_actions_previous_card_info)

    context(_: Fragment)
    val previousCardInfo get() = TR.actionsPreviousCardInfo().toSentenceCase(R.string.sentence_actions_previous_card_info)

    context(_: Context)
    val ankiWebAccount get() = TR.preferencesAccount().toSentenceCase(R.string.sentence_ankiweb_account)

    context(_: Fragment)
    val ankiWebAccount get() = TR.preferencesAccount().toSentenceCase(R.string.sentence_ankiweb_account)

    context(_: Context)
    val browserAppearance get() = TR.browsingBrowserAppearance().toSentenceCase(R.string.sentence_browser_appearance)

    context(_: Fragment)
    val browserAppearance get() = TR.browsingBrowserAppearance().toSentenceCase(R.string.sentence_browser_appearance)

    // TR aboutCopyDebugInfo() is a duplicate
    context(_: Context)
    val copyDebugInfo get() = TR.errorsCopyDebugInfoButton().toSentenceCase(R.string.sentence_copy_debug_info)

    context(_: Fragment)
    val copyDebugInfo get() = TR.errorsCopyDebugInfoButton().toSentenceCase(R.string.sentence_copy_debug_info)

    context(_: Context)
    val addField get() = TR.fieldsAddField().toSentenceCase(R.string.sentence_add_field)

    context(_: Context)
    val allDecks get() = TR.exportingAllDecks().toSentenceCase(R.string.sentence_all_decks)

    context(_: Fragment)
    val allDecks get() = TR.exportingAllDecks().toSentenceCase(R.string.sentence_all_decks)

    context(_: Fragment)
    val browserOptions get() = TR.browsingBrowserOptions().toSentenceCase(R.string.sentence_browser_options)
}

/**
 * Provides properties converting from Anki Desktop's 'Title Case' strings to AnkiDroid's
 * 'Sentence case' strings.
 *
 * Sentence case is a material design guideline
 */
@Suppress("UnusedReceiverParameter")
val GeneratedTranslations.sentenceCase get() = SentenceCase
