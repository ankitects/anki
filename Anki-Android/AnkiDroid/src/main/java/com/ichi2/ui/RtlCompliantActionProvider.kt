/*
 Copyright (c) 2021 Tarek Mohamed Abdalla <tarekkma@gmail.com>
 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.
 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.
 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.MenuItem
import android.view.View
import android.widget.ImageButton
import androidx.annotation.VisibleForTesting
import com.ichi2.anki.ActionProviderCompat
import com.ichi2.anki.compat.setTooltipTextCompat

/**
 * An Rtl version of a normal action view, where the drawable is mirrored
 */
class RtlCompliantActionProvider(
    context: Context,
) : ActionProviderCompat(context) {
    @VisibleForTesting
    val activity: Activity = unwrapContext(context)

    /**
     * The action to perform when clicking the associated menu item. By default this delegates to
     * the activity's [Activity.onOptionsItemSelected] callback, but a custom action can be set and
     * used instead.
     */
    var clickHandler: (Activity, MenuItem) -> Unit = { activity, menuItem ->
        activity.onOptionsItemSelected(menuItem)
    }

    override fun onCreateActionView(forItem: MenuItem): View {
        val actionView = ImageButton(context, null, android.R.attr.actionButtonStyle)
        actionView.setTooltipTextCompat(forItem.title)
        forItem.icon?.let {
            it.isAutoMirrored = true
            actionView.setImageDrawable(it)
        }
        actionView.setOnClickListener {
            if (!forItem.isEnabled) {
                return@setOnClickListener
            }
            clickHandler(activity, forItem)
        }
        return actionView
    }

    companion object {
        /**
         * Unwrap a context to get the base activity back.
         * @param context a context that may be of type [ContextWrapper]
         * @return The activity of the passed context
         */
        fun unwrapContext(context: Context): Activity {
            var unwrappedContext: Context? = context
            while (unwrappedContext !is Activity && unwrappedContext is ContextWrapper) {
                unwrappedContext = unwrappedContext.baseContext
            }
            return unwrappedContext as? Activity
                ?: throw ClassCastException(
                    "Passed context should be either an instanceof Activity or a ContextWrapper wrapping an Activity",
                )
        }
    }
}
