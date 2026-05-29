// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Shridhar Goel <shridhar.goel@gmail.com>

package com.ichi2.anki.common.json

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.lessThan
import org.junit.Test

class JSONNamedObject(
    override val name: String,
) : NamedObject

/** Tests [NamedJSONComparator] */
class NamedJSONComparatorTest {
    @Test
    fun checkIfReturnsCorrectValueForSameNames() {
        val firstObject = JSONNamedObject("TestName")
        val secondObject = JSONNamedObject("TestName")
        assertThat(NamedJSONComparator.INSTANCE.compare(firstObject, secondObject), equalTo(0))
    }

    @Test
    fun checkIfReturnsCorrectValueForDifferentNames() {
        val firstObject = JSONNamedObject("TestName1")
        val secondObject = JSONNamedObject("TestName2")
        assertThat(NamedJSONComparator.INSTANCE.compare(firstObject, secondObject), lessThan(0))
    }
}
