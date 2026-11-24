// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.speech.tts.TextToSpeech
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CommonString
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.dialogs.utils.message
import com.ichi2.anki.dialogs.utils.title
import com.ichi2.testutils.EmptyApplication
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric.buildActivity
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
class TtsPlaybackErrorDialogTest : RobolectricTest() {
    @Test
    fun `no engines explains how to install one instead of offering voice options`() {
        buildActivity(FragmentActivity::class.java).use { controller ->
            val activity = controller.setup().get()
            TtsPlaybackErrorDialog.ttsPlaybackErrorDialog(activity, activity.supportFragmentManager, null)
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog

            assertEquals("No text-to-speech engine installed", dialog.title)
            assertEquals(activity.getString(CommonString.tts_no_engine_message), dialog.message)
            assertEquals(activity.getString(CommonString.dialog_ok), dialog.getButton(AlertDialog.BUTTON_POSITIVE).text)
            assertFalse(dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isVisible)
            assertEquals(activity.getString(CommonString.help), dialog.getButton(AlertDialog.BUTTON_NEUTRAL).text)
        }
    }

    @Test
    fun `an installed engine still offers language troubleshooting`() {
        buildActivity(FragmentActivity::class.java).use { controller ->
            val activity = controller.setup().get()
            shadowOf(activity.packageManager).apply {
                val engine = ComponentName("test.tts", "TestTtsService")
                addServiceIfNotPresent(engine)
                addIntentFilterForService(
                    engine,
                    IntentFilter(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE).apply { addCategory(Intent.CATEGORY_DEFAULT) },
                )
            }
            TtsPlaybackErrorDialog.ttsPlaybackErrorDialog(activity, activity.supportFragmentManager, null)
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog

            assertEquals(activity.getString(CommonString.tts_error_dialog_title), dialog.title)
            assertTrue(dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isVisible)
            assertEquals(
                activity.getString(CommonString.tts_error_dialog_supported_voices_button_text),
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).text,
            )
        }
    }
}
