// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.lint.rules

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

/**
 * Requires `getAppWidgetManager` instead of `AppWidgetManager.getInstance()`.
 *
 * The Supernote A5 X unexpectedly returns `null`, and `getInstance()` doesn't return a nullable
 * value.
 */
class DirectAppWidgetManagerInstanceUsage :
    Detector(),
    SourceCodeScanner {
    override fun getApplicableMethodNames(): List<String> = listOf("getInstance")

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        // Tests may need to mock or inspect the platform service directly.
        if (context.isTestSource) return
        if (!context.evaluator.isMemberInClass(method, "android.appwidget.AppWidgetManager")) return

        context.report(
            ISSUE,
            node,
            context.getCallLocation(node, includeReceiver = true, includeArguments = true),
            "Use getAppWidgetManager(context) and handle its nullable result",
        )
    }

    companion object {
        val ISSUE: Issue =
            Issue.create(
                "DirectAppWidgetManagerInstanceUsage",
                "Use the nullable widget manager helper",
                "Use com.ichi2.widget.getAppWidgetManager(). AppWidgetManager.getInstance() returns null on devices such as the Supernote A5 X. ",
                Category.CORRECTNESS,
                6,
                Severity.ERROR,
                Implementation(DirectAppWidgetManagerInstanceUsage::class.java, Scope.JAVA_FILE_SCOPE),
            )
    }
}
