// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.sync

import anki.sync.FullUploadOrDownloadRequest
import anki.sync.syncAuth
import com.ichi2.anki.libanki.testutils.InMemoryAnkiTest
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendFactory
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FullSyncTest : InMemoryAnkiTest() {
    private lateinit var request: FullUploadOrDownloadRequest
    private val auth =
        SyncAuth(
            syncAuth {
                hkey = "test"
                endpoint = "https://sync.example.com/"
                ioTimeoutSecs = 123
            },
        )

    override fun setUp() {
        BackendFactory.setOverride {
            object : Backend() {
                override fun fullUploadOrDownload(input: FullUploadOrDownloadRequest) {
                    request = input
                }
            }
        }
        super.setUp()
    }

    override fun tearDown() {
        try {
            super.tearDown()
        } finally {
            BackendFactory.setOverride(null)
        }
    }

    @Test
    fun `skipping media omits the backend startup trigger in both directions`() {
        for (upload in listOf(false, true)) {
            col.fullUploadOrDownload(auth, upload = upload, mediaSync = MediaSync.Skip)

            assertFalse(request.hasServerUsn())
            assertEquals(upload, request.upload)
            assertEquals(auth.toProto(), request.auth)
        }
    }

    @Test
    fun `starting media preserves the server USN including zero in both directions`() {
        for (upload in listOf(false, true)) {
            for (serverUsn in listOf(0, 42)) {
                col.fullUploadOrDownload(auth, upload = upload, mediaSync = MediaSync.Start(serverUsn))

                assertTrue(request.hasServerUsn())
                assertEquals(serverUsn, request.serverUsn)
                assertEquals(upload, request.upload)
                assertEquals(auth.toProto(), request.auth)
            }
        }
    }
}
