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

@file:JvmName("ActivityViewBindings")

package dev.androidbroadcast.vbpd

import android.app.Activity
import android.app.Application.ActivityLifecycleCallbacks
import android.os.Bundle
import android.view.View
import androidx.annotation.IdRes
import androidx.annotation.RestrictTo
import androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP
import androidx.viewbinding.ViewBinding
import dev.androidbroadcast.vbpd.internal.findRootView
import dev.androidbroadcast.vbpd.internal.requireViewByIdCompat
import dev.androidbroadcast.vbpd.internal.weakReference
import kotlin.reflect.KProperty

@RestrictTo(LIBRARY_GROUP)
public class ActivityViewBindingProperty<in A : Activity, T : ViewBinding>(
    viewBinder: (A) -> T,
) : LazyViewBindingProperty<A, T>(viewBinder) {
    private var lifecycleCallbacks: ActivityLifecycleCallbacks? = null
    private var activity: Activity? by weakReference(null)

    override fun getValue(
        thisRef: A,
        property: KProperty<*>,
    ): T =
        super
            .getValue(thisRef, property)
            .also { registerLifecycleCallbacksIfNeeded(thisRef) }

    private fun registerLifecycleCallbacksIfNeeded(activity: Activity) {
        if (lifecycleCallbacks != null) return
        this.activity = activity
        VBActivityLifecycleCallbacks()
            .also { callbacks -> this.lifecycleCallbacks = callbacks }
            .let(activity.application::registerActivityLifecycleCallbacks)
    }

    override fun clear() {
        super.clear()
        val lifecycleCallbacks = lifecycleCallbacks
        if (lifecycleCallbacks != null) {
            activity?.application?.unregisterActivityLifecycleCallbacks(lifecycleCallbacks)
        }

        this.activity = null
        this.lifecycleCallbacks = null
    }

    private inner class VBActivityLifecycleCallbacks : ActivityLifecycleCallbacks {
        override fun onActivityCreated(
            activity: Activity,
            savedInstanceState: Bundle?,
        ) {
        }

        override fun onActivityStarted(activity: Activity) {
        }

        override fun onActivityResumed(activity: Activity) {
        }

        override fun onActivityPaused(activity: Activity) {
        }

        override fun onActivityStopped(activity: Activity) {
        }

        override fun onActivitySaveInstanceState(
            activity: Activity,
            outState: Bundle,
        ) {
        }

        override fun onActivityDestroyed(activity: Activity) {
            if (activity === this@ActivityViewBindingProperty.activity) clear()
        }
    }
}

/**
 * Create new [ViewBinding] associated with the [Activity].
 * Cached [ViewBinding] will be cleaned after [Activity.onDestroy]
 *
 * @param viewBinder Function that creates a new instance of [ViewBinding]. Use `MyViewBinding::bind` as default
 *
 * @return [ViewBindingProperty] associated with the [Activity]'s view
 */
@JvmName("viewBindingActivityWithCallbacks")
@Suppress("UnusedReceiverParameter")
public fun <A : Activity, T : ViewBinding> Activity.viewBinding(viewBinder: (A) -> T): ViewBindingProperty<A, T> =
    ActivityViewBindingProperty(viewBinder = viewBinder)

/**
 * Create new [ViewBinding] associated with the [Activity].
 * Cached [ViewBinding] will be cleaned after [Activity.onDestroy]
 *
 * @param vbFactory Function that creates a new instance of [ViewBinding]. Use `MyViewBinding::bind` as default
 * @param viewProvider Function that provides a root view for the view binding
 *
 * @return [ViewBindingProperty] associated with the [Activity]'s view
 */
@JvmName("viewBindingActivityWithCallbacks")
public inline fun <A : Activity, T : ViewBinding> Activity.viewBinding(
    crossinline vbFactory: (View) -> T,
    crossinline viewProvider: (A) -> View = ::findRootView,
): ViewBindingProperty<A, T> = viewBinding { activity -> vbFactory(viewProvider(activity)) }

/**
 * Create new [ViewBinding] associated with the [Activity][this] and allow customization of how
 * a [View] will be bound to the view binding.
 *
 * @param vbFactory Function that creates a new instance of [ViewBinding]. `MyViewBinding::bind` can be used
 * @param viewBindingRootId Root view's id that will be used as a root for the view binding
 *
 * @return [ViewBindingProperty] associated with the [Activity]'s view
 */
@JvmName("viewBindingActivity")
public inline fun <A : Activity, T : ViewBinding> Activity.viewBinding(
    crossinline vbFactory: (View) -> T,
    @IdRes viewBindingRootId: Int,
): ViewBindingProperty<A, T> =
    viewBinding { activity ->
        vbFactory(activity.requireViewByIdCompat(viewBindingRootId))
    }
