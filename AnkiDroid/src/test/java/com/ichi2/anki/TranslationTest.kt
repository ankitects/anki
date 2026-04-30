/*
 *  Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.i18n.GeneratedTranslations
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.TranslationTest.Companion.BASELINE_CASE_INSENSITIVE_DUPLICATES
import com.ichi2.anki.TranslationTest.Companion.BASELINE_DUPLICATES
import com.ichi2.testutils.BackendTranslation
import com.ichi2.testutils.XmlStringResource
import com.ichi2.testutils.getBackendNonArgStrings
import com.ichi2.testutils.getTranslatableXmlStrings
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.fail

/**
 * Ensures that translatable strings defined in the app's XML resource files
 * (01-core, 02-strings, etc.) do not duplicate strings already available
 * from the backend via [GeneratedTranslations]/[TR].
 */
@RunWith(AndroidJUnit4::class) // TODO: no Android dependencies; could be JvmTest
class TranslationTest : RobolectricTest() {
    @Test
    fun `translatable strings do not duplicate GeneratedTranslations`() =
        runTest {
            val backendStrings = getBackendNonArgStrings()
            val backendByText = backendStrings.groupBy { it.text }
            val backendByTextLower = backendStrings.groupBy { it.text.lowercase() }
            val xmlStrings = getTranslatableXmlStrings()

            val exactDuplicates =
                xmlStrings
                    .filter { it.text !in BASELINE_DUPLICATES }
                    .filter { it.text in backendByText }

            if (exactDuplicates.isNotEmpty()) {
                val xmlByText = xmlStrings.groupBy { it.text }
                val entries =
                    exactDuplicates
                        .map { it.text }
                        .distinct()
                        .sorted()
                        .joinToString("\n") { text ->
                            formatBaselineEntry(text, xmlByText[text]!!, backendByText[text]!!)
                        }
                fail(
                    "${exactDuplicates.size} XML string(s) duplicate a backend translation.\n" +
                        "If caused by a backend update, add to BASELINE_DUPLICATES:\n$entries\n\n" +
                        "If manually added, use a string from `TR`.",
                )
            }

            // case-insensitive match check (excluding exact matches already baselined)
            val caseInsensitiveBaseline = BASELINE_CASE_INSENSITIVE_DUPLICATES.mapTo(mutableSetOf()) { it.lowercase() }
            val caseInsensitiveDuplicates =
                xmlStrings
                    .filter { it.text !in BASELINE_DUPLICATES }
                    .filter { it.text.lowercase() !in caseInsensitiveBaseline }
                    .filter { it.text !in backendByText } // not an exact match
                    .filter { it.text.lowercase() in backendByTextLower }

            if (caseInsensitiveDuplicates.isNotEmpty()) {
                val xmlByTextLower = xmlStrings.groupBy { it.text.lowercase() }
                val entries =
                    caseInsensitiveDuplicates
                        .map { it.text }
                        .distinct()
                        .sorted()
                        .joinToString("\n") { text ->
                            formatBaselineEntry(text, xmlByTextLower[text.lowercase()]!!, backendByTextLower[text.lowercase()]!!)
                        }
                fail(
                    "${caseInsensitiveDuplicates.size} XML string(s) case-insensitively duplicate a backend translation.\n" +
                        "If caused by a backend update, add to BASELINE_CASE_INSENSITIVE_DUPLICATES:\n$entries\n\n" +
                        "If caused by a manually added XML string, use TR instead of defining a new string resource.",
                )
            }

            // ensure baselines don't contain stale entries
            val xmlTexts = xmlStrings.mapTo(mutableSetOf()) { it.text }
            val unusedExact = BASELINE_DUPLICATES.filter { it !in xmlTexts || it !in backendByText }
            val xmlTextsLower = xmlStrings.mapTo(mutableSetOf()) { it.text.lowercase() }
            val unusedCaseInsensitive =
                BASELINE_CASE_INSENSITIVE_DUPLICATES.filter {
                    it.lowercase() !in xmlTextsLower || it.lowercase() !in backendByTextLower
                }
            if (unusedExact.isNotEmpty() || unusedCaseInsensitive.isNotEmpty()) {
                val details =
                    buildString {
                        if (unusedExact.isNotEmpty()) {
                            appendLine("Unused BASELINE_DUPLICATES (remove these):")
                            unusedExact.sorted().forEach { appendLine("  \"$it\"") }
                        }
                        if (unusedCaseInsensitive.isNotEmpty()) {
                            appendLine("Unused BASELINE_CASE_INSENSITIVE_DUPLICATES (remove these):")
                            unusedCaseInsensitive.sorted().forEach { appendLine("  \"$it\"") }
                        }
                    }
                fail(details.trim())
            }
        }

    companion object {
        /**
         * Formats a baseline entry as copy-pasteable Kotlin code.
         *
         * @see BASELINE_DUPLICATES
         * @see BASELINE_CASE_INSENSITIVE_DUPLICATES
         */
        private fun formatBaselineEntry(
            text: String,
            xmlResources: List<XmlStringResource>,
            trMethods: List<BackendTranslation>,
        ): String {
            val rStrings = xmlResources.map { "R.string.${it.name}" }.distinct().sorted()
            val trNames = trMethods.map { "TR.${it.methodName}()" }.distinct().sorted()

            val indent = " ".repeat("\"$text\", ".length)
            val isOneToOne = rStrings.size == 1 && trNames.size == 1

            return if (isOneToOne) {
                "\"$text\", // ${rStrings[0]} | ${trNames[0]}"
            } else {
                val lines = mutableListOf<String>()
                lines.add("\"$text\", // ${rStrings.joinToString(", ")}")
                trNames.forEach { lines.add("$indent// $it") }
                lines.joinToString("\n")
            }
        }

        /**
         * English string values which exist in both the app's XML resources and
         * [GeneratedTranslations] (exact match).
         *
         * TODO: These should be migrated to use [TR] and removed from this set.
         *
         * Do not remove this set when empty.
         *
         * If a backend update adds new translations that overlap with existing
         * XML strings, add entries here. Do not remove the R.string definitions
         * in the same PR — migration of R.string usages to TR should be done
         * separately.
         *
         * If a manually added XML string matches a backend translation, use
         * TR directly instead of adding to this baseline.
         */
        private val BASELINE_DUPLICATES =
            setOf(
                // example usages:
                // "Add",                        // R.string.import_message_add, R.string.menu_add
                // "General",                    // R.string.deck_conf_general, R.string.pref_cat_general
                //                               // TR.preferencesGeneral()
                //                               // TR.schedulingGeneral()
                "Add", // R.string.import_message_add, R.string.menu_add
                // TR.actionsAdd()
                "Add tag", // R.string.add_tag | TR.editingTagsAdd()
                "Advanced", // R.string.pref_cat_advanced | TR.deckConfigAdvancedTitle()
                "Again", // R.string.ease_button_again
                // TR.browsingAgainToday()
                // TR.studyingAgain()
                "All", // R.string.hide_system_bars_all_bars | TR.statisticsTrueRetentionAll()
                "Always", // R.string.sync_media_always
                // TR.preferencesAlways()
                // TR.importingUpdateAlways()
                "Answer", // R.string.card_side_answer | TR.browsingAnswer()
                "Appearance", // R.string.pref_cat_appearance | TR.preferencesAppearance()
                "Back", // R.string.back_field_name, R.string.previewer_back
                // TR.notetypesBackField()
                "Bury", // R.string.menu_bury | TR.studyingBury()
                "Cancel", // R.string.dialog_cancel
                // TR.actionsCancel()
                // TR.syncCancelButton()
                "Card", // R.string.card, R.string.reviewer_frame_style_card
                // TR.browsingCard()
                "Cards", // R.string.show_cards
                // TR.browsingCards()
                // TR.editingCards()
                // TR.notetypesCards()
                "Close", // R.string.close | TR.actionsClose()
                "Collapse", // R.string.collapse
                // TR.editingCollapse()
                // TR.browsingSidebarCollapse()
                // TR.changeNotetypeCollapse()
                "Continue", // R.string.dialog_continue | TR.studyingContinue()
                "Copied to clipboard", // R.string.about_ankidroid_successfully_copied_debug_info
                // TR.aboutCopiedToClipboard()
                // TR.errorsCopiedToClipboard()
                "Dark", // R.string.night_theme_dark | TR.preferencesThemeDark()
                "Decks", // R.string.decks
                // TR.actionsDecks()
                // TR.browsingSidebarDecks()
                "Delete", // R.string.dialog_positive_delete
                // TR.actionsDelete()
                // TR.editingImageOcclusionDelete()
                // TR.emptyCardsDeleteButton()
                "Description", // R.string.deck_description_field_hint
                // TR.fieldsDescription()
                // TR.schedulingDescription()
                "Discard", // R.string.discard | TR.actionsDiscard()
                "Due", // R.string.tags_dialog_option_due_cards
                // TR.decksReviewHeader()
                // TR.statisticsDueCount()
                // TR.statisticsDueDate()
                // TR.browsingSidebarDueToday()
                "Easy", // R.string.ease_button_easy | TR.studyingEasy()
                "Editing", // R.string.pref_cat_editing | TR.preferencesEditing()
                "Empty", // R.string.empty_cram_label | TR.studyingEmpty()
                "Error", // R.string.import_title_error, R.string.pref__etc__summary__error
                // R.string.pref__widget_text__error, R.string.vague_error
                // TR.qtMiscError()
                "Expand", // R.string.expand
                // TR.editingExpand()
                // TR.browsingSidebarExpand()
                // TR.changeNotetypeExpand()
                "Fields", // R.string.standard_fields_tab_header
                // TR.editingFields()
                // TR.notetypesFields()
                // TR.changeNotetypeFields()
                "Flag", // R.string.menu_flag | TR.browsingFlag()
                "Flags", // R.string.filter_by_flags | TR.browsingSidebarFlags()
                "Flip", // R.string.image_cropper_action_flip | TR.cardTemplatesFlip()
                "Front", // R.string.front_field_name | TR.notetypesFrontField()
                "General", // R.string.deck_conf_general, R.string.pref_cat_general
                // TR.preferencesGeneral()
                // TR.schedulingGeneral()
                "Good", // R.string.ease_button_good | TR.studyingGood()
                "Hard", // R.string.ease_button_hard | TR.studyingHard()
                "Help", // R.string.help | TR.actionsHelp()
                "Image Occlusion", // R.string.image_occlusion | TR.notetypesImageOcclusionName()
                "Import", // R.string.menu_import | TR.actionsImport()
                "Language", // R.string.language | TR.preferencesLanguage()
                "Later", // R.string.button_backup_later | TR.schedulingUpdateLaterButton()
                "Learn More", // R.string.scoped_storage_learn_more | TR.schedulingUpdateMoreInfoButton()
                "Learn ahead limit", // R.string.learn_cutoff | TR.preferencesLearnAheadLimit()
                "Light", // R.string.day_theme_light | TR.preferencesThemeLight()
                "Media", // R.string.media
                // TR.editingMedia()
                // TR.preferencesMedia()
                "Never", // R.string.sync_media_never | TR.importingUpdateNever()
                "New", // R.string.tags_dialog_option_new_cards
                // TR.actionsNew()
                // TR.changeNotetypeNew()
                // TR.statisticsCountsNewCards()
                "Note", // R.string.note
                // TR.browsingNote()
                // TR.preferencesNote()
                // TR.notetypesOcclusionNote()
                "Notes", // R.string.show_notes | TR.browsingNotes()
                "OK", // R.string.dialog_ok
                // TR.customStudyOk()
                // TR.helpOk()
                "Open", // R.string.open | TR.profilesOpen()
                "Options", // R.string.error_handling_options, R.string.study_options
                // TR.actionsOptions()
                // TR.notetypesOptions()
                // TR.cardTemplatesPreviewSettings()
                "Paste clipboard images as PNG", // R.string.paste_as_png | TR.preferencesPasteClipboardImagesAsPng()
                "Preview", // R.string.card_editor_preview_card
                // TR.actionsPreview()
                // TR.cardTemplatesPreviewBox()
                "Question", // R.string.card_side_question | TR.browsingQuestion()
                "Rebuild", // R.string.rebuild_cram_label | TR.actionsRebuild()
                "Record audio", // R.string.multimedia_editor_popup_audio | TR.editingRecordAudio()
                "Redo", // R.string.redo | TR.undoRedo()
                "Rename", // R.string.rename | TR.actionsRename()
                "Reposition", // R.string.card_editor_reposition_card, R.string.card_template_reposition_template
                // TR.actionsReposition()
                "Reschedule", // R.string.card_editor_reschedule_card | TR.browsingReschedule()
                "Reviews", // R.string.pref_controls_reviews_tab
                // TR.schedulingReviews()
                // TR.cardStatsReviewCount()
                // TR.deckConfigFsrsSimulatorRadioCount()
                // TR.statisticsReviewsTitle()
                "Save", // R.string.save
                // TR.actionsSave()
                // TR.deckConfigSaveButton()
                "Scheduling", // R.string.pref_cat_scheduling | TR.preferencesScheduling()
                "Search", // R.string.card_browser_cram_search, R.string.card_browser_search_hint
                // R.string.deck_conf_cram_search
                // TR.actionsSearch()
                // TR.statisticsRangeSearch()
                "Select", // R.string.select
                // TR.actionsSelect()
                // TR.customStudySelect()
                // TR.editingImageOcclusionSelectTool()
                "Show remaining card count", // R.string.show_progress_summ | TR.preferencesShowRemainingCardCount()
                "Statistics", // R.string.statistics | TR.statisticsTitle()
                "Study", // R.string.studyoptions_start | TR.decksStudy()
                "Styling", // R.string.card_template_editor_styling | TR.cardTemplatesTemplateStyling()
                "Suspend", // R.string.menu_suspend | TR.studyingSuspend()
                "Sync", // R.string.button_sync, R.string.pref_cat_sync
                // TR.qtMiscSync()
                "Synchronization", // R.string.sync_title | TR.preferencesTabSynchronisation()
                "Tags", // R.string.card_details_tags
                // TR.editingTags()
                // TR.browsingSidebarTags()
                "Theme", // R.string.app_theme | TR.preferencesTheme()
                "Timebox time limit", // R.string.time_limit | TR.preferencesTimeboxTimeLimit()
                "Unbury", // R.string.unbury | TR.studyingUnbury()
                "Undo", // R.string.undo | TR.undoUndo()
            )

        /**
         * English string values which case-insensitively match a [GeneratedTranslations]
         * value but differ in casing (e.g. "Card browser" vs "Card Browser").
         *
         * Do not remove this set when empty.
         *
         * TODO: These should be migrated to use [TR] and removed from this set.
         *
         * If a backend update adds new translations that overlap with existing
         * XML strings, add entries here. Do not remove the R.string definitions
         * in the same PR — migration of R.string usages to TR should be done
         * separately.
         *
         * If a manually added XML string matches a backend translation, use
         * TR directly instead of adding to this baseline.
         */
        private val BASELINE_CASE_INSENSITIVE_DUPLICATES =
            setOf(
                // example usages:
                // "Add field",          // R.string.model_field_editor_add | TR.fieldsAddField()
                // "Check media",        // R.string.check_media
                //                       // TR.mediaCheckCheckMediaAction()
                //                       // TR.mediaCheckWindowTitle()
                "Add field", // R.string.model_field_editor_add | TR.fieldsAddField()
                "Add note", // R.string.menu_add_note | TR.actionsAddNote()
                "All decks", // R.string.card_browser_all_decks | TR.exportingAllDecks()
                "AnkiWeb account", // R.string.sync_account | TR.preferencesAccount()
                "Answer again", // R.string.answer_again | TR.deckConfigAnswerAgain()
                "Answer buttons", // R.string.answer_buttons | TR.statisticsAnswerButtonsTitle()
                "Answer good", // R.string.answer_good | TR.deckConfigAnswerGood()
                "Answer hard", // R.string.answer_hard | TR.deckConfigAnswerHard()
                "Back template", // R.string.card_template_editor_back | TR.cardTemplatesBackTemplate()
                "Blank", // R.string.reviewer_tts_cloze_spoken_replacement | TR.cardTemplatesBlank()
                "Browser appearance", // R.string.card_template_browser_appearance_title | TR.browsingBrowserAppearance()
                "Browser options", // R.string.browser_options_dialog_heading | TR.browsingBrowserOptions()
                "Bury card", // R.string.menu_bury_card | TR.studyingBuryCard()
                "Bury note", // R.string.menu_bury_note | TR.studyingBuryNote()
                "Card info", // R.string.card_info_title | TR.actionsCardInfo()
                "Change deck", // R.string.card_browser_change_deck | TR.browsingChangeDeck()
                "Copy debug info", // R.string.feedback_copy_debug
                // TR.aboutCopyDebugInfo()
                // TR.errorsCopyDebugInfoButton()
                "Create deck", // R.string.new_deck | TR.decksCreateDeck()
                "Custom study", // R.string.custom_study
                // TR.actionsCustomStudy()
                // TR.schedulingCustomStudy()
                "Deck options", // R.string.menu__deck_options | TR.deckConfigTitle()
                "Delete deck", // R.string.contextmenu_deckpicker_delete_deck | TR.decksDeleteDeck()
                "Delete note", // R.string.menu_delete_note | TR.studyingDeleteNote()
                "Empty cards", // R.string.empty_cards
                // TR.actionsEmptyCards()
                // TR.emptyCardsWindowTitle()
                "Flag card", // R.string.menu_flag_card | TR.studyingFlagCard()
                "Follow system", // R.string.theme_follow_system | TR.preferencesThemeFollowSystem()
                "Front template", // R.string.card_template_editor_front | TR.cardTemplatesFrontTemplate()
                "Log in", // R.string.log_in | TR.syncLogInButton()
                "Log out", // R.string.log_out | TR.syncLogOutButton()
                "Manage note types", // R.string.model_browser_label
                // TR.browsingManageNoteTypes()
                // TR.qtMiscManageNoteTypes()
                "Mark note", // R.string.menu_mark_note | TR.studyingMarkNote()
                "Previous card info", // R.string.previous_card_info_title | TR.actionsPreviousCardInfo()
                "Rename deck", // R.string.rename_deck | TR.actionsRenameDeck()
                "Select all", // R.string.card_browser_select_all | TR.editingImageOcclusionSelectAll()
                "Select deck", // R.string.select_deck | TR.browsingSelectDeck()
                "Select image", // R.string.choose_an_image | TR.notetypesIoSelectImage()
                "Show answer", // R.string.show_answer
                // TR.studyingShowAnswer()
                // TR.deckConfigQuestionActionShowAnswer()
                "Suspend card", // R.string.menu_suspend_card | TR.actionsSuspendCard()
                "Suspend note", // R.string.menu_suspend_note | TR.studyingSuspendNote()
            )
    }
}
