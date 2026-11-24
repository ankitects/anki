// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import com.ichi2.testutils.JvmTest
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import timber.log.Timber
import kotlin.test.assertNotNull

/** @see CardBrowserColumn */
@RunWith(Parameterized::class)
class CardBrowserColumnTest : JvmTest() {
    companion object {
        @Parameterized.Parameters(name = "{0}")
        @JvmStatic // required for initParameters
        fun initParameters(): Collection<Array<Any>> =
            CardBrowserColumn.entries
                .map { arrayOf(it) }
    }

    @Parameterized.Parameter
    @JvmField // required for Parameter
    var columnParam: CardBrowserColumn? = null
    private val column get() = columnParam!!

    @Test
    fun ensureAllColumnsMapped() {
        val collectionColumns = col.backend.allBrowserColumns()
        Timber.w("%s", collectionColumns.joinToString { it.key })
        assertNotNull(collectionColumns.find(column))
    }
}
