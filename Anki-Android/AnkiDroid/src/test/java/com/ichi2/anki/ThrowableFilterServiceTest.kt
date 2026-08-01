/*
 *  Copyright (c) 2024 voczi <dev@voczi.com>
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

package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.backendError
import com.ichi2.anki.exception.StorageAccessException
import com.ichi2.anki.servicelayer.ThrowableFilterService
import com.ichi2.anki.servicelayer.ThrowableFilterService.safeFromPII
import com.ichi2.testutils.JvmTest
import net.ankiweb.rsdroid.exceptions.BackendDeckIsFilteredException
import net.ankiweb.rsdroid.exceptions.BackendNetworkException
import net.ankiweb.rsdroid.exceptions.BackendSyncException
import net.ankiweb.rsdroid.exceptions.BackendSyncException.BackendSyncServerMessageException
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class ThrowableFilterServiceTest : JvmTest() {
    @Test
    fun `Normal exceptions are flagged as PII-safe`() {
        val exception = BackendDeckIsFilteredException(backendError {})
        assertTrue(exception.safeFromPII(), "Exception reported as safe from PII")
    }

    @Test
    fun `BackendSyncServerMessage exceptions are flagged as PII-unsafe`() {
        val exception1 = BackendSyncServerMessageException(backendError { })
        assertFalse(exception1.safeFromPII(), "Exception reported as not safe from PII")

        val exception2 = Exception("", Exception("", exception1))
        assertFalse(exception2.safeFromPII(), "Nested exception reported as not safe from PII")
    }

    @Test
    fun `exceptions are discarded correctly by type`() {
        // regular exceptions should go through
        assertFalse(ThrowableFilterService.shouldDiscardThrowable(Exception("wanted")))

        // exceptions of known unwanted types should not go through
        val exception1 = BackendNetworkException(backendError {})
        assertTrue(ThrowableFilterService.shouldDiscardThrowable(exception1))
        val exception2 = BackendSyncException(backendError {})
        assertTrue(ThrowableFilterService.shouldDiscardThrowable(exception2))
        val exception3 = StorageAccessException("test exception")
        assertTrue(ThrowableFilterService.shouldDiscardThrowable(exception3))
    }
}
