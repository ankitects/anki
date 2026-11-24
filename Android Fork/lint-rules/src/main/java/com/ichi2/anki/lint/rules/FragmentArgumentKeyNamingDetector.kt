// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.lint.rules

import com.android.tools.lint.checks.DataFlowAnalyzer
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.isReturningContext
import com.ichi2.anki.lint.utils.Constants
import com.ichi2.anki.lint.utils.ext.resolveToField
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.skipParenthesizedExprDown

/**
 * Requires `ARG_` names for constant keys in a fragment's arguments.
 *
 * ```kotlin
 * fun newInstance(deckId: DeckId) =
 *     DeckFragment().apply { arguments = Bundle().apply { putLong(ARG_DECK_ID, deckId) } }
 *
 * val deckId by lazy { requireArguments().getLong(ARG_DECK_ID) }
 * ```
 *
 * Keys are checked where the arguments are written and where they are read, so a key that only
 * a ViewModel reads, through its `SavedStateHandle`, is still checked where it is written.
 * `EXTRA_` is also allowed, for intent extras that an activity passes on as the fragment's arguments.
 *
 * Literal keys, and keys read or written inside other helper methods, are not checked.
 *
 * @see IntentExtraNamingDetector for `EXTRA_`
 */
class FragmentArgumentKeyNamingDetector :
    Detector(),
    SourceCodeScanner {
    /** UAST visits the `x` in `arguments = x` twice, so keys are reported once per file. */
    private val reportedKeys = mutableSetOf<PsiElement>()

    override fun beforeCheckFile(context: Context) {
        reportedKeys.clear()
    }

    override fun getApplicableConstructorTypes() = listOf(BUNDLE)

    /** Writes: checks a new `Bundle` if it becomes a fragment's arguments in the same method. */
    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod,
    ) {
        val scope = node.getParentOfType<UMethod>() ?: return
        checkBundle(context, scope, node, isArguments = false)
    }

    override fun getApplicableMethodNames() = listOf("requireArguments", "getArguments")

    /**
     * Reads: checks the bundle from `requireArguments()`, or `arguments` in Kotlin.
     * A property initializer such as `by lazy { requireArguments() }` has no method, so its class is searched.
     */
    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        if (!context.evaluator.isMemberInClass(method, FRAGMENT)) return
        val scope = node.getParentOfType<UMethod>() ?: node.getParentOfType<UClass>() ?: return
        checkBundle(context, scope, node, isArguments = true)
    }

    /** Collects the keys used on [bundle] and its aliases in [scope], and checks them if they are arguments. */
    private fun checkBundle(
        context: JavaContext,
        scope: UElement,
        bundle: UElement,
        isArguments: Boolean,
    ) {
        val keys = mutableListOf<UExpression>()
        var passedAsArguments = false
        scope.accept(
            object : DataFlowAnalyzer(listOf(bundle)) {
                /** Follows scope functions such as `Bundle().apply { putLong(KEY, value) }`. */
                override fun returnsSelf(call: UCallExpression) = isReturningContext(call)

                /** Collects keys in bundle calls such as `args.getLong(KEY)`. */
                override fun receiver(call: UCallExpression) {
                    val method = call.resolve() ?: return
                    if (!context.evaluator.isMemberInClass(method, BUNDLE) &&
                        !context.evaluator.isMemberInClass(method, BASE_BUNDLE)
                    ) {
                        return
                    }
                    call.keyArgument(method)?.let(keys::add)
                }

                /**
                 * Collects keys in helper calls such as `BundleCompat.getParcelable(args, KEY, type)`
                 * or `args.getLongOrNull(KEY)`.
                 *
                 * Also notes when the bundle is passed on as a fragment's arguments: `setArguments(args)`,
                 * which is how `fragment.arguments = args` is visited, or a parameter named `arguments`,
                 * as in `SingleFragmentActivity.getIntent(context, fragmentClass, arguments)`.
                 */
                override fun argument(
                    call: UCallExpression,
                    reference: UElement,
                ) {
                    val method = call.resolve() ?: return
                    val parameter = context.evaluator.computeArgumentMapping(call, method)[reference] ?: return
                    if (parameter.type.canonicalText != BUNDLE) return
                    val isSetArguments = method.name == "setArguments" && context.evaluator.isMemberInClass(method, FRAGMENT)
                    if (isSetArguments || parameter.name == "arguments") {
                        passedAsArguments = true
                    } else if (method.containingClass?.qualifiedName in BUNDLE_HELPERS) {
                        call.keyArgument(method)?.let(keys::add)
                    }
                }
            },
        )
        if (isArguments || passedAsArguments) keys.forEach { checkKey(context, it) }
    }

    /** Returns the key: the first `String` argument. */
    private fun UCallExpression.keyArgument(method: PsiMethod): UExpression? {
        val keyIndex = method.parameterList.parameters.indexOfFirst { it.type.canonicalText == "java.lang.String" }
        if (keyIndex < 0) return null
        return getArgumentForParameter(keyIndex)
    }

    /** Reports a constant key unless it starts with `ARG_` or `EXTRA_`. */
    private fun checkKey(
        context: JavaContext,
        expression: UExpression,
    ) {
        val argument = expression.skipParenthesizedExprDown()
        val field = argument.resolveToField() ?: return
        if (!field.hasModifierProperty(PsiModifier.STATIC) || !field.hasModifierProperty(PsiModifier.FINAL)) return
        if (field.name.startsWith("ARG_") || field.name.startsWith("EXTRA_")) return
        if (ConstantEvaluator.evaluate(context, argument) !is String) return
        if (!reportedKeys.add(argument.sourcePsi ?: return)) return
        context.report(
            ISSUE,
            argument,
            context.getLocation(argument),
            "Fragment argument key constants must start with ARG_: ${field.name}",
        )
    }

    companion object {
        private const val FRAGMENT = "androidx.fragment.app.Fragment"
        private const val BUNDLE = "android.os.Bundle"
        private const val BASE_BUNDLE = "android.os.BaseBundle"

        /** Helpers which take the bundle and a key, including Kotlin extensions. */
        private val BUNDLE_HELPERS =
            setOf(
                "androidx.core.os.BundleCompat",
                "com.ichi2.anki.utils.ext.BundleUtilsKt",
                "com.ichi2.anki.compat.CompatHelper.Companion",
                "com.ichi2.anki.compat.CompatHelperKt",
            )

        val ISSUE =
            Issue.create(
                id = "FragmentArgumentKeyNaming",
                briefDescription = "Fragment argument keys must start with ARG_",
                explanation =
                    """
                    Constants used as keys in a Fragment's arguments should be named
                    ARG_<DESCRIPTION>, for example ARG_DECK_ID.

                    The check looks at a new Bundle that becomes the arguments in the same
                    method, and at the bundle returned by requireArguments() or arguments,
                    including reads in property initializers such as `by lazy`.

                    Arguments forwarded from the hosting activity's intent extras may keep
                    their EXTRA_ name. Literals, parameters and other non-constant keys are
                    not checked.
                    """.trimIndent(),
                category = Constants.ANKI_CODE_STYLE_CATEGORY,
                priority = Constants.ANKI_CODE_STYLE_PRIORITY,
                severity = Constants.ANKI_CODE_STYLE_SEVERITY,
                implementation = Implementation(FragmentArgumentKeyNamingDetector::class.java, Scope.JAVA_FILE_SCOPE),
            )
    }
}
