/*
 *  Copyright (c) 2024 Mike Hardy <github@mikehardy.net>
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
package com.ichi2.utils

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.IsEqual.equalTo
import org.junit.Test

class WebViewUtilsTest {
    @Test
    @Suppress("ktlint:standard:max-line-length")
    fun testWebviewVersionCodes() {
        assertThat(
            "Known old webview determined correctly",
            checkWebViewVersionComponents(
                "com.google.android.webview",
                "53.0.2785.124",
                OLDEST_WORKING_WEBVIEW_VERSION_CODE.value - 1,
                "Mozilla/5.0 (Linux; Android 7.0; Android SDK built for arm64 Build/NYC; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/53.0.2785.124 Mobile Safari/537.36",
            ),
            equalTo(53),
        )

        assertThat(
            "Known good webview determined correctly",
            checkWebViewVersionComponents(
                "com.google.android.webview",
                "131.0.6778.39",
                677803933L,
                "Mozilla/5.0 (Linux; Android 14; sdk_gphone64_arm64 Build/UE1A.230829.036.A4; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/131.0.6778.39 Mobile Safari/537.36",
            ),
            equalTo(null),
        )

        assertThat(
            "Known confusing webview determined correctly",
            checkWebViewVersionComponents(
                "com.google.android.webview",
                "74.0.0.0",
                OLDEST_WORKING_WEBVIEW_VERSION_CODE.value - 1,
                "Mozilla/5.0 (Linux; Android 9; SM-A730F Build/PPR1.180610.011; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/130.0.6723.102 Mobile Safari/537.36",
            ),
            equalTo(null),
        )
        assertThat(
            "Known old huawei webview determined correctly",
            checkWebViewVersionComponents(
                "com.huawei.webview",
                "unknown",
                356L,
                "Mozilla/5.0 (Linux; Android 10; CDY-AN90 Build/HUAWEICDY-AN90; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/78.0.3904.108 Mobile Safari/537.36",
            ),
            equalTo(78),
        )
    }
}
