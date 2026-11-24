// SPDX-License-Identifier: GPL-3.0-or-later

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
