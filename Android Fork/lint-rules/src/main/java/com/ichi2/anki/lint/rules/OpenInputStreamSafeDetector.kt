// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Nishtha Jain <jnishtha305@gmail.com>

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
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getParentOfType

/**
 * Detector that ensures ContentResolver.openInputStream() is not called directly.
 * Instead, developers should use the openInputStreamSafe() extension function
 * which includes path traversal protection.
 */

class OpenInputStreamSafeDetector :
    Detector(),
    SourceCodeScanner {
    companion object {
        private const val EXPLANATION = """
            Use openInputStreamSafe() instead of openInputStream() to prevent \
            path traversal vulnerabilities. The safe version normalizes paths and blocks \
            access to sensitive directories like /data.
        """

        val ISSUE =
            Issue.create(
                id = "UnsafeOpenInputStream",
                briefDescription = "Use openInputStreamSafe() instead of openInputStream()",
                explanation = EXPLANATION,
                category = Category.SECURITY,
                priority = 9,
                severity = Severity.ERROR,
                implementation =
                    Implementation(
                        OpenInputStreamSafeDetector::class.java,
                        Scope.JAVA_FILE_SCOPE,
                    ),
            )
    }

    override fun getApplicableMethodNames(): List<String> = listOf("openInputStream")

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        // Only warn on ContentResolver.openInputStream()
        if (!context.evaluator.isMemberInClass(method, "android.content.ContentResolver")) return
        if (node.enclosingMethodName == "openInputStreamSafe") return
        context.report(
            issue = ISSUE,
            location = context.getNameLocation(node),
            message = "Use openInputStreamSafe() instead of openInputStream()",
        )
    }
}

val UCallExpression.enclosingMethodName: String
    get() = getParentOfType(UMethod::class.java)!!.name
