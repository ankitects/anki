// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.reviewer

import android.view.KeyEvent
import com.github.ivanshafran.sharedpreferencesmock.SPMockBuilder
import com.ichi2.anki.cardviewer.ViewerCommand
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasSize
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever

class BindingMapTest {
    @Test
    fun flagAndAnswerDoNotConflict() {
        val processed: MutableList<ViewerCommand> = ArrayList()

        val sharedPrefs = SPMockBuilder().createSharedPreferences()
        val bindingMap = BindingMap(sharedPrefs, ViewerCommand.entries) { e: ViewerCommand, _ -> processed.add(e) }
        val event = mock(KeyEvent::class.java)
        whenever(event.unicodeChar).thenReturn(0)
        whenever(event.isCtrlPressed).thenReturn(true)
        whenever(event.getUnicodeChar(0)).thenReturn(49)
        whenever(event.keyCode).thenReturn(KeyEvent.KEYCODE_1)

        assertThat(event.unicodeChar.toChar(), equalTo('\u0000'))
        assertThat(event.getUnicodeChar(0).toChar(), equalTo('1'))
        bindingMap.onKeyDown(event)

        assertThat<List<ViewerCommand>>(processed, hasSize(1))
        assertThat(processed[0], equalTo(ViewerCommand.TOGGLE_FLAG_RED))
    }
}
