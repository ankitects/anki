// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.utils

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import com.ichi2.anki.EmptyApplicationCategory
import com.ichi2.testutils.EmptyApplication
import com.ichi2.testutils.parcelledCopy
import com.ichi2.testutils.saveState
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class SavedStateViewModelFactoryTest(
    private val fragmentScoped: Boolean,
) {
    private val factory = savedStateViewModelFactory(create = ::TestViewModel)

    @Test
    fun `factory excludes launch arguments without changing owner defaults`() {
        val intent = Intent().putExtra("id", 42L).putExtra("unrelated", "payload")
        Robolectric.buildActivity(FragmentActivity::class.java, intent).setup().use { controller ->
            val owner = owner(controller.get())
            val model = ViewModelProvider(owner, factory)[TestViewModel::class.java]

            assertEquals(emptySet(), model.state.keys())

            // Other factories must still receive the owner's original launch arguments.
            val unfiltered = ViewModelProvider(owner)["unfiltered", TestViewModel::class.java]
            assertEquals(42L, unfiltered.state.get<Long>("id"))
            assertEquals("payload", unfiltered.state.get<String>("unrelated"))
        }
    }

    @Test
    fun `process death restores handle and flow values instead of launch arguments`() {
        val intent = Intent().putExtra("id", 42L).putExtra("unrelated", "payload")
        val savedState =
            Robolectric.buildActivity(FragmentActivity::class.java, intent).setup().use { controller ->
                val original = ViewModelProvider(owner(controller.get()), factory)[TestViewModel::class.java]
                original.state["id"] = 43L
                original.state.getMutableStateFlow("new_ui_state", "initial value").value = "edited value"
                controller.saveState().parcelledCopy(javaClass.classLoader)
            }

        Robolectric.buildActivity(FragmentActivity::class.java, intent).setup(savedState).use { controller ->
            val restored = ViewModelProvider(owner(controller.get()), factory)[TestViewModel::class.java]

            assertEquals(setOf("id", "new_ui_state"), restored.state.keys())
            assertEquals(43L, restored.state.get<Long>("id"))
            assertEquals("edited value", restored.state.getStateFlow("new_ui_state", "initial value").value)
        }
    }

    private fun owner(activity: FragmentActivity): ViewModelStoreOwner {
        if (!fragmentScoped) return activity
        val manager = activity.supportFragmentManager
        return manager.findFragmentByTag("owner") ?: Fragment().also {
            it.arguments = Bundle(activity.intent.extras)
            manager.beginTransaction().add(it, "owner").commitNow()
        }
    }

    class TestViewModel(
        val state: ViewModelSavedStateHandle,
    ) : ViewModel()

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "fragment scoped = {0}")
        fun owners() = listOf(arrayOf(false), arrayOf(true))
    }
}
