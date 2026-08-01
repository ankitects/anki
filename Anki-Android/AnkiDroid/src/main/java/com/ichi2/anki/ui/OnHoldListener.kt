/*
 *  Copyright (c) 2024 Sumit Singh <sumitsinghkoranga7@gmail.com>
 *  Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
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
 */

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
