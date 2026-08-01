// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 lukstbit <52494258+lukstbit@users.noreply.github.com>

package com.ichi2.anki.lint.rules

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.google.common.annotations.VisibleForTesting
import org.w3c.dom.Document

/**
 * Lint rule to enforce that layouts used in the app follow our conventions related to names, ex:
 *  layout used by a Fragment => layout name has "fragment_" prefix
 */
class LayoutPrefixDetector : LayoutDetector() {
    override fun visitDocument(
        context: XmlContext,
        document: Document,
    ) {
        val layoutFileName = context.file.name

        if (!ENFORCED_PREFIXES.any { prefix -> layoutFileName.startsWith(prefix) }) {
            context.report(
                ISSUE,
                context.getNameLocation(document),
                "Layout doesn't follow naming convention: ${context.file.name} " +
                    "See https://github.com/ankidroid/Anki-Android/wiki/Code-style#files-in-the-resourceslayout-folders-should-use-the-following-prefixes",
            )
        }
    }

    companion object {
        @VisibleForTesting
        const val ID = "LayoutPrefixDetector"

        @VisibleForTesting
        const val DESCRIPTION = "Layout filename doesn't follow naming conventions"
        private const val EXPLANATION =
            "Layouts filenames need to be prefixed based on where they are used, " +
                "see https://github.com/ankidroid/Anki-Android/wiki/Code-style#files-in-the-resourceslayout-folders-should-use-the-following-prefixes"
        private val IMPLEMENTATION =
            Implementation(
                LayoutPrefixDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE,
            )
        val ISSUE: Issue =
            Issue.create(
                ID,
                DESCRIPTION,
                EXPLANATION,
                Category.CORRECTNESS,
                6,
                Severity.ERROR,
                IMPLEMENTATION,
            )

        val ENFORCED_PREFIXES =
            listOf(
                "activity_",
                "fragment_",
                "dialog_",
                "view_",
                // prefix for layouts that will be included in other layouts
                "include_",
                // layouts used by items in an adapter => item_locale.xml
                "item_",
                // layouts used by widgets
                "widget_",
                // layouts used by preferences
                "preference_",
                // layouts used by Anki HTML pages
                "page_",
                // layouts used inside PopupWindow
                "popup_",
            )
    }
}
