// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.worker

import android.app.PendingIntent
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.NetworkType
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import anki.sync.syncAuth
import com.ichi2.anki.NOTIFICATION_MIN_DELAY
import com.ichi2.anki.sync.SyncAuth
import com.ichi2.testutils.EmptyApplication
import com.ichi2.utils.TruncatedString
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.nullValue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
class SyncMediaWorkerTest {
    private lateinit var worker: SyncMediaWorker

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        worker = TestListenableWorkerBuilder<SyncMediaWorker>(context).build()
    }

    @Test
    @Suppress("SimplifyBooleanWithConstants")
    fun `notification update delay is not lower than min delay`() {
        assert(SyncMediaWorker.NOTIFICATION_UPDATE_RATE >= NOTIFICATION_MIN_DELAY)
    }

    // Issue 18937: an unset protobuf string must not become an explicitly empty URL in work data.
    @Test
    fun `default AnkiWeb endpoint remains absent in work data`() {
        val auth = SyncAuth(syncAuth { hkey = "test" })

        val input = SyncMediaWorker.getWorkRequest(auth, NetworkType.CONNECTED).workSpec.input
        val restoredAuth = input.toSyncAuth()!!

        assertThat(restoredAuth.endpoint, nullValue())
        assertThat(restoredAuth.hkey, equalTo("test"))
    }

    // https://github.com/ankidroid/Anki-Android/issues/20826
    @Test
    fun `copy to clipboard intent is immutable`() {
        val pendingIntent = worker.getCopyToClipboardIntent(TruncatedString.from("error text"))

        assertThat(
            "an intent attached to a notification must not be modifiable by other apps",
            shadowOf(pendingIntent).flags and PendingIntent.FLAG_IMMUTABLE,
            not(equalTo(0)),
        )
    }
}
