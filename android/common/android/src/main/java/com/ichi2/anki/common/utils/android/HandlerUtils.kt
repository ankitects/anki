// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Shridhar Goel <shridhar.goel@gmail.com>

package com.ichi2.anki.common.utils.android

import android.os.Handler
import android.os.Looper

object HandlerUtils {
    fun getDefaultLooper(): Looper = Looper.getMainLooper()

    fun newHandler(): Handler = Handler(getDefaultLooper())

    /**
     * Creates a new [Handler] and adds [r] to the message queue.
     * The runnable will be run on main thread
     *
     * @param r The Runnable that will be executed.
     */
    fun postOnNewHandler(r: Runnable): Handler {
        val newHandler = newHandler()
        newHandler.post(r)
        return newHandler
    }

    /**
     * Causes the Runnable r to be added to the message queue, to be run
     * after the specified amount of time elapses.
     * The runnable will be run on main thread
     *
     * <b>The time-base is [android.os.SystemClock.uptimeMillis]</b>
     * Time spent in deep sleep will add an additional delay to execution.
     *
     * @param r The Runnable that will be executed.
     * @param delayMillis The delay (in milliseconds) until the Runnable
     *        will be executed.
     */
    fun postDelayedOnNewHandler(
        r: Runnable,
        delayMillis: Long,
    ): Handler {
        val newHandler = newHandler()
        newHandler.postDelayed(r, delayMillis)
        return newHandler
    }

    /**
     * Add runnable to message queue and run on the thread to which this handler is attached.
     * This will run on the main thread if called from the main thread.
     *
     * @param function The function which needs to be executed.
     */
    fun executeFunctionUsingHandler(function: () -> Unit) {
        Handler(Looper.getMainLooper()).post {
            function()
        }
    }

    /**
     * Execute a function after a certain delay.
     * This will run on the main thread if called from the main thread.
     *
     * @param time The time by which the function execution needs to be delayed.
     * @param function The function which needs to be executed.
     */
    fun executeFunctionWithDelay(
        time: Long,
        function: () -> Unit,
    ) {
        Handler(Looper.getMainLooper()).postDelayed(
            {
                function()
            },
            time,
        )
    }

    /**
     * Executes a method on the main thread. Either immediately, or via
     * [HandlerUtils.executeFunctionUsingHandler]
     *
     * @param function The function which needs to be executed.
     */
    fun executeOnMainThread(function: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            function()
        } else {
            executeFunctionUsingHandler { function() }
        }
    }
}
