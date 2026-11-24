// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 Sumit Singh <sumitsinghkoranga7@gmail.com>

package com.ichi2.anki.ui

import android.view.MotionEvent
import android.view.View
import timber.log.Timber

interface OnHoldListener {
    fun onTouchStart()

    fun onHoldEnd()
}

fun View.setOnHoldListener(listener: OnHoldListener) {
    val listenerWrapper =
        object : View.OnTouchListener, View.OnLongClickListener {
            var isHolding = false

            override fun onTouch(
                v: View?,
                event: MotionEvent?,
            ): Boolean {
                when (event?.action) {
                    MotionEvent.ACTION_DOWN -> {
                        Timber.v("ACTION_DOWN: onTouchStart()")
                        listener.onTouchStart()
                    }
                    MotionEvent.ACTION_UP -> {
                        Timber.v("ACTION_UP")
                        if (isHolding) {
                            Timber.v("onHoldEnd()")
                            listener.onHoldEnd()
                        }
                        isHolding = false
                    }
                }
                return false
            }

            override fun onLongClick(v: View?): Boolean {
                Timber.v("onLongClick")
                // this method is called once the threshold for a long press is reached
                // not when the long press is released
                isHolding = true
                return true
            }
        }
    this.setOnLongClickListener(listenerWrapper)
    this.setOnTouchListener(listenerWrapper)
}
