// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.worker

import android.content.Context
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import anki.sync.syncAuth
import com.google.common.util.concurrent.ListenableFuture
import com.ichi2.anki.sync.SyncAuth
import com.ichi2.testutils.EmptyApplication
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
class SyncMediaSchedulingTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val auth = SyncAuth(syncAuth { hkey = "test" })
    private lateinit var manager: WorkManager
    private val workers = mutableListOf<HeldMediaWorker>()
    private val workTasks = ArrayDeque<Runnable>()
    private var deferWorkTasks = false

    @Before
    fun setUp() {
        val configuration =
            Configuration
                .Builder()
                .setExecutor(SynchronousExecutor())
                .setTaskExecutor { task ->
                    if (deferWorkTasks) workTasks.addLast(task) else task.run()
                }.setWorkerFactory(
                    object : WorkerFactory() {
                        override fun createWorker(
                            appContext: Context,
                            workerClassName: String,
                            workerParameters: WorkerParameters,
                        ): ListenableWorker? =
                            if (workerClassName == SyncMediaWorker::class.java.name) {
                                HeldMediaWorker(appContext, workerParameters).also(workers::add)
                            } else {
                                null
                            }
                    },
                ).build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, configuration)
        manager = WorkManager.getInstance(context)
    }

    @After
    fun tearDown() {
        deferWorkTasks = false
        drainWorkTasks()
        manager.cancelAllWork().result.get()
        WorkManagerTestInitHelper.closeWorkDatabase()
    }

    @Test
    fun `another automatic request preserves approval for waiting media`() =
        runBlocking {
            SyncMediaWorker.start(context, auth, NetworkType.CONNECTED)
            val approved = unfinishedMediaWork()

            SyncMediaWorker.start(context, auth, NetworkType.UNMETERED)

            val remaining = unfinishedMediaWork()
            assertThat(remaining.id, equalTo(approved.id))
            assertThat(remaining.constraints.requiredNetworkType, equalTo(NetworkType.CONNECTED))
        }

    @Test
    fun `metered approval does not cancel a running media sync`() =
        runBlocking {
            SyncMediaWorker.start(context, auth, NetworkType.UNMETERED)
            val original = unfinishedMediaWork()
            WorkManagerTestInitHelper.getTestDriver(context)!!.setAllConstraintsMet(original.id)
            assertThat(unfinishedMediaWork().state, equalTo(WorkInfo.State.RUNNING))
            val worker = workers.single()

            SyncMediaWorker.start(context, auth, NetworkType.CONNECTED)

            val remaining = unfinishedMediaWork()
            assertThat(remaining.id, equalTo(original.id))
            assertThat(remaining.state, equalTo(WorkInfo.State.RUNNING))
            assertThat(worker.isStopped, equalTo(false))
            assertThat(workers.size, equalTo(1))

            worker.completion.set(ListenableWorker.Result.success())
            assertThat(manager.getWorkInfoById(original.id).get()!!.state, equalTo(WorkInfo.State.SUCCEEDED))
        }

    @Test
    fun `approval does not transfer to an automatic request after approved work completes`() {
        deferWorkTasks = true
        val approvedScope = TestScope()
        val automaticScope = TestScope()
        try {
            val approvedCall =
                approvedScope.launch {
                    SyncMediaWorker.start(context, auth, NetworkType.CONNECTED)
                }
            approvedScope.runCurrent()
            drainWorkTasks()
            // WorkManager enqueues the approved request while its caller waits to resume.
            val original = unfinishedMediaWork()
            assertThat(original.constraints.requiredNetworkType, equalTo(NetworkType.CONNECTED))

            WorkManagerTestInitHelper.getTestDriver(context)!!.setAllConstraintsMet(original.id)
            drainWorkTasks()
            assertThat(unfinishedMediaWork().state, equalTo(WorkInfo.State.RUNNING))
            workers.single().completion.set(ListenableWorker.Result.success())
            drainWorkTasks()
            val finished = manager.getWorkInfoById(original.id)
            drainWorkTasks()
            assertThat(finished.get()!!.state, equalTo(WorkInfo.State.SUCCEEDED))

            // A separate automatic request arrives before the approved caller resumes.
            val automaticCall =
                automaticScope.launch {
                    SyncMediaWorker.start(context, auth, NetworkType.UNMETERED)
                }
            automaticScope.runCurrent()
            drainWorkTasks()
            automaticScope.runCurrent()

            // Let the approved caller finish its query/update, then finish the automatic request.
            approvedScope.runCurrent()
            drainWorkTasks()
            approvedScope.runCurrent()
            drainWorkTasks()
            approvedScope.runCurrent()
            automaticScope.runCurrent()
            drainWorkTasks()
            automaticScope.runCurrent()
            assertThat(approvedCall.isCompleted, equalTo(true))
            assertThat(approvedCall.isCancelled, equalTo(false))
            assertThat(automaticCall.isCompleted, equalTo(true))
            assertThat(automaticCall.isCancelled, equalTo(false))

            val remaining = unfinishedMediaWork()
            assertThat(remaining.id, not(equalTo(original.id)))
            assertThat(remaining.constraints.requiredNetworkType, equalTo(NetworkType.UNMETERED))
        } finally {
            approvedScope.cancel()
            automaticScope.cancel()
            approvedScope.runCurrent()
            automaticScope.runCurrent()
        }
    }

    private fun drainWorkTasks() {
        while (workTasks.isNotEmpty()) workTasks.removeFirst().run()
    }

    private fun unfinishedMediaWork(): WorkInfo {
        val future = manager.getWorkInfosForUniqueWork(UniqueWorkNames.SYNC_MEDIA)
        drainWorkTasks()
        return future.get().single { !it.state.isFinished }
    }

    private class HeldMediaWorker(
        context: Context,
        parameters: WorkerParameters,
    ) : ListenableWorker(context, parameters) {
        lateinit var completion: CallbackToFutureAdapter.Completer<Result>

        override fun startWork(): ListenableFuture<Result> =
            CallbackToFutureAdapter.getFuture { completer ->
                completion = completer
                "media sync held by test"
            }
    }
}
