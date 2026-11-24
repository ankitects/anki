// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 Sanjay Sargam  <sargamsanjaykumar@gmail.com>

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
