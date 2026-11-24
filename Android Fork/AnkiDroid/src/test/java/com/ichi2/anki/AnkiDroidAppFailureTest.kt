// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.testutils.AnkiDroidAppWithFatalError
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = AnkiDroidAppWithFatalError::class)
class AnkiDroidAppFailureTest : RobolectricTest() {
    @Test
    fun `fatal error does not crash onCreate`() {
    }
}
