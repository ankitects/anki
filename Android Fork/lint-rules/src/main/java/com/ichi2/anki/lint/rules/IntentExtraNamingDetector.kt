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

class IntentExtraNamingDetector :
    Detector(),
    SourceCodeScanner {
    override fun getApplicableMethodNames() =
        listOf(
            "putExtra",
            "hasExtra",
            "removeExtra",
            "getBooleanExtra",
            "getByteExtra",
            "getShortExtra",
            "getCharExtra",
            "getIntExtra",
            "getLongExtra",
            "getFloatExtra",
            "getDoubleExtra",
            "getStringExtra",
            "getCharSequenceExtra",
            "getBundleExtra",
            "getParcelableExtra",
            "getParcelableArrayExtra",
            "getParcelableArrayListExtra",
            "getSerializableExtra",
            "getIntegerArrayListExtra",
            "getStringArrayListExtra",
            "getCharSequenceArrayListExtra",
            "getBooleanArrayExtra",
            "getByteArrayExtra",
            "getShortArrayExtra",
            "getCharArrayExtra",
            "getIntArrayExtra",
            "getLongArrayExtra",
            "getFloatArrayExtra",
            "getDoubleArrayExtra",
            "getStringArrayExtra",
            "getCharSequenceArrayExtra",
            "getParcelableExtraCompat",
            "getSerializableExtraCompat",
        )

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        val keyIndex =
            when (method.containingClass?.qualifiedName) {
                "android.content.Intent" -> 0
                "androidx.core.content.IntentCompat",
                "com.ichi2.anki.common.utils.ext.IntentKt",
                "com.ichi2.anki.compat.CompatHelper.Companion",
                -> 1
                else -> return
            }
        val argument = node.getArgumentForParameter(keyIndex)?.skipParenthesizedExprDown() ?: return
        val field = argument.resolveToField() ?: return
        if (!field.hasModifierProperty(PsiModifier.STATIC) || !field.hasModifierProperty(PsiModifier.FINAL)) return
        if (field.name.startsWith("EXTRA_")) return
        if (ConstantEvaluator.evaluate(context, argument) !is String) return
        context.report(
            ISSUE,
            argument,
            context.getLocation(argument),
            "Intent extra key constants must start with EXTRA_: ${field.name}",
        )
    }

    companion object {
        val ISSUE =
            Issue.create(
                id = "IntentExtraNaming",
                briefDescription = "Intent extra key constants must start with EXTRA_",
                explanation =
                    """
                    Intent extra key constants should be named: EXTRA_<DESCRIPTION>.
                    The name should describe the value being passed. e.g. EXTRA_DECK_ID.
                    """.trimIndent(),
                category = Constants.ANKI_CODE_STYLE_CATEGORY,
                priority = Constants.ANKI_CODE_STYLE_PRIORITY,
                severity = Constants.ANKI_CODE_STYLE_SEVERITY,
                implementation = Implementation(IntentExtraNamingDetector::class.java, Scope.JAVA_FILE_SCOPE),
            )
    }
}
