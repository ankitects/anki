// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.utils.ext

/**
 * @return the receiver if non-zero, otherwise the result of calling [defaultValue]
 */
@Suppress("KotlinConstantConditions") // lint is incorrect
fun Long.ifZero(defaultValue: () -> Long) = if (this == 0L) defaultValue() else this
