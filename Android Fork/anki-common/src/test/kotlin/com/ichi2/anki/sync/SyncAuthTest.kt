// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.sync

import anki.sync.syncAuth
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test

class SyncAuthTest {
    @Test
    fun `updating endpoint preserves credentials and leaves original unchanged`() {
        val proto =
            syncAuth {
                hkey = "test"
                endpoint = "https://original.example.com/"
                ioTimeoutSecs = 123
            }
        val auth = SyncAuth(proto)
        val newEndpoint = "https://redirect.example.com/"

        val updated = auth.withEndpoint(newEndpoint).toProto()

        assertThat(updated.endpoint, equalTo(newEndpoint))
        assertThat(updated.hkey, equalTo(proto.hkey))
        assertThat(updated.ioTimeoutSecs, equalTo(proto.ioTimeoutSecs))
        assertThat(auth.endpoint, equalTo(proto.endpoint))
    }

    @Test
    fun `default and custom endpoints map correctly without changing credentials`() {
        val customEndpoint = "https://sync.example.com/"
        for ((inputEndpoint, expectedEndpoint) in listOf(null to null, customEndpoint to customEndpoint)) {
            val proto =
                syncAuth {
                    hkey = "test"
                    inputEndpoint?.let { endpoint = it }
                    ioTimeoutSecs = 123
                }

            val auth = SyncAuth(proto)
            val backendAuth = auth.toProto()

            assertThat(auth.endpoint, equalTo(expectedEndpoint))
            assertThat(backendAuth.hasEndpoint(), equalTo(expectedEndpoint != null))
            assertThat(backendAuth.endpoint, equalTo(expectedEndpoint ?: ""))
            assertThat(backendAuth.hkey, equalTo(proto.hkey))
            assertThat(backendAuth.ioTimeoutSecs, equalTo(proto.ioTimeoutSecs))
        }
    }
}
