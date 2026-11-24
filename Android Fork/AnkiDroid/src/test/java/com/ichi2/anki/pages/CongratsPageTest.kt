// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki.pages

import android.app.Activity
import android.content.DialogInterface
import android.os.Bundle
import android.os.Looper
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.BackendError
import anki.scheduler.CongratsInfoResponse
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.StudyOptionsActivity
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.common.crashreporting.CrashReporter
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.CustomStudyAction
import com.ichi2.anki.exception.CollectionLockedException
import com.ichi2.anki.ioDispatcher
import com.ichi2.anki.launchCatchingIO
import com.ichi2.anki.libanki.Consts
import com.ichi2.anki.observability.undoableOp
import com.ichi2.anki.throwOnShowError
import com.ichi2.testutils.AnkiFragmentScenario
import com.ichi2.testutils.EmptyAnkiActivity
import com.ichi2.testutils.launchFragmentInContainer
import com.ichi2.testutils.saveState
import com.ichi2.testutils.uninitializeField
import com.ichi2.testutils.withFragment
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbLockedException
import org.hamcrest.Matchers.allOf
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class CongratsPageTest : RobolectricTest() {
    @Test
    fun `congrats polling does not require an attached activity`() =
        runTest {
            // Issues 15658 and 15679: a surviving page polled after its fragment detached.
            // requireActivity() turned each poll into a 500 response and a JavaScript alert.
            val fragment = CongratsPage()
            assertNull(fragment.activity)

            val response = fragment.handlePostRequest(PostRequestUri("/_anki/congratsInfo"), byteArrayOf())

            assertFalse(CongratsInfoResponse.parseFrom(response).reviewRemaining)
        }

    @Test
    fun `congrats polling succeeds while the page is stopped`() =
        runTest {
            launchFragmentInContainer<CongratsPage>().use { scenario ->
                lateinit var fragment: CongratsPage
                scenario.onFragment { fragment = it }
                scenario.moveToState(Lifecycle.State.CREATED)
                assertEquals(Lifecycle.State.CREATED, fragment.lifecycle.currentState)

                val response = fragment.handlePostRequest(PostRequestUri("/_anki/congratsInfo"), byteArrayOf())

                assertFalse(CongratsInfoResponse.parseFrom(response).reviewRemaining)
            }
        }

    // https://github.com/ankidroid/Anki-Android/pull/21409#pullrequestreview-3568785419
    @Test
    fun `resuming after a background rebuild refills the deck - navigates to study options`() =
        runTest {
            addBasicNote("Front", "Back")
            val deckId = addDynamicDeck("Filtered", "")
            withCol { sched.emptyFilteredDeck(deckId) }

            launchFragmentInContainer<CongratsPage>().use { scenario ->
                scenario.moveToState(Lifecycle.State.CREATED)

                // rebuild happens while the screen isn't visible - nobody is collecting congratsRefreshState
                undoableOp { sched.rebuildFilteredDeck(deckId) }

                scenario.moveToState(Lifecycle.State.RESUMED)
                advanceUntilIdle()

                scenario.onFragment { fragment ->
                    val next = shadowOf(fragment.requireActivity()).nextStartedActivity
                    assertEquals(StudyOptionsActivity::class.java.name, next?.component?.className)
                }
            }
        }

    @Test
    fun `unbury completing during recreation opens study options once`() =
        runTest {
            val card = addBasicNote().firstCard()
            withCol { sched.buryCards(listOf(card.id), manual = true) }

            launchFragmentInContainer<CongratsPage>().use { scenario ->
                advanceUntilIdle()
                val originalModel = scenario.withFragment { ViewModelProvider(this)[CongratsViewModel::class.java] }
                ioDispatcher = StandardTestDispatcher(testScheduler)
                scenario.onFragment { page ->
                    page.parentFragmentManager.registerFragmentLifecycleCallbacks(
                        object : FragmentManager.FragmentLifecycleCallbacks() {
                            override fun onFragmentDestroyed(
                                fm: FragmentManager,
                                fragment: Fragment,
                            ) {
                                if (fragment === page) {
                                    // Let the in-flight operation finish between the old and new views.
                                    testScheduler.runCurrent()
                                }
                            }
                        },
                        false,
                    )
                    page.bridgeCommands.getValue("unbury").invoke()
                }

                scenario.recreate()
                advanceUntilIdle()

                scenario.onFragment { page ->
                    assertSame(originalModel, ViewModelProvider(page)[CongratsViewModel::class.java])
                    assertOpenedStudyOptionsAndFinished(page.requireActivity())
                }
            }
        }

    @Test
    fun `custom study input dialog survives activity recreation`() =
        runTest {
            updateDeckConfig(Consts.DEFAULT_DECK_ID) { new.perDay = 0 }
            addBasicNote()
            ioDispatcher = StandardTestDispatcher(testScheduler)

            launchFragmentInContainer<CongratsPage>().use { scenario ->
                scenario.onFragment { it.bridgeCommands.getValue("customStudy").invoke() }
                shadowOf(Looper.getMainLooper()).idle()
                advanceUntilIdle()
                onView(withText(TR.customStudyIncreaseTodaysNewCardLimit()))
                    .inRoot(isDialog())
                    .perform(click())
                advanceUntilIdle()

                scenario.recreate()

                onView(withId(R.id.details_edit_text_2))
                    .inRoot(isDialog())
                    .check(matches(isDisplayed()))
            }
        }

    @Test
    fun `custom study results open study options and finish the screen`() =
        runTest {
            // Keep the deck finished so the Congrats page stays open.
            updateDeckConfig(Consts.DEFAULT_DECK_ID) { new.perDay = 0 }
            addBasicNote()

            for (action in CustomStudyAction.entries) {
                // hosted by EmptyAnkiActivity, not SingleFragmentActivity: the page handles the result itself
                launchFragmentInContainer<CongratsPage>().use { scenario ->
                    val activity = scenario.withFragment { requireActivity() }
                    advanceUntilIdle()
                    assertNull(shadowOf(activity).nextStartedActivity, "$action: page opened study options early")

                    scenario.postCustomStudyResult(action)

                    assertOpenedStudyOptionsAndFinished(activity)
                }
            }
        }

    @Test
    fun `custom study result is handled after the page is recreated`() =
        runTest {
            // Keep the deck finished so the Congrats page stays open.
            updateDeckConfig(Consts.DEFAULT_DECK_ID) { new.perDay = 0 }
            addBasicNote()

            launchFragmentInContainer<CongratsPage>().use { scenario ->
                scenario.recreate()
                val activity = scenario.withFragment { requireActivity() }
                advanceUntilIdle()
                assertNull(shadowOf(activity).nextStartedActivity)

                scenario.postCustomStudyResult(CustomStudyAction.EXTEND_STUDY_LIMITS)

                assertOpenedStudyOptionsAndFinished(activity)
            }
        }

    /** Issue 22801: the activity owns the custom study operation, so its result can arrive while the page is stopped */
    @Test
    fun `custom study result received while stopped is handled when the page starts`() =
        runTest {
            // Keep the deck finished so the Congrats page stays open.
            updateDeckConfig(Consts.DEFAULT_DECK_ID) { new.perDay = 0 }
            addBasicNote()

            launchFragmentInContainer<CongratsPage>().use { scenario ->
                val activity = scenario.withFragment { requireActivity() }
                scenario.moveToState(Lifecycle.State.CREATED)

                scenario.postCustomStudyResult(CustomStudyAction.CUSTOM_STUDY_SESSION)
                assertNull(shadowOf(activity).nextStartedActivity, "a stopped page should not navigate")
                assertFalse(activity.isFinishing)

                scenario.moveToState(Lifecycle.State.RESUMED)
                advanceUntilIdle()

                assertOpenedStudyOptionsAndFinished(activity)
            }
        }

    @Test
    fun `custom study tag selection survives process death`() =
        runTest {
            // Keep the deck finished so the Congrats page stays open.
            updateDeckConfig(Consts.DEFAULT_DECK_ID) { new.perDay = 0 }
            val selectedNote = addBasicNote().update { addTag("selected") }
            val otherNote = addBasicNote().update { addTag("other") }

            val savedState =
                Robolectric.buildActivity(EmptyAnkiActivity::class.java).use { controller ->
                    val activity = controller.setup().get()
                    val page = CongratsPage()
                    activity.supportFragmentManager.commitNow { add(android.R.id.content, page) }
                    page.bridgeCommands.getValue("customStudy").invoke()
                    shadowOf(Looper.getMainLooper()).idle()
                    onView(withText(TR.customStudyStudyByCardStateOrTag()))
                        .inRoot(isDialog())
                        .perform(scrollTo(), click())
                    clickDialogPositiveButton()
                    controller.saveState()
                }

            // recreate() retains ViewModels and process-local defaults. Restore a fresh activity
            // from saved state and clear the defaults to model process death with the picker open.
            uninitializeField<CustomStudyDialog>("deferredInitialization")
            Robolectric.buildActivity(EmptyAnkiActivity::class.java).use { controller ->
                controller.setup(savedState)
                shadowOf(Looper.getMainLooper()).idle()
                // Submit through the real picker and FragmentResult listener, without injecting a result.
                onView(allOf(withId(R.id.tags_dialog_tag_item), hasDescendant(withText("selected"))))
                    .inRoot(isDialog())
                    .perform(click())
                clickDialogPositiveButton()
            }

            assertEquals(assertNotNull(col.decks.customStudySession).id, selectedNote.firstCard().did)
            assertEquals(Consts.DEFAULT_DECK_ID, otherNote.firstCard().did)
        }

    @Test
    fun `an unexpected ViewModel error is reported when its dialog is dismissed`() =
        runTest {
            throwOnShowError = false
            val crashReporter = mock<CrashReporter>()
            val originalCrashReporter = CrashReportService.getReporter()
            CrashReportService.setReporter(crashReporter)
            try {
                launchFragmentInContainer<CongratsPage>().use { scenario ->
                    failViewModel(scenario, IllegalStateException("unexpected"))

                    ShadowDialog.getLatestDialog().dismiss()
                    shadowOf(Looper.getMainLooper()).idle()

                    verify(crashReporter).sendExceptionReport(any<IllegalStateException>(), anyOrNull(), anyOrNull(), any())
                }
            } finally {
                CrashReportService.setReporter(originalCrashReporter)
            }
        }

    @Test
    fun `a locked collection error in the ViewModel links to settings`() =
        runTest {
            throwOnShowError = false
            launchFragmentInContainer<CongratsPage>().use { scenario ->
                failViewModel(scenario, CollectionLockedException(BackendDbLockedException(mock<BackendError>())))

                val helpButton = (ShadowDialog.getLatestDialog() as AlertDialog).getButton(DialogInterface.BUTTON_NEUTRAL)
                assertEquals("Settings", helpButton?.text?.toString())
            }
        }

    /** Throws [exception] in a [CongratsViewModel] task, and waits for the resulting error */
    private fun TestScope.failViewModel(
        scenario: AnkiFragmentScenario<CongratsPage>,
        exception: Exception,
    ) {
        val viewModel = scenario.withFragment { ViewModelProvider(this)[CongratsViewModel::class.java] }
        viewModel.launchCatchingIO { throw exception }
        advanceUntilIdle()
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** Posts [action] where [CustomStudyDialog] posts it: the activity's fragment manager */
    private fun AnkiFragmentScenario<CongratsPage>.postCustomStudyResult(action: CustomStudyAction) {
        onFragment { page ->
            page.requireActivity().supportFragmentManager.setFragmentResult(
                CustomStudyAction.REQUEST_KEY,
                Bundle().apply { putInt(CustomStudyAction.RESULT_CUSTOM_STUDY_ACTION, action.ordinal) },
            )
        }
    }

    private fun assertOpenedStudyOptionsAndFinished(activity: Activity) {
        assertEquals(StudyOptionsActivity::class.java.name, shadowOf(activity).nextStartedActivity?.component?.className)
        assertNull(shadowOf(activity).nextStartedActivity, "study options should open once")
        assertTrue(activity.isFinishing)
    }

    private fun clickDialogPositiveButton() {
        onView(withId(android.R.id.button1))
            .inRoot(isDialog())
            .perform(click())
        shadowOf(Looper.getMainLooper()).idle()
    }
}
