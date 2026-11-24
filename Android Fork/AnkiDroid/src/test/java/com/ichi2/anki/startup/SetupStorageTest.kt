// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.startup

import android.os.Build
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.anki.exception.StorageNotConfiguredException
import com.ichi2.anki.exception.SystemStorageException
import com.ichi2.testutils.publicCollectionPath
import com.ichi2.testutils.withAllFilesAccess
import com.ichi2.testutils.withManageExternalStorageInManifest
import com.ichi2.testutils.withWritePermissions
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class SetupStorageTest : RobolectricTest() {
    private val prefs
        get() = targetContext.sharedPrefs()

    private val collectionPath: String?
        get() = prefs.getString(CollectionHelper.PREF_COLLECTION_PATH, null)

    @After
    fun resetCollectionHelperState() {
        CollectionHelper.ankiDroidDirectoryOverride = null
        CollectionHelper.systemStorageFailure = null
    }

    /**
     * Reading the collection path must not silently choose and persist a default:
     * that decision belongs to startup ([maybeSetDefaultCollectionPath]).
     */
    @Test
    fun `reading the collection path throws when unset`() {
        prefs.edit { remove(CollectionHelper.PREF_COLLECTION_PATH) }

        assertFailsWith<StorageNotConfiguredException> {
            CollectionHelper.getCurrentAnkiDroidDirectory(targetContext)
        }
    }

    /**
     * A storage failure at startup must not masquerade as the expected 'no collection path set'
     * state: the [SystemStorageException] edge case (OS bug/SD card issue) is reported as itself.
     */
    @Test
    fun `reading the collection path reports a recorded startup storage failure`() {
        prefs.edit { remove(CollectionHelper.PREF_COLLECTION_PATH) }
        val failure = SystemStorageException.build("simulated getExternalFilesDir failure")
        CollectionHelper.systemStorageFailure = failure

        val thrown =
            assertFailsWith<SystemStorageException> {
                CollectionHelper.getCurrentAnkiDroidDirectory(targetContext)
            }
        assertSame(failure, thrown)
    }

    @Test
    fun `reading the collection path returns the stored value`() {
        prefs.edit { putString(CollectionHelper.PREF_COLLECTION_PATH, "/a/collection/path") }

        assertEquals(File("/a/collection/path"), CollectionHelper.getCurrentAnkiDroidDirectory(targetContext))
    }

    @Test
    fun `the directory override takes precedence over the stored value`() {
        prefs.edit { putString(CollectionHelper.PREF_COLLECTION_PATH, "/a/collection/path") }
        CollectionHelper.ankiDroidDirectoryOverride = File("/an/override")

        assertEquals(File("/an/override"), CollectionHelper.getCurrentAnkiDroidDirectory(targetContext))
    }

    @Test
    fun `maybeSetDefaultCollectionPath persists a default when unset and storage is accessible`() {
        prefs.edit { remove(CollectionHelper.PREF_COLLECTION_PATH) }

        withAllFilesAccess { maybeSetDefaultCollectionPath(targetContext) }

        assertTrue(!collectionPath.isNullOrEmpty(), "a default collection path should be set")
    }

    @Test
    fun `maybeSetDefaultCollectionPath does not overwrite an existing value`() {
        prefs.edit { putString(CollectionHelper.PREF_COLLECTION_PATH, "/a/custom/path") }

        maybeSetDefaultCollectionPath(targetContext)

        assertEquals("/a/custom/path", collectionPath)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.Q])
    fun `Android 10 - public storage is persisted before permissions are granted`() {
        prefs.edit { remove(CollectionHelper.PREF_COLLECTION_PATH) }

        maybeSetDefaultCollectionPath(targetContext)

        assertEquals(publicCollectionPath, collectionPath)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.Q])
    fun `Android 10 - public storage is persisted once permissions are granted`() {
        prefs.edit { remove(CollectionHelper.PREF_COLLECTION_PATH) }

        withWritePermissions { maybeSetDefaultCollectionPath(targetContext) }

        assertEquals(publicCollectionPath, collectionPath)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.R])
    fun `full build - storage decision is deferred before 'All files access' is granted`() {
        prefs.edit { remove(CollectionHelper.PREF_COLLECTION_PATH) }

        withManageExternalStorageInManifest { maybeSetDefaultCollectionPath(targetContext) }

        assertNull(collectionPath, "the storage decision should be deferred to the user")
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.R])
    fun `full build - public storage is persisted once 'All files access' is granted`() {
        prefs.edit { remove(CollectionHelper.PREF_COLLECTION_PATH) }

        withManageExternalStorageInManifest {
            withAllFilesAccess { maybeSetDefaultCollectionPath(targetContext) }
        }

        assertEquals(publicCollectionPath, collectionPath)
    }
}
