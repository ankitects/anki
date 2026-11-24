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

class PreferenceKeyNamingDetector :
    Detector(),
    SourceCodeScanner {
    override fun getApplicableMethodNames() =
        listOf(
            "contains",
            "getBoolean",
            "getFloat",
            "getInt",
            "getLong",
            "getString",
            "getStringSet",
            "putBoolean",
            "putFloat",
            "putInt",
            "putLong",
            "putString",
            "putStringSet",
            "remove",
            "get",
            "getOrSetString",
            "getOrSetLong",
            "booleanPref",
        )

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        val keyIndex =
            when (method.containingClass?.qualifiedName) {
                "android.content.SharedPreferences",
                "android.content.SharedPreferences.Editor",
                "com.ichi2.anki.settings.PrefsRepository",
                -> 0
                "com.ichi2.anki.preferences.PreferenceUtilsKt",
                "com.ichi2.preferences.PreferenceExtensionsKt",
                -> 1
                else -> return
            }
        val argument = node.getArgumentForParameter(keyIndex)?.skipParenthesizedExprDown() ?: return
        val field = argument.resolveToField() ?: return
        if (!field.hasModifierProperty(PsiModifier.STATIC) || !field.hasModifierProperty(PsiModifier.FINAL)) return
        if (field.name.startsWith("PREF_")) return
        if (ConstantEvaluator.evaluate(context, argument) !is String) return
        context.report(
            ISSUE,
            argument,
            context.getLocation(argument),
            "Preference key constants must start with PREF_: ${field.name}",
        )
    }

    companion object {
        val ISSUE =
            Issue.create(
                id = "PreferenceKeyNaming",
                briefDescription = "Preference key constants must start with PREF_",
                explanation =
                    """
                    SharedPreferences key constants should be named PREF_<DESCRIPTION>,
                    for example PREF_LAST_DECK_ID.
                    """.trimIndent(),
                category = Constants.ANKI_CODE_STYLE_CATEGORY,
                priority = Constants.ANKI_CODE_STYLE_PRIORITY,
                severity = Constants.ANKI_CODE_STYLE_SEVERITY,
                implementation = Implementation(PreferenceKeyNamingDetector::class.java, Scope.JAVA_FILE_SCOPE),
            )
    }
}
