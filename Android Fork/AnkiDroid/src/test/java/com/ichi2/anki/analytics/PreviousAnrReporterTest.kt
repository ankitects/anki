// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.analytics

import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.exception.AnrReportException
import com.ichi2.anki.multiprofile.ProfileContextWrapper
import com.ichi2.anki.multiprofile.ProfileId
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.spyk
import io.mockk.unmockkObject
import io.mockk.verify
import org.acra.ACRAConstants
import org.acra.ReportField
import org.acra.config.CoreConfigurationBuilder
import org.acra.config.LimiterConfigurationBuilder
import org.acra.config.LimiterData
import org.acra.config.LimitingReportAdministrator
import org.acra.data.CrashReportData
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [30])
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.R)
class PreviousAnrReporterTest {
    private val context = mockk<AnkiDroidApp>()
    private val manager = mockk<ActivityManager>()
    private val reports = mutableListOf<Throwable>()
    private val reportMetadata = mutableListOf<String?>()

    @Before
    fun setUp() {
        val application = getApplicationContext<Application>()
        every { context.baseContext } returns application
        every { context.packageName } returns application.packageName
        every { context.getSharedPreferences(any(), Context.MODE_PRIVATE) } answers {
            application.getSharedPreferences(firstArg(), Context.MODE_PRIVATE)
        }
        every { context.getSystemService(ActivityManager::class.java) } returns manager
        val packageManager = spyk(application.packageManager)
        val packageName = application.packageName
        every { context.packageManager } returns packageManager
        @Suppress("DEPRECATION")
        val info = packageManager.getPackageInfo(packageName, 0)
        info.lastUpdateTime = 0
        every { packageManager.getPackageInfo(packageName, 0) } returns info
        mockkObject(CrashReportService)
        every {
            CrashReportService.sendExceptionReport(any<Throwable>(), "PreviousAnrReporter", any(), true)
        } answers {
            reports.add(firstArg())
            reportMetadata.add(thirdArg())
        }
    }

    @After
    fun tearDown() {
        unmockkObject(CrashReportService)
    }

    @Test
    fun `latest main process ANR is reported on first launch and only once`() {
        val old = exit(150)
        val latest = exit(200)
        val crash = exit(250, reason = ApplicationExitInfo.REASON_CRASH)
        val sender = exit(280, process = "${context.packageName}:acra")
        every { manager.getHistoricalProcessExitReasons(null, 0, 0) } returns listOf(sender, old, crash, latest)

        with(context) { reportPreviousAnr() }
        with(context) { reportPreviousAnr() }

        assertEquals(1, reports.size)
        val exception = assertIs<AnrReportException>(reports.single())
        assertEquals("Previous ANR: process exited at 1970-01-01T00:00:00.200Z.", reportMetadata.single())
        assertTrue(exception.message!!.contains("Other report metadata describes this reporting launch"))
        assertContentEquals(MAIN_STACK_TRACE, exception.stackTrace)
        verify(exactly = 0) { old.traceInputStream }
        verify(exactly = 0) { crash.traceInputStream }
        verify(exactly = 0) { sender.traceInputStream }
    }

    @Test
    fun `ACRA limits repeated ANR stacks and decoding failures but accepts distinct stacks`() {
        every { manager.getHistoricalProcessExitReasons(null, 0, 0) } returnsMany
            listOf(
                listOf(exit(200)),
                listOf(exit(300)),
                listOf(exit(400, trace = TRACE.replace("blockMainThread", "anotherBlock"))),
                listOf(exit(500, trace = "Unknown trace format")),
                listOf(exit(600, trace = "Another unknown trace format")),
            )
        repeat(5) { with(context) { reportPreviousAnr() } }
        assertEquals(5, reports.size)

        val application = getApplicationContext<Application>()
        LimiterData().store(application)
        val config =
            CoreConfigurationBuilder()
                .withPluginConfigurations(
                    LimiterConfigurationBuilder().withStacktraceLimit(1).withExceptionClassLimit(1000).build(),
                ).build()
        val limiter = LimitingReportAdministrator()
        val reportedAt = SimpleDateFormat(ACRAConstants.DATE_TIME_FORMAT_STRING, Locale.ENGLISH).format(TimeManager.time.currentDate)
        val accepted =
            reports.map { exception ->
                val data =
                    CrashReportData().apply {
                        put(ReportField.STACK_TRACE, exception.stackTraceToString())
                        put(ReportField.USER_CRASH_DATE, reportedAt)
                    }
                limiter.shouldSendReport(application, config, data)
            }

        assertTrue(accepted[0])
        assertFalse(accepted[1], "An identical ANR stack must hit ACRA's duplicate limit despite a different exit timestamp")
        assertTrue(accepted[2], "A distinct ANR stack must still be reported")
        assertTrue(accepted[3], "A decoding failure must be reported")
        assertFalse(accepted[4], "Repeated decoding failures must hit ACRA's duplicate limit")
    }

    @Test
    fun `switching profiles does not report the same package exit again`() {
        val application = getApplicationContext<Application>()
        val latest = exit(200)
        every { manager.getHistoricalProcessExitReasons(null, 0, 0) } returns listOf(latest)

        for (id in listOf("p_anr_a", "p_anr_b")) {
            val profile = ProfileContextWrapper.create(application, ProfileId(id), File(application.filesDir, id))
            every { context.getSharedPreferences(any(), Context.MODE_PRIVATE) } answers {
                profile.getSharedPreferences(firstArg(), Context.MODE_PRIVATE)
            }
            with(context) { reportPreviousAnr() }
        }

        assertEquals(1, reports.size)
        verify(exactly = 1) { latest.traceInputStream }
    }

    @Test
    fun `reports from before an app update are skipped`() {
        val packageManager = context.packageManager
        val packageName = context.packageName

        @Suppress("DEPRECATION")
        val info = packageManager.getPackageInfo(packageName, 0)
        info.lastUpdateTime = 250
        every { packageManager.getPackageInfo(packageName, 0) } returns info
        every { manager.getHistoricalProcessExitReasons(null, 0, 0) } returns listOf(exit(200))
        with(context) { reportPreviousAnr() }
        assertTrue(reports.isEmpty())
    }

    @Test
    fun `undecodable traces produce a compact diagnostic once without the dump or current stack`() {
        val invalidTraces =
            listOf(
                "",
                "Future ANR format with private diagnostic data",
                "\"main\" prio=5 tid=1 Waiting\n  frame com.example.NewFormat::blockingCall",
                "x".repeat(256 * 1024 + 1),
            )
        for ((index, trace) in invalidTraces.withIndex()) {
            val undecodable = exit(200L + index * 100, trace = trace)
            every { manager.getHistoricalProcessExitReasons(null, 0, 0) } returns listOf(undecodable)
            with(context) { reportPreviousAnr() }
            with(context) { reportPreviousAnr() }
            verify(exactly = 1) { undecodable.traceInputStream }
        }

        assertEquals(invalidTraces.size, reports.size)
        for (report in reports) {
            val exception = assertIs<AnrReportException>(report)
            assertEquals(
                "Previous ANR: unable to decode main-thread trace.\nOther report metadata describes this reporting launch.",
                exception.message,
            )
            assertTrue(exception.stackTrace.isEmpty())
            assertNull(exception.cause)
            assertTrue(exception.stackTraceToString().length < 256)
        }
        assertContentEquals(
            listOf(200, 300, 400, 500).map { "Previous ANR: process exited at 1970-01-01T00:00:00.${it}Z." },
            reportMetadata,
        )
    }

    @Test
    fun `missing and unreadable traces do not produce reports`() {
        every { manager.getHistoricalProcessExitReasons(null, 0, 0) } returns listOf(exit(200, trace = null))
        with(context) { reportPreviousAnr() }
        val unreadable = exit(600)
        every { unreadable.traceInputStream } throws IOException("unavailable")
        every { manager.getHistoricalProcessExitReasons(null, 0, 0) } returns listOf(unreadable)
        assertFailsWith<IOException> { with(context) { reportPreviousAnr() } }
        with(context) { reportPreviousAnr() }
        verify(exactly = 1) { unreadable.traceInputStream }
        assertTrue(reports.isEmpty())
    }

    @Test
    fun `failed system query can be retried on a later launch`() {
        every { manager.getHistoricalProcessExitReasons(null, 0, 0) } throws SecurityException("unavailable")
        assertFailsWith<SecurityException> { with(context) { reportPreviousAnr() } }
        every { manager.getHistoricalProcessExitReasons(null, 0, 0) } returns listOf(exit(200))
        with(context) { reportPreviousAnr() }
        assertEquals(1, reports.size)
    }

    @Test
    fun `an exit exposed after an empty scan is still reported`() {
        every { manager.getHistoricalProcessExitReasons(null, 0, 0) } returns emptyList()
        with(context) { reportPreviousAnr() }
        every { manager.getHistoricalProcessExitReasons(null, 0, 0) } returns listOf(exit(200))
        with(context) { reportPreviousAnr() }
        assertEquals(1, reports.size)
    }

    private fun exit(
        timestamp: Long,
        reason: Int = ApplicationExitInfo.REASON_ANR,
        process: String = context.packageName,
        trace: String? = TRACE,
    ): ApplicationExitInfo =
        mockk {
            every { this@mockk.timestamp } returns timestamp
            every { this@mockk.reason } returns reason
            every { processName } returns process
            every { traceInputStream } answers { trace?.byteInputStream() }
        }
}

private val MAIN_STACK_TRACE =
    arrayOf(
        StackTraceElement("java.util.concurrent.CountDownLatch", "await", "CountDownLatch.java", 230),
        StackTraceElement("com.example.AnrActivity", "blockMainThread", "AnrActivity.kt", 58),
    )

private val TRACE =
    """
    Subject: Input dispatching timed out

    "main" prio=5 tid=1 Waiting
      at java.util.concurrent.CountDownLatch.await(CountDownLatch.java:230)
      at com.example.AnrActivity.blockMainThread(AnrActivity.kt:58)

    "worker" prio=5 tid=2 Runnable
      at com.example.Worker.run(Worker.kt:10)
    """.trimIndent()
