/*
 *  Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.libanki.testutils.AnkiTest
import com.ichi2.anki.utils.ext.flag
import com.ichi2.testutils.EmptyApplication
import com.ichi2.testutils.JvmTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.hasSize
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Tests for [Flag] */
@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
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
