// SPDX-License-Identifier: GPL-3.0-or-later

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
