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

import com.ichi2.anki.libanki.testutils.DEFAULT_TEST_TIMEOUT
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

/**
 * Tests for our testing framework
 */
class MetaTest {
    @Test
    fun `test timeout is unchanged`() {
        // a number of users are changing the default timeout on tests to try to fix a timeout,
        // rather than debug the cause of the timeout.

        // This is normally a hung thread (often due to Robolectric), so increasing the timeout
        // just makes the problem worse
        assertEquals(
            60.seconds,
            DEFAULT_TEST_TIMEOUT,
            "Default test timeout should be unchanged",
        )
    }
}
