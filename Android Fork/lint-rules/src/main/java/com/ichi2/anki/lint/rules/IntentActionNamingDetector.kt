// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.lint.rules

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.ichi2.anki.lint.utils.Constants
import com.ichi2.anki.lint.utils.ext.resolveToField
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULocalVariable
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.USwitchClauseExpression
import org.jetbrains.uast.USwitchExpression
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.skipParenthesizedExprDown
import org.jetbrains.uast.toUElementOfType
import org.jetbrains.uast.tryResolve

/**
 * Requires intent action names to start with `ACTION_`, such as `ACTION_UPDATE_WIDGET`.
 *
 * Checks action arguments in `Intent` and `IntentFilter` APIs, assignments to `Intent.action`,
 * equality comparisons, and `when` cases.
 */
class IntentActionNamingDetector :
    Detector(),
    SourceCodeScanner {
    // Kotlin property assignments can be visited both as assignments and synthetic setter calls.
    private val reportedActions = mutableSetOf<PsiElement>()

    override fun beforeCheckFile(context: Context) {
        reportedActions.clear()
    }

    override fun getApplicableConstructorTypes() = listOf(INTENT, INTENT_FILTER)

    /** Checks the action in constructors such as `Intent(ACTION)` and `IntentFilter(ACTION, mimeType)`. */
    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod,
    ) {
        // Copy constructors and explicit component intents do not have an action argument.
        val index = constructor.parameterList.parameters.indexOfFirst { it.type.canonicalText == "java.lang.String" }
        if (index < 0) return
        // Only the first String parameter is the action; a later String may be a MIME type.
        node.getArgumentForParameter(index)?.let { ensureConstantNameUsesActionPrefix(context, it) }
    }

    override fun getApplicableMethodNames() = listOf("setAction", "addAction", "create", "equals")

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        when (method.name) {
            "setAction" -> checkActionArgument(context, node, method, INTENT)
            "addAction", "create" -> checkActionArgument(context, node, method, INTENT_FILTER)
            "equals" -> checkStringEquals(context, node, method)
        }
    }

    /** Checks the first argument of an action API, ignoring methods with the same name in unrelated classes. */
    private fun checkActionArgument(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
        owner: String,
    ) {
        // ensures the call is for Intent[Filter]
        if (!context.evaluator.isMemberInClass(method, owner)) return
        node.getArgumentForParameter(0)?.let { ensureConstantNameUsesActionPrefix(context, it) }
    }

    /** Handles `intent.action.equals(CONSTANT)` and `CONSTANT.equals(intent.action)`. */
    private fun checkStringEquals(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        if (!context.evaluator.isMemberInClass(method, "java.lang.String")) return
        val receiver = node.receiver ?: return
        val other = node.getArgumentForParameter(0) ?: return
        checkComparison(context, receiver, other)
    }

    override fun getApplicableUastTypes() = listOf(UBinaryExpression::class.java, USwitchExpression::class.java)

    override fun createUastHandler(context: JavaContext) =
        object : UElementHandler() {
            override fun visitBinaryExpression(node: UBinaryExpression) {
                // intent.action = "str"
                if (node.operator == UastBinaryOperator.ASSIGN && isIntentAction(context, node.leftOperand)) {
                    ensureConstantNameUsesActionPrefix(context, node.rightOperand)
                }
                // intent.action == "str" and identity comparisons.
                if (node.operator in EQUALITY_OPERATORS) checkComparison(context, node.leftOperand, node.rightOperand)
            }

            override fun visitSwitchExpression(node: USwitchExpression) {
                // when (intent.action) { CONSTANT -> ... }
                val subject = node.expression ?: return
                if (!isIntentAction(context, subject)) return
                node.body.expressions.filterIsInstance<USwitchClauseExpression>().forEach { clause ->
                    clause.caseValues.forEach { ensureConstantNameUsesActionPrefix(context, it) }
                }
            }
        }

    /** Checks the constant for a prefix in `intent.action == CONST` or `CONST == intent.action`. */
    private fun checkComparison(
        context: JavaContext,
        left: UExpression,
        right: UExpression,
    ) {
        if (isIntentAction(context, left)) ensureConstantNameUsesActionPrefix(context, right)
        if (isIntentAction(context, right)) ensureConstantNameUsesActionPrefix(context, left)
    }

    /** Recognizes `intent.action` and `intent.getAction()`  */
    private fun isIntentAction(
        context: JavaContext,
        expression: UExpression,
    ): Boolean {
        val value = expression.skipParenthesizedExprDown()
        val reference = (value as? UQualifiedReferenceExpression)?.selector ?: value
        val resolved = reference.tryResolve()
        if (resolved is PsiMethod) {
            return resolved.name in listOf("getAction", "setAction") && context.evaluator.isMemberInClass(resolved, INTENT)
        }
        val initializer = getImmutableLocalInitializer(resolved) ?: return false
        return isIntentAction(context, initializer)
    }

    /** Returns the initializer of a local `val`; mutable variables and non-local properties are ignored. */
    private fun getImmutableLocalInitializer(element: PsiElement?): UExpression? {
        val variable = element?.unwrapped?.toUElementOfType<ULocalVariable>() ?: return null
        val property = variable.sourcePsi as? KtProperty ?: return null
        if (property.isVar) return null
        return variable.uastInitializer
    }

    /** Reports when the name of a constant does not start with `ACTION_` */
    private fun ensureConstantNameUsesActionPrefix(
        context: JavaContext,
        expression: UExpression,
    ) {
        val argument = expression.skipParenthesizedExprDown()
        val field = argument.resolveToField() ?: return
        if (!field.hasModifierProperty(PsiModifier.STATIC) || !field.hasModifierProperty(PsiModifier.FINAL)) return
        if (field.name.startsWith("ACTION_")) return
        if (ConstantEvaluator.evaluate(context, argument) !is String) return
        // Report `intent.action = CONSTANT` once, even if UAST also visits it as a setter call.
        if (!reportedActions.add(argument.sourcePsi ?: return)) return
        context.report(
            ISSUE,
            argument,
            context.getLocation(argument),
            "Intent action constants must start with ACTION_: ${field.name}",
        )
    }

    companion object {
        private const val INTENT = "android.content.Intent"
        private const val INTENT_FILTER = "android.content.IntentFilter"
        private val EQUALITY_OPERATORS =
            setOf(
                UastBinaryOperator.EQUALS,
                UastBinaryOperator.NOT_EQUALS,
                UastBinaryOperator.IDENTITY_EQUALS,
                UastBinaryOperator.IDENTITY_NOT_EQUALS,
            )

        val ISSUE =
            Issue.create(
                id = "IntentActionNaming",
                briefDescription = "Intent action constants must start with ACTION_",
                explanation =
                    """
                    Intent action constants should be named ACTION_<DESCRIPTION>, for example ACTION_UPDATE_WIDGET.
                    Use this prefix when sending, receiving or filtering an intent action.
                    """.trimIndent(),
                category = Constants.ANKI_CODE_STYLE_CATEGORY,
                priority = Constants.ANKI_CODE_STYLE_PRIORITY,
                severity = Constants.ANKI_CODE_STYLE_SEVERITY,
                implementation = Implementation(IntentActionNamingDetector::class.java, Scope.JAVA_FILE_SCOPE),
            )
    }
}
