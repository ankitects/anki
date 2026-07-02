/*
 * Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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
import anki.ankidroid.SchedTimingTodayLegacyRequest
import net.ankiweb.rsdroid.ankiutil.InstrumentedTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.Is
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class BackendIntegrationTests : InstrumentedTest() {
    /** Ensure that the database can't be locked  */
    @get:Rule
    var timeout = Timeout(60, TimeUnit.SECONDS)

    @Before
    fun test() {
        require(isEmulator) { "do not run on real device yet" }
    }

    @Test
    fun testBackendException() {
        val Backend = closedBackend
        try {
            Backend.closeCollection(true)
            Assert.fail("call should have failed - needs an open collection")
        } catch (ex: BackendException) {
            // OK
        }
    }

    @Test
    fun schedTimingTodayCall() {
        val backend = getBackend("initial_version_2_12_1.anki2")
        backend.schedTimingTodayLegacy(SchedTimingTodayLegacyRequest.getDefaultInstance())
    }

    @Test
    fun fullQueryTest() {
        val backendV1 = getBackend("initial_version_2_12_1.anki2")
        backendV1.fullQuery("select * from col")
    }

    @Test
    fun columnNamesTest() {
        val backendV1 = getBackend("initial_version_2_12_1.anki2")
        val names = backendV1.getColumnNames("select * from col")
        assertThat(
            names,
            Is.`is`(
                arrayOf(
                    "id",
                    "crt",
                    "mod",
                    "scm",
                    "ver",
                    "dty",
                    "usn",
                    "ls",
                    "conf",
                    "models",
                    "decks",
                    "dconf",
                    "tags",
                ),
            ),
        )
    }
}
