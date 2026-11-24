// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.lint.rules

import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.ichi2.anki.lint.utils.Constants
import com.ichi2.anki.lint.utils.ext.resolveToField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.skipParenthesizedExprDown

/** Checks constant request keys, leaving runtime-generated keys and forwarded parameters alone. */
class FragmentRequestKeyNamingDetector :
    Detector(),
    SourceCodeScanner {
    override fun getApplicableMethodNames() =
        listOf("setFragmentResult", "setFragmentResultListener", "clearFragmentResult", "clearFragmentResultListener")

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        val owner = method.containingClass?.qualifiedName
        if (owner != "androidx.fragment.app.FragmentManager" &&
            owner != "androidx.fragment.app.FragmentKt" &&
            owner != "com.ichi2.anki.utils.ext.FragmentResultKt"
        ) {
            return
        }
        // Extension methods have a receiver parameter; named arguments may also be reordered.
        val index = method.parameterList.parameters.indexOfFirst { it.name == "requestKey" }
        if (index < 0) return
        val argument = node.getArgumentForParameter(index)?.skipParenthesizedExprDown() ?: return
        val field = argument.resolveToField() ?: return
        if (!field.hasModifierProperty(PsiModifier.STATIC) || !field.hasModifierProperty(PsiModifier.FINAL)) return
        if (field.name.startsWith("REQUEST_")) return
        if (ConstantEvaluator.evaluate(context, argument) !is String) return
        context.report(
            ISSUE,
            argument,
            context.getLocation(argument),
            "Fragment Result API request key constants must start with REQUEST_: ${field.name}",
        )
    }

    companion object {
        val ISSUE =
            Issue.create(
                id = "FragmentRequestKeyNaming",
                briefDescription = "Fragment request key constants must start with REQUEST_",
                explanation =
                    """
                    Fragment Result API request key constants should be named: REQUEST_<DESCRIPTION>.
                    The name should be an action to represent the request. e.g. REQUEST_SELECT_TAGS.
                    """.trimIndent(),
                category = Constants.ANKI_CODE_STYLE_CATEGORY,
                priority = Constants.ANKI_CODE_STYLE_PRIORITY,
                severity = Constants.ANKI_CODE_STYLE_SEVERITY,
                implementation = Implementation(FragmentRequestKeyNamingDetector::class.java, Scope.JAVA_FILE_SCOPE),
            )
    }
}
