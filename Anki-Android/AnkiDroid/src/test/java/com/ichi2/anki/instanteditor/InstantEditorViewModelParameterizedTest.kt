/*
 *  Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.instanteditor

import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.instanteditor.InstantEditorViewModelTest.Companion.runInstantEditorViewModelTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner as Parameterized

@RunWith(Parameterized::class)
class InstantEditorViewModelParameterizedTest : RobolectricTest() {
    @JvmField // required for Parameter
    @Parameterized.Parameter(0)
    var input: String? = null

    @JvmField // required for Parameter
    @Parameterized.Parameter(1)
    var expected: String? = null

    @Test
    fun `buildClozeText punctuation handling`() =
        runInstantEditorViewModelTest {
            val result = buildClozeText(input!!)
            assertEquals(expected, result)
            val undone = buildClozeText(result)
            assertEquals("applying buildClozeText twice should not change the input", input, undone)
        }

    companion object {
        @Parameterized.Parameters(name = "{0} -> {1}")
        @JvmStatic // required for initParameters
        fun initParameters(): Collection<Array<out Any>> =
            listOf(
                arrayOf("Student's", "{{c1::Student's}}"),
                arrayOf("Note(s)", "{{c1::Note(s)}}"),
                arrayOf("20%", "{{c1::20}}%"),
                arrayOf("hello-world", "{{c1::hello-world}}"),
                arrayOf("hello_world", "{{c1::hello_world}}"),
                arrayOf("(hello)", "({{c1::hello}})"),
                arrayOf("[hello]", "[{{c1::hello}}]"),
                arrayOf("{hello}", "{{{c1::hello}}}"),
                arrayOf("test,", "{{c1::test}},"),
                arrayOf("{{c1::test}},", "test,"),
            )
    }
}
