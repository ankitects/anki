// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Piyush Goel <piyushgoel2008@gmail.com>

package com.ichi2.anki.utils

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.utils.AssetHelper
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AssetHelperTest {
    @Test
    fun guessMimeTypeTest() {
        assertThat(AssetHelper.guessMimeType("test.js"), equalTo("text/javascript"))
        assertThat(AssetHelper.guessMimeType("test.mjs"), equalTo("text/javascript"))
        assertThat(AssetHelper.guessMimeType("test.json"), equalTo("application/json"))
        assertThat(AssetHelper.guessMimeType("test.css"), equalTo("text/css"))
        assertThat(AssetHelper.guessMimeType("test.txt"), equalTo("text/plain"))
        assertThat(AssetHelper.guessMimeType("test.png"), equalTo("image/png"))
        assertThat(AssetHelper.guessMimeType("test.zip"), equalTo("application/zip"))
        // #14696: path with space in it
        assertThat(AssetHelper.guessMimeType("/Dominant seventh arpeggio-C-right-1-increasing_test.svg"), equalTo("image/svg+xml"))
    }
}
