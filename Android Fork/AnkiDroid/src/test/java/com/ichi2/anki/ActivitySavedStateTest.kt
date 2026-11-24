// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.activity.ComponentActivity
import com.ichi2.anki.instantnoteeditor.InstantNoteEditorActivity
import com.ichi2.testutils.ActivityList
import com.ichi2.testutils.ActivityList.ActivityLaunchParam
import com.ichi2.testutils.createSavedStateHandleWithDefaultFactory
import com.ichi2.testutils.parcelledCopy
import com.ichi2.testutils.saveState
import com.ichi2.testutils.savedStateHandlePaths
import com.ichi2.testutils.savedStateHandlePathsToKey
import com.ichi2.testutils.withLaunchPayload
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.empty
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Exercises the real activities and their ViewModels, including ViewModels in hosted fragments. */
@RunWith(ParameterizedRobolectricTestRunner::class)
class ActivitySavedStateTest(
    private val activityName: String,
    private val launcher: ActivityLaunchParam,
) : RobolectricTest() {
    override fun getCollectionStorageMode() = CollectionStorageMode.IN_MEMORY_WITH_MEDIA

    @Test
    fun `ViewModels do not persist unrelated launch arguments`() {
        ensureCollectionLoadIsSynchronous()
        setIntroductionSlidesShown(true)
        val intent = launcher.buildIntent(this).withLaunchPayload(launcher.activity, UNRELATED_EXTRA, ByteArray(300_000))

        val controller = startActivityControllerNormallyOpenCollectionWithIntent(launcher.activity, intent)
        assertFalse(controller.get().isFinishing, "$activityName must reach its normal screen")
        // New ViewModels must be safe without opting into a custom factory.
        (controller.get() as ComponentActivity).createSavedStateHandleWithDefaultFactory()
        val savedState = controller.saveState().parcelledCopy(javaClass.classLoader)

        // Check the serialized handles, allowing FragmentManager to retain the original arguments.
        assertTrue(savedState.savedStateHandlePaths().isNotEmpty(), "No SavedStateHandle provider found for $activityName")
        val copies = savedState.savedStateHandlePathsToKey(UNRELATED_EXTRA)
        assertThat("$activityName persisted an unrelated argument in these ViewModels", copies, empty())
    }

    companion object {
        private const val UNRELATED_EXTRA = "saved_state_test_unrelated_extra"

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun activities(): Collection<Array<Any>> =
            ActivityList
                .allActivitiesAndIntents()
                // Plain Activities do not own ViewModels or SavedStateHandles.
                .filter { ComponentActivity::class.java.isAssignableFrom(it.activity) }
                // TODO: Re-enable once the instant editor delivers its LiveData note type before opening the dialog.
                .filterNot { it.activity == InstantNoteEditorActivity::class.java }
                .map { arrayOf(it.simpleName, it) }
    }
}
