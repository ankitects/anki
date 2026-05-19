// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.lint.utils.ext

import com.android.tools.lint.detector.api.XmlContext

private val rtlLanguages =
    listOf("ar", "ckb", "fa", "heb", "iw", "ur")
        .map { "values-$it" }
        .toHashSet()

fun XmlContext.isRightToLeftLanguage(): Boolean = rtlLanguages.contains(file.parentFile.name)
