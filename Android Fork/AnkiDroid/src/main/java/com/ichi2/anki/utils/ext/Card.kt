// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.utils.ext

import com.ichi2.anki.Flag
import com.ichi2.anki.libanki.Card

val Card.flag: Flag get() = Flag.fromCode(userFlag())
