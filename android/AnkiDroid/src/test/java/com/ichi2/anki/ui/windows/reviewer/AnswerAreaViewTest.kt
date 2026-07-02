/*
 * Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.ui.windows.reviewer

import android.content.Context
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.scheduler.CardAnswer.Rating
import com.ichi2.anki.databinding.ViewAnswerAreaBinding
import com.ichi2.themes.Themes
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class AnswerAreaViewTest {
    private lateinit var answerArea: AnswerAreaView
    private val binding by lazy { ViewAnswerAreaBinding.bind(answerArea) }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Themes.setTheme(context)
        answerArea = AnswerAreaView(context)
    }

    @Test
    fun `clicking show answer button calls onShowAnswerClicked`() {
        val onShowAnswer: () -> Unit = mock()
        answerArea.setButtonListeners(onRatingClicked = {}, onShowAnswerClicked = onShowAnswer)

        binding.showAnswerButton.performClick()

        verify(onShowAnswer).invoke()
    }

    @Test
    fun `clicking rating buttons calls onRatingClicked with correct rating`() {
        val onRating: (Rating) -> Unit = mock()
        answerArea.setButtonListeners(onRatingClicked = onRating, onShowAnswerClicked = {})

        fun AnswerButton.invokesCallbackWith(rating: Rating) {
            performClick()
            verify(onRating).invoke(rating)
        }
        binding.againButton.invokesCallbackWith(Rating.AGAIN)
        binding.hardButton.invokesCallbackWith(Rating.HARD)
        binding.goodButton.invokesCallbackWith(Rating.GOOD)
        binding.easyButton.invokesCallbackWith(Rating.EASY)
    }

    @Test
    fun `setAnswerState(true) shows rating buttons and hides show answer button`() {
        answerArea.setAnswerState(isAnswerShown = true)

        assertEquals(View.INVISIBLE, binding.showAnswerButton.visibility)
        assertEquals(View.VISIBLE, binding.answerButtonsLayout.visibility)
    }

    @Test
    fun `setAnswerState(false) hides rating buttons and shows show answer button`() {
        answerArea.setAnswerState(isAnswerShown = false)

        assertEquals(View.VISIBLE, binding.showAnswerButton.visibility)
        assertEquals(View.INVISIBLE, binding.answerButtonsLayout.visibility)
    }

    @Test
    fun `hideHardAndEasyButtons sets visibility to GONE`() {
        answerArea.hideHardAndEasyButtons()

        assertEquals(View.GONE, binding.hardButton.visibility)
        assertEquals(View.GONE, binding.easyButton.visibility)
    }
}
