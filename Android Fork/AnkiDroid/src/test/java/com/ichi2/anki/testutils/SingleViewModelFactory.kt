// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.testutils

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * Implementation of [androidx.lifecycle.ViewModelProvider.Factory] returning a single ViewModel
 *
 * ```kotlin
 * // FragmentUnderTest requires a SampleViewModel
 * val viewModelFactory = SingleViewModelFactory.create(SampleViewModel())
 *
 * val fragmentArgs = Bundle()
 * val scenario = launchFragmentInContainer(fragmentArgs) {
 *     FragmentUnderTest(viewModelFactory)
 * }
 * ```
 */
@Suppress("UNCHECKED_CAST")
class SingleViewModelFactory<T : ViewModel>(
    private val instance: T,
    private val clazz: Class<T>,
) : ViewModelProvider.Factory {
    override fun <R : ViewModel> create(modelClass: Class<R>): R {
        require(modelClass == clazz)
        return instance as R
    }

    companion object {
        inline fun <reified T : ViewModel> create(instance: T): SingleViewModelFactory<T> =
            SingleViewModelFactory(
                instance,
                T::class.java,
            )
    }
}
