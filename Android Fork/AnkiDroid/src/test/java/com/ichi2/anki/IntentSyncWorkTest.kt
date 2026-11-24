// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.ichi2.anki.CollectionManager.withLeaseForTest
import com.ichi2.anki.worker.SyncWorker
import com.ichi2.anki.worker.UniqueWorkNames
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.MutableSharedFlow
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.nullValue
import org.hamcrest.Matchers.sameInstance
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import java.util.UUID
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class IntentSyncWorkTest : RobolectricTest() {
    @Test
    fun `sync intent leaves pending background sync intact`() {
        val workId = enqueueSync()

        val activity = launchIntent("com.ichi2.anki.DO_SYNC")

        assertThat(workManager.getWorkInfoById(workId).get()!!.state, equalTo(WorkInfo.State.ENQUEUED))
        assertThat("a duplicate sync must not open the collection in DeckPicker", shadowOf(activity).nextStartedActivity, nullValue())
    }

    @Test
    fun `sync intent leaves foreground collection sync intact`() =
        runTest {
            for (operation in listOf(CollectionOperation.SYNC, CollectionOperation.FULL_UPLOAD, CollectionOperation.FULL_DOWNLOAD)) {
                withLeaseForTest(operation) {
                    val lease = CollectionManager.collectionLease

                    val activity = launchIntent("com.ichi2.anki.DO_SYNC")

                    assertThat(
                        "a duplicate $operation intent must not open DeckPicker",
                        shadowOf(activity).nextStartedActivity,
                        nullValue(),
                    )
                    assertThat("the existing $operation must retain its lease", CollectionManager.collectionLease, sameInstance(lease))
                }
            }
        }

    @Test
    fun `sync intent checks foreground sync after the work query completes`() =
        runTest {
            withPendingSyncQuery { results ->
                Robolectric.buildActivity(IntentHandler::class.java, Intent("com.ichi2.anki.DO_SYNC")).use { controller ->
                    controller.create().start().resume()
                    val activity = controller.get()
                    assertThat(results.subscriptionCount.value, equalTo(1))

                    withLeaseForTest(CollectionOperation.SYNC) {
                        results.emit(emptyList())

                        assertThat(activity.isFinishing, equalTo(true))
                        assertThat("a new foreground sync must prevent navigation", shadowOf(activity).nextStartedActivity, nullValue())
                    }
                }
            }
        }

    @Test
    fun `opening the app still cancels background collection sync`() {
        val workId = enqueueSync()

        launchIntent(Intent.ACTION_MAIN)

        assertThat(workManager.getWorkInfoById(workId).get()!!.state, equalTo(WorkInfo.State.CANCELLED))
    }

    @Test
    fun `sync intent opens DeckPicker when no sync is pending`() {
        val activity = launchIntent("com.ichi2.anki.DO_SYNC")
        val intent = shadowOf(activity).nextStartedActivity

        assertThat(intent.component!!.className, equalTo(DeckPicker::class.java.name))
        assertThat(intent.action, equalTo("com.ichi2.anki.DO_SYNC"))
    }

    @Test
    fun `finishing the handler before the work query completes prevents navigation`() =
        runTest {
            withPendingSyncQuery { results ->
                Robolectric.buildActivity(IntentHandler::class.java, Intent("com.ichi2.anki.DO_SYNC")).use { controller ->
                    controller.create().start().resume()
                    val activity = controller.get()
                    assertThat(results.subscriptionCount.value, equalTo(1))

                    activity.finish()
                    results.emit(emptyList())

                    assertThat("a finishing handler must not launch DeckPicker", shadowOf(activity).nextStartedActivity, nullValue())
                }
            }
        }

    @Test
    fun `destroying the handler cancels its pending work query`() =
        runTest {
            withPendingSyncQuery { results ->
                Robolectric.buildActivity(IntentHandler::class.java, Intent("com.ichi2.anki.DO_SYNC")).use { controller ->
                    controller.create().start().resume()
                    val activity = controller.get()
                    assertThat(results.subscriptionCount.value, equalTo(1))

                    activity.finish()
                    controller.pause().stop().destroy()
                    assertThat(results.subscriptionCount.value, equalTo(0))
                    results.emit(emptyList())

                    assertThat("a destroyed handler must not launch DeckPicker", shadowOf(activity).nextStartedActivity, nullValue())
                }
            }
        }

    private suspend fun withPendingSyncQuery(block: suspend (MutableSharedFlow<List<WorkInfo>>) -> Unit) {
        val results = MutableSharedFlow<List<WorkInfo>>()
        val manager = mockk<WorkManager>()
        mockkObject(WorkManager.Companion)
        try {
            every { WorkManager.getInstance(any()) } returns manager
            every { manager.getWorkInfosForUniqueWorkFlow(UniqueWorkNames.SYNC) } returns results
            block(results)
        } finally {
            unmockkObject(WorkManager.Companion)
        }
    }

    private val workManager get() = WorkManager.getInstance(targetContext)

    private fun enqueueSync(): UUID {
        // Keep the real worker queued without starting a network request.
        val work =
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setInitialDelay(1, TimeUnit.HOURS)
                .build()
        workManager.enqueueUniqueWork(UniqueWorkNames.SYNC, ExistingWorkPolicy.KEEP, work).result.get()
        assertThat(workManager.getWorkInfoById(work.id).get()!!.state, equalTo(WorkInfo.State.ENQUEUED))
        return work.id
    }

    private fun launchIntent(action: String): IntentHandler {
        val controller = Robolectric.buildActivity(IntentHandler::class.java, Intent(action))
        saveControllerForCleanup(controller)
        val activity = controller.create().get()
        advanceRobolectricLooperUntil { activity.isFinishing }
        return activity
    }
}
