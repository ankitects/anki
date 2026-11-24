// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.lint.rules

import com.android.tools.lint.checks.DataFlowAnalyzer
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.isReturningContext
import com.ichi2.anki.lint.utils.Constants
import com.ichi2.anki.lint.utils.ext.resolveToField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.skipParenthesizedExprDown

/**
 * Requires `RESULT_` names for constant payload keys in Fragment Result API bundles.
 *
 * @see FragmentRequestKeyNamingDetector for `REQUEST_`
 */
class FragmentResultKeyNamingDetector :
    Detector(),
    SourceCodeScanner {
    /** Starts tracking explicit `Bundle(...)` constructions as potential result payloads. */
    override fun getApplicableConstructorTypes() = listOf("android.os.Bundle")

    /**
     * Checks a constructed bundle only if it flows into `setFragmentResult` in this method.
     *
     * ```kotlin
     * val result = Bundle().apply { putLong(SELECTED_DECK, 1L) }
     * parentFragmentManager.setFragmentResult(REQUEST_SELECT_DECK, result)
     * ```
     */
    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod,
    ) {
        val scope = node.getParentOfType<UMethod>() ?: return
        checkBundle(context, scope, node, isResult = false)
    }

    /** Selects listener registrations whose callback receives a result bundle. */
    override fun getApplicableMethodNames() = listOf("setFragmentResultListener")

    /**
     * Tracks the second parameter of an inline result listener.
     *
     * ```kotlin
     * setFragmentResultListener(REQUEST_SELECT_DECK) { _, result ->
     *     val deckId = result.getLong(SELECTED_DECK)
     * }
     * ```
     */
    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        if (!method.isFragmentResultApi()) return
        val listenerIndex =
            method.parameterList.parameters.indexOfLast {
                it.type.canonicalText.contains("FragmentResultListener") || it.name == "listener"
            }
        if (listenerIndex < 0) return
        val listener = node.getArgumentForParameter(listenerIndex)?.skipParenthesizedExprDown() as? ULambdaExpression ?: return
        val bundle = listener.valueParameters.getOrNull(1) ?: return
        checkBundle(context, listener.body, bundle, isResult = true)
    }

    /**
     * Collects key accesses on [bundle] and its aliases, reporting them only for result payloads.
     * Tracking stays within [scope]; it does not follow helper bodies or unrelated argument/saved-state bundles.
     */
    private fun checkBundle(
        context: JavaContext,
        scope: UElement,
        bundle: UElement,
        isResult: Boolean,
    ) {
        val keys = mutableListOf<UExpression>()
        var sentAsResult = isResult
        scope.accept(
            object : DataFlowAnalyzer(listOf(bundle)) {
                /** Follows scope functions such as `apply`, but treats `getBundle()` as a separate bundle. */
                override fun returnsSelf(call: UCallExpression) = isReturningContext(call)

                /** Collects string key arguments in bundle calls such as `result.putString(KEY, value)`. */
                override fun receiver(call: UCallExpression) {
                    val method = call.resolve() ?: return
                    if (!context.evaluator.isMemberInClass(method, "android.os.Bundle") &&
                        !context.evaluator.isMemberInClass(method, "android.os.BaseBundle")
                    ) {
                        return
                    }
                    if (method.parameterList.parameters
                            .firstOrNull()
                            ?.type
                            ?.canonicalText != "java.lang.String"
                    ) {
                        return
                    }
                    call.getArgumentForParameter(0)?.let(keys::add)
                }

                /** Tracks helper keys such as `BundleCompat.getParcelable(result, KEY, type)` and result sends. */
                override fun argument(
                    call: UCallExpression,
                    reference: UElement,
                ) {
                    val method = call.resolve() ?: return
                    call.bundleHelperKey(method, reference)?.let(keys::add)
                    if (method.name != "setFragmentResult" || !method.isFragmentResultApi()) return
                    val index = method.parameterList.parameters.indexOfFirst { it.type.canonicalText == "android.os.Bundle" }
                    if (index >= 0 && call.getArgumentForParameter(index) == reference) sentAsResult = true
                }
            },
        )
        if (sentAsResult) keys.forEach { checkKey(context, it) }
    }

    /**
     * Extracts `KEY` from helper calls such as `BundleCompat.getParcelable(result, KEY, type)`.
     * Only matches when the helper's bundle argument is the tracked [bundleReference].
     */
    private fun UCallExpression.bundleHelperKey(
        method: PsiMethod,
        bundleReference: UElement,
    ): UExpression? {
        if (method.containingClass?.qualifiedName !in BUNDLE_HELPERS) return null

        val parameters = method.parameterList.parameters
        val bundleIndex = parameters.indexOfFirst { it.type.canonicalText == "android.os.Bundle" }
        if (bundleIndex < 0 || getArgumentForParameter(bundleIndex) != bundleReference) return null

        val keyIndex = parameters.indexOfFirst { it.type.canonicalText == "java.lang.String" }
        if (keyIndex < 0) return null
        return getArgumentForParameter(keyIndex)
    }

    /** Reports non-`RESULT_` fields used as Bundle result keys. */
    private fun checkKey(
        context: JavaContext,
        expression: UExpression,
    ) {
        val argument = expression.skipParenthesizedExprDown()
        val field = argument.resolveToField() ?: return
        if (!field.hasModifierProperty(PsiModifier.STATIC) || !field.hasModifierProperty(PsiModifier.FINAL)) return
        if (field.name.startsWith("RESULT_")) return
        if (ConstantEvaluator.evaluate(context, argument) !is String) return
        context.report(
            ISSUE,
            argument,
            context.getLocation(argument),
            "Fragment Result API bundle key constants must start with RESULT_: ${field.name}",
        )
    }

    /** Restricts matching to AndroidX Fragment APIs and AnkiDroid's FragmentActivity extensions. */
    private fun PsiMethod.isFragmentResultApi() =
        containingClass?.qualifiedName in
            setOf(
                "androidx.fragment.app.FragmentManager",
                "androidx.fragment.app.FragmentKt",
                "com.ichi2.anki.utils.ext.FragmentResultKt",
            )

    companion object {
        /** Helpers whose bundle and string arguments represent payload access, including Kotlin extensions. */
        private val BUNDLE_HELPERS =
            setOf(
                "androidx.core.os.BundleCompat",
                "com.ichi2.anki.utils.ext.BundleUtilsKt",
                "com.ichi2.anki.compat.CompatHelper.Companion",
                "com.ichi2.anki.compat.CompatHelperKt",
            )

        /** Lint diagnostic for constant payload keys that do not follow the `RESULT_` naming convention. */
        val ISSUE =
            Issue.create(
                id = "FragmentResultKeyNaming",
                briefDescription = "Fragment result bundle keys must start with RESULT_",
                explanation =
                    """
                    Fragment Result API bundle result keys should be named RESULT_<DESCRIPTION>,
                    for example RESULT_SELECTED_DECK.
                    """.trimIndent(),
                category = Constants.ANKI_CODE_STYLE_CATEGORY,
                priority = Constants.ANKI_CODE_STYLE_PRIORITY,
                severity = Constants.ANKI_CODE_STYLE_SEVERITY,
                implementation = Implementation(FragmentResultKeyNamingDetector::class.java, Scope.JAVA_FILE_SCOPE),
            )
    }
}
