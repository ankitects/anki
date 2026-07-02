/*
 *  Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
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
