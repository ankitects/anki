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

package com.ichi2.anki

import android.content.Context
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.utils.android.SdCard
import com.ichi2.anki.dialogs.utils.ankiListView
import com.ichi2.anki.dialogs.utils.message
import com.ichi2.anki.dialogs.utils.title
import com.ichi2.anki.exception.StorageAccessException
import com.ichi2.testutils.TestException
import com.ichi2.testutils.withNoWritePermission
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.unmockkObject
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowDialog
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolutePathString

@RunWith(AndroidJUnit4::class)
@Config(shadows = [ShadowNullExternalFilesDir::class])
class DeckPickerNoExternalFilesDirTest : RobolectricTest() {
    // TODO: @Config(sdk = [Build.VERSION_CODES.BAKLAVA]) // Bug 460912704 occurs on Android 16
    @Test
    fun `Fatal error is shown on fresh startup getExternalFilesDir is null`() {
        // Currently undefined if we should fail when PREF_COLLECTION_PATH is set
        //  but getExternalFilesDir returns null

        // IntroductionActivity should be skipped by our code so we can show the error
        // without user interaction
        deckPicker(skipIntroduction = false) {
            val message = (ShadowDialog.getLatestDialog() as AlertDialog).message
            assertThat(message, containsString("getExternalFilesDir unexpectedly returned null"))
        }
    }

    @Test
    @Config(application = AnkiDroidAppWithCollectionButUnwritableStorage::class)
    fun `Fatal error is shown when getExternalFilesDir is null and collection is set but unwritable`() {
        // IntroductionActivity should be skipped by our code so we can show the error
        // without user interaction
        deckPicker(skipIntroduction = false) {
            val message = (ShadowDialog.getLatestDialog() as AlertDialog).message
            assertThat(message, containsString("getExternalFilesDir unexpectedly returned null"))
        }
    }

    @Test
    fun `fatal error is shown after 'Create a new collection' and getExternalFilesDir is null`() =
        try {
            AnkiDroidApp.clearFatalError()
            withNoWritePermission {
                CollectionHelper.ankiDroidDirectoryOverride = tempFolder.newFolder()

                mockkObject(CollectionHelper, CollectionManager, SdCard)
                every { CollectionHelper.isCurrentAnkiDroidDirAccessible(any()) } returns false
                every { CollectionManager.getColUnsafe() } throws TestException("")
                every { SdCard.isMounted } returns true

                setIntroductionSlidesShown(true)

                deckPicker {
                    (ShadowDialog.getLatestDialog() as AlertDialog).also { dialog ->
                        assertThat(dialog.title, equalTo("Inaccessible collection"))
                        shadowOf(dialog.ankiListView).clickFirstItemContainingText("Create a new collection")
                    }

                    val dialog = (ShadowDialog.getLatestDialog() as AlertDialog)
                    assertThat(dialog.message, containsString("getExternalFilesDir unexpectedly returned null"))
                }
            }
        } finally {
            CollectionHelper.ankiDroidDirectoryOverride = null
            unmockkAll()
        }
}

/**
 * A shadow which makes [Context.getExternalFilesDir] return `null`
 */
@Suppress("ProtectedInFinal", "unused")
@Implements(
    className = "android.app.ContextImpl",
    isInAndroidSdk = false,
)
class ShadowNullExternalFilesDir {
    @Implementation
    protected fun getExternalFilesDir(type: String?): File? = null
}

class AnkiDroidAppWithCollectionButUnwritableStorage : AnkiDroidApp() {
    override fun onCreate() =
        withTempDir("DeckPickerNoExternalFilesDirTest") { path ->
            try {
                mockkObject(CollectionHelper)
                every { CollectionHelper.initializeAnkiDroidDirectory(any()) } throws StorageAccessException("testing")
                this.sharedPrefs().edit {
                    putString(CollectionHelper.PREF_COLLECTION_PATH, path.absolutePathString())
                }
                super.onCreate()
            } finally {
                unmockkObject(CollectionHelper)
            }
        }
}

fun <T> withTempDir(
    prefix: String,
    block: (Path) -> T,
): T {
    val dir = Files.createTempDirectory(prefix)
    return try {
        block(dir)
    } finally {
        dir.toFile().deleteRecursively()
    }
}
