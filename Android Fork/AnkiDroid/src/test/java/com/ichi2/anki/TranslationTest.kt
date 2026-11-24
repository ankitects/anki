// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import anki.i18n.GeneratedTranslations
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.TranslationTest.Companion.BASELINE_CASE_INSENSITIVE_DUPLICATES
import com.ichi2.anki.TranslationTest.Companion.BASELINE_DUPLICATES
import com.ichi2.testutils.BackendTranslation
import com.ichi2.testutils.JvmTest
import com.ichi2.testutils.XmlStringResource
import com.ichi2.testutils.getAndroidManifestStringResourceNames
import com.ichi2.testutils.getBackendNonArgStrings
import com.ichi2.testutils.getTranslatableXmlStrings
import org.junit.Test
import java.io.File
import kotlin.test.fail

/**
 * Ensures that translatable strings defined in our XML resource files
 * (01-core, 02-strings, etc.) do not duplicate strings already available
 * from the backend via [GeneratedTranslations]/[TR].
 */
class TranslationTest : JvmTest() {
    @Test
    fun `CrowdIn-managed files do not reappear in this module`() {
        val resDir = File("src/main/res")
        val resourceDirs =
            resDir.listFiles { dir -> dir.name == "values" || dir.name.startsWith("values-") }
                ?: error("Could not list files in $resDir")
        val staleFiles =
            resourceDirs
                .flatMap { dir -> dir.listFiles()?.toList() ?: emptyList() }
                .filter { file -> file.name.matches(Regex("\\d+-.*\\.xml")) }
        if (staleFiles.isNotEmpty()) {
            fail(
                "CrowdIn-managed string files belong in common/android/src/main/res, not AnkiDroid/src/main/res:\n" +
                    staleFiles.joinToString("\n"),
            )
        }
    }

    @Test
    fun `translatable strings do not duplicate GeneratedTranslations`() =
        runTest {
            val backendStrings =
                getBackendNonArgStrings()
                    .filterNot { it.methodName in IGNORED_BACKEND_TRANSLATIONS }
            val backendByText = backendStrings.groupBy { it.text }
            val backendByTextLower = backendStrings.groupBy { it.text.lowercase() }
            val xmlStrings = getTranslatableXmlStrings()

            // strings referenced from AndroidManifest.xml cannot be converted to use the backend
            val manifestStringsLower = ANDROID_MANIFEST_STRINGS.mapTo(mutableSetOf()) { it.lowercase() }

            val exactDuplicates =
                xmlStrings
                    .filter { it.text !in BASELINE_DUPLICATES }
                    .filter { it.text.lowercase() !in manifestStringsLower }
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
                        "If referenced from AndroidManifest.xml, add to ANDROID_MANIFEST_STRINGS.\n" +
                        "If manually added, use a string from `TR`.",
                )
            }

            // case-insensitive match check (excluding exact matches already baselined)
            val caseInsensitiveBaseline = BASELINE_CASE_INSENSITIVE_DUPLICATES.mapTo(mutableSetOf()) { it.lowercase() }
            val caseInsensitiveDuplicates =
                xmlStrings
                    .filter { it.text !in BASELINE_DUPLICATES }
                    .filter { it.text.lowercase() !in caseInsensitiveBaseline }
                    .filter { it.text.lowercase() !in manifestStringsLower }
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
                        "If referenced from AndroidManifest.xml, add to ANDROID_MANIFEST_STRINGS.\n" +
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
            val manifestXmlTextsLower =
                xmlStrings
                    .filter { it.name in getAndroidManifestStringResourceNames() }
                    .mapTo(mutableSetOf()) { it.text.lowercase() }
            val unusedManifest =
                ANDROID_MANIFEST_STRINGS.filter {
                    it.lowercase() !in manifestXmlTextsLower || it.lowercase() !in backendByTextLower
                }
            if (unusedExact.isNotEmpty() || unusedCaseInsensitive.isNotEmpty() || unusedManifest.isNotEmpty()) {
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
                        if (unusedManifest.isNotEmpty()) {
                            appendLine("Unused ANDROID_MANIFEST_STRINGS (remove these):")
                            unusedManifest.sorted().forEach { appendLine("  \"$it\"") }
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
            val rStrings = xmlResources.map { "CommonString.${it.name}" }.distinct().sorted()
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
                // "Add",                        // CommonString.import_message_add, CommonString.menu_add
                // "General",                    // CommonString.deck_conf_general, CommonString.pref_cat_general
                //                               // TR.preferencesGeneral()
                //                               // TR.schedulingGeneral()
                "Add", // CommonString.import_message_add, CommonString.menu_add
                // TR.actionsAdd()
                "Add tag", // CommonString.add_tag | TR.editingTagsAdd()
                "Advanced", // CommonString.pref_cat_advanced | TR.deckConfigAdvancedTitle()
                "Again", // CommonString.ease_button_again
                // TR.browsingAgainToday()
                // TR.studyingAgain()
                "All", // CommonString.hide_system_bars_all_bars | TR.statisticsTrueRetentionAll()
                "Always", // CommonString.sync_media_always
                // TR.preferencesAlways()
                // TR.importingUpdateAlways()
                "Answer", // CommonString.card_side_answer | TR.browsingAnswer()
                "Back", // CommonString.back_field_name, CommonString.previewer_back
                // TR.notetypesBackField()
                "Cancel", // CommonString.dialog_cancel
                // TR.actionsCancel()
                // TR.syncCancelButton()
                "Card", // CommonString.card, CommonString.reviewer_frame_style_card
                // TR.browsingCard()
                "Cards", // CommonString.show_cards
                // TR.browsingCards()
                // TR.editingCards()
                // TR.notetypesCards()
                "Close", // CommonString.close | TR.actionsClose()
                "Collapse", // CommonString.collapse
                // TR.editingCollapse()
                // TR.browsingSidebarCollapse()
                // TR.changeNotetypeCollapse()
                "Continue", // CommonString.dialog_continue | TR.studyingContinue()
                "Copied to clipboard", // CommonString.about_ankidroid_successfully_copied_debug_info
                // TR.aboutCopiedToClipboard()
                // TR.errorsCopiedToClipboard()
                "Delete", // CommonString.dialog_positive_delete
                // TR.actionsDelete()
                // TR.editingImageOcclusionDelete()
                // TR.emptyCardsDeleteButton()
                "Description", // CommonString.deck_description_field_hint
                // TR.fieldsDescription()
                // TR.schedulingDescription()
                "Discard", // CommonString.discard | TR.actionsDiscard()
                "Due", // CommonString.tags_dialog_option_due_cards
                // TR.decksReviewHeader()
                // TR.statisticsDueCount()
                // TR.statisticsDueDate()
                // TR.browsingSidebarDueToday()
                "Easy", // CommonString.ease_button_easy | TR.studyingEasy()
                "Editing", // CommonString.pref_cat_editing | TR.preferencesEditing()
                "Empty", // CommonString.empty_cram_label | TR.studyingEmpty()
                "Error", // CommonString.import_title_error, CommonString.pref__etc__summary__error
                // CommonString.pref__widget_text__error, CommonString.vague_error
                // TR.qtMiscError()
                "Expand", // CommonString.expand
                // TR.editingExpand()
                // TR.browsingSidebarExpand()
                // TR.changeNotetypeExpand()
                "Fields", // CommonString.standard_fields_tab_header
                // TR.editingFields()
                // TR.notetypesFields()
                // TR.changeNotetypeFields()
                "Flags", // CommonString.filter_by_flags | TR.browsingSidebarFlags()
                "Flip", // CommonString.image_cropper_action_flip | TR.cardTemplatesFlip()
                "General", // CommonString.deck_conf_general, CommonString.pref_cat_general
                // TR.preferencesGeneral()
                // TR.schedulingGeneral()
                "Good", // CommonString.ease_button_good | TR.studyingGood()
                "Hard", // CommonString.ease_button_hard | TR.studyingHard()
                "Help", // CommonString.help | TR.actionsHelp()
                "Language", // CommonString.language | TR.preferencesLanguage()
                "Later", // CommonString.button_backup_later | TR.schedulingUpdateLaterButton()
                "Learn More", // CommonString.scoped_storage_learn_more | TR.schedulingUpdateMoreInfoButton()
                "Learn ahead limit", // CommonString.learn_cutoff | TR.preferencesLearnAheadLimit()
                "Media", // CommonString.media
                // TR.editingMedia()
                // TR.preferencesMedia()
                "Never", // CommonString.sync_media_never | TR.importingUpdateNever()
                "New", // CommonString.tags_dialog_option_new_cards
                // TR.actionsNew()
                // TR.changeNotetypeNew()
                // TR.statisticsCountsNewCards()
                "Note", // CommonString.note
                // TR.browsingNote()
                // TR.preferencesNote()
                // TR.notetypesOcclusionNote()
                "Notes", // CommonString.show_notes | TR.browsingNotes()
                "OK", // CommonString.dialog_ok
                // TR.customStudyOk()
                // TR.helpOk()
                "Open", // CommonString.open | TR.profilesOpen()
                "Options", // CommonString.error_handling_options, CommonString.study_options
                // TR.actionsOptions()
                // TR.notetypesOptions()
                // TR.cardTemplatesPreviewSettings()
                "Preview", // CommonString.card_editor_preview_card
                // TR.actionsPreview()
                // TR.cardTemplatesPreviewBox()
                "Question", // CommonString.card_side_question | TR.browsingQuestion()
                "Record audio", // CommonString.multimedia_editor_popup_audio | TR.editingRecordAudio()
                "Redo", // CommonString.redo | TR.undoRedo()
                "Rename", // CommonString.rename | TR.actionsRename()
                "Reposition", // CommonString.card_editor_reposition_card, CommonString.card_template_reposition_template
                // TR.actionsReposition()
                "Reschedule", // CommonString.card_editor_reschedule_card | TR.browsingReschedule()
                "Reviews", // CommonString.pref_controls_reviews_tab
                // TR.schedulingReviews()
                // TR.cardStatsReviewCount()
                // TR.deckConfigFsrsSimulatorRadioCount()
                // TR.statisticsReviewsTitle()
                "Save", // CommonString.save
                // TR.actionsSave()
                // TR.deckConfigSaveButton()
                "Scheduling", // CommonString.pref_cat_scheduling | TR.preferencesScheduling()
                "Search", // CommonString.card_browser_cram_search, CommonString.card_browser_search_hint
                // CommonString.deck_conf_cram_search
                // TR.actionsSearch()
                // TR.statisticsRangeSearch()
                "Select", // CommonString.select
                // TR.actionsSelect()
                // TR.customStudySelect()
                // TR.editingImageOcclusionSelectTool()
                "Show remaining card count", // CommonString.show_progress_summ | TR.preferencesShowRemainingCardCount()
                "Study", // CommonString.studyoptions_start | TR.decksStudy()
                "Sync", // CommonString.button_sync, CommonString.pref_cat_sync
                // TR.qtMiscSync()
                "Synchronization", // CommonString.sync_title | TR.preferencesTabSynchronisation()
                "Tags", // CommonString.card_details_tags
                // TR.editingTags()
                // TR.browsingSidebarTags()
                "Theme", // CommonString.app_theme | TR.preferencesTheme()
                "Timebox time limit", // CommonString.time_limit | TR.preferencesTimeboxTimeLimit()
                "Undo", // CommonString.undo | TR.undoUndo()
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
                // "Add field",          // CommonString.model_field_editor_add | TR.fieldsAddField()
                // "Check media",        // CommonString.check_media
                //                       // TR.mediaCheckCheckMediaAction()
                //                       // TR.mediaCheckWindowTitle()
                "Answer buttons", // CommonString.answer_buttons | TR.statisticsAnswerButtonsTitle()
                "Select all", // CommonString.card_browser_select_all | TR.editingImageOcclusionSelectAll()
                "Show answer", // CommonString.show_answer
                // TR.studyingShowAnswer()
                // TR.deckConfigQuestionActionShowAnswer()
            )

        /**
         * English string values which match a [GeneratedTranslations] value, but are referenced
         * from `AndroidManifest.xml`.
         *
         * These cannot be converted until we extract the backend resources at build time/
         *
         *
         * ept for reference, alternate framing of [getAndroidManifestStringResourceNames].
         *
         */
        private val ANDROID_MANIFEST_STRINGS =
            setOf(
                "Add note", // CommonString.menu_add_note | TR.actionsAddNote()
                "Image Occlusion", // CommonString.image_occlusion | TR.notetypesImageOcclusionName()
                "Manage note types", // CommonString.model_browser_label
                // TR.browsingManageNoteTypes()
                // TR.qtMiscManageNoteTypes()
            )

        /**
         * Backend translation method names (e.g. `TR.xx()`) excluded from the
         * duplicate check because they are unrelated conceptually or intentionally kept in XML.
         *
         * Do not remove this set when empty.
         */
        private val IGNORED_BACKEND_TRANSLATIONS =
            setOf(
                "launcherOff", // "Off" - unrelated to CommonString.full_screen_off
                "launcherOn", // "On" - unrelated to CommonString.reminder_troubleshooting_status_on
                // "Advanced Settings" - this upstream string is Title Case; CommonString.add_edit_reminder_advanced_label
                // is both unrelated and sentence case (per AnkiDroid convention)
                "deckConfigAdvancedSettings",
                // Keep theme labels in XML: migrating adds maintenance complexity, loses XML previews
                // and translations for languages absent from Anki; the apps' themes also differ.
                // https://github.com/ankidroid/Anki-Android/pull/21325#pullrequestreview-5483017226
                "preferencesThemeDark", // CommonString.night_theme_dark
                "preferencesThemeLight", // CommonString.day_theme_light
                "preferencesThemeFollowSystem", // CommonString.theme_follow_system
            )
    }
}
