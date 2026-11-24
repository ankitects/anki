// SPDX-License-Identifier: GPL-3.0-or-later

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
