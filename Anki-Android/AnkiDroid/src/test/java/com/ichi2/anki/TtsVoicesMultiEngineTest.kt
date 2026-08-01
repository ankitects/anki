// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki

import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * Regression tests for #18737: voices should be listed from every installed TTS engine,
 * not only the user's default engine.
 *
 * @see TtsVoices.loadVoicesFromEngines
 */
@RunWith(AndroidJUnit4::class)
class TtsVoicesMultiEngineTest {
    @Test
    fun `voices are aggregated across multiple engines`() =
        runBlocking {
            val ttsA =
                fakeTts(
                    engineVoices = setOf(fakeVoice("a-voice", Locale.forLanguageTag("en-US"))),
                    engineLanguages = setOf(Locale.forLanguageTag("en-US")),
                )
            val ttsB =
                fakeTts(
                    engineVoices = setOf(fakeVoice("b-voice", Locale.forLanguageTag("fr-FR"))),
                    engineLanguages = setOf(Locale.forLanguageTag("fr-FR")),
                )

            val (voices, _) =
                TtsVoices.loadVoicesFromEngines(listOf(ENGINE_A, ENGINE_B)) { engine ->
                    when (engine) {
                        ENGINE_A -> ttsA
                        ENGINE_B -> ttsB
                        else -> null
                    }
                }

            // both engines contribute their voices, each tagged with the owning engine
            assertThat(voices.map { it.engine }.toSet(), equalTo(setOf(ENGINE_A, ENGINE_B)))
            assertThat(voices.any { it.engine == ENGINE_A && it.voice.name == "a-voice" }, equalTo(true))
            assertThat(voices.any { it.engine == ENGINE_B && it.voice.name == "b-voice" }, equalTo(true))
        }

    @Test
    fun `an engine which fails to initialise is skipped`() =
        runBlocking {
            val workingTts =
                fakeTts(
                    engineVoices = setOf(fakeVoice("ok-voice", Locale.forLanguageTag("de-DE"))),
                    engineLanguages = setOf(Locale.forLanguageTag("de-DE")),
                )

            val (voices, _) =
                TtsVoices.loadVoicesFromEngines(listOf(ENGINE_A, ENGINE_B)) { engine ->
                    // ENGINE_A 'fails' to initialise (returns null) and must not abort the scan
                    if (engine == ENGINE_B) workingTts else null
                }

            assertThat(voices.map { it.engine }.toSet(), equalTo(setOf(ENGINE_B)))
        }

    private fun fakeVoice(
        voiceName: String,
        voiceLocale: Locale,
    ): Voice =
        mockk(relaxed = true) {
            every { name } returns voiceName
            every { locale } returns voiceLocale
            every { features } returns emptySet()
        }

    private fun fakeTts(
        engineVoices: Set<Voice>,
        engineLanguages: Set<Locale>,
    ): TextToSpeech =
        mockk(relaxed = true) {
            every { voices } returns engineVoices
            every { availableLanguages } returns engineLanguages
        }

    companion object {
        private const val ENGINE_A = "com.example.engine.a"
        private const val ENGINE_B = "com.example.engine.b"
    }
}
