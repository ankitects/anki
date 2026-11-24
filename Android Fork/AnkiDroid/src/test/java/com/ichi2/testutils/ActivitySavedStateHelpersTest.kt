// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.DrawingFragment
import com.ichi2.anki.EmptyApplicationCategory
import com.ichi2.anki.SingleFragmentActivity
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import kotlin.test.assertContentEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class ActivitySavedStateHelpersTest : AndroidTest {
    @Test
    fun `launch payload reaches fragment arguments when the launch has none`() {
        val intent = DrawingFragment.getIntent(targetContext)
        val payload = byteArrayOf(1, 2, 3)
        assertNull(intent.getBundleExtra(SingleFragmentActivity.EXTRA_FRAGMENT_ARGS))

        intent.withLaunchPayload(SingleFragmentActivity::class.java, EXTRA_PAYLOAD, payload)

        assertContentEquals(payload, intent.getByteArrayExtra(EXTRA_PAYLOAD))
        assertContentEquals(payload, intent.getBundleExtra(SingleFragmentActivity.EXTRA_FRAGMENT_ARGS)?.getByteArray(EXTRA_PAYLOAD))
    }

    @Test
    fun `saved state inspection detects copies made by the unfiltered default factory`() {
        val intent = Intent().putExtra(EXTRA_PAYLOAD, ByteArray(300_000))
        // A plain AndroidX activity reproduces argument copying without AnkiActivity's override.
        Robolectric.buildActivity(FragmentActivity::class.java, intent).setup().use { controller ->
            controller.get().createSavedStateHandleWithDefaultFactory()
            val savedState = controller.saveState().parcelledCopy(javaClass.classLoader)

            assertTrue(savedState.savedStateHandlePaths().isNotEmpty(), "Expected a serialized SavedStateHandle provider")
            assertTrue(
                savedState.savedStateHandlePathsToKey(EXTRA_PAYLOAD).isNotEmpty(),
                "Expected the copied intent payload to be detected",
            )
        }
    }

    @Test
    fun `saved state inspection ignores payload retained in fragment arguments`() {
        val payload = byteArrayOf(1, 2, 3)
        val savedState =
            Robolectric.buildActivity(FragmentActivity::class.java).setup().use { controller ->
                val activity = controller.get()
                val fragment = Fragment().apply { arguments = Bundle().apply { putByteArray(EXTRA_PAYLOAD, payload) } }
                activity.supportFragmentManager
                    .beginTransaction()
                    .add(fragment, "payload")
                    .commitNow()
                activity.createSavedStateHandleWithDefaultFactory()
                controller.saveState().parcelledCopy(javaClass.classLoader)
            }

        assertTrue(savedState.savedStateHandlePaths().isNotEmpty(), "Expected a serialized SavedStateHandle provider")
        assertTrue(savedState.savedStateHandlePathsToKey(EXTRA_PAYLOAD).isEmpty(), "Fragment arguments are not ViewModel state")
        Robolectric.buildActivity(FragmentActivity::class.java).setup(savedState).use { controller ->
            val restoredFragment = controller.get().supportFragmentManager.findFragmentByTag("payload")
            assertContentEquals(payload, restoredFragment?.arguments?.getByteArray(EXTRA_PAYLOAD))
        }
    }

    companion object {
        private const val EXTRA_PAYLOAD = "saved_state_test_payload"
    }
}
