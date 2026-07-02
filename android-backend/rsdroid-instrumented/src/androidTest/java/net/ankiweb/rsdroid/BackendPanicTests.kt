/*
 * Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>
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
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.ankiweb.rsdroid

import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.notes.copy
import anki.notes.note
import net.ankiweb.rsdroid.BackendException.BackendFatalError
import net.ankiweb.rsdroid.ankiutil.InstrumentedTest
import net.ankiweb.rsdroid.rules.LogcatRule
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.everyItem
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasSize
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackendPanicTests : InstrumentedTest() {
    @get:Rule
    val logcat = LogcatRule()

    @Test
    fun panicsAreLogcatErrors() {
        panic(memoryBackend)

        val errors = logcat.errors

        assertThat("logcat has errors", errors, not(empty()))

        assertThat("all tags are valid", logcat.errors.map { it.tag }, everyItem(equalTo("rsdroid")))
        // line 1: ": rsdroid::logging: Panic at anki/rslib/src/storage/note/mod.rs:73: assertion `left == right` failed"
        assertThat(errors[0].message, containsString(": rsdroid::logging: Panic at "))
        assertThat(errors[0].message, containsString("assertion `left == right` failed"))

        assertThat(errors[1].message, equalTo(":   left: 1"))
        assertThat(errors[2].message, equalTo(":  right: 0"))
        assertThat("logcat has no more errors", errors, hasSize(3))
    }

    /** Causes future backend operations to throw a `PoisonError` */
    private fun panic(backend: Backend): BackendFatalError {
        panicExpected = true
        val validNote =
            note {
                fields.addAll(listOf("Hello", "World"))
                notetypeId = backend.getNotetypeIdByName("Basic")
            }

        val invalidNote = validNote.copy { id = 1 }
        try {
            backend.addNote(invalidNote, deckId = 1)
            fail("Expected BackendFatalError")
            throw IllegalStateException("fail")
        } catch (err: BackendFatalError) {
            return err
        }
    }
}
