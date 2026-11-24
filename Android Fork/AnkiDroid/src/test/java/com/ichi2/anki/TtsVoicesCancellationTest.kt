// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.speech.tts.TextToSpeech
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.testutils.EmptyApplication
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runCurrent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow.extract
import org.robolectric.shadows.ShadowTextToSpeech
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(
    application = EmptyApplication::class,
    // Newer constructors read fields which Robolectric's TTS shadow does not initialize.
    sdk = [33],
    shadows = [TtsVoicesCancellationTest.RecordingTextToSpeech::class],
)
class TtsVoicesCancellationTest : RobolectricTest() {
    @Test
    fun `cancelling initialization defers engine cleanup until after cancellation returns`() =
        runTest {
            val cleanupScheduler = TestCoroutineScheduler()
            ioDispatcher = StandardTestDispatcher(cleanupScheduler)
            val creation = launch { TtsVoices.createTts() }
            runCurrent()
            val tts = extract<RecordingTextToSpeech>(ShadowTextToSpeech.getLastTextToSpeechInstance())

            creation.cancel()

            // Engine calls can block on a binder lock: cancellation must return without making them.
            assertEquals(0, tts.stopCalls)
            assertFalse(tts.isShutdown)
            runCurrent()
            assertEquals(0, tts.stopCalls)
            assertFalse(tts.isShutdown)
            // Cleanup must survive cancellation of the coroutine creating the engine.
            cleanupScheduler.runCurrent()
            assertEquals(1, tts.stopCalls)
            assertTrue(tts.isStopped)
            assertTrue(tts.isShutdown)
        }

    @Implements(TextToSpeech::class)
    class RecordingTextToSpeech : ShadowTextToSpeech() {
        var stopCalls = 0
            private set

        @Implementation
        override fun stop(): Int {
            stopCalls++
            return super.stop()
        }
    }
}
