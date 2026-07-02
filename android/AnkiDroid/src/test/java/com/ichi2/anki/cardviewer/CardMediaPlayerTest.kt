/*
 *  Copyright (c) 2023 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.cardviewer

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CardUtils
import com.ichi2.anki.cardviewer.MediaErrorBehavior.CONTINUE_MEDIA
import com.ichi2.anki.cardviewer.MediaErrorBehavior.RETRY_MEDIA
import com.ichi2.anki.cardviewer.MediaErrorBehavior.STOP_MEDIA
import com.ichi2.anki.cardviewer.SingleCardSide.BACK
import com.ichi2.anki.libanki.AvTag
import com.ichi2.anki.libanki.SoundOrVideoTag
import com.ichi2.anki.libanki.TemplateManager
import com.ichi2.anki.libanki.TtsPlayer
import com.ichi2.testutils.JvmTest
import com.ichi2.testutils.TestException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.coVerifySequence
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CardMediaPlayerTest : JvmTest() {
    internal val tagPlayer: SoundTagPlayer = mockk<SoundTagPlayer>()
    internal val ttsPlayer: TtsPlayer = mockk<TtsPlayer>()
    internal val onMediaGroupCompleted: () -> Unit =
        mockk<() -> Unit>().also {
            every { it.invoke() } answers { }
        }

    @Test
    fun `no sounds fires completed listener`() =
        runSoundPlayerTest(
            answers = emptyList(),
            questions = emptyList(),
        ) {
            playAllAndWait(BACK)

            verifyNoSoundsPlayed()
        }

    @Test
    fun singleSoundSuccess() =
        runSoundPlayerTest(
            questions = listOf(SoundOrVideoTag("abc.mp3")),
        ) {
            playAllAndWait()

            coVerify(exactly = 1) { tagPlayer.play(SoundOrVideoTag("abc.mp3"), any()) }
            coVerify(exactly = 0) { ttsPlayer.play(any()) }
            ensureOnMediaGroupCompletedCalled()
        }

    @Test
    fun `back is not played on front`() =
        runSoundPlayerTest(
            answers = listOf(SoundOrVideoTag("abc.mp3")),
        ) {
            playAllAndWait()

            verifyNoSoundsPlayed()
        }

    @Test
    fun `front is not played on back`() =
        runSoundPlayerTest(
            questions = listOf(SoundOrVideoTag("abc.mp3")),
        ) {
            playAllAndWait(BACK)

            verifyNoSoundsPlayed()
        }

    @Test
    fun `replay - front may be played on back`() =
        runSoundPlayerTest(
            questions = listOf(SoundOrVideoTag("front.mp3")),
            answers = listOf(SoundOrVideoTag("back.mp3")),
            replayQuestion = true,
        ) {
            replayAllAndWait(BACK)

            coVerifyOrder {
                tagPlayer.play(SoundOrVideoTag("front.mp3"), any())
                tagPlayer.play(SoundOrVideoTag("back.mp3"), any())
            }
        }

    @Test
    fun `replay when replayQuestion is false`() =
        runSoundPlayerTest(
            questions = listOf(SoundOrVideoTag("front.mp3")),
            answers = listOf(SoundOrVideoTag("back.mp3")),
            replayQuestion = false,
        ) {
            replayAllAndWait(BACK)

            coVerifyOrder {
                tagPlayer.play(SoundOrVideoTag("back.mp3"), any())
            }
        }

    @Test
    fun `onMediaGroupCompleted is called after exception`() =
        runSoundPlayerTest(
            questions = listOf(SoundOrVideoTag("aa.mp3")),
        ) {
            coEvery { tagPlayer.play(any(), any()) } throws TestException("test")

            playAllAndWait()

            coVerify(exactly = 1) { tagPlayer.play(any(), any()) }
            ensureOnMediaGroupCompletedCalled()
        }

    @Test
    fun `replay calls play twice`() =
        runSoundPlayerTest(
            questions = listOf(SoundOrVideoTag("aa.mp3"), SoundOrVideoTag("bb.mp3")),
        ) {
            coEvery { tagPlayer.play(any(), any()) } throws MediaException(RETRY_MEDIA)

            playAllAndWait()

            coVerifySequence {
                tagPlayer.play(SoundOrVideoTag("aa.mp3"), any())
                tagPlayer.play(SoundOrVideoTag("aa.mp3"), any())
                tagPlayer.play(SoundOrVideoTag("bb.mp3"), any())
                tagPlayer.play(SoundOrVideoTag("bb.mp3"), any())
            }

            ensureOnMediaGroupCompletedCalled()
        }

    @Test
    fun `stop stops playback and calls completed listener`() =
        runSoundPlayerTest(
            questions = listOf(SoundOrVideoTag("aa.mp3"), SoundOrVideoTag("bb.mp3")),
        ) {
            coEvery { tagPlayer.play(any(), any()) } throws MediaException(STOP_MEDIA)

            playAllAndWait()

            coVerifySequence {
                tagPlayer.play(SoundOrVideoTag("aa.mp3"), any())
            }

            ensureOnMediaGroupCompletedCalled()
        }

    @Test
    fun `continue continues playback and calls completed listener`() =
        runSoundPlayerTest(
            questions = listOf(SoundOrVideoTag("aa.mp3"), SoundOrVideoTag("bb.mp3")),
        ) {
            coEvery { tagPlayer.play(any(), any()) } throws MediaException(CONTINUE_MEDIA)

            playAllAndWait()

            coVerifySequence {
                tagPlayer.play(SoundOrVideoTag("aa.mp3"), any())
                tagPlayer.play(SoundOrVideoTag("bb.mp3"), any())
            }

            ensureOnMediaGroupCompletedCalled()
        }

    @Test
    fun `retry playing single sound`() =
        runSoundPlayerTest {
            coEvery { tagPlayer.play(any(), any()) } throws MediaException(RETRY_MEDIA)

            playOneAndWait(SoundOrVideoTag("a.mp3"))

            coVerifySequence {
                tagPlayer.play(SoundOrVideoTag("a.mp3"), any())
                tagPlayer.play(SoundOrVideoTag("a.mp3"), any())
            }
        }

    private fun verifyNoSoundsPlayed() {
        coVerify(exactly = 0) { tagPlayer.play(any(), any()) }
        coVerify(exactly = 0) { ttsPlayer.play(any()) }
        ensureOnMediaGroupCompletedCalled()
    }

    private fun ensureOnMediaGroupCompletedCalled() {
        verify(exactly = 1) { onMediaGroupCompleted.invoke() }
    }

    private suspend fun CardMediaPlayer.playAllAndWait(side: SingleCardSide = SingleCardSide.FRONT) {
        this.playAllForSide(side.toCardSide())
        playAvTagsJob?.join()
    }

    private suspend fun CardMediaPlayer.replayAllAndWait(side: SingleCardSide) {
        this.replayAll(side)
        playAvTagsJob?.join()
    }

    private suspend fun CardMediaPlayer.playOneAndWait(tag: AvTag) {
        playOne(tag)
        playAvTagsJob?.join()
    }

    suspend fun CardMediaPlayer.setup(
        questions: List<AvTag>,
        answers: List<AvTag>,
        replayQuestion: Boolean?,
        autoplay: Boolean?,
    ) {
        val card = addBasicNote().firstCard()
        mockkObject(card)

        every { card.renderOutput(any()) } answers {
            TemplateManager.TemplateRenderContext.TemplateRenderOutput(
                questionText = "",
                answerText = "",
                questionAvTags = questions,
                answerAvTags = answers,
                css = "",
            )
        }

        if (replayQuestion != null) {
            updateDeckConfig(CardUtils.getDeckIdForCard(card)) {
                replayq = replayQuestion
            }
        }
        if (autoplay != null) {
            updateDeckConfig(CardUtils.getDeckIdForCard(card)) {
                this.autoplay = autoplay
            }
        }

        this.loadCardAvTags(card)
    }
}

/**
 *
 * @param autoplay [CardSoundConfig.autoplay]
 * @param replayQuestion [CardSoundConfig.replayQuestion]
 */
fun CardMediaPlayerTest.runSoundPlayerTest(
    questions: List<AvTag> = emptyList(),
    answers: List<AvTag> = emptyList(),
    replayQuestion: Boolean? = null,
    autoplay: Boolean? = null,
    testBody: suspend CardMediaPlayer.() -> Unit,
) = runTest {
    val cardMediaPlayer =
        CardMediaPlayer(
            soundTagPlayer = tagPlayer,
            ttsPlayer = CompletableDeferred(ttsPlayer),
            mediaErrorListener = mockk(),
        )
    cardMediaPlayer.setOnMediaGroupCompletedListener(onMediaGroupCompleted)
    assertThat("can play sounds", cardMediaPlayer.isEnabled)
    cardMediaPlayer.setup(questions, answers, replayQuestion, autoplay)
    testBody(cardMediaPlayer)
}
