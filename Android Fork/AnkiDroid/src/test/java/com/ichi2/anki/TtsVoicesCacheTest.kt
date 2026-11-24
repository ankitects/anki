// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.speech.tts.TextToSpeech
import com.ichi2.anki.i18n.normalize
import com.ichi2.testutils.TtsVoicesCacheOverride
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.Locale
import kotlin.test.assertEquals

class TtsVoicesCacheTest {
    @Test
    fun `a new reviewer engine gets its own cached languages`() =
        runTest {
            TtsVoicesCacheOverride(ENGINE_A, listOf(Locale.US.normalize())).use {
                val engineB =
                    mockk<TextToSpeech> {
                        every { availableLanguages } returns setOf(Locale.FRANCE)
                        every { shutdown() } just Runs
                    }
                mockkObject(TtsVoices) {
                    every { TtsVoices.launchBuildLocalesJob() } just Runs
                    coEvery { TtsVoices.createTts(any()) } coAnswers {
                        check(firstArg<String?>() == ENGINE_B)
                        engineB
                    }

                    assertEquals(listOf(Locale.US.normalize()), TtsVoices.localesForEngine(ENGINE_A))
                    assertEquals(listOf(Locale.FRANCE.normalize()), TtsVoices.localesForEngine(ENGINE_B))
                    assertEquals(listOf(Locale.FRANCE.normalize()), TtsVoices.localesForEngine(ENGINE_B))
                    // The second lookup of the new engine must use its cached languages.
                    coVerify(exactly = 1) { TtsVoices.createTts(ENGINE_B) }
                }
            }
        }

    companion object {
        private const val ENGINE_A = "engine.a"
        private const val ENGINE_B = "engine.b"
    }
}
