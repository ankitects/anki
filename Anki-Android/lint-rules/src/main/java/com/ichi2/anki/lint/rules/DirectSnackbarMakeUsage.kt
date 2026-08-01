// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Mrudul Tora <mrudultora@gmail.com>

package com.ichi2.anki.lint.rules

import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.google.common.annotations.VisibleForTesting
import com.ichi2.anki.lint.utils.Constants
import com.ichi2.anki.lint.utils.LintUtils
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

/**
 * This custom Lint rule will raise an error if a developer uses
 * the com.google.android.material.snackbar.Snackbar.make method instead of
 * using the method provided in com.ichi2.anki.snackbar.SnackbarsKt.showSnackbar.
 */
class DirectSnackbarMakeUsage :
    Detector(),
    SourceCodeScanner {
    companion object {
        @VisibleForTesting
        const val ID = "DirectSnackbarMakeUsage"

        @VisibleForTesting
        const val DESCRIPTION = "Use SnackbarsKt.showSnackbar instead of Snackbar.make"
        private const val EXPLANATION =
            "To improve code consistency within the codebase " +
                "you should use SnackbarsKt.showSnackbar " +
                "in place of the library Snackbar.make(...).show()"
        private val implementation = Implementation(DirectSnackbarMakeUsage::class.java, Scope.JAVA_FILE_SCOPE)
        val ISSUE: Issue =
            Issue.create(
                ID,
                DESCRIPTION,
                EXPLANATION,
                Constants.ANKI_CODE_STYLE_CATEGORY,
                Constants.ANKI_CODE_STYLE_PRIORITY,
                Constants.ANKI_CODE_STYLE_SEVERITY,
                implementation,
            )
    }

    override fun getApplicableMethodNames() = mutableListOf("make")

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        super.visitMethodCall(context, node, method)
        val evaluator = context.evaluator
        val foundClasses = context.uastFile!!.classes
        if (!LintUtils.isAnAllowedClass(foundClasses, "SnackbarsKt") &&
            evaluator.isMemberInClass(method, "com.google.android.material.snackbar.Snackbar")
        ) {
            context.report(
                ISSUE,
                node,
                context.getCallLocation(node, includeReceiver = true, includeArguments = true),
                DESCRIPTION,
            )
        }
    }
}
