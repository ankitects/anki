// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import android.Manifest.permission.RECORD_AUDIO
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.annotation.CheckResult
import com.ichi2.anki.CardBrowser
import com.ichi2.anki.CardTemplateBrowserAppearanceEditor
import com.ichi2.anki.CardTemplateBrowserAppearanceEditor.Companion.EXTRA_ANSWER_FORMAT
import com.ichi2.anki.CardTemplateBrowserAppearanceEditor.Companion.EXTRA_QUESTION_FORMAT
import com.ichi2.anki.CardTemplateEditor
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.DrawingFragment
import com.ichi2.anki.Info
import com.ichi2.anki.IntentHandler
import com.ichi2.anki.IntentHandler.Companion.getReviewDeckIntent
import com.ichi2.anki.IntentHandler2
import com.ichi2.anki.IntroductionActivity
import com.ichi2.anki.NoteEditorActivity
import com.ichi2.anki.NoteEditorFragment
import com.ichi2.anki.NoteTypeFieldEditor
import com.ichi2.anki.Reviewer
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.StoragePermissionSet
import com.ichi2.anki.StudyOptionsActivity
import com.ichi2.anki.account.AccountActivity
import com.ichi2.anki.browser.IdsFile
import com.ichi2.anki.instantnoteeditor.InstantNoteEditorActivity
import com.ichi2.anki.libanki.Note
import com.ichi2.anki.multimedia.AudioRecordingFragment
import com.ichi2.anki.multimedia.MultimediaActivity
import com.ichi2.anki.multimedia.MultimediaActivityExtra
import com.ichi2.anki.multimediacard.fields.AudioRecordingField
import com.ichi2.anki.multimediacard.impl.MultimediaEditableNote
import com.ichi2.anki.notetype.ManageNotetypes
import com.ichi2.anki.preferences.PreferencesActivity
import com.ichi2.anki.previewer.CardViewerActivity
import com.ichi2.anki.previewer.PreviewerFragment
import com.ichi2.anki.shareddeck.SharedDecksActivity
import com.ichi2.anki.ui.windows.managespace.ManageSpaceActivity
import com.ichi2.anki.ui.windows.permissions.AllPermissionsExplanationActivity
import com.ichi2.anki.ui.windows.permissions.PermissionsActivity
import com.ichi2.anki.utils.ConfigAwareSingleFragmentActivity
import com.ichi2.testutils.ActivityList.ActivityLaunchParam.Companion.get
import com.ichi2.widget.cardanalysis.CardAnalysisWidgetConfig
import com.ichi2.widget.deckpicker.DeckPickerWidgetConfig
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import java.util.function.Function

object ActivityList {
    // TODO: This needs a test to ensure that all activities are valid with the given intents
    // Otherwise, ActivityStartupUnderBackup and other classes could be flaky
    @CheckResult
    fun allActivitiesAndIntents(): List<ActivityLaunchParam> =
        listOf(
            get(DeckPicker::class.java),
            // IntentHandler has unhandled intents
            get(IntentHandler::class.java) { ctx: Context ->
                getReviewDeckIntent(
                    ctx,
                    1L,
                )
            },
            get(IntentHandler2::class.java),
            get(StudyOptionsActivity::class.java),
            get(CardBrowser::class.java),
            get(
                NoteTypeFieldEditor::class.java,
                normalIntent = { note ->
                    Intent().apply {
                        putExtra(NoteTypeFieldEditor.EXTRA_NOTETYPE_ID, note.noteTypeId)
                        putExtra(NoteTypeFieldEditor.EXTRA_NOTETYPE_NAME, "Basic")
                    }
                },
            ),
            get(NoteEditorActivity::class.java, normalIntent = { Intent().putExtras(NoteEditorFragment.addNoteArgs()) }),
            // Likely has unhandled intents
            get(Reviewer::class.java),
            get(PreferencesActivity::class.java, normalIntent = { PreferencesActivity.getIntent(targetContext) }),
            get(Info::class.java),
            get(CardTemplateEditor::class.java, normalIntent = { note -> intentForCardTemplateEditor(note.noteTypeId) }) {
                intentForCardTemplateEditor()
            },
            get(CardTemplateBrowserAppearanceEditor::class.java) { intentForCardTemplateBrowserAppearanceEditor() },
            get(SharedDecksActivity::class.java),
            get(IntroductionActivity::class.java),
            get(ManageNotetypes::class.java),
            get(ManageSpaceActivity::class.java),
            get(
                PermissionsActivity::class.java,
                normalIntent = { PermissionsActivity.getIntent(targetContext, StoragePermissionSet.entries.first()) },
            ),
            get(AllPermissionsExplanationActivity::class.java),
            get(SingleFragmentActivity::class.java, normalIntent = { DrawingFragment.getIntent(targetContext) }),
            get(
                ConfigAwareSingleFragmentActivity::class.java,
                normalIntent = { ConfigAwareSingleFragmentActivity.getIntent(targetContext, DrawingFragment::class) },
            ),
            get(
                CardViewerActivity::class.java,
                normalIntent = { note ->
                    PreviewerFragment.getIntent(
                        targetContext,
                        idsFile = IdsFile(tempFolder.root, note.cardIds(col), IdsFile.Purpose.PREVIEW),
                        currentIndex = 0,
                    )
                },
            ),
            get(
                InstantNoteEditorActivity::class.java,
                normalIntent = {
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "A note")
                    }
                },
            ),
            get(MultimediaActivity::class.java, normalIntent = { intentForAudioRecording() }),
            get(DeckPickerWidgetConfig::class.java, normalIntent = { intentForWidgetConfig() }),
            get(CardAnalysisWidgetConfig::class.java) { intentForWidgetConfig() },
            get(AccountActivity::class.java, normalIntent = { AccountActivity.getIntent(targetContext) }),
        )

    private fun intentForCardTemplateBrowserAppearanceEditor(): Intent {
        // bundle != null
        return Intent().apply {
            putExtra(EXTRA_QUESTION_FORMAT, "{{Front}}")
            putExtra(EXTRA_ANSWER_FORMAT, "{{FrontSide}}\n{{Back}}")
        }
    }

    private fun intentForCardTemplateEditor(noteTypeId: Long = 1L): Intent =
        Intent().putExtra(CardTemplateEditor.EXTRA_NOTE_TYPE_ID, noteTypeId)

    private fun intentForWidgetConfig(): Intent = Intent().apply { putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 1) }

    private fun RobolectricTest.intentForAudioRecording(): Intent {
        grantPermissions(RECORD_AUDIO)
        val field = AudioRecordingField()
        val multimediaNote =
            MultimediaEditableNote().apply {
                setNumFields(1)
                setField(0, field)
                freezeInitialFieldValues()
            }
        return AudioRecordingFragment.getIntent(targetContext, MultimediaActivityExtra(index = 0, field = field, note = multimediaNote))
    }

    class ActivityLaunchParam(
        var activity: Class<out Activity>,
        private var intentBuilder: Function<Context, Intent>,
        private val normalIntentBuilder: (RobolectricTest.(Note) -> Intent)? = null,
    ) {
        val simpleName: String = activity.simpleName

        fun build(context: Context): ActivityController<out Activity> =
            Robolectric
                .buildActivity(activity, buildIntent(context))

        fun buildIntent(context: Context): Intent = intentBuilder.apply(context)

        /** Supplies a real note and screen arguments for normal startup, rather than recovery paths. */
        fun buildIntent(test: RobolectricTest): Intent {
            val note = test.addBasicNote()
            return normalIntentBuilder?.invoke(test, note) ?: buildIntent(test.targetContext)
        }

        val className: String = activity.name

        companion object {
            operator fun get(
                clazz: Class<out Activity>,
                normalIntent: (RobolectricTest.(Note) -> Intent)? = null,
                i: Function<Context, Intent> = Function { Intent() },
            ): ActivityLaunchParam = ActivityLaunchParam(clazz, i, normalIntent)
        }
    }
}
