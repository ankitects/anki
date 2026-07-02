/*
 *  Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * This file incorporates code under the following license:
 *
 *     Copyright 2020-2025 Kirill Rozov
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 */

@file:JvmName("FragmentViewBindings")

package dev.androidbroadcast.vbpd

import android.view.View
import androidx.annotation.IdRes
import androidx.annotation.RestrictTo
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.viewbinding.ViewBinding
import dev.androidbroadcast.vbpd.internal.findRootView
import dev.androidbroadcast.vbpd.internal.requireViewByIdCompat
import dev.androidbroadcast.vbpd.internal.weakReference
import kotlin.reflect.KProperty

private class FragmentViewBindingProperty<F : Fragment, T : ViewBinding>(
    viewBinder: (F) -> T,
) : LazyViewBindingProperty<F, T>(viewBinder) {
    private var lifecycleCallbacks: FragmentManager.FragmentLifecycleCallbacks? = null
    private var fragmentManager: FragmentManager? = null

    override fun getValue(
        thisRef: F,
        property: KProperty<*>,
    ): T {
        val viewBinding = super.getValue(thisRef, property)
        registerLifecycleCallbacksIfNeeded(thisRef)
        return viewBinding
    }

    /**
     * Register callbacks to listen event about Fragment's View
     */
    private fun registerLifecycleCallbacksIfNeeded(fragment: Fragment) {
        if (lifecycleCallbacks != null) return

        val fragmentManager =
            fragment.parentFragmentManager
                .also { fm -> this.fragmentManager = fm }
        lifecycleCallbacks =
            VBFragmentLifecycleCallback(fragment).also { callbacks ->
                fragmentManager.registerFragmentLifecycleCallbacks(callbacks, false)
            }
    }

    override fun clear() {
        super.clear()

        val lifecycleCallbacks = lifecycleCallbacks
        if (lifecycleCallbacks != null) {
            fragmentManager?.unregisterFragmentLifecycleCallbacks(lifecycleCallbacks)
        }

        fragmentManager = null
        this.lifecycleCallbacks = null
    }

    inner class VBFragmentLifecycleCallback(
        fragment: Fragment,
    ) : FragmentManager.FragmentLifecycleCallbacks() {
        private val fragment by weakReference(fragment)

        override fun onFragmentViewDestroyed(
            fm: FragmentManager,
            f: Fragment,
        ) {
            if (fragment === f) clear()
        }
    }
}

/**
 * Create new [ViewBinding] associated with the [Fragment]
 */
@Suppress("UnusedReceiverParameter")
@JvmName("viewBindingFragmentWithCallbacks")
public fun <F : Fragment, T : ViewBinding> Fragment.viewBinding(viewBinder: (F) -> T): ViewBindingProperty<F, T> =
    fragmentViewBinding(viewBinder = viewBinder)

/**
 * Create new [ViewBinding] associated with the [Fragment]
 *
 * @param vbFactory Function that creates a new instance of [ViewBinding]. `MyViewBinding::bind` can be used
 * @param viewProvider Provide a [View] from the Fragment. By default call [Fragment.requireView]
 */
@Suppress("UnusedReceiverParameter")
@JvmName("viewBindingFragment")
public inline fun <F : Fragment, T : ViewBinding> Fragment.viewBinding(
    crossinline vbFactory: (View) -> T,
    crossinline viewProvider: (F) -> View = Fragment::requireView,
): ViewBindingProperty<F, T> = fragmentViewBinding(viewBinder = { fragment -> viewProvider(fragment).let(vbFactory) })

/**
 * Create new [ViewBinding] associated with the [Fragment]
 *
 * @param vbFactory Function that creates a new instance of [ViewBinding]. `MyViewBinding::bind` can be used
 * @param viewBindingRootId Root view's id that will be used as a root for the view binding
 */
@JvmName("viewBindingFragmentWithCallbacks")
public inline fun <F : Fragment, T : ViewBinding> Fragment.viewBinding(
    crossinline vbFactory: (View) -> T,
    @IdRes viewBindingRootId: Int,
): ViewBindingProperty<F, T> =
    when (this) {
        is DialogFragment -> {
            fragmentViewBinding { fragment ->
                (fragment as DialogFragment)
                    .findRootView(viewBindingRootId)
                    .let(vbFactory)
            }
        }

        else -> {
            fragmentViewBinding { fragment ->
                fragment
                    .requireView()
                    .requireViewByIdCompat<View>(viewBindingRootId)
                    .let(vbFactory)
            }
        }
    }

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public fun <F : Fragment, T : ViewBinding> fragmentViewBinding(viewBinder: (F) -> T): ViewBindingProperty<F, T> =
    FragmentViewBindingProperty(viewBinder)
