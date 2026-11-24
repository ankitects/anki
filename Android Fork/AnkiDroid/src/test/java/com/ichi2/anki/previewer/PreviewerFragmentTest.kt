// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.previewer

import android.view.KeyEvent
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CommonString
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.browser.IdsFile
import com.ichi2.testutils.createTransientDirectory
import com.ichi2.testutils.parcelledCopy
import com.ichi2.testutils.saveState
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.shadows.ShadowToast
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@RunWith(AndroidJUnit4::class)
class PreviewerFragmentTest : RobolectricTest() {
    // Changing the selected card renders its media controls, which requires a media folder.
    override fun getCollectionStorageMode() = CollectionStorageMode.IN_MEMORY_WITH_MEDIA

    @Test
    fun `missing selection closes previewer`() = assertUnavailableSelection { assertTrue(delete()) }

    @Test
    fun `truncated selection closes previewer`() = assertUnavailableSelection { writeBytes(readBytes().dropLast(1).toByteArray()) }

    @Test
    fun `empty selection closes previewer`() {
        assertUnavailableSelection { writeBytes(byteArrayOf(0, 0, 0, 0)) }
    }

    private fun assertUnavailableSelection(changeFile: IdsFile.() -> Unit) {
        val file = IdsFile(createTransientDirectory(), addBasicNote().cardIds(col), IdsFile.Purpose.PREVIEW).apply(changeFile)
        val intent = PreviewerFragment.getIntent(targetContext, file, currentIndex = 0)
        Robolectric.buildActivity(CardViewerActivity::class.java, intent).use { controller ->
            val activity = controller.setup().get()
            assertTrue(activity.isFinishing)
            assertEquals(targetContext.getString(CommonString.something_wrong), ShadowToast.getTextOfLatestToast())
        }
    }

    @Test
    fun `restoring after selection loss closes previewer and accepts key dispatch`() {
        val file = IdsFile(createTransientDirectory(), addBasicNote().cardIds(col), IdsFile.Purpose.PREVIEW)
        val intent = PreviewerFragment.getIntent(targetContext, file, currentIndex = 0)
        val savedState =
            Robolectric.buildActivity(CardViewerActivity::class.java, intent).use { controller ->
                controller.setup()
                controller.saveState().parcelledCopy(CardViewerActivity::class.java.classLoader)
            }
        assertTrue(file.delete())
        ShadowToast.reset()
        Robolectric.buildActivity(CardViewerActivity::class.java, intent).use { controller ->
            val activity = controller.setup(savedState).get()
            assertTrue(activity.isFinishing)
            assertEquals(targetContext.getString(CommonString.something_wrong), ShadowToast.getTextOfLatestToast())
            activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
        }
    }

    @Test
    fun `launch index is honored and restored selection overrides it`() {
        ensureCollectionLoadIsSynchronous()
        val ids = (1..3).flatMap { addBasicNote().cardIds(col) }
        val file = IdsFile(createTransientDirectory(), ids, IdsFile.Purpose.PREVIEW)
        val intent = PreviewerFragment.getIntent(targetContext, file, currentIndex = 1)
        lateinit var original: PreviewerViewModel
        val savedState =
            Robolectric.buildActivity(CardViewerActivity::class.java, intent).use { controller ->
                val activity = controller.setup().get()
                original = (activity.supportFragmentManager.fragments.single() as PreviewerFragment).viewModel
                assertEquals(1, original.currentIndex.value)
                runBlocking { withTimeout(10.seconds) { assertEquals(ids[1], original.currentCard.await().id) } }

                // Slider positions are one-based; move away from the launch index before saving.
                val launchCard = original.currentCard
                original.onSliderChange(3)
                advanceRobolectricLooperUntil { original.currentCard !== launchCard && original.currentCard.isCompleted }
                assertEquals(2, original.currentIndex.value)
                runBlocking { withTimeout(10.seconds) { assertEquals(ids[2], original.currentCard.await().id) } }
                controller.saveState().parcelledCopy(javaClass.classLoader)
            }

        // A new controller creates a new ViewModel, unlike a configuration-only recreation.
        Robolectric.buildActivity(CardViewerActivity::class.java, intent).use { controller ->
            val activity = controller.setup(savedState).get()
            val restored = (activity.supportFragmentManager.fragments.single() as PreviewerFragment).viewModel
            assertNotSame(original, restored)
            assertEquals(2, restored.currentIndex.value)
            runBlocking { withTimeout(10.seconds) { assertEquals(ids[2], restored.currentCard.await().id) } }
        }
    }

    @Test
    fun `rotation retains loaded selection when its file has disappeared`() {
        val ids = addBasicAndReversedNote().cardIds(col)
        val file = IdsFile(createTransientDirectory(), ids, IdsFile.Purpose.PREVIEW)
        val intent = PreviewerFragment.getIntent(targetContext, file, currentIndex = 0)
        ActivityScenario.launch<CardViewerActivity>(intent).use { scenario ->
            lateinit var original: PreviewerViewModel
            scenario.onActivity { activity ->
                original = (activity.supportFragmentManager.fragments.single() as PreviewerFragment).viewModel
            }
            assertTrue(file.delete())
            scenario.recreate()
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
                val restored = (activity.supportFragmentManager.fragments.single() as PreviewerFragment).viewModel
                assertSame(original, restored)
                assertEquals(ids, restored.selectedCardIds)
            }
        }
    }

    @Test
    fun `previewer - back button`() {
        val note = addBasicAndReversedNote()

        val intent =
            PreviewerFragment.getIntent(
                targetContext,
                idsFile = IdsFile(createTransientDirectory(), note.cardIds(col), IdsFile.Purpose.PREVIEW),
                currentIndex = 0,
            )

        ActivityScenario.launch<CardViewerActivity>(intent).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { previewer ->
                assertThat("Activity is not finishing", !previewer.isFinishing)
                // this needs to test the keypress, not the dispatcher
                Espresso.pressBack()
                assertThat("Activity is finishing after back press", previewer.isFinishing)
            }
        }
    }
}
