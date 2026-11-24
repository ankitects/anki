// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.utils

import androidx.compose.ui.text.AnnotatedString

/** Returns this [AnnotatedString] unchanged, or wraps this sequence as plain text. */
fun CharSequence.toAnnotatedString(): AnnotatedString = if (this is AnnotatedString) this else AnnotatedString(toString())
