// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.reviewer

import android.graphics.Path
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.core.content.getSystemService
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.cardviewer.Gesture
import com.ichi2.anki.preferences.reviewer.ViewerAction
import com.ichi2.anki.preferences.reviewer.WhiteboardAction
import com.ichi2.anki.previewer.CardViewerActivity
import com.ichi2.anki.reviewer.MappableBinding.Companion.toPreferenceString
import com.ichi2.anki.reviewer.ReviewerBinding
import com.ichi2.anki.scheduling.SetDueDateDialog
import com.ichi2.anki.ui.windows.reviewer.whiteboard.WhiteboardFragment
import com.ichi2.anki.ui.windows.reviewer.whiteboard.WhiteboardRepository
import com.ichi2.anki.utils.ext.DIALOG_FRAGMENT_TAG
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.SensorBuilder
import org.robolectric.shadows.SensorEventBuilder
import org.robolectric.shadows.ShadowSensorManager
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration
import kotlin.reflect.jvm.jvmName
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Tests for [ReviewerFragment]'s shake detector */
@RunWith(AndroidJUnit4::class)
class ReviewerShakeTest : RobolectricTest() {
    private lateinit var sensors: ShadowSensorManager

    @Before
    fun enableShakeGesture() {
        ensureCollectionLoadIsSynchronous()
        editPreferences {
            putString(ViewerAction.RESCHEDULE_NOTE.preferenceKey, listOf(ReviewerBinding.fromGesture(Gesture.SHAKE)).toPreferenceString())
        }
        sensors =
            shadowOf(targetContext.getSystemService<SensorManager>()).apply {
                addSensor(SensorBuilder.newBuilder().setType(Sensor.TYPE_ACCELEROMETER).build())
            }
    }

    @After
    fun clearShakeGesture() {
        editPreferences {
            remove(ViewerAction.RESCHEDULE_NOTE.preferenceKey)
        }
    }

    @Test
    fun `shake opens set due date after repeated recreation`() =
        runTest {
            val cardId = addBasicNote().firstCard().id
            withReviewer { scenario ->
                repeat(3) {
                    sensors.shake()
                    scenario.withSetDueDate {
                        assertEquals(listOf(cardId), cardIds)
                    }

                    scenario.recreate()
                    scenario.withSetDueDate {
                        assertEquals(listOf(cardId), cardIds)
                        // The next shake must open a new dialog, not find the restored one.
                        dismissNow()
                    }
                    assertEquals(1, sensors.listeners.size)
                }
            }
            assertEquals(0, sensors.listeners.size)
        }

    @Test
    fun `shake detector stops and restarts with reviewer`() =
        runTest {
            addBasicNote()
            withReviewer { scenario ->
                assertEquals(1, sensors.listeners.size)
                scenario.moveToState(Lifecycle.State.CREATED)
                assertEquals(0, sensors.listeners.size)
                scenario.moveToState(Lifecycle.State.RESUMED)
                assertEquals(1, sensors.listeners.size)
                sensors.shake()
                scenario.withSetDueDate()
            }
            assertEquals(0, sensors.listeners.size)
        }

    @Test
    fun `shake bound only to the whiteboard clears it`() = checkShakeClearsWhiteboard(stylusOnly = false)

    @Test
    fun `shake clears the whiteboard in stylus mode`() = checkShakeClearsWhiteboard(stylusOnly = true)

    private fun checkShakeClearsWhiteboard(stylusOnly: Boolean) =
        runTest {
            editPreferences {
                remove(ViewerAction.RESCHEDULE_NOTE.preferenceKey)
                putString(WhiteboardAction.CLEAR.preferenceKey, listOf(ReviewerBinding.fromGesture(Gesture.SHAKE)).toPreferenceString())
            }
            StudyScreenRepository().isWhiteboardEnabled = true
            WhiteboardRepository(getPreferences()).stylusOnlyMode = stylusOnly
            addBasicNote()
            withReviewer { scenario ->
                scenario.onActivity { activity ->
                    val reviewer = activity.fragment as ReviewerFragment
                    val whiteboard =
                        assertIs<WhiteboardFragment>(reviewer.childFragmentManager.findFragmentByTag(WhiteboardFragment::class.jvmName))
                    whiteboard.binding.whiteboardView.onNewPath!!(Path().apply { lineTo(10f, 10f) })
                    assertEquals(1, whiteboard.viewModel.paths.value.size)

                    sensors.shake()

                    assertEquals(emptyList(), whiteboard.viewModel.paths.value)
                    assertEquals(stylusOnly, whiteboard.binding.whiteboardView.isStylusOnlyMode)
                }
            }
        }

    private fun withReviewer(block: (ActivityScenario<CardViewerActivity>) -> Unit) {
        ActivityScenario.launch<CardViewerActivity>(ReviewerFragment.getIntent(targetContext)).use { scenario ->
            advanceRobolectricLooper()
            block(scenario)
        }
    }

    private fun ActivityScenario<CardViewerActivity>.withSetDueDate(block: SetDueDateDialog.() -> Unit = {}) {
        advanceRobolectricLooper()
        onActivity { activity ->
            assertIs<SetDueDateDialog>(activity.supportFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG)).block()
        }
    }

    private fun ShadowSensorManager.shake() {
        // Clear the cooldown, then supply enough accelerating samples to trigger Seismic.
        ShadowSystemClock.advanceBy(Duration.ofSeconds(1))
        repeat(5) {
            val event =
                SensorEventBuilder
                    .newBuilder(getSensorList(Sensor.TYPE_ACCELEROMETER).single(), floatArrayOf(20f, 0f, 0f))
                    .setTimestamp(SystemClock.elapsedRealtimeNanos())
                    .build()
            sendSensorEventToListeners(event)
            ShadowSystemClock.advanceBy(Duration.ofMillis(100))
        }
    }
}
