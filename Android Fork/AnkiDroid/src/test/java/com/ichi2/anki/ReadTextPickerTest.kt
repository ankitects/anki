// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.os.Looper
import android.speech.tts.TextToSpeech
import androidx.appcompat.app.AlertDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.reviewer.CardSide
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.mockkObject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

@RunWith(AndroidJUnit4::class)
@Config(shadows = [ReadTextTest.NoLanguageQueryTextToSpeech::class])
class ReadTextPickerTest : RobolectricTest() {
    override fun getCollectionStorageMode() = CollectionStorageMode.IN_MEMORY_WITH_MEDIA

    @Test
    fun `releasing TTS while the picker awaits languages prevents the dialog`() =
        runTest {
            addBasicNote()
            val viewer = startRegularActivity<Reviewer>()
            val refreshed = CompletableDeferred<List<Locale>>()
            try {
                mockkObject(TtsVoices) {
                    coEvery { TtsVoices.localesForEngine(any()) } returns listOf(Locale.US)
                    ReadText.initializeTts(viewer, mockk(relaxed = true))
                    shadowOf(ReadText.textToSpeech).onInitListener.onInit(TextToSpeech.SUCCESS)
                    runCurrent()

                    coEvery { TtsVoices.localesForEngine(any()) } coAnswers { refreshed.await() }
                    ReadText.selectTts("text", 1, 0, CardSide.QUESTION)
                    runCurrent()
                    ReadText.releaseTts(viewer)
                    assertFalse(refreshed.isCancelled)
                    refreshed.complete(listOf(Locale.FRANCE))
                    runCurrent()
                    advanceTimeBy(500.milliseconds)
                    runCurrent()
                    shadowOf(Looper.getMainLooper()).idleFor(500, TimeUnit.MILLISECONDS)

                    assertTrue(ShadowDialog.getShownDialogs().none { it.isShowing })
                }
            } finally {
                ShadowDialog.getShownDialogs().forEach { it.dismiss() }
                ReadText.releaseTts(viewer)
            }
        }

    @Test
    fun `language picker uses refreshed cached languages`() =
        runTest {
            addBasicNote()
            val viewer = startRegularActivity<Reviewer>()
            var languages = listOf(Locale.US)
            try {
                mockkObject(TtsVoices) {
                    coEvery { TtsVoices.localesForEngine(any()) } coAnswers { languages }
                    ReadText.initializeTts(viewer, mockk(relaxed = true))
                    shadowOf(ReadText.textToSpeech).onInitListener.onInit(TextToSpeech.SUCCESS)
                    runCurrent()

                    // A successful voice refresh replaces the shared cache while this reviewer stays open.
                    languages = listOf(Locale.FRANCE)
                    ReadText.selectTts("text", 1, 0, CardSide.QUESTION)
                    runCurrent()
                    advanceTimeBy(500.milliseconds)
                    runCurrent()
                    shadowOf(Looper.getMainLooper()).idleFor(500, TimeUnit.MILLISECONDS)

                    val dialog = ShadowDialog.getLatestDialog() as AlertDialog
                    try {
                        assertEquals(2, dialog.listView.adapter.count)
                        assertEquals(
                            Locale.FRANCE.displayName,
                            dialog.listView.adapter
                                .getItem(1)
                                .toString(),
                        )
                    } finally {
                        dialog.dismiss()
                    }
                }
            } finally {
                ReadText.releaseTts(viewer)
            }
        }
}
