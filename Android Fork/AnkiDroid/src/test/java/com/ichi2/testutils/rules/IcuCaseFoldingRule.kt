// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils.rules

import android.icu.lang.UCharacter
import io.mockk.every
import io.mockk.excludeRecords
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.rules.ExternalResource
import com.ibm.icu.lang.UCharacter as JvmUCharacter

/** Delegates Android's Unicode case folding to ICU4J in plain JVM tests. */
class IcuCaseFoldingRule : ExternalResource() {
    override fun before() {
        mockkStatic(UCharacter::class)
        every { UCharacter.foldCase(any<String>(), any<Boolean>()) } answers {
            JvmUCharacter.foldCase(firstArg<String>(), secondArg<Boolean>())
        }
        // Large tag fixtures fold every code point; recording those calls exhausts the test heap.
        excludeRecords { UCharacter.foldCase(any<String>(), any<Boolean>()) }
    }

    override fun after() {
        unmockkStatic(UCharacter::class)
    }
}
