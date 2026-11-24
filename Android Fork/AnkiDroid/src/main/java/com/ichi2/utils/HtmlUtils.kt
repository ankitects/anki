// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.utils

import androidx.core.text.HtmlCompat

/** Removes HTML tags from a string */
fun stripHtml(html: String): String = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
