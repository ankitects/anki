// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.deckpicker

import anki.sync.SyncAuth
import anki.sync.SyncStatusResponse
import anki.sync.syncStatusResponse
import com.ichi2.anki.deckpicker.DeckPickerViewModel.AutomaticSyncAction
import com.ichi2.anki.setLastSyncTimeToNow
import com.ichi2.anki.settings.Prefs
import com.ichi2.testutils.JvmPreferencesRule
import com.ichi2.testutils.JvmTest
import com.ichi2.utils.NetworkUtils
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import net.ankiweb.rsdroid.Backend
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class AutomaticSyncActionTest : JvmTest() {
    @get:Rule(order = 0)
    val preferences = JvmPreferencesRule()

    private lateinit var backend: Backend
    private val viewModel by lazy { DeckPickerViewModel() }

    @Before
    fun enableAutomaticSync() {
        Prefs.isAutoSyncEnabled = true
        Prefs.hkey = "test"
        Prefs.lastSyncTime = 0
        Prefs.allowSyncOnMeteredConnections = true
        backend = col.backend
        mockkObject(backend, NetworkUtils)
        every { NetworkUtils.isOnline } returns true
        every { backend.syncStatus(any<SyncAuth>()) } returns
            syncStatusResponse { required = SyncStatusResponse.Required.NORMAL_SYNC }
    }

    @After
    fun resetMocks() {
        unmockkObject(backend, NetworkUtils)
    }

    @Test
    fun `disabled automatic sync does not contact the server`() =
        runTest {
            Prefs.isAutoSyncEnabled = false

            assertEquals(AutomaticSyncAction.NONE, viewModel.automaticSyncAction())
            verify(exactly = 0) { backend.syncStatus(any<SyncAuth>()) }
            assertEquals(0L, Prefs.lastSyncTime)
        }

    @Test
    fun `recent sync skips the startup check but permits sync on exit`() =
        runTest {
            setLastSyncTimeToNow()
            val previousSyncTime = Prefs.lastSyncTime

            assertEquals(AutomaticSyncAction.NONE, viewModel.automaticSyncAction())
            verify(exactly = 0) { backend.syncStatus(any<SyncAuth>()) }
            assertEquals(AutomaticSyncAction.COLLECTION, viewModel.automaticSyncAction(runInBackground = true))
            assertEquals(previousSyncTime, Prefs.lastSyncTime)
        }

    @Test
    fun `no collection changes permits media without recording a sync attempt`() =
        runTest {
            every { backend.syncStatus(any<SyncAuth>()) } returns SyncStatusResponse.getDefaultInstance()

            assertEquals(AutomaticSyncAction.MEDIA_ONLY, viewModel.automaticSyncAction())
            assertEquals(0L, Prefs.lastSyncTime)
        }

    @Test
    fun `normal and full sync status request collection sync`() =
        runTest {
            for (required in listOf(SyncStatusResponse.Required.NORMAL_SYNC, SyncStatusResponse.Required.FULL_SYNC)) {
                every { backend.syncStatus(any<SyncAuth>()) } returns syncStatusResponse { this.required = required }

                assertEquals(AutomaticSyncAction.COLLECTION, viewModel.automaticSyncAction())
                assertEquals(0L, Prefs.lastSyncTime)
            }
        }
}
