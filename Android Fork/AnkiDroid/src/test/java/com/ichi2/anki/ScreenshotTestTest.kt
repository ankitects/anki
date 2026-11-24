// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import com.ichi2.utils.VersionUtils
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

/** Meta tests for [ScreenshotTest] */
class ScreenshotTestTest : ScreenshotTest() {
    @Test
    fun `version name does not change between releases - issue 21453`() {
        // 'Info' displays the version in its toolbar, which shows as a break in screenshot diffs
        assertThat(VersionUtils.pkgVersionName, equalTo(STABLE_VERSION_NAME))
    }
}
