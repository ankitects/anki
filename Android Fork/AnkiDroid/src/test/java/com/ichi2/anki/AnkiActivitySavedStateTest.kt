// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.utils.ViewModelSavedStateHandle
import com.ichi2.testutils.EmptyAnkiActivity
import com.ichi2.testutils.parcelSize
import com.ichi2.testutils.parcelledCopy
import com.ichi2.testutils.saveState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/** Guards against the activity's export ViewModel duplicating launch arguments in saved state. */
@RunWith(AndroidJUnit4::class)
class AnkiActivitySavedStateTest : RobolectricTest() {
    @Test
    fun `unrelated intent extras do not increase activity saved state`() {
        fun savedSize(payload: ByteArray): Int {
            val intent = Intent(targetContext, EmptyAnkiActivity::class.java).putExtra("payload", payload)
            val size = savedStateSize(EmptyAnkiActivity::class.java, intent)
            assertContentEquals(payload, intent.getByteArrayExtra("payload"))
            return size
        }

        assertEquals(savedSize(ByteArray(0)), savedSize(ByteArray(300_000)))
    }

    @Test
    fun `fragment arguments are saved exactly once`() {
        fun savedSize(payload: ByteArray): Int =
            savedStateSize(
                SingleFragmentActivity::class.java,
                SingleFragmentActivity.getIntent(
                    targetContext,
                    Fragment::class,
                    Bundle().apply { putByteArray("payload", payload) },
                ),
            )

        val payload = ByteArray(300_000)
        assertEquals(
            payload.size,
            savedSize(payload) - savedSize(ByteArray(0)),
            "Saved state should grow by exactly one copy of the fragment arguments",
        )
    }

    @Test
    fun `default factory restores UI state without copying launch arguments`() {
        val intent = Intent().putExtra("id", 42L).putExtra("unrelated", "payload")
        val controller = startActivityControllerNormallyOpenCollectionWithIntent(EmptyAnkiActivity::class.java, intent)
        val original = ViewModelProvider(controller.get())[TestViewModel::class.java]
        assertEquals(emptySet(), original.state.keys())
        assertEquals(42L, controller.get().intent.getLongExtra("id", -1))
        original.state["id"] = 43L
        original.state.getMutableStateFlow("ui_state", "initial").value = "edited"
        val savedState = controller.saveState().parcelledCopy(javaClass.classLoader)
        controller.destroy()

        val restoredController =
            Robolectric.buildActivity(EmptyAnkiActivity::class.java, intent).setup(savedState).also(::saveControllerForCleanup)
        val restored = ViewModelProvider(restoredController.get())[TestViewModel::class.java]
        assertEquals(setOf("id", "ui_state"), restored.state.keys())
        assertEquals(43L, restored.state.get<Long>("id"))
        assertEquals("edited", restored.state.getStateFlow("ui_state", "initial").value)
    }

    class TestViewModel(
        val state: ViewModelSavedStateHandle,
    ) : ViewModel()

    private fun <T : AnkiActivity> savedStateSize(
        activityClass: Class<T>,
        intent: Intent,
    ): Int = startActivityControllerNormallyOpenCollectionWithIntent(activityClass, intent).saveState().parcelSize()
}
