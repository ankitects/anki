// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber

@RunWith(AndroidJUnit4::class)
class AbstractFlashcardViewerKeyboardInputTest : RobolectricTest() {
    @Test
    fun spaceShowsAnswer() {
        val underTest = KeyboardInputTestCardViewer.create()

        underTest.handleKeyPress(KeyEvent.KEYCODE_SPACE)

        assertThat("Space should display answer on any card viewer", underTest.didDisplayAnswer())
    }

    @Test
    fun enterShowsAnswer() {
        val underTest = KeyboardInputTestCardViewer.create()

        underTest.handleKeyPress(KeyEvent.KEYCODE_ENTER)

        assertThat("Enter should display answer on any card viewer", underTest.didDisplayAnswer())
    }

    @Test
    fun numPadEnterShowsAnswer() {
        val underTest = KeyboardInputTestCardViewer.create()

        underTest.handleKeyPress(KeyEvent.KEYCODE_NUMPAD_ENTER)

        assertThat("NumPad Enter should display answer on any card viewer", underTest.didDisplayAnswer())
    }

    @Test
    fun spaceDoesNotShowAnswerIfTextFieldFocused() {
        val underTest = KeyboardInputTestCardViewer.create()
        underTest.focusTextField()

        underTest.handleKeyPress(KeyEvent.KEYCODE_SPACE)

        assertThat(
            "When text field is focused, space should not display answer",
            !underTest.didDisplayAnswer(),
        )
    }

    private class KeyboardInputTestCardViewer : AbstractFlashcardViewer() {
        private var displayAnswer = false
        private var focusTextField = false

        override fun answerFieldIsFocused(): Boolean = focusTextField

        override fun performReload() {
            // intentionally blank
        }

        override fun displayCardAnswer() {
            displayAnswer = true
        }

        fun didDisplayAnswer(): Boolean = displayAnswer

        fun handleKeyPress(keycode: Int) {
            // COULD_BE_BETTER: Saves 20 seconds on tests to remove AndroidJUnit4,
            // but may let something slip through the cracks.
            try {
                onKeyDown(keycode, KeyEvent(KeyEvent.ACTION_DOWN, keycode))
            } catch (e: Exception) {
                Timber.e(e)
            }
            try {
                onKeyUp(keycode, KeyEvent(KeyEvent.ACTION_UP, keycode))
            } catch (e: Exception) {
                Timber.e(e)
            }
        }

        fun focusTextField() {
            focusTextField = true
        }

        companion object {
            fun create(): KeyboardInputTestCardViewer {
                displayAnswer = false
                return KeyboardInputTestCardViewer()
            }
        }
    }
}
