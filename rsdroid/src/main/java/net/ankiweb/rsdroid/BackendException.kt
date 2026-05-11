/*
 * Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteFullException
import anki.backend.BackendError
import anki.links.HelpPageLinkRequest.HelpPage
import net.ankiweb.rsdroid.exceptions.*
import net.ankiweb.rsdroid.exceptions.BackendSyncException.BackendSyncAuthFailedException
import net.ankiweb.rsdroid.exceptions.BackendSyncException.BackendSyncServerMessageException
import java.util.*
import java.util.regex.Pattern

open class BackendException : RuntimeException {
    private val error: BackendError?

    constructor(error: BackendError) : super(error.message) {
        this.error = error
    }

    constructor(message: String?) : super(message) {
        error = null
    }

    val helpPage: HelpPage?
        get() = if (error?.hasHelpPage() == true) error.helpPage else null

    /**
     * Returns a link to the Anki Desktop help page for a given [BackendException]
     *
     * e.g. [HelpPage.CARD_TYPE_TEMPLATE_ERROR] => `https://docs.ankiweb.net/templates/errors.html#template-syntax-error`
     */
    @Suppress("unused")
    fun getDesktopHelpPageLink(backend: Backend): String? = helpPage?.let { backend.helpPageLink(it) }

    open fun toSQLiteException(query: String): RuntimeException {
        val message = String.format(Locale.ROOT, "error while compiling: \"%s\": %s", query, this.localizedMessage)
        return SQLiteException(message, this)
    }

    class BackendDbException(
        error: BackendError,
    ) : BackendException(error) {
        override fun toSQLiteException(query: String): RuntimeException {
            val message = this.localizedMessage
            if (message == null) {
                val outMessage = String.format(Locale.ROOT, "Unknown error while compiling: \"%s\"", query)
                return SQLiteException(outMessage, this)
            }
            if (message.contains("InvalidParameterCount")) {
                val p = Pattern.compile("InvalidParameterCount\\((\\d*), (\\d*)\\)").matcher(message)
                if (p.find()) {
                    val givenParams = p.group(1)!!.toInt()
                    val expectedParams = p.group(2)!!.toInt()
                    val errorMessage =
                        String.format(
                            Locale.ROOT,
                            "Cannot bind argument at index %d because the index is out of range.  The statement has %d parameters.",
                            givenParams,
                            expectedParams,
                        )
                    return IllegalArgumentException(errorMessage, this)
                }
            } else if (message.contains("ConstraintViolation")) {
                return SQLiteConstraintException(message)
            } else if (message.contains("DiskFull")) {
                return SQLiteFullException(message)
            } else if (message.contains("DatabaseCorrupt")) {
                val outMessage = String.format(Locale.ROOT, "error while compiling: \"%s\": %s", query, message)
                return SQLiteDatabaseCorruptException(outMessage)
            }
            val outMessage = String.format(Locale.ROOT, "error while compiling: \"%s\": %s", query, message)
            return SQLiteException(outMessage, this)
        }

        class BackendDbFileTooNewException(
            error: BackendError,
        ) : BackendException(error)

        class BackendDbFileTooOldException(
            error: BackendError,
        ) : BackendException(error)

        class BackendDbLockedException(
            error: BackendError,
        ) : BackendException(error)

        class BackendDbMissingEntityException(
            error: BackendError,
        ) : BackendException(error)

        companion object {
            fun fromDbError(error: BackendError): BackendException {
                val localised = error.message ?: return BackendDbException(error)
                if (localised.contains("kind: FileTooNew")) {
                    return BackendDbFileTooNewException(error)
                }
                if (localised.contains("kind: FileTooOld")) {
                    return BackendDbFileTooOldException(error)
                }
                if (localised.contains("kind: MissingEntity")) {
                    return BackendDbMissingEntityException(error)
                }
                if (localised.contains("kind: Other")) {
                    return BackendDbException(error)
                }
                // Anki already open, or media currently syncing.
                return if (localised.startsWith("Anki already open")) {
                    BackendDbLockedException(error)
                } else {
                    BackendDbException(error)
                }
            }
        }
    }

    class BackendSearchException(
        error: BackendError,
    ) : BackendException(error)

    class BackendUndoEmptyException(
        error: BackendError,
    ) : BackendException(error)

    class BackendCustomStudyException(
        error: BackendError,
    ) : BackendException(error)

    /** @see BackendError.Kind.IMPORT_ERROR */
    class BackendImportException(
        error: BackendError,
    ) : BackendException(error)

    /** @see BackendError.Kind.DELETED */
    class BackendItemDeletedException(
        error: BackendError,
    ) : BackendException(error)

    class BackendCardTypeException(
        error: BackendError,
    ) : BackendException(error)

    /** @see BackendError.Kind.UNRECOGNIZED */
    class BackendUnrecognizedException(
        error: BackendError,
    ) : BackendException(error)

    class BackendOsErrorException(
        error: BackendError,
    ) : BackendException(error)

    class BackendSchedulerUpgradeRequiredException(
        error: BackendError,
    ) : BackendException(error)

    class BackendInvalidCertificateFormatException(
        error: BackendError,
    ) : BackendException(error)

    class BackendInvalidChecksumException(
        error: BackendError,
    ) : BackendException(error)

    class BackendFatalError(
        error: BackendError,
    ) : BackendException(error)

    companion object {
        fun fromError(error: BackendError): BackendException {
            when (error.kind!!) {
                BackendError.Kind.DB_ERROR -> return BackendDbException.fromDbError(error)
                BackendError.Kind.JSON_ERROR -> return BackendJsonException(error)
                BackendError.Kind.SYNC_AUTH_ERROR -> return BackendSyncAuthFailedException(error)
                BackendError.Kind.SYNC_OTHER_ERROR -> return BackendSyncException(error)
                BackendError.Kind.SYNC_SERVER_MESSAGE -> return BackendSyncServerMessageException(error)
                BackendError.Kind.ANKIDROID_PANIC_ERROR -> return BackendFatalError(error)
                BackendError.Kind.EXISTS -> return BackendExistingException(error)
                BackendError.Kind.FILTERED_DECK_ERROR -> return BackendDeckIsFilteredException(error)
                BackendError.Kind.INTERRUPTED -> return BackendInterruptedException(error)
                BackendError.Kind.PROTO_ERROR -> return BackendProtoException(error)
                BackendError.Kind.NOT_FOUND_ERROR -> return BackendNotFoundException(error)
                BackendError.Kind.INVALID_INPUT -> return BackendInvalidInputException.fromInvalidInputError(error)
                BackendError.Kind.NETWORK_ERROR -> return BackendNetworkException(error)
                BackendError.Kind.TEMPLATE_PARSE -> return BackendTemplateException.fromTemplateError(error)
                BackendError.Kind.IO_ERROR -> return BackendIoException(error)
                BackendError.Kind.SEARCH_ERROR -> return BackendSearchException(error)

                BackendError.Kind.UNDO_EMPTY -> return BackendUndoEmptyException(error)
                BackendError.Kind.CUSTOM_STUDY_ERROR -> return BackendCustomStudyException(error)
                BackendError.Kind.IMPORT_ERROR -> return BackendImportException(error)
                BackendError.Kind.DELETED -> return BackendItemDeletedException(error)
                BackendError.Kind.CARD_TYPE_ERROR -> return BackendCardTypeException(error)
                BackendError.Kind.UNRECOGNIZED -> return BackendUnrecognizedException(error)
                BackendError.Kind.OS_ERROR -> return BackendOsErrorException(error)
                BackendError.Kind.SCHEDULER_UPGRADE_REQUIRED -> return BackendSchedulerUpgradeRequiredException(error)
                BackendError.Kind.INVALID_CERTIFICATE_FORMAT -> return BackendInvalidCertificateFormatException(error)
                BackendError.Kind.INVALID_CHECKSUM -> return BackendInvalidChecksumException(error)
            }
        }

        fun fromException(ex: Exception?): RuntimeException = RuntimeException(ex)
    }
}
