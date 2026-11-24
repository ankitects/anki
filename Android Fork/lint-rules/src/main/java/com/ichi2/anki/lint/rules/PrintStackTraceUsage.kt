// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.lint.rules

import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.google.common.annotations.VisibleForTesting
import com.ichi2.anki.lint.utils.Constants
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class PrintStackTraceUsage :
    Detector(),
    SourceCodeScanner {
    companion object {
        @VisibleForTesting
        const val ID = "PrintStackTraceUsage"

        @VisibleForTesting
        val DESCRIPTION = "Use Timber to log exceptions (typically Timber.w if non-fatal)"
        private const val EXPLANATION =
            "AnkiDroid exclusively uses Timber for logging exceptions. " +
                "See: https://github.com/ankidroid/Anki-Android/wiki/Code-style#logging"
        private val implementation = Implementation(PrintStackTraceUsage::class.java, Scope.JAVA_FILE_SCOPE)
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

    override fun getApplicableMethodNames() = mutableListOf("printStackTrace")

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        super.visitMethodCall(context, node, method)
        val evaluator = context.evaluator

        // if we have arguments, we're not writing to stdout, so it's an OK call
        val hasArguments = node.valueArgumentCount != 0
        if (hasArguments || !evaluator.isMemberInSubClassOf(method, "java.lang.Throwable", false)) {
            return
        }
        val fix =
            LintFix
                .create()
                .replace()
                .select(node.asSourceString())
                .with("Timber.w(" + node.receiver!!.asSourceString() + ")")
                .autoFix()
                .build()
        context.report(
            ISSUE,
            context.getCallLocation(node, includeReceiver = true, includeArguments = true),
            DESCRIPTION,
            fix,
        )
    }
}
