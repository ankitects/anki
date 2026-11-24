// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.multiprofile

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import net.ankiweb.rsdroid.database.AnkiSupportSQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
// TODO: remove N when minSdk reaches 27. Before API 27, SQLiteOpenHelper opens through openOrCreateDatabase
@Config(sdk = [Build.VERSION_CODES.N, Config.TARGET_SDK], application = Application::class)
class ProfileContextWrapperDatabaseTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val profile = ProfileContextWrapper.create(context, ProfileId("p_work"), File(context.filesDir.parentFile, "p_work"))

    @Test
    fun `absolute database name keeps its original file`() {
        val file = File(profile.noBackupFilesDir, "absolute.db")

        profile.openOrCreateDatabase(file.path, Context.MODE_PRIVATE, null).use { db ->
            assertEquals(file.path, db.path)
        }
    }

    @Test
    fun `WorkManager database opens in the profile's no_backup folder`() {
        val config =
            SupportSQLiteOpenHelper.Configuration
                .builder(profile)
                .name("androidx.work.workdb")
                .noBackupDirectory(true)
                .callback(AnkiSupportSQLiteDatabase.DefaultDbCallback(1))
                .build()

        FrameworkSQLiteOpenHelperFactory().create(config).use { helper ->
            assertEquals(File(profile.noBackupFilesDir, "androidx.work.workdb").path, helper.writableDatabase.path)
        }
    }
}
