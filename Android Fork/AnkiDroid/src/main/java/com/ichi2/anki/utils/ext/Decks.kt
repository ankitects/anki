// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.utils.ext

import com.ichi2.anki.libanki.Decks

val Decks.defaultConfig
    get() = getConfig(confId = 1)!!
