// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import android.os.Bundle
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.hasItem
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat
import kotlin.test.assertNotNull

object IntentAssert {
    /**
     * Check that bundle does noe have [extraKey]
     */
    fun doesNotHaveExtra(
        arguments: Bundle?,
        extraKey: String?,
    ) {
        val keySet = assertNotNull(arguments).keySet()
        assertThat("Intent should not have extra '$extraKey'", keySet, not(hasItem(extraKey)))
    }

    /**
     * Check that bundle has [extraKey]
     */
    fun hasExtra(
        arguments: Bundle?,
        extraKey: String?,
        value: Long,
    ) {
        val keySet = assertNotNull(arguments).keySet()
        assertThat("Intent should have extra '$extraKey'", keySet, hasItem(extraKey))

        assertThat(arguments.getLong(extraKey, -1337), equalTo(value))
    }
}
