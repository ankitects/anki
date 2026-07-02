// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.time

class SystemTime : Time() {
    override fun intTimeMS(): Long = System.currentTimeMillis()
}
