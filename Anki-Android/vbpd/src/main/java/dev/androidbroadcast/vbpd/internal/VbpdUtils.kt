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
 *    Copyright 2020-2025 Kirill Rozov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)

package dev.androidbroadcast.vbpd.internal

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import androidx.annotation.IdRes
import androidx.annotation.RestrictTo
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.fragment.app.DialogFragment
import java.lang.ref.WeakReference
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

@RestrictTo(RestrictTo.Scope.LIBRARY)
public fun <V : View> View.requireViewByIdCompat(
    @IdRes id: Int,
): V = ViewCompat.requireViewById(this, id)

@RestrictTo(RestrictTo.Scope.LIBRARY)
public fun <V : View> Activity.requireViewByIdCompat(
    @IdRes id: Int,
): V = ActivityCompat.requireViewById(this, id)

/**
 * Utility to find root view for ViewBinding in Activity
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public fun findRootView(activity: Activity): View {
    val contentView = activity.findViewById<ViewGroup>(android.R.id.content)
    checkNotNull(contentView) { "Activity has no content view" }
    return when (contentView.childCount) {
        1 -> contentView.getChildAt(0)
        0 -> error("Content view has no children. Provide a root view explicitly")
        else -> error("More than one child view found in the Activity content view")
    }
}

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public fun DialogFragment.findRootView(
    @IdRes viewBindingRootId: Int,
): View {
    if (showsDialog) {
        val dialog =
            checkNotNull(dialog) {
                "DialogFragment doesn't have a dialog. Use viewBinding delegate after onCreateDialog"
            }
        val window = checkNotNull(dialog.window) { "Fragment's Dialog has no window" }
        return with(window.decorView) {
            if (viewBindingRootId != 0) requireViewByIdCompat(viewBindingRootId) else this
        }
    } else {
        return requireView().findViewById(viewBindingRootId)
    }
}

@RestrictTo(RestrictTo.Scope.LIBRARY)
internal fun <T : Any> weakReference(value: T? = null): ReadWriteProperty<Any, T?> =
    object : ReadWriteProperty<Any, T?> {
        private var weakRef = WeakReference(value)

        override fun getValue(
            thisRef: Any,
            property: KProperty<*>,
        ): T? = weakRef.get()

        override fun setValue(
            thisRef: Any,
            property: KProperty<*>,
            value: T?,
        ) {
            weakRef = WeakReference(value)
        }
    }
