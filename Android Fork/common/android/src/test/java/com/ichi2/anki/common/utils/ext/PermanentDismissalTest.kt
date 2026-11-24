// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.utils.ext

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModel
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 35])
class PermanentDismissalTest {
    @Test
    fun `finishing triggers cleanup on destruction`() =
        Robolectric.buildActivity(ComponentActivity::class.java).use { controller ->
            controller.setup()
            val lifecycle = controller.get().lifecycle as LifecycleRegistry
            val observersBeforeRegistration = lifecycle.observerCount
            var cleanups = 0
            controller.get().onPermanentDismissal { cleanups++ }
            assertThat(lifecycle.observerCount, equalTo(observersBeforeRegistration + 1))

            controller.get().finish()
            assertThat(cleanups, equalTo(0))
            controller.pause().stop().destroy()
            assertThat(cleanups, equalTo(1))
            assertThat("destroyed activity releases the observer and its callback", lifecycle.observerCount, equalTo(0))
        }

    @Test
    fun `non-finishing destruction clears ViewModels without permitting cleanup`() =
        Robolectric.buildActivity(ComponentActivity::class.java).use { controller ->
            controller.setup()
            val lifecycle = controller.get().lifecycle as LifecycleRegistry
            val viewModel = DetectOnClearedViewModel()
            controller.get().viewModelStore.put("probe", viewModel)
            var cleanedUp = false
            controller.get().onPermanentDismissal { cleanedUp = true }

            controller.pause().saveInstanceState(Bundle()).stop()
            assertThat(controller.get().isFinishing, equalTo(false))
            assertThat(controller.get().isChangingConfigurations, equalTo(false))
            controller.destroy()

            assertThat("onCleared runs even though the activity has not finished", viewModel.cleared, equalTo(true))
            assertThat("saved-state resources must remain available for restoration", cleanedUp, equalTo(false))
            assertThat("the hook is released even when cleanup does not run", lifecycle.observerCount, equalTo(0))
        }

    @Test
    fun `configuration recreation does not trigger cleanup`() =
        Robolectric.buildActivity(ComponentActivity::class.java).use { controller ->
            controller.setup()
            val oldLifecycle = controller.get().lifecycle as LifecycleRegistry
            var cleanedUp = false
            controller.get().onPermanentDismissal { cleanedUp = true }

            controller.recreate()

            assertThat(cleanedUp, equalTo(false))
            assertThat("recreation releases the old activity's hook", oldLifecycle.observerCount, equalTo(0))

            var newActivityCleanups = 0
            controller.get().onPermanentDismissal { newActivityCleanups++ }
            controller.get().finish()
            controller.pause().stop().destroy()
            assertThat(newActivityCleanups, equalTo(1))
            assertThat("the old activity's callback is not reused", cleanedUp, equalTo(false))
        }

    private class DetectOnClearedViewModel : ViewModel() {
        var cleared = false
            private set

        override fun onCleared() {
            cleared = true
        }
    }
}
