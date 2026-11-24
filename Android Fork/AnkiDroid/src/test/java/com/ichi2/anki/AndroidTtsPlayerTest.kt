// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki

import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.dialogs.viewmodel.play
import com.ichi2.anki.libanki.TTSTag
import com.ichi2.anki.libanki.TtsPlayer.TtsCompletionStatus
import com.ichi2.anki.libanki.TtsVoiceMatch
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
import org.hamcrest.Matchers.nullValue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

/** Test for [AndroidTtsPlayer] */
@RunWith(AndroidJUnit4::class)
class AndroidTtsPlayerTest {
    @Test
    fun `locale fallback respects engine preference order`() {
        val first = AndroidTtsVoice(fakeVoice("first", Locale.FRANCE), ENGINE_A)
        val second = AndroidTtsVoice(fakeVoice("second", Locale.FRANCE), ENGINE_B)

        for (voices in listOf(listOf(first, second), listOf(second, first))) {
            val player = AndroidTtsPlayer(voices)
            assertThat(player.voiceForTag(frenchTag()), equalTo(TtsVoiceMatch(voices.first(), rank = -100)))
        }
    }

    @Test
    fun `unavailable voices follow installed voices with engine order preserved in both groups`() {
        val installedB = AndroidTtsVoice(fakeVoice("installed-b", Locale.FRANCE), ENGINE_B)
        val unavailableB = AndroidTtsVoice(fakeVoice("unavailable-b", Locale.FRANCE, installed = false), ENGINE_B)
        val installedA = AndroidTtsVoice(fakeVoice("installed-a", Locale.FRANCE), ENGINE_A)
        val unavailableA = AndroidTtsVoice(fakeVoice("unavailable-a", Locale.FRANCE, installed = false), ENGINE_A)
        val installedC = AndroidTtsVoice(fakeVoice("installed-c", Locale.FRANCE), ENGINE_C)
        val unavailableC = AndroidTtsVoice(fakeVoice("unavailable-c", Locale.FRANCE, installed = false), ENGINE_C)
        val player = AndroidTtsPlayer(listOf(unavailableB, installedB, unavailableA, installedA, unavailableC, installedC))

        assertThat(player.voices(), equalTo(listOf(installedB, installedA, installedC, unavailableB, unavailableA, unavailableC)))
        assertThat(player.voiceForTag(frenchTag()), equalTo(TtsVoiceMatch(installedB, rank = -100)))
    }

    @Test
    fun `locale fallback uses another engine when the default lacks the language`() {
        val french = AndroidTtsVoice(fakeVoice("french", Locale.FRANCE), ENGINE_A)
        val english = AndroidTtsVoice(fakeVoice("english", Locale.US), ENGINE_B)
        val player = AndroidTtsPlayer(listOf(english, french))

        assertThat(player.voiceForTag(frenchTag()), equalTo(TtsVoiceMatch(french, rank = -100)))
    }

    @Test
    fun `installed voice from another engine precedes unavailable default engine voice`() {
        val installed = AndroidTtsVoice(fakeVoice("installed", Locale.FRANCE), ENGINE_A)
        val unavailable = AndroidTtsVoice(fakeVoice("unavailable", Locale.FRANCE, installed = false), ENGINE_B)
        val player = AndroidTtsPlayer(listOf(unavailable, installed))

        assertThat(player.voiceForTag(frenchTag()), equalTo(TtsVoiceMatch(installed, rank = -100)))
    }

    @Test
    fun `explicit voice from another engine takes precedence over the default engine`() {
        val requested = AndroidTtsVoice(fakeVoice("requested", Locale.FRANCE), ENGINE_A)
        val preferred = AndroidTtsVoice(fakeVoice("preferred", Locale.FRANCE), ENGINE_B)
        val player = AndroidTtsPlayer(listOf(preferred, requested))

        assertThat(player.voiceForTag(frenchTag(requested.name)), equalTo(TtsVoiceMatch(requested, rank = 0)))
        assertThat(player.voiceForTag(frenchTag("missing")), equalTo(TtsVoiceMatch(preferred, rank = -100)))
    }

    @Test
    fun `network timeout is reported with its specific error code`() {
        val error = AndroidTtsError.fromErrorCode(TextToSpeech.ERROR_NETWORK_TIMEOUT)

        assertThat(error.developerString, equalTo("ERROR_NETWORK_TIMEOUT"))
    }

    @Test
    fun `locale fallback prefers the first installed voice`() {
        val unavailable = frenchVoice("unavailable", installed = false)
        val first = frenchVoice("first")
        val second = frenchVoice("second")
        val player = AndroidTtsPlayer(listOf(unavailable, first, second))

        assertThat(player.voiceForTag(frenchTag()), equalTo(TtsVoiceMatch(first, rank = -100)))
    }

    @Test
    fun `locale fallback keeps the first unavailable voice when no matching voice is installed`() {
        val first = frenchVoice("first", installed = false)
        val second = frenchVoice("second", installed = false)
        val english = AndroidTtsVoice(fakeVoice("english", Locale.US), ENGINE_A)
        val player = AndroidTtsPlayer(listOf(first, english, second))

        assertThat(player.voiceForTag(frenchTag()), equalTo(TtsVoiceMatch(first, rank = -100)))
    }

    @Test
    fun `requested voice order takes precedence over availability`() {
        val installed = frenchVoice("installed")
        val unavailable = frenchVoice("unavailable", installed = false)
        val player = AndroidTtsPlayer(listOf(installed, unavailable))
        val tag = frenchTag("missing", unavailable.name, installed.name)

        assertThat(player.voiceForTag(tag), equalTo(TtsVoiceMatch(unavailable, rank = -1)))
    }

    @Test
    fun `unmatched requested voices fall back to an installed voice`() {
        val unavailable = frenchVoice("unavailable", installed = false)
        val installed = frenchVoice("installed")
        val player = AndroidTtsPlayer(listOf(unavailable, installed))

        assertThat(player.voiceForTag(frenchTag("missing")), equalTo(TtsVoiceMatch(installed, rank = -100)))
    }

    @Test
    fun `no match when no voice shares the language`() {
        val english = AndroidTtsVoice(fakeVoice("english", Locale.US), ENGINE_A)
        val player = AndroidTtsPlayer(listOf(english))

        assertThat(player.voiceForTag(frenchTag()), nullValue())
    }

    @Test
    fun `no match when there are no voices`() {
        val player = AndroidTtsPlayer(emptyList())

        assertThat(player.voiceForTag(frenchTag()), nullValue())
    }

    @Test
    fun `voice preview selects an unavailable voice so the engine can download it`() =
        runBlocking {
            val installed = frenchVoice("installed")
            val unavailable = frenchVoice("unavailable", installed = false)
            val tts = fakeTts()
            val player = AndroidTtsPlayer(listOf(installed, unavailable)) { tts }
            player.init(this)

            try {
                val result = withTimeout(PLAYBACK_TIMEOUT) { player.play("bonjour", unavailable) }

                assertThat(result.success, equalTo(true))
                verify(exactly = 1) { tts.voice = unavailable.voice }
                verify(exactly = 1) { tts.speak(any(), any(), any(), any()) }
            } finally {
                player.close()
            }
        }

    @Test
    fun `tags with obsolete language codes play voices with current codes`() =
        runBlocking {
            val hebrew = AndroidTtsVoice(fakeVoice("hebrew", Locale.forLanguageTag("he-IL")), ENGINE_A)
            val indonesian = AndroidTtsVoice(fakeVoice("indonesian", Locale.forLanguageTag("id-ID")), ENGINE_A)
            val tts = fakeTts()
            val player = AndroidTtsPlayer(listOf(hebrew, indonesian)) { tts }
            player.init(this)

            try {
                for ((lang, voice) in listOf("iw_IL" to hebrew, "in_ID" to indonesian)) {
                    val tag =
                        TTSTag(
                            fieldText = "hello world",
                            lang = lang,
                            voices = listOf(voice.name),
                            speed = null,
                            otherArgs = emptyList(),
                        )
                    val result = withTimeout(PLAYBACK_TIMEOUT) { player.play(tag) }

                    assertThat(lang, result.success, equalTo(true))
                    verify(exactly = 1) { tts.voice = voice.voice }
                }
            } finally {
                player.close()
            }
        }

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
        val first = runBlocking { withTimeout(PLAYBACK_TIMEOUT) { player.play(tag) } }
        val second = runBlocking { withTimeout(PLAYBACK_TIMEOUT) { player.play(tag) } }

        assertThat("first playback fails", first.success, equalTo(false))
        assertThat("second playback fails", second.success, equalTo(false))
        // the failed engine is cached and not initialised a second time
        assertThat(factoryCalls.count { it == ENGINE_FAILING }, equalTo(1))
    }

    private fun frenchVoice(
        name: String,
        installed: Boolean = true,
    ) = AndroidTtsVoice(fakeVoice(name, Locale.FRANCE, installed), ENGINE_A)

    private fun frenchTag(vararg voices: String) =
        TTSTag(fieldText = "bonjour", lang = "fr_FR", voices = voices.toList(), speed = null, otherArgs = emptyList())

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
                withTimeout(PLAYBACK_TIMEOUT) {
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
        installed: Boolean = true,
    ): Voice =
        mockk(relaxed = true) {
            every { name } returns voiceName
            every { locale } returns voiceLocale
            every { features } returns if (installed) emptySet() else setOf(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
        }

    companion object {
        private const val ENGINE_A = "com.example.engine.a"
        private const val ENGINE_B = "com.example.engine.b"
        private const val ENGINE_C = "com.example.engine.c"
        private const val ENGINE_FAILING = "com.example.engine.failing"
        private val PLAYBACK_TIMEOUT = 5.seconds
    }
}
