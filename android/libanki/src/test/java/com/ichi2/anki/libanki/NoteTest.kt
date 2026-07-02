/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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
package com.ichi2.anki.libanki

import com.ichi2.anki.libanki.Note.ClozeUtils
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.Test

class NoteTest {
    @Test
    fun noFieldDataReturnsFirstClozeIndex() {
        val expected = ClozeUtils.getNextClozeIndex(emptyList())
        MatcherAssert.assertThat("No data should return a cloze index of 1 the next.", expected, Matchers.equalTo(1))
    }

    @Test
    fun negativeFieldIsIgnored() {
        val fieldValue = "{{c-1::foo}}"
        val actual = ClozeUtils.getNextClozeIndex(listOf(fieldValue))
        MatcherAssert.assertThat("The next consecutive value should be returned.", actual, Matchers.equalTo(1))
    }

    @Test
    fun singleFieldReturnsNextValue() {
        val fieldValue = "{{c2::bar}}{{c1::foo}}"
        val actual = ClozeUtils.getNextClozeIndex(listOf(fieldValue))
        MatcherAssert.assertThat("The next consecutive value should be returned.", actual, Matchers.equalTo(3))
    }

    @Test
    fun multiFieldIsHandled() {
        val fields = listOf("{{c1::foo}}", "{{c2::bar}}")
        val actual = ClozeUtils.getNextClozeIndex(fields)
        MatcherAssert.assertThat("The highest of all fields should be used.", actual, Matchers.equalTo(3))
    }

    @Test
    fun missingFieldIsSkipped() {
        // this mimics Anki Desktop
        val fields = listOf("{{c1::foo}}", "{{c3::bar}}{{c4::baz}}")
        val actual = ClozeUtils.getNextClozeIndex(fields)
        MatcherAssert.assertThat("A missing cloze index should not be selected if there are higher values.", actual, Matchers.equalTo(5))
    }
}
