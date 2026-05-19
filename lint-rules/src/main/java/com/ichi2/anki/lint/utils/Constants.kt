// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2020 lukstbit <52494258+lukstbit@users.noreply.github.com>

@file:Suppress("UnstableApiUsage")

package com.ichi2.anki.lint.utils

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Category.Companion.create
import com.android.tools.lint.detector.api.Severity

/**
 * Hold some constants applicable to all lint issues.
 */
object Constants {
    /**
     * A special [Category] which groups the Lint issues related to the usage of the new SystemTime class as a
     * sub category for [Category.CORRECTNESS].
     */
    val ANKI_TIME_CATEGORY: Category = create(Category.CORRECTNESS, "AnkiTime", 10)

    /**
     * The priority for the Lint issues used by all rules related to the restrictions introduced by SystemTime.
     */
    const val ANKI_TIME_PRIORITY = 10

    /**
     * The severity for the Lint issues used by all rules related to the restrictions introduced by SystemTime.
     */
    val ANKI_TIME_SEVERITY = Severity.FATAL

    /**
     * The priority for the Lint issues used by all rules related to the restrictions introduced by SystemTime.
     */
    const val ANKI_CROWDIN_PRIORITY = 10

    /**
     * A special [Category] which groups the Lint issues related to the usage of CrowdIn as a
     * sub category for [Category.CORRECTNESS].
     */
    val ANKI_CROWDIN_CATEGORY: Category =
        create(Category.CORRECTNESS, "AnkiCrowdIn", ANKI_CROWDIN_PRIORITY)

    /**
     * The severity for the Lint issues used by all rules related to CrowdIn restrictions.
     */
    val ANKI_CROWDIN_SEVERITY = Severity.FATAL

    /**
     * A special [Category] which groups the Lint issues related to Code Style as a
     * sub category for [Category.COMPLIANCE].
     */
    val ANKI_CODE_STYLE_CATEGORY: Category = create(Category.COMPLIANCE, "CodeStyle", 10)

    /**
     * The priority for the Lint issues used by rules related to Code Style.
     */
    const val ANKI_CODE_STYLE_PRIORITY = 10

    /**
     * The severity for the Lint issues used by rules related to Code Style.
     */
    val ANKI_CODE_STYLE_SEVERITY = Severity.FATAL

    /**
     * A special [Category] which groups the Lint issues related to XML as a
     * sub category for [Category.CORRECTNESS].
     */
    val ANKI_XML_CATEGORY: Category = create(Category.CORRECTNESS, "XML", 10)

    /**
     * The priority for the Lint issues used by rules related to XML.
     */
    const val ANKI_XML_PRIORITY = 10

    /**
     * The severity for the Lint issues used by rules related to XML.
     */
    val ANKI_XML_SEVERITY = Severity.FATAL
}
