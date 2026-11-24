// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.anki.common.storage.StorageDecision
import com.ichi2.anki.exception.StorageNotConfiguredException
import com.ichi2.anki.exception.SystemStorageException
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

/**
 * Proves the storage-decision gate in [CollectionManager.ensureOpenInner] is wired up.
 * [CollectionHelper.storageDecision] is [StorageDecision.Decided] once a collection path has
 * been set ([CollectionHelper.PREF_COLLECTION_PATH]); tests force
 * [StorageDecision.Undecided] via the test override.
 */
@RunWith(AndroidJUnit4::class)
class StorageDecisionGateTest : RobolectricTest() {
    private val prefs
        get() = targetContext.sharedPrefs()

    @After
    fun resetOverrides() {
        CollectionHelper.ankiDroidDirectoryOverride = null
        CollectionHelper.storageDecisionTestOverride = null
        CollectionHelper.systemStorageFailure = null
    }

    @Test
    fun `storage decision is decided when the collection path is set`() {
        prefs.edit { putString(CollectionHelper.PREF_COLLECTION_PATH, "/a/collection/path") }
        assertEquals(StorageDecision.Decided, CollectionHelper.storageDecision(prefs))
    }

    @Test
    fun `storage decision is undecided when the collection path is unset`() {
        prefs.edit { remove(CollectionHelper.PREF_COLLECTION_PATH) }
        assertEquals(StorageDecision.Undecided, CollectionHelper.storageDecision(prefs))
    }

    @Test
    fun `storage decision is decided when a directory override is active`() {
        prefs.edit { remove(CollectionHelper.PREF_COLLECTION_PATH) }
        CollectionHelper.ankiDroidDirectoryOverride = File("/an/override")
        assertEquals(StorageDecision.Decided, CollectionHelper.storageDecision(prefs))
    }

    @Test
    fun `opening the collection throws when storage is undecided`() {
        CollectionHelper.storageDecisionTestOverride = StorageDecision.Undecided
        assertFailsWith<StorageNotConfiguredException> { CollectionManager.getColUnsafe() }
    }

    /** No collection access should be attempted: a crash report would otherwise be generated */
    @Test
    fun `startup failure is StorageUndecided when storage is undecided`() {
        CollectionHelper.storageDecisionTestOverride = StorageDecision.Undecided
        val failure = InitialActivity.getStartupFailureType(prefs) { true }
        assertEquals(InitialActivity.StartupFailure.StorageUndecided, failure)
    }

    /**
     * A storage failure at startup ([SystemStorageException]: OS bug/SD card issue) must not
     * masquerade as the expected 'storage not configured' state when opening the collection.
     */
    @Test
    fun `opening the collection reports a recorded startup storage failure`() {
        val failure = SystemStorageException.build("simulated getExternalFilesDir failure")
        CollectionHelper.storageDecisionTestOverride = StorageDecision.Undecided
        CollectionHelper.systemStorageFailure = failure

        val thrown = assertFailsWith<SystemStorageException> { CollectionManager.getColUnsafe() }
        assertSame(failure, thrown)
    }
}
