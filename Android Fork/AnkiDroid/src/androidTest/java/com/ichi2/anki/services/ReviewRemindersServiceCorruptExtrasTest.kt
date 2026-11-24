// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.services

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Parcel
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.reviewreminders.ReviewReminderId
import com.ichi2.anki.reviewreminders.ReviewReminderScope
import com.ichi2.anki.services.NotificationService.NotificationServiceAction
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.tests.InstrumentedTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.minutes

/**
 * #23084: Alarms and notification actions survive app updates, so their parceled extras may name a class
 * which a newer version of the app has given to an unrelated class (ex. R8 renamed both).
 */
@RunWith(AndroidJUnit4::class)
class ReviewRemindersServiceCorruptExtrasTest : InstrumentedTest() {
    private var originalNewReviewRemindersEnabled = false

    @Before
    fun setUp() {
        originalNewReviewRemindersEnabled = Prefs.newReviewRemindersEnabled
        Prefs.newReviewRemindersEnabled = true
    }

    @After
    fun tearDown() {
        Prefs.newReviewRemindersEnabled = originalNewReviewRemindersEnabled
    }

    @Test
    fun notificationServiceSurvivesAlarmWithCorruptExtras() {
        val intent =
            NotificationService.getIntent(
                testContext,
                ReviewReminderId(1),
                ReviewReminderScope.Global,
                NotificationServiceAction.ScheduleRecurringNotifications,
            )

        deliver(intent.withReminderIdParceledAsNonParcelable())
    }

    @Test
    fun snoozeServiceSurvivesSnoozeWithCorruptExtras() {
        val intent = SnoozeService.getIntent(testContext, ReviewReminderId(1), ReviewReminderScope.Global, 5.minutes)

        deliver(intent.withReminderIdParceledAsNonParcelable())
    }

    /** Broadcasts [intent] and waits for its receiver to finish handling it. */
    private fun deliver(intent: Intent) {
        val handled = CountDownLatch(1)
        // An ordered broadcast is used for its result receiver, which runs once the receiver under test has finished
        testContext.sendOrderedBroadcast(
            intent,
            null, // receiverPermission
            object : BroadcastReceiver() { // resultReceiver
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) = handled.countDown()
            },
            null, // scheduler: run the result receiver on the main thread
            Activity.RESULT_OK, // initialCode
            null, // initialData
            null, // initialExtras
        )
        assertThat("broadcast handled", handled.await(10, TimeUnit.SECONDS), equalTo(true))
    }

    /**
     * A copy of this intent whose [ReviewReminderId] extra is parceled under the name of [NotParcelableReminderId],
     * as happens when an update gives the old name of [ReviewReminderId] to a class which isn't Parcelable.
     */
    private fun Intent.withReminderIdParceledAsNonParcelable(): Intent {
        val bytes =
            Parcel.obtain().run {
                writeToParcel(this, 0)
                marshall().also { recycle() }
            }

        // Kotlin Strings use UTF-16 chars but Parcels use UTF-16LE bytes (little-endian)
        val oldName = ReviewReminderId::class.java.name.toByteArray(Charsets.UTF_16LE)
        val newName = NotParcelableReminderId::class.java.name.toByteArray(Charsets.UTF_16LE)
        // The rest of the parcel only stays readable if the length is unchanged
        check(oldName.size == newName.size) { "class names must have the same length" }
        val start = bytes.asList().windowed(oldName.size).indexOf(oldName.asList())
        check(start != -1) { "class name not found in parcel" }
        newName.copyInto(bytes, destinationOffset = start)

        // The extras stay parceled until the receiver reads them, so the corruption is only detected there
        return Parcel.obtain().run {
            unmarshall(bytes, 0, bytes.size)
            setDataPosition(0)
            Intent.CREATOR.createFromParcel(this).also { recycle() }
        }
    }
}

/** A class which isn't Parcelable, with a name of the same length as [ReviewReminderId]'s. */
private class NotParcelableReminderId
