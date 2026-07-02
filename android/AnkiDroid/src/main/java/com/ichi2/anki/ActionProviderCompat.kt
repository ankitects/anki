/*
 * Copyright (c) 2022 lukstbit <52494258+lukstbit@users.noreply.github.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki

import android.content.Context
import android.view.View
import androidx.core.view.ActionProvider

/**
 * Fix for [ActionProvider.onCreateActionView] deprecation on API 16+. This class should be used
 * instead of the library [ActionProvider].
 *
 * @see ActionProvider
 */
abstract class ActionProviderCompat(
    context: Context,
) : ActionProvider(context) {
    @Deprecated("Override onCreateActionView(MenuItem)")
    override fun onCreateActionView(): View {
        // The previous code returned null from this method but updates to the core-ktx library
        // forced with an annotation the return of a non null View. Throwing an exception is safe
        // because the system doesn't use this method anymore.
        throw UnsupportedOperationException("This should no longer be called. onCreateActionView(MenuItem) should be used")
    }
}
