// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.internationalization

import android.content.Context
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import anki.i18n.GeneratedTranslations
import anki.i18n.PreviewTranslations
import anki.i18n.TranslateArgMap
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.ankicommon.R

/**
 * Provides translations from the Rust backend/Anki desktop code in composable code.
 * For sentence case translations use [trCase].
 */
@Composable
fun tr(): GeneratedTranslations = if (LocalInspectionMode.current) PreviewTranslations else BackendTranslations

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
object BackendTranslations : GeneratedTranslations {
    override fun translate(
        module: Int,
        translation: Int,
        args: TranslateArgMap,
    ): String = TR.translate(module = module, translation = translation, args = args)
}

/**
 * Provides a subset of translations provided by [tr] which are converted from Anki Desktop's
 * 'Title Case' strings to AnkiDroid's 'Sentence case' strings.
 *
 * Sentence case is a material design guideline.
 */
@Composable
fun trCase(): SentenceCaseExtension = SentenceCaseExtension

/** See [trCase] */
object SentenceCaseExtension {
    /** A thin composable wrapper around [toSentenceCase] to enable access to [Context] */
    @Composable
    private fun resourceCase(
        backendString: String,
        @StringRes resourceId: Int,
    ): String {
        val context = LocalContext.current
        return with(context) { backendString.toSentenceCase(resourceId) }
    }

    @Composable
    fun addNoteType(): String = resourceCase(tr().actionsAddNotetype(), R.string.sentence_add_note_type)

    @Composable
    fun changeNoteType(): String = resourceCase(tr().browsingChangeNotetype(), R.string.sentence_change_note_type)

    @Composable
    fun checkDatabase() = resourceCase(tr().databaseCheckTitle(), R.string.sentence_check_db)

    @Composable
    fun checkMediaTitle() = resourceCase(tr().mediaCheckWindowTitle(), R.string.sentence_check_media)

    @Composable
    fun checkMediaAction() = resourceCase(tr().mediaCheckCheckMediaAction(), R.string.sentence_check_media)

    @Composable
    fun clearUnusedTags() = resourceCase(tr().browsingClearUnusedTags(), R.string.sentence_clear_unused_tags)

    @Composable
    fun customStudy() = resourceCase(tr().actionsCustomStudy(), R.string.sentence_custom_study)

    /** The 'Empty Cards' menu action. For the window/dialog title, use [emptyCardsTitle]. */
    @Composable
    fun emptyCards() = resourceCase(tr().actionsEmptyCards(), R.string.sentence_empty_cards)

    /** The 'Empty Cards' window/dialog title. For the menu action, use [emptyCards]. */
    @Composable
    fun emptyCardsTitle() = resourceCase(tr().emptyCardsWindowTitle(), R.string.sentence_empty_cards)

    @Composable
    fun emptyTrash() = resourceCase(tr().mediaCheckEmptyTrash(), R.string.sentence_empty_trash)

    @Composable
    fun gradeNow() = resourceCase(tr().actionsGradeNow(), R.string.sentence_grade_now)

    @Composable
    fun mediaSyncLog() = resourceCase(tr().syncMediaLogTitle(), R.string.sentence_sync_media_log)

    @Composable
    fun restoreDeleted() = resourceCase(tr().mediaCheckRestoreTrash(), R.string.sentence_restore_deleted)

    @Composable
    fun restoreToDefault() = resourceCase(tr().cardTemplatesRestoreToDefault(), R.string.sentence_restore_to_default)

    @Composable
    fun setDueDate() = resourceCase(tr().actionsSetDueDate(), R.string.sentence_set_due_date)

    @Composable
    fun toggleBury() = resourceCase(tr().browsingToggleBury(), R.string.sentence_toggle_bury)

    @Composable
    fun toggleCardsNotes() = resourceCase(tr().browsingToggleShowingCardsNotes(), R.string.sentence_toggle_cards_notes)

    @Composable
    fun toggleSuspend() = resourceCase(tr().browsingToggleSuspend(), R.string.sentence_toggle_suspend)

    @Composable
    fun copyToClipboard() = resourceCase(tr().qtMiscCopyToClipboard(), R.string.sentence_copy_to_clipboard)

    @Composable
    fun findAndReplace() = resourceCase(tr().browsingFindAndReplace(), R.string.sentence_find_and_replace)

    @Composable
    fun frontTemplate() = resourceCase(tr().cardTemplatesFrontTemplate(), R.string.sentence_front_template)

    @Composable
    fun backTemplate() = resourceCase(tr().cardTemplatesBackTemplate(), R.string.sentence_back_template)

    @Composable
    fun renameDeck() = resourceCase(tr().actionsRenameDeck(), R.string.sentence_rename_deck)

    @Composable
    fun renameTag() = resourceCase(tr().actionsRenameTag(), R.string.sentence_rename_tag)

    @Composable
    fun deckOptions() = resourceCase(tr().deckConfigTitle(), R.string.sentence_deck_options)

    @Composable
    fun deleteDeck() = resourceCase(tr().decksDeleteDeck(), R.string.sentence_delete_deck)

    @Composable
    fun logIn() = resourceCase(tr().syncLogInButton(), R.string.sentence_log_in)

    @Composable
    fun logOut() = resourceCase(tr().syncLogOutButton(), R.string.sentence_log_out)

    @Composable
    fun cardInfo() = resourceCase(tr().actionsCardInfo(), R.string.sentence_card_info)

    @Composable
    fun buryNote() = resourceCase(tr().studyingBuryNote(), R.string.sentence_bury_note)

    @Composable
    fun buryCard() = resourceCase(tr().studyingBuryCard(), R.string.sentence_bury_card)

    @Composable
    fun suspendNote() = resourceCase(tr().studyingSuspendNote(), R.string.sentence_suspend_note)

    @Composable
    fun suspendCard() = resourceCase(tr().actionsSuspendCard(), R.string.sentence_suspend_card)

    @Composable
    fun markNote() = resourceCase(tr().studyingMarkNote(), R.string.sentence_mark_note)

    @Composable
    fun deleteNote() = resourceCase(tr().studyingDeleteNote(), R.string.sentence_delete_note)

    @Composable
    fun previousCardInfo() = resourceCase(tr().actionsPreviousCardInfo(), R.string.sentence_actions_previous_card_info)

    @Composable
    fun ankiWebAccount() = resourceCase(tr().preferencesAccount(), R.string.sentence_ankiweb_account)

    @Composable
    fun browserAppearance() = resourceCase(tr().browsingBrowserAppearance(), R.string.sentence_browser_appearance)

    // TR aboutCopyDebugInfo() is a duplicate
    @Composable
    fun copyDebugInfo() = resourceCase(tr().errorsCopyDebugInfoButton(), R.string.sentence_copy_debug_info)

    @Composable
    fun addField() = resourceCase(tr().fieldsAddField(), R.string.sentence_add_field)

    @Composable
    fun allDecks() = resourceCase(tr().exportingAllDecks(), R.string.sentence_all_decks)

    @Composable
    fun ankiCollectionPackage() =
        resourceCase(
            tr().exportingAnkiCollectionPackage(),
            R.string.sentence_anki_collection_package,
        )

    @Composable
    fun ankiDeckPackage() = resourceCase(tr().exportingAnkiDeckPackage(), R.string.sentence_anki_deck_package)

    @Composable
    fun notesInPlainText() = resourceCase(tr().exportingNotesInPlainText(), R.string.sentence_notes_in_plain_text)

    @Composable
    fun cardsInPlainText() = resourceCase(tr().exportingCardsInPlainText(), R.string.sentence_cards_in_plain_text)

    @Composable
    fun browserOptions() = resourceCase(tr().browsingBrowserOptions(), R.string.sentence_browser_options)

    @Composable
    fun changeDeck() = resourceCase(tr().browsingChangeDeck(), R.string.sentence_change_deck)

    @Composable
    fun toggleMark() = resourceCase(tr().browsingToggleMark(), R.string.sentence_toggle_mark)

    @Composable
    fun createDeck() = resourceCase(tr().decksCreateDeck(), R.string.sentence_create_deck)

    @Composable
    fun selectDeck() = resourceCase(tr().browsingSelectDeck(), R.string.sentence_select_deck)

    @Composable
    fun flagCard() = resourceCase(tr().studyingFlagCard(), R.string.sentence_flag_card)

    @Composable
    fun selectImage() = resourceCase(tr().notetypesIoSelectImage(), R.string.sentence_select_image)

    // 'Answer easy' is not provided by the backend
    @Composable
    fun answerAgain() = resourceCase(tr().deckConfigAnswerAgain(), R.string.sentence_answer_again)

    @Composable
    fun answerHard() = resourceCase(tr().deckConfigAnswerHard(), R.string.sentence_answer_hard)

    @Composable
    fun answerGood() = resourceCase(tr().deckConfigAnswerGood(), R.string.sentence_answer_good)

    @Composable
    fun selectiveStudy() = resourceCase(tr().customStudySelectiveStudy(), R.string.sentence_selective_study)

    @Composable
    fun chooseTags() = resourceCase(tr().customStudyChooseTags(), R.string.sentence_choose_tags)

    @Composable
    fun repositionNewCards() = resourceCase(tr().browsingRepositionNewCards(), R.string.sentence_reposition_new_cards)

    @Composable
    fun allFields() = resourceCase(tr().browsingAllFields(), R.string.sentence_all_fields)

    @Composable
    fun tagMissing() = resourceCase(tr().mediaCheckAddTag(), R.string.sentence_tag_missing)

    @Composable
    fun checkMediaDeleteUnused() = resourceCase(tr().mediaCheckDeleteUnused(), R.string.sentence_check_media_delete_unused)

    @Composable
    fun noFlag() = resourceCase(tr().browsingNoFlag(), R.string.sentence_no_flag)

    @Composable
    fun keepEditing() = resourceCase(tr().addingKeepEditing(), R.string.sentence_keep_editing)

    @Composable
    fun creatingBackup() = resourceCase(tr().profilesCreatingBackup(), R.string.sentence_creating_backup)

    @Composable
    fun cardStatsCurrentCardStudy(): String {
        val backendString = tr().cardStatsCurrentCard(tr().decksStudy())
        return resourceCase(backendString, R.string.sentence_card_stats_current_card_study)
    }

    @Composable
    fun cardStatsCurrentCardBrowse(): String {
        val backendString = tr().cardStatsCurrentCard(tr().qtMiscBrowse())
        return resourceCase(backendString, R.string.sentence_card_stats_current_card_browse)
    }

    @Composable
    fun cardStatsPreviousCardStudy(): String {
        val backendString = tr().cardStatsPreviousCard(tr().decksStudy())
        return resourceCase(backendString, R.string.sentence_card_stats_previous_card_study)
    }
}
