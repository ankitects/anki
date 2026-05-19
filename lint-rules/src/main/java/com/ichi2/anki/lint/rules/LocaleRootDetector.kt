// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 Spencer Poisseroux <me@spoisseroux.com>

@file:Suppress("UnstableApiUsage")

package com.ichi2.anki.lint.rules

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.google.common.annotations.VisibleForTesting
import com.ichi2.anki.lint.utils.Constants
import com.intellij.psi.PsiField
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression

/**
 * Flags any usage of Locale.ROOT in code, requiring explicit suppression for valid cases.
 */
class LocaleRootDetector :
    Detector(),
    SourceCodeScanner {
    companion object {
        @VisibleForTesting
        const val ID = "LocaleRootUsage"

        @VisibleForTesting
        const val DESCRIPTION = "Avoid using Locale.ROOT for user-facing text without explicit suppression"

        private const val EXPLANATION =
            "Using Locale.ROOT for user-facing text formatting is discouraged. " +
                "Please use Locale.getDefault() or the device's locale for proper localization, or suppress."

        private val implementation =
            Implementation(
                LocaleRootDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
            )

        val ISSUE: Issue =
            Issue.create(
                ID,
                DESCRIPTION,
                EXPLANATION,
                Constants.ANKI_TIME_CATEGORY,
                Constants.ANKI_TIME_PRIORITY,
                Constants.ANKI_TIME_SEVERITY,
                implementation,
            )
    }

    override fun getApplicableUastTypes() = listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                node.valueArguments.forEach { arg ->
                    if (isLocaleRootUsage(arg)) {
                        context.report(
                            ISSUE,
                            arg,
                            context.getLocation(arg),
                            DESCRIPTION,
                            createFix(),
                        )
                    }
                }
            }
        }

    private fun isLocaleRootUsage(arg: UExpression?): Boolean {
        val uRef = arg as? UQualifiedReferenceExpression ?: return false
        val receiver = uRef.receiver
        val selector = uRef.selector as? UReferenceExpression ?: return false

        // Check if receiver is "Locale" and selector resolves to Locale.ROOT
        val receiverText = receiver.asSourceString()
        val resolvedField = selector.resolve() as? PsiField ?: return false

        return receiverText == "Locale" &&
            resolvedField.containingClass?.qualifiedName == "java.util.Locale" &&
            resolvedField.name == "ROOT"
    }

    private fun createFix(): LintFix =
        fix()
            .name("Replace with Locale.getDefault(), or suppress")
            .replace()
            .text("Locale.ROOT")
            .with("Locale.getDefault()")
            .build()
}
