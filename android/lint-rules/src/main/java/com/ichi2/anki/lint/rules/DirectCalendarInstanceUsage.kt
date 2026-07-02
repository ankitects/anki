// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2020 lukstbit <52494258+lukstbit@users.noreply.github.com>

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
import java.util.Calendar

/**
 * This custom Lint rules will raise an error if a developer uses the [Calendar.getInstance] method instead
 * of using the [Calendar] provided by the collection's getTime() method.
 */
class DirectCalendarInstanceUsage :
    Detector(),
    SourceCodeScanner {
    companion object {
        @VisibleForTesting
        const val ID = "DirectCalendarInstanceUsage"

        @VisibleForTesting
        const val DESCRIPTION = "Use the collection's getTime() method instead of directly creating Calendar instances"
        private const val EXPLANATION =
            "Manually creating Calendar instances means time cannot be controlled " +
                "during testing. Calendar instances must be obtained through the collection's getTime() method"
        private val implementation = Implementation(DirectCalendarInstanceUsage::class.java, Scope.JAVA_FILE_SCOPE)
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

    override fun getApplicableMethodNames(): List<String> = mutableListOf("getInstance")

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        super.visitMethodCall(context, node, method)
        val evaluator = context.evaluator
        val foundClasses = context.uastFile!!.classes
        if (!LintUtils.isAnAllowedClass(foundClasses, "Time") && evaluator.isMemberInClass(method, "java.util.Calendar")) {
            context.report(
                ISSUE,
                context.getCallLocation(node, includeReceiver = true, includeArguments = true),
                DESCRIPTION,
            )
        }
    }
}
