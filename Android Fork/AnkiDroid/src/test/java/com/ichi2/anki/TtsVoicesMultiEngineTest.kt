// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki

import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.common.android.ApplicationContextInitializer
import com.ichi2.anki.i18n.normalize
import com.ichi2.testutils.EmptyApplication
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowTextToSpeech
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Regression tests for #18737: voices should be listed from every installed TTS engine,
 * not only the user's default engine.
 *
 * @see TtsVoices.loadVoicesFromEngines
 */
@RunWith(AndroidJUnit4::class)
class TtsVoicesMultiEngineTest {
    @Test
    @Suppress("DEPRECATION") // Engine-provided variants may not be valid BCP 47 tags.
    fun `default languages are deduplicated after normalization`() =
        runTest {
            val aliases = setOf(Locale("en", "GB", "f00"), Locale("en", "GB", "f01"))
            val defaultLocales = CompletableDeferred<TtsVoices.EngineLocales>()
            val probe = probeTts()
            every { probe.availableLanguages } returns aliases

            TtsVoices.loadVoices(defaultLocales) { engine -> if (engine == null) probe else null }

            assertEquals(listOf(Locale.UK.normalize()), defaultLocales.await().locales)
        }

    @Test
    fun `default languages are ready while another engine is still initializing`() =
        runTest {
            val defaultLocales = CompletableDeferred<TtsVoices.EngineLocales>()
            val otherEngine = CompletableDeferred<TextToSpeech>()
            val defaultTts = fakeTts(setOf(fakeVoice("a", Locale.US)), setOf(Locale.US))
            val allVoices =
                async {
                    TtsVoices.loadVoices(defaultLocales) { engine ->
                        when (engine) {
                            null -> probeTts()
                            ENGINE_A -> defaultTts
                            ENGINE_B -> otherEngine.await()
                            else -> error("Unexpected engine: $engine")
                        }
                    }
                }
            try {
                runCurrent()
                assertTrue(defaultLocales.isCompleted)
                assertEquals(listOf(Locale.US.normalize()), defaultLocales.await().locales)
                assertFalse(allVoices.isCompleted)

                otherEngine.complete(fakeTts(setOf(fakeVoice("b", Locale.FRANCE)), setOf(Locale.FRANCE)))
                assertEquals(
                    setOf(ENGINE_A, ENGINE_B),
                    allVoices
                        .await()
                        .map { it.engine }
                        .toSet(),
                )
                assertEquals(listOf(Locale.US.normalize()), defaultLocales.await().locales)
            } finally {
                allVoices.cancel()
            }
        }

    @Test
    fun `a failed default engine completes both caches with no voices`() =
        runTest {
            val defaultLocales = CompletableDeferred<TtsVoices.EngineLocales>()
            val voices = TtsVoices.loadVoices(defaultLocales) { null }

            assertTrue(defaultLocales.isCompleted)
            assertEquals(emptyList(), defaultLocales.await().locales)
            assertEquals(emptyList(), voices)
        }

    @Test
    fun `a default language query error does not stop scanning other engines`() =
        runTest {
            val defaultLocales = CompletableDeferred<TtsVoices.EngineLocales>()
            val probe = probeTts()
            every { probe.availableLanguages } throws IllegalStateException("Language query failed")
            val voices =
                TtsVoices.loadVoices(defaultLocales) { engine ->
                    when (engine) {
                        null -> probe
                        ENGINE_B -> fakeTts(setOf(fakeVoice("b", Locale.FRANCE)), setOf(Locale.FRANCE))
                        else -> null
                    }
                }

            assertTrue(defaultLocales.isCompleted)
            assertEquals(emptyList(), defaultLocales.await().locales)
            assertEquals(setOf(ENGINE_B), voices.map { it.engine }.toSet())
        }

    @Test
    fun `unexpected discovery failure does not leave default languages pending`() =
        runTest {
            val defaultLocales = CompletableDeferred<TtsVoices.EngineLocales>()
            assertFailsWith<IllegalStateException> {
                TtsVoices.loadVoices(defaultLocales) { throw IllegalStateException("Discovery failed") }
            }

            assertTrue(defaultLocales.isCompleted)
            assertFailsWith<IllegalStateException> { defaultLocales.await() }
        }

    private fun probeTts(): TextToSpeech =
        fakeTts(emptySet(), setOf(Locale.US)).apply {
            every { defaultEngine } returns ENGINE_A
            every { engines } returns
                listOf(ENGINE_A, ENGINE_B).map { engine ->
                    TextToSpeech.EngineInfo().apply { name = engine }
                }
        }

    @Test
    fun `default engine comes first while other engines retain discovery order`() =
        runTest {
            val probe = probeTts()
            every { probe.defaultEngine } returns ENGINE_B
            every { probe.engines } returns
                listOf(ENGINE_C, ENGINE_B, ENGINE_A).map { engine ->
                    TextToSpeech.EngineInfo().apply { name = engine }
                }

            val voices =
                TtsVoices.loadVoices(CompletableDeferred()) { engine ->
                    if (engine == null) {
                        probe
                    } else {
                        fakeTts(setOf(fakeVoice("first", Locale.US), fakeVoice("second", Locale.FRANCE)), emptySet())
                    }
                }

            assertEquals(listOf(ENGINE_B, ENGINE_B, ENGINE_C, ENGINE_C, ENGINE_A, ENGINE_A), voices.map { it.engine })
            assertEquals(listOf("first", "second", "first", "second", "first", "second"), voices.map { it.voice.name })
        }

    @Test
    fun `default engine is included first even when missing from discovered engines`() =
        runTest {
            val probe = probeTts()
            every { probe.defaultEngine } returns ENGINE_C

            val voices =
                TtsVoices.loadVoices(CompletableDeferred()) { engine ->
                    if (engine == null) probe else fakeTts(setOf(fakeVoice("voice", Locale.US)), emptySet())
                }

            assertEquals(listOf(ENGINE_C, ENGINE_A, ENGINE_B), voices.map { it.engine })
        }

    @Test
    fun `voices are distinct by engine and name while preserving their first occurrence`() =
        runTest {
            val first = fakeVoice("shared", Locale.US)
            val duplicate = fakeVoice("shared", Locale.US)
            val other = fakeVoice("other", Locale.FRANCE)
            val engineB = fakeTts(setOf(first, duplicate, other), emptySet())
            val engineA = fakeTts(setOf(fakeVoice("shared", Locale.US)), emptySet())

            val voices =
                TtsVoices.loadVoicesFromEngines(listOf(ENGINE_B, ENGINE_A, ENGINE_B)) { engine ->
                    if (engine == ENGINE_B) engineB else engineA
                }

            assertEquals(listOf("$ENGINE_B-shared", "$ENGINE_B-other", "$ENGINE_A-shared"), voices.map { it.name })
            assertSame(first, voices.first().voice)
        }

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

            val voices =
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

            val voices =
                TtsVoices.loadVoicesFromEngines(listOf(ENGINE_A, ENGINE_B)) { engine ->
                    // ENGINE_A 'fails' to initialise (returns null) and must not abort the scan
                    if (engine == ENGINE_B) workingTts else null
                }

            assertThat(voices.map { it.engine }.toSet(), equalTo(setOf(ENGINE_B)))
        }

    @Test
    @Config(application = EmptyApplication::class, shadows = [FailingTextToSpeech::class])
    @Category(EmptyApplicationCategory::class)
    fun `an inaccessible default engine is unavailable`() =
        runTest {
            setUpFailingTts()

            assertNull(TtsVoices.createTts())
        }

    @Test
    @Config(application = EmptyApplication::class, shadows = [FailingTextToSpeech::class])
    @Category(EmptyApplicationCategory::class)
    fun `an inaccessible selected engine is unavailable`() =
        runTest {
            setUpFailingTts()

            assertNull(TtsVoices.createTts("inaccessible.engine"))
        }

    @Test
    @Config(application = EmptyApplication::class, shadows = [FailingTextToSpeech::class])
    @Category(EmptyApplicationCategory::class)
    fun `an inaccessible default engine completes discovery with empty caches`() =
        runTest {
            setUpFailingTts()
            val locales = CompletableDeferred<TtsVoices.EngineLocales>()

            assertEquals(emptyList(), TtsVoices.loadVoices(locales, TtsVoices::createTts))
            assertEquals(TtsVoices.EngineLocales(null, emptyList()), locales.await())
        }

    @Test
    @Config(application = EmptyApplication::class, shadows = [FailingTextToSpeech::class])
    @Category(EmptyApplicationCategory::class)
    fun `discovery continues after an inaccessible engine`() =
        runTest {
            setUpFailingTts()
            val workingTts = mockk<TextToSpeech>(relaxed = true)

            TtsVoices.loadVoicesFromEngines(listOf("inaccessible.engine", "working.engine")) { engine ->
                if (engine == "working.engine") workingTts else TtsVoices.createTts(engine)
            }

            verify(exactly = 1) { workingTts.voices }
            verify(exactly = 1) { workingTts.shutdown() }
        }

    @Test
    @Config(application = EmptyApplication::class, shadows = [FailingTextToSpeech::class])
    @Category(EmptyApplicationCategory::class)
    fun `other initialization exceptions make an engine unavailable`() =
        runTest {
            setUpFailingTts(IllegalStateException("Engine initialization failed"))

            for (engine in listOf(null, "broken.engine")) {
                assertNull(TtsVoices.createTts(engine))
            }
        }

    @Test
    @Config(application = EmptyApplication::class, shadows = [FailingTextToSpeech::class])
    @Category(EmptyApplicationCategory::class)
    fun `discovery continues after another initialization exception`() =
        runTest {
            setUpFailingTts(IllegalStateException("Engine initialization failed"))
            val workingTts = mockk<TextToSpeech>(relaxed = true)

            TtsVoices.loadVoicesFromEngines(listOf("broken.engine", "working.engine")) { engine ->
                if (engine == "working.engine") workingTts else TtsVoices.createTts(engine)
            }

            verify(exactly = 1) { workingTts.voices }
            verify(exactly = 1) { workingTts.shutdown() }
        }

    @Test
    @Config(application = EmptyApplication::class, shadows = [FailingTextToSpeech::class])
    @Category(EmptyApplicationCategory::class)
    fun `initialization cancellation is propagated`() =
        runTest {
            val cancellation = CancellationException("TTS creation cancelled")
            setUpFailingTts(cancellation)

            for (engine in listOf(null, "cancelled.engine")) {
                assertSame(cancellation, assertFailsWith<CancellationException> { TtsVoices.createTts(engine) })
            }
        }

    private fun setUpFailingTts(failure: Exception = SecurityException("Not allowed to bind to TTS service")) {
        ApplicationContextInitializer.setInstance(ApplicationProvider.getApplicationContext())
        FailingTextToSpeech.failure = failure
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

    @Implements(TextToSpeech::class)
    class FailingTextToSpeech : ShadowTextToSpeech() {
        @Implementation
        override fun initTts(): Int = throw failure

        companion object {
            lateinit var failure: Exception
        }
    }

    companion object {
        private const val ENGINE_A = "com.example.engine.a"
        private const val ENGINE_B = "com.example.engine.b"
        private const val ENGINE_C = "com.example.engine.c"
    }
}
