// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.app.Activity
import android.os.Looper
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.utils.ImportResolution
import com.ichi2.utils.ImportResult
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class ImportUiThreadTest : RobolectricTest() {
    @Test
    fun `prepared text import from a worker launches on the main thread`() {
        val controller = Robolectric.buildActivity(Activity::class.java).create()
        saveControllerForCleanup(controller)
        val activity = spy(controller.get())
        val launchThreads = mutableListOf<Thread>()
        doAnswer {
            launchThreads.add(Thread.currentThread())
            null
        }.whenever(activity).startActivities(any(), anyOrNull())
        val result =
            ImportResult.Success(
                ImportResolution.Text(ImportResolution.Source("content://import/notes.csv".toUri(), "notes.csv")),
                "/cache/notes.csv",
            )
        val executor = Executors.newSingleThreadExecutor()
        try {
            executor.submit { activity.openImport(result) }.get(10, TimeUnit.SECONDS)
            assertTrue(launchThreads.isEmpty(), "A background caller must queue the UI work")

            shadowOf(Looper.getMainLooper()).idle()

            assertEquals(listOf(Looper.getMainLooper().thread), launchThreads)
        } finally {
            executor.shutdownNow()
        }
    }
}
