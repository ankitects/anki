/*
 * Copyright (c) 2024 Sanjay Sargam  <sargamsanjaykumar@gmail.com>
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

import android.view.View
import timber.log.Timber

/**
 * A listener that has the same action for both "context click" (i.e., mostly right-click) and "long click" (i.e., holding the finger on the view).
 *
 *  * Note: In some contexts, a long press (long click) is expected to be informational, whereas a right-click (context click) is expected to be functional.
 *  * Ensure that using the same action for both is appropriate for your use case.
 */
fun interface OnContextAndLongClickListener :
    View.OnContextClickListener,
    View.OnLongClickListener {
    /**
     * The action to do for both contextClick and long click
     * @returns whether the operation was successful
     */
    fun onAction(v: View): Boolean

    override fun onContextClick(v: View): Boolean {
        Timber.i("${this.javaClass}: user context clicked")
        return onAction(v)
    }

    override fun onLongClick(v: View): Boolean {
        Timber.i("${this.javaClass}: user long clicked")
        return onAction(v)
    }

    companion object {
        /**
         * Ensures [this] gets both a long click and a context click listener.
         * @see View.setOnLongClickListener
         * @see View.setOnContextClickListener
         */
        fun View.setOnContextAndLongClickListener(listener: OnContextAndLongClickListener?) {
            setOnLongClickListener(listener)
            setOnContextClickListener(listener)
        }
    }
}
