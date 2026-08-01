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

package dev.androidbroadcast.vbpd

import androidx.annotation.CallSuper
import androidx.annotation.RestrictTo
import androidx.viewbinding.ViewBinding
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * Base ViewBindingProperty interface that provides access to operations in the property delegate.
 */
public interface ViewBindingProperty<in R : Any, out T : ViewBinding> : ReadOnlyProperty<R, T> {
    /**
     * Clear all cached data. Will be called when own object destroys view
     */
    public fun clear() {
        // Do nothing
    }
}

/**
 * Eager implementation of [ViewBindingProperty] that holds [ViewBinding] instance.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public open class EagerViewBindingProperty<in R : Any, out T : ViewBinding>(
    private val viewBinding: T,
) : ViewBindingProperty<R, T> {
    public override fun getValue(
        thisRef: R,
        property: KProperty<*>,
    ): T = viewBinding
}

/**
 * Lazy implementation of [ViewBindingProperty] that creates [ViewBinding] instance on the first access.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public open class LazyViewBindingProperty<in R : Any, T : ViewBinding>(
    private val viewBinder: (R) -> T,
) : ViewBindingProperty<R, T> {
    private var viewBinding: T? = null

    public override fun getValue(
        thisRef: R,
        property: KProperty<*>,
    ): T = viewBinding ?: viewBinder(thisRef).also { viewBinding = it }

    @CallSuper
    public override fun clear() {
        viewBinding = null
    }
}
