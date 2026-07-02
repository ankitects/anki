/*
 * Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.ankiweb.rsdroid

import net.ankiweb.rsdroid.BackendException.BackendDbException
import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbFileTooNewException
import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbFileTooOldException
import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbLockedException
import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbMissingEntityException
import net.ankiweb.rsdroid.database.NotImplementedException
import net.ankiweb.rsdroid.exceptions.BackendDeckIsFilteredException
import net.ankiweb.rsdroid.exceptions.BackendExistingException
import net.ankiweb.rsdroid.exceptions.BackendInterruptedException
import net.ankiweb.rsdroid.exceptions.BackendInvalidInputException
import net.ankiweb.rsdroid.exceptions.BackendInvalidInputException.BackendCollectionAlreadyOpenException
import net.ankiweb.rsdroid.exceptions.BackendInvalidInputException.BackendCollectionNotOpenException
import net.ankiweb.rsdroid.exceptions.BackendJsonException
import net.ankiweb.rsdroid.exceptions.BackendNetworkException
import net.ankiweb.rsdroid.exceptions.BackendNotFoundException
import net.ankiweb.rsdroid.exceptions.BackendProtoException
import net.ankiweb.rsdroid.exceptions.BackendSyncException
import net.ankiweb.rsdroid.exceptions.BackendSyncException.BackendSyncAuthFailedException
import net.ankiweb.rsdroid.exceptions.BackendTemplateException
import org.junit.Assert
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.*

@RunWith(Parameterized::class)
class ExceptionTest {
    private var backend: BackendForTesting? = null

    @Parameterized.Parameter
    @JvmField
    var errorType: BackendForTesting.ErrorType? = null

    @Parameterized.Parameter(value = 1)
    @JvmField
    var clazz: Class<out Exception?>? = null

    @Before
    fun errorProducesNamedException() {
        backend = BackendForTesting.create()
    }

    @Test
    fun testError() {
        if (NotImplementedException::class.java == clazz) {
            Assume.assumeTrue("This case cannot be handled yet", false)
        }
        assertThrows(errorType!!, clazz)
    }

    private fun assertThrows(
        e: BackendForTesting.ErrorType,
        clazz: Class<out Exception?>?,
    ) {
        try {
            backend!!.debugProduceError(e)
            Assert.fail()
        } catch (ex: Throwable) {
            // we catch BackendFatalError here
            if (ex.javaClass != clazz) {
                Assert.fail("ex was not an instance of " + clazz!!.simpleName + ". Instead: " + ex.javaClass + ". message: " + ex.message)
            }
        }
    }

    companion object {
        @Parameterized.Parameters(name = "{0}")
        @JvmStatic
        fun initParameters(): Collection<Array<Any>> {
            // This does one run with schedVersion injected as 1, and one run as 2

            // If an exception does not provide enough information to handle it
            val NOT_POSSIBLE = NotImplementedException::class.java
            return Arrays.asList(
                *arrayOf(
                    arrayOf(
                        BackendForTesting.ErrorType.CollectionAlreadyOpen,
                        BackendCollectionAlreadyOpenException::class.java,
                    ),
                    arrayOf(
                        BackendForTesting.ErrorType.CollectionNotOpen,
                        BackendCollectionNotOpenException::class.java,
                    ),
                    arrayOf(BackendForTesting.ErrorType.SearchError, NOT_POSSIBLE),
                    arrayOf(
                        BackendForTesting.ErrorType.SyncErrorAuthFailed,
                        BackendSyncAuthFailedException::class.java,
                    ),
                    arrayOf(
                        BackendForTesting.ErrorType.SyncErrorOther,
                        BackendSyncException::class.java,
                    ),
                    arrayOf(
                        BackendForTesting.ErrorType.SyncErrorServerMessage,
                        BackendSyncException.BackendSyncServerMessageException::class.java,
                    ),
                    arrayOf(BackendForTesting.ErrorType.DbErrorCorrupt, NOT_POSSIBLE),
                    arrayOf(
                        BackendForTesting.ErrorType.DbErrorFileTooNew,
                        BackendDbFileTooNewException::class.java,
                    ),
                    arrayOf(
                        BackendForTesting.ErrorType.DbErrorFileTooOld,
                        BackendDbFileTooOldException::class.java,
                    ),
                    arrayOf(
                        BackendForTesting.ErrorType.DbErrorLocked,
                        BackendDbLockedException::class.java,
                    ),
                    arrayOf(
                        BackendForTesting.ErrorType.DbErrorMissingEntity,
                        BackendDbMissingEntityException::class.java,
                    ),
                    arrayOf(
                        BackendForTesting.ErrorType.DbErrorOther,
                        BackendDbException::class.java,
                    ),
                    arrayOf(
                        BackendForTesting.ErrorType.NetworkError,
                        BackendNetworkException::class.java,
                    ),
                    arrayOf(
                        BackendForTesting.ErrorType.FilteredDeckError,
                        BackendDeckIsFilteredException::class.java,
                    ),
                    arrayOf(
                        BackendForTesting.ErrorType.Existing,
                        BackendExistingException::class.java,
                    ),
                    arrayOf(
                        BackendForTesting.ErrorType.Interrupted,
                        BackendInterruptedException::class.java,
                    ),
                    arrayOf(
                        BackendForTesting.ErrorType.InvalidInput,
                        BackendInvalidInputException::class.java,
                    ),
                    arrayOf(
                        BackendForTesting.ErrorType.JSONError,
                        BackendJsonException::class.java,
                    ),
                    arrayOf(
                        BackendForTesting.ErrorType.ProtoError,
                        BackendProtoException::class.java,
                    ),
                    arrayOf(
                        BackendForTesting.ErrorType.TemplateError,
                        BackendTemplateException::class.java,
                    ),
                    arrayOf(
                        BackendForTesting.ErrorType.NotFound,
                        BackendNotFoundException::class.java,
                    ),
                ),
            )
        }
    }
}
