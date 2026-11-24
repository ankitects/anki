// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class FieldEditLineTest : NoteEditorTest() {
    @Test
    fun testSetters() {
        val line =
            fieldEditLine().apply {
                setContent("Hello", true)
                name = "Name"
                setOrd(5)
            }
        val text = line.editText
        assertThat(text.ord, equalTo(5))
        assertThat(text.text.toString(), equalTo("Hello"))
        assertThat(line.name, equalTo("Name"))
    }

    @Test
    fun testSaveRestore() {
        val toSave =
            fieldEditLine().apply {
                setContent("Hello", true)
                name = "Name"
                setOrd(5)
            }
        val b = toSave.onSaveInstanceState()

        val restored = fieldEditLine()
        restored.onRestoreInstanceState(b!!)

        val text = restored.editText
        assertThat(text.ord, equalTo(5))
        assertThat(text.text.toString(), equalTo("Hello"))
        assertThat(toSave.name, equalTo("Name"))
    }

    private fun fieldEditLine(): FieldEditLine {
        val reference = AtomicReference<FieldEditLine>()
        activityRule!!.scenario.onActivity { activity ->
            reference.set(FieldEditLine(activity.baseContext))
        }
        return reference.get()
    }
}

val FieldEditLine.editText get() = binding.editText
