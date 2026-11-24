// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.scheduling

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.nullValue
import org.junit.Test

class FsrsTest {
    @Test
    fun `FSRS version is mapped to user-facing version`() {
        assertThat("FSRS ${Fsrs.version.libraryVersion} should be mapped to a user-facing version", Fsrs.displayVersion, not(nullValue()))
    }
}
