// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.backend

import android.database.Cursor
import androidx.annotation.CheckResult
import com.ichi2.anki.common.annotations.DuplicatedCode
import com.ichi2.anki.libanki.DB
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.database.StreamingProtobufSQLiteCursor

/**
 * Wrap a Rust backend connection (which provides an SQL interface).
 * Caller is responsible for opening&closing the database.
 */
@DuplicatedCode("libanki:createDatabaseUsingRustBackend")
@CheckResult
fun createDatabaseUsingRustBackend(backend: Backend): DB = DB(backend)

/** Android cursor access for consumers such as the collection content provider. */
fun DB.queryCursor(
    query: String,
    vararg bindArgs: Any?,
): Cursor = StreamingProtobufSQLiteCursor(database, query, bindArgs)
