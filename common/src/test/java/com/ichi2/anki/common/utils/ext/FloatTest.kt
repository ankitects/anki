// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.utils.ext

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

/**
 * Tests for methods in [Float][clamp]
 */
class FloatTest {
    @Test
    fun clampOneZero() {
        fun Float.clampOneZero() = this.clamp(0f, 1f)

        assertThat(1.0f.clampOneZero(), equalTo(1.0f))
        assertThat(1.1f.clampOneZero(), equalTo(1.0f))

        assertThat(0.0f.clampOneZero(), equalTo(0.0f))
        assertThat((-1.0f).clampOneZero(), equalTo(0.0f))

        assertThat((0.5f).clampOneZero(), equalTo(0.5f))
        assertThat((0.3f).clampOneZero(), equalTo(0.3f))
    }

    @Test
    fun clampCustomValue() {
        fun Float.clampPercentage() = this.clamp(0f, 1000f)

        assertThat(1.0f.clampPercentage(), equalTo(1.0f))
        assertThat(1.1f.clampPercentage(), equalTo(1.1f))
    }
}
