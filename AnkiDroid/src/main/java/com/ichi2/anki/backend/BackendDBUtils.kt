/*
 *  Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.backend

import android.content.Context
import androidx.annotation.CheckResult
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.common.crashreporting.CrashReportService.sendExceptionReport
import com.ichi2.anki.libanki.DB
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.database.AnkiSupportSQLiteDatabase
import timber.log.Timber
import java.io.File

/**
 * Open a connection using the system framework.
 */
@CheckResult
fun createDatabaseUsingAndroidFramework(
    context: Context,
    path: File,
): DB {
    val db =
        AnkiSupportSQLiteDatabase.withFramework(
            context,
            path.absolutePath,
            SupportSQLiteOpenHelperCallback(1),
        )
    db.disableWriteAheadLogging()
    db.query("PRAGMA synchronous = 2")
    return DB(db)
}

/**
 * Wrap a Rust backend connection (which provides an SQL interface).
 * Caller is responsible for opening&closing the database.
 */
@CheckResult
fun createDatabaseUsingRustBackend(backend: Backend): DB = DB(AnkiSupportSQLiteDatabase.withRustBackend(backend))

/**
 * The default AnkiDroid SQLite database callback.
 *
 * IMPORTANT: [SupportSQLiteOpenHelperCallback] disables the default Android behaviour of removing the file if corruption
 * is encountered.
 *
 * We do not handle versioning or connection config using the framework APIs, so those methods
 * do nothing in our implementation. However, on corruption events we want to send messages but
 * not delete the database.
 *
 * Note: this does not apply when using the Rust backend (ie for Collection)
 */
private class SupportSQLiteOpenHelperCallback(
    version: Int,
) : AnkiSupportSQLiteDatabase.DefaultDbCallback(version) {
    /** Send error message when corruption is encountered. We don't call super() as we don't accidentally
     * want to opt-in to the standard Android behaviour of removing the corrupted file, but as we're
     * inheriting from DefaultDbCallback which does not call super either, it would be technically safe
     * if we did so.  */
    override fun onCorruption(db: SupportSQLiteDatabase) {
        Timber.e("The database has been corrupted: %s", db.path)
        sendExceptionReport(
            RuntimeException("Database corrupted"),
            "DB.MyDbErrorHandler.onCorruption",
            "Db has been corrupted: " + db.path,
        )
        Timber.i("closeCollection: %s", "Database corrupted")
        CollectionManager.closeCollectionBlocking()
        DatabaseCorruption.isDetected = true
    }
}
