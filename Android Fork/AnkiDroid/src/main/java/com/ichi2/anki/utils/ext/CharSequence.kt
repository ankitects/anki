// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.utils.ext

fun <C : CharSequence> C?.ifNullOrEmpty(defaultValue: () -> C): C = this?.ifEmpty(defaultValue) ?: defaultValue()
