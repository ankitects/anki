// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils.common

import org.junit.rules.ExternalResource
import java.util.Locale

/** Overrides the JVM default locale for a test and restores the previous locale afterwards. */
class LocaleRule(
    private val locale: Locale,
) : ExternalResource() {
    private lateinit var original: Locale
    private lateinit var originalDisplay: Locale
    private lateinit var originalFormat: Locale

    override fun before() {
        original = Locale.getDefault()
        originalDisplay = Locale.getDefault(Locale.Category.DISPLAY)
        originalFormat = Locale.getDefault(Locale.Category.FORMAT)
        Locale.setDefault(locale)
    }

    override fun after() {
        Locale.setDefault(original)
        Locale.setDefault(Locale.Category.DISPLAY, originalDisplay)
        Locale.setDefault(Locale.Category.FORMAT, originalFormat)
    }
}
