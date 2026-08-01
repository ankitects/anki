// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki

import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.libanki.TTSTag
import com.ichi2.anki.libanki.TtsPlayer.TtsCompletionStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.not
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

/** Test for [AndroidTtsPlayer] */
@RunWith(AndroidJUnit4::class)
class AndroidTtsPlayerTest {
    @Test
    fun `playback is routed to the engine which owns the voice`() {
        val test = playerTest()

        val result = test.play(test.voiceB)

        assertThat("playback succeeded", result.success, equalTo(true))
        // engine B owns the voice: it speaks, engine A is never touched
        assertThat(test.factoryCalls, hasItem(ENGINE_B))
        assertThat(test.factoryCalls, not(hasItem(ENGINE_A)))
        verify(exactly = 1) { test.ttsB.speak(any(), any(), any(), any()) }
        verify(exactly = 0) { test.ttsA.speak(any(), any(), any(), any()) }
    }

    @Test
    fun `each engine is only created once`() {
        val test = playerTest()

        test.play(test.voiceB)
        test.play(test.voiceB)

        // second playback reuses the cached engine instance
        assertThat(test.factoryCalls.count { it == ENGINE_B }, equalTo(1))
        verify(exactly = 2) { test.ttsB.speak(any(), any(), any(), any()) }
    }

    @Test
    fun `voices from different engines route to their own engine`() {
        val test = playerTest()

        test.play(test.voiceB)
        test.play(test.voiceA)

        assertThat(test.factoryCalls.count { it == ENGINE_A }, equalTo(1))
        assertThat(test.factoryCalls.count { it == ENGINE_B }, equalTo(1))
        verify(exactly = 1) { test.ttsB.speak(any(), any(), any(), any()) }
        verify(exactly = 1) { test.ttsA.speak(any(), any(), any(), any()) }
    }

    @Test
    fun `an engine that fails to initialise is not retried`() {
        val factoryCalls = mutableListOf<String>()
        val voice = AndroidTtsVoice(fakeVoice("voice-x", Locale.forLanguageTag("en-US")), ENGINE_FAILING)
        val factory: suspend (String) -> TextToSpeech? = { engine ->
            factoryCalls.add(engine)
            null // this engine always fails to initialise
        }
        val player = AndroidTtsPlayer(listOf(voice), factory)
        runBlocking { player.init(CoroutineScope(SupervisorJob() + Dispatchers.IO)) }

        val tag =
            TTSTag(
                fieldText = "hello world",
                lang = voice.lang,
                voices = listOf(voice.name),
                speed = null,
                otherArgs = emptyList(),
            )
        val first = runBlocking { withTimeout(PLAYBACK_TIMEOUT_MS.milliseconds) { player.play(tag) } }
        val second = runBlocking { withTimeout(PLAYBACK_TIMEOUT_MS.milliseconds) { player.play(tag) } }

        assertThat("first playback fails", first.success, equalTo(false))
        assertThat("second playback fails", second.success, equalTo(false))
        // the failed engine is cached and not initialised a second time
        assertThat(factoryCalls.count { it == ENGINE_FAILING }, equalTo(1))
    }

    /** Builds a player backed by two engines, each owning a single voice */
    private fun playerTest(): PlayerTestFixture {
        val ttsA = fakeTts()
        val ttsB = fakeTts()
        val voiceA = AndroidTtsVoice(fakeVoice("voice-a", Locale.forLanguageTag("en-US")), ENGINE_A)
        val voiceB = AndroidTtsVoice(fakeVoice("voice-b", Locale.forLanguageTag("fr-FR")), ENGINE_B)
        val factoryCalls = mutableListOf<String>()

        val factory: suspend (String) -> TextToSpeech? = { engine ->
            factoryCalls.add(engine)
            when (engine) {
                ENGINE_A -> ttsA
                ENGINE_B -> ttsB
                else -> null
            }
        }

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val player = AndroidTtsPlayer(listOf(voiceA, voiceB), factory)
        runBlocking { player.init(scope) }

        return PlayerTestFixture(player, ttsA, ttsB, voiceA, voiceB, factoryCalls)
    }

    private class PlayerTestFixture(
        val player: AndroidTtsPlayer,
        val ttsA: TextToSpeech,
        val ttsB: TextToSpeech,
        val voiceA: AndroidTtsVoice,
        val voiceB: AndroidTtsVoice,
        val factoryCalls: MutableList<String>,
    ) {
        fun play(voice: AndroidTtsVoice): TtsCompletionStatus =
            runBlocking {
                withTimeout(PLAYBACK_TIMEOUT_MS.milliseconds) {
                    player.play(
                        TTSTag(
                            fieldText = "hello world",
                            lang = voice.lang,
                            voices = listOf(voice.name),
                            speed = null,
                            otherArgs = emptyList(),
                        ),
                    )
                }
            }
    }

    /** A relaxed [TextToSpeech] mock whose `speak()` immediately reports completion */
    private fun fakeTts(): TextToSpeech {
        val listenerSlot = slot<UtteranceProgressListener>()
        val utteranceSlot = slot<String>()
        return mockk(relaxed = true) {
            every { setOnUtteranceProgressListener(capture(listenerSlot)) } returns TextToSpeech.SUCCESS
            every { speak(any(), any(), any(), capture(utteranceSlot)) } answers {
                // drive the player's completion channel so play() returns
                listenerSlot.captured.onDone(utteranceSlot.captured)
                TextToSpeech.SUCCESS
            }
        }
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

    companion object {
        private const val ENGINE_A = "com.example.engine.a"
        private const val ENGINE_B = "com.example.engine.b"
        private const val ENGINE_FAILING = "com.example.engine.failing"
        private const val PLAYBACK_TIMEOUT_MS = 5_000L
    }
}
