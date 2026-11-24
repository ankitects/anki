// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2018 Mike Hardy <github@mikehardy.net>

package com.ichi2.anki.tests.libanki

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabaseCorruptException
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.backend.FrameworkDatabase
import com.ichi2.anki.startup.getDefaultAnkiDroidDirectory
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.testutil.GrantStoragePermission
import net.ankiweb.rsdroid.database.AnkiSupportSQLiteDatabase
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.Random

@RunWith(AndroidJUnit4::class)
class DBTest : InstrumentedTest() {
    @get:Rule
    var runtimePermissionRule = GrantStoragePermission.instance

    @Test
    @Throws(Exception::class)
    fun testDBCorruption() {
        val storagePath = getDefaultAnkiDroidDirectory(testContext)
        val illFatedDBFile = File(storagePath, "illFatedDB.anki2")

        // Make sure we have clean state to start with
        SQLiteDatabase.deleteDatabase(illFatedDBFile)
        Assert.assertFalse("database exists already", illFatedDBFile.exists())
        val callback = TestCallback(1)
        val illFatedDB =
            FrameworkDatabase(
                AnkiSupportSQLiteDatabase.withFramework(
                    testContext,
                    illFatedDBFile.canonicalPath,
                    callback,
                ),
            )
        Assert.assertFalse("database should not be corrupt yet", callback.databaseIsCorrupt)

        // Scribble in it
        val b = ByteArray(1024)
        Random().nextBytes(b)
        FileOutputStream(illFatedDBFile).use { illFatedDBFileStream ->
            illFatedDBFileStream.write(b, 0, 1024)
            illFatedDBFileStream.flush()
        }

        // Try to do something
        try {
            illFatedDB.execute("CREATE TABLE test_table (test_column INTEGER NOT NULL);")
            Assert.fail("There should have been a corruption exception")
        } catch (e: SQLiteDatabaseCorruptException) {
            // do nothing, it is expected
        }
        Assert.assertTrue("database corruption not detected", callback.databaseIsCorrupt)

        // our handler avoids deleting databases, in contrast with default handler
        Assert.assertTrue("database incorrectly deleted on corruption", illFatedDBFile.exists())
        illFatedDB.close()
        SQLiteDatabase.deleteDatabase(illFatedDBFile)
    }

    // Test fixture that lets us inspect corruption handler status
    inner class TestCallback(
        version: Int,
    ) : AnkiSupportSQLiteDatabase.DefaultDbCallback(version) {
        internal var databaseIsCorrupt = false

        override fun onCorruption(db: SupportSQLiteDatabase) {
            databaseIsCorrupt = true
            super.onCorruption(db)
        }
    }
}
