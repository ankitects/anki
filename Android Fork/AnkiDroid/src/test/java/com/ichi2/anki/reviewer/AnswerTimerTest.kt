// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.reviewer

import android.os.SystemClock
import android.view.View
import android.widget.Chronometer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.EmptyApplicationCategory
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.libanki.Collection
import com.ichi2.testutils.EmptyApplication
import com.ichi2.testutils.JvmTest
import com.ichi2.testutils.rules.MockitoCollectionRule
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.robolectric.annotation.Config
import timber.log.Timber

// This is difficult to test as Chronometer.mStarted isn't visible
@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class AnswerTimerTest : JvmTest() {
    private val chronometer = spy(Chronometer(ApplicationProvider.getApplicationContext()))

    @get:Rule
    val mockColRule = MockitoCollectionRule()
    override val col: Collection
        get() = mockColRule.col

    @Test
    fun disabledTimer() {
        val timer = getTimer()

        val card: Card =
            mock {
                on { shouldShowTimer(any()) } doReturn false
            }

        timer.setupForCard(col, card)

        assertThat("timer should not be enabled", timer.showTimer, equalTo(false))

        verify(chronometer).stop()
        verify(chronometer).visibility
        verify(chronometer).visibility = View.INVISIBLE
        verify(chronometer, never()).start()
        // verifyNoMoreInteractions(chronometer) // unable to use: android.view.View.$$robo$$android_view_View$setFlags
    }

    @Test
    fun enabledTimer() {
        val timer = getTimer()

        val card: Card =
            mock {
                on { shouldShowTimer(any()) } doReturn true
                on { timeLimit(any()) } doReturn 12
            }

        Mockito.mockStatic(SystemClock::class.java).use { mocked ->
            mocked.`when`<Long> { SystemClock.elapsedRealtime() }.doReturn(13)

            timer.setupForCard(col, card)
        }

        assertThat("timer should be enabled", timer.showTimer, equalTo(true))
        assertThat("Time limit should be 12 minutes", timer.limit, equalTo(12))

        verify(chronometer).start()
        verify(chronometer, atLeast(1)).visibility // we call twice due to the else branch
        // already visible: verify(chronometer).visibility = View.VISIBLE
        verify(chronometer, never()).stop()
        verify(chronometer).base = 13
        // verifyNoMoreInteractions(chronometer) // unable to use: android.view.View.$$robo$$android_view_View$setFlags
    }

    @Test
    fun toggle() {
        val timer = getTimer()

        val timerCard: Card =
            mock {
                on { shouldShowTimer(any()) } doReturn true
            }

        val nonTimerCard: Card =
            mock {
                on { shouldShowTimer(any()) } doReturn false
            }

        timer.setupForCard(col, timerCard)
        assertThat("timer should be enabled", timer.showTimer, equalTo(true))
        assertThat("chronometer should be visible", chronometer.visibility, equalTo(View.VISIBLE))

        timer.setupForCard(col, nonTimerCard)

        assertThat("timer should not be enabled", timer.showTimer, equalTo(false))
        assertThat("chronometer should not be visible", chronometer.visibility, equalTo(View.INVISIBLE))

        timer.setupForCard(col, timerCard)
        assertThat("timer should be enabled", timer.showTimer, equalTo(true))
        assertThat("chronometer should be visible", chronometer.visibility, equalTo(View.VISIBLE))
    }

    @Test
    fun testNoCrashOnEarlyPauseResume() =
        runTest {
            val timer = getTimer()
            // before we call setupForCard
            timer.pause()
            timer.resume()
        }

    @Test
    fun pauseResumeIfEnabled() =
        runTest {
            Timber.v("aaa")
            val timer = getTimer()

            val timerCard: Card =
                mock {
                    on { shouldShowTimer(col) } doReturn true
                    on { timeLimit(col) } doReturn 1000
                }

            timer.setupForCard(col, timerCard)

            reset(chronometer)
            timer.pause()
            verify(chronometer).stop()
            timer.resume()
            verify(chronometer).start()
        }

    @Test
    fun pauseResumeDoesNotCallStartIfTimeElapsed() =
        runTest {
            val timer = getTimer()

            val timerCard: Card =
                mock {
                    on { shouldShowTimer(any()) } doReturn true
                    on { timeLimit(any()) } doReturn 1000
                    on { timeTaken(any()) } doReturn 1001
                }

            timer.setupForCard(col, timerCard)

            reset(chronometer)
            timer.pause()
            verify(chronometer).stop()
            timer.resume()
            verify(chronometer, never()).start()
        }

    @Test
    fun cardTimerIsRestartedEvenIfDisabled() =
        runTest {
            // The class is responsible for the pause/resume handling of the card, not just the UI element
            // This may be a candidate for later refactoring

            val timer = getTimer()

            val nonTimerCard: Card =
                mock {
                    on { shouldShowTimer(any()) } doReturn false
                }

            timer.setupForCard(col, nonTimerCard)

            timer.pause()
            verify(nonTimerCard).stopTimer()
            verify(nonTimerCard, never()).resumeTimer()
            timer.resume()
            verify(nonTimerCard).resumeTimer()

            verify(chronometer, never()).start()
        }

    private fun getTimer(): AnswerTimer = AnswerTimer(chronometer)
}
