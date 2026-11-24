// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.libanki.testutils.ext

import com.ichi2.anki.libanki.Card

@Suppress("VisibleForTests")
fun Card.setFlag(flag: Int) {
    flags = flag
}
