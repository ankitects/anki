// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki

import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import androidx.core.content.edit
import anki.scheduler.CardAnswer.Rating
import com.ichi2.anki.AbstractFlashcardViewer.Companion.RESULT_NO_MORE_CARDS
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.NavigationDrawerActivity.Companion.EXTRA_STARTED_WITH_SHORTCUT
import com.ichi2.anki.libanki.Consts.DEFAULT_DECK_ID
import com.ichi2.anki.pages.CongratsPage
import com.ichi2.testutils.ext.snackbarText
import org.json.JSONArray
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowToast
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(ParameterizedRobolectricTestRunner::class)
class ReviewCompletionTest : RobolectricTest() {
    @ParameterizedRobolectricTestRunner.Parameter(0)
    @JvmField
    var newCongratsScreen = false

    @ParameterizedRobolectricTestRunner.Parameter(1)
    @JvmField
    var fromShortcut = false

    override fun getCollectionStorageMode() = CollectionStorageMode.IN_MEMORY_WITH_MEDIA

    @Before
    fun configureCompletionScreen() {
        getPreferences().edit { putBoolean("new_congrats_screen", newCongratsScreen) }
        ShadowToast.reset()
    }

    @Test
    fun `finished parent with cards only in its child still shows completion`() {
        val child = addDeck("Parent::Child")
        col.setDeck(listOf(addBasicNote().firstCard().id), child)
        col.decks.select(col.decks.id("Parent"))

        completeReview(expectEmpty = false) {
            col.sched.answerCard(assertNotNull(col.sched.card), Rating.EASY)
            assertEquals(0, col.sched.totalCount())
            assertEquals(1, col.decks.cardCount(child, includeSubdecks = true))
        }
    }

    @Test
    fun `filtered deck emptied by the final review still shows completion`() {
        addBasicNote()
        val filtered = addDynamicDeck("Filtered", "")

        completeReview(expectEmpty = false) {
            col.sched.answerCard(assertNotNull(col.sched.card), Rating.EASY)
            assertEquals(0, col.decks.cardCount(filtered, includeSubdecks = true))
        }
    }

    @Test
    fun `learning later still shows completion with the next available time`() {
        col.config.set("collapseTime", 0)
        val config = col.decks.configDictForDeckId(DEFAULT_DECK_ID)
        config.new.delays = JSONArray(listOf(1))
        col.decks.save(config)
        addBasicNote()

        completeReview(
            expectEmpty = false,
            expectedMessage = {
                it.getString(
                    CommonString.studyoptions_congrats_next_due_in,
                    TR.schedulingNextLearnDue("minutes", 1),
                )
            },
        ) {
            col.sched.answerCard(assertNotNull(col.sched.card), Rating.AGAIN)
            assertNull(col.sched.card)
        }
    }

    private fun completeReview(
        expectEmpty: Boolean,
        expectedMessage: (Context) -> String = {
            it.getString(if (expectEmpty) CommonString.empty_deck else CommonString.studyoptions_congrats_finished)
        },
        finishCards: () -> Unit,
    ) {
        if (fromShortcut) {
            runTest {
                val reviewer =
                    startActivityNormallyOpenCollectionWithIntent(
                        Reviewer::class.java,
                        Intent().putExtra(EXTRA_STARTED_WITH_SHORTCUT, true),
                    )
                advanceRobolectricLooper()
                assertNotNull(reviewer.currentCardId)
                finishCards()
                val selected = col.decks.selected()
                val undoStatus = col.undoStatus()

                reviewer.updateCardAndRedraw()
                advanceRobolectricLooper()

                assertTrue(reviewer.isFinishing)
                assertEquals(RESULT_NO_MORE_CARDS, shadowOf(reviewer).resultCode)
                if (newCongratsScreen && !expectEmpty) {
                    assertCongratsIntent(shadowOf(reviewer).nextStartedActivity)
                } else {
                    assertNull(shadowOf(reviewer).nextStartedActivity)
                    assertEquals(expectedMessage(reviewer), ShadowToast.getTextOfLatestToast())
                }
                assertEquals(selected, col.decks.selected())
                assertEquals(undoStatus, col.undoStatus())
            }
        } else {
            deckPicker {
                advanceRobolectricLooper()
                val shadow = shadowOf(this)
                shadow.clearNextStartedActivities()
                assertTrue(onKeyUp(KeyEvent.KEYCODE_S, KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_S)))
                advanceRobolectricLooperUntil { shadow.peekNextStartedActivity() != null }
                val review = assertNotNull(shadow.nextStartedActivityForResult)
                assertEquals(Reviewer::class.java.name, review.intent.component?.className)
                shadow.clearNextStartedActivities()
                finishCards()
                val selected = col.decks.selected()
                val undoStatus = col.undoStatus()

                activityResultRegistry.dispatchResult(review.requestCode, RESULT_NO_MORE_CARDS, null)

                if (newCongratsScreen && !expectEmpty) {
                    advanceRobolectricLooperUntil { shadow.peekNextStartedActivity() != null }
                    assertCongratsIntent(shadow.nextStartedActivity)
                } else {
                    advanceRobolectricLooperUntil { snackbarText == expectedMessage(this) }
                    assertNull(shadow.nextStartedActivity)
                }
                assertEquals(selected, col.decks.selected())
                assertEquals(undoStatus, col.undoStatus())
            }
        }
    }

    private fun assertCongratsIntent(intent: Intent?) {
        assertEquals(
            CongratsPage::class.java.name,
            assertNotNull(intent).getStringExtra(SingleFragmentActivity.EXTRA_FRAGMENT_NAME),
        )
    }

    companion object {
        @ParameterizedRobolectricTestRunner.Parameters(name = "new screen = {0}, shortcut = {1}")
        @JvmStatic
        fun configurations() =
            listOf(false, true).flatMap { newScreen ->
                listOf(false, true).map { shortcut -> arrayOf(newScreen, shortcut) }
            }
    }
}
