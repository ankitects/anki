// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View

/**
 * Adapter handling the case of needing to handle both single and double taps on a control
 * To be used in [View.setOnTouchListener]
 *
 * ```kotlin
 * view.setOnTouchListener(object: DoubleTapListener(context) {
 *     override fun onDoubleTap(e: MotionEvent?) {
 *     }
 *
 *     // either of the below, likely not both:
 *     override fun onConfirmedSingleTap(e: MotionEvent?) {
 *     override fun onUnconfirmedSingleTap(e: MotionEvent?) {
 *     }
 * })
 * ```
 *
 * Source: modified from https://stackoverflow.com/a/19629851
 */
abstract class DoubleTapListener(
    context: Context,
) : View.OnTouchListener {
    /**
     * When a single-tap occurs. this is not certain to be a double-tap.
     *
     * Use this if you want a tap action immediately regardless of whether the double tap occurs.
     *
     * Use [onConfirmedSingleTap] if you want to wait.
     *
     * @param e The down motion event of the single-tap.
     * @return true if the event is consumed, else false
     */
    open fun onUnconfirmedSingleTap(e: MotionEvent?) {
        // intentionally blank - a maximum of one of the single taps should be overridden
    }

    /**
     * When a single-tap occurs. this is certain to be a double-tap.
     * Uses [android.view.ViewConfiguration.getDoubleTapTimeout] for the timeout
     *
     * @param e The down motion event of the single-tap.
     * @return true if the event is consumed, else false
     */
    open fun onConfirmedSingleTap(e: MotionEvent?) {
        // intentionally blank - a maximum of one of the single taps should be overridden
    }

    /**
     * Notified when a double-tap occurs. Triggered on the down event of second tap.
     * Uses [android.view.ViewConfiguration.getDoubleTapTimeout] for the timeout
     *
     * @param e The down motion event of the first tap of the double-tap.
     * @return true if the event is consumed, else false
     */
    abstract fun onDoubleTap(e: MotionEvent?)

    private val detector =
        object : GestureDetector(
            context,
            object : SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    this@DoubleTapListener.onDoubleTap(e)
                    return super.onDoubleTap(e)
                }

                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    this@DoubleTapListener.onUnconfirmedSingleTap(e)
                    return super.onSingleTapUp(e)
                }

                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    this@DoubleTapListener.onConfirmedSingleTap(e)
                    return super.onSingleTapConfirmed(e)
                }

                override fun onDown(e: MotionEvent): Boolean {
                    super.onDown(e)
                    return true
                }
            },
        ) {}

    override fun onTouch(
        v: View?,
        event: MotionEvent,
    ): Boolean = detector.onTouchEvent(event)
}
