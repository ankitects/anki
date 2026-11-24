// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import com.ichi2.anki.libanki.testutils.AnkiTest
import com.ichi2.anki.utils.ext.flag
import com.ichi2.testutils.JvmTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.hasSize
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests for [Flag] */
class FlagTest : JvmTest() {
    @Test
    fun `search node conversion integration test`() {
        val allFlags = Flag.entries

        for (flag in allFlags) {
            addCardWithFlag(flag)
        }

        assertThat("empty search returns all cards", col.findCards(""), hasSize(allFlags.size))

        for (flag in allFlags) {
            val searchString = col.buildSearchString(listOf(flag.toSearchNode()))
            val results = col.findCards(searchString)

            assertThat("$flag search returns results", results, hasSize(1))
            val result = col.getCard(results.single())
            assertEquals("$flag search returns correct result", result.flag, flag)
        }
    }
}

context(test: AnkiTest)
private fun addCardWithFlag(flag: Flag) =
    with(test) {
        val cid = addBasicNote().cids().single()
        col.setUserFlagForCards(listOf(cid), flag.code)
    }
