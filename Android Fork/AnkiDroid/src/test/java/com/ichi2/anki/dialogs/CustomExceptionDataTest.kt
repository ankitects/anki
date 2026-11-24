// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs

import com.ichi2.anki.dialogs.DatabaseErrorDialog.CustomExceptionData
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.arrayWithSize
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.Test

class CustomExceptionDataTest {
    @Test
    fun `exception type and message is printed`() {
        val exception = IllegalStateException("Java heap space")
        assertThat("stack trace should exist", exception.stackTrace, not(arrayWithSize(0)))

        val exceptionData = CustomExceptionData.fromException(exception)

        val outputString = exceptionData.toHumanReadableString()

        assertThat(outputString, containsString("IllegalStateException: Java heap space"))
    }
}
