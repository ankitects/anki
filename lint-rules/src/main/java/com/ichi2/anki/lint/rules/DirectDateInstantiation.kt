// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2020 lukstbit <52494258+lukstbit@users.noreply.github.com>

@file:Suppress("UnstableApiUsage")

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

/**
 * This custom Lint rules will raise an error if a developer instantiates the [java.util.Date] class directly
 * instead of using a [java.util.Date] provided through the collection's getTime() method.
 */
class DirectDateInstantiation :
    Detector(),
    SourceCodeScanner {
    companion object {
        @VisibleForTesting
        const val ID = "DirectDateInstantiation"

        @VisibleForTesting
        const val DESCRIPTION = "Use the collection's getTime() method instead of directly instantiating Date"
        private const val EXPLANATION =
            "Creating Date instances directly means dates cannot be controlled during" +
                " testing, so it is not allowed. Use the collection's getTime() method instead"
        private val implementation = Implementation(DirectDateInstantiation::class.java, Scope.JAVA_FILE_SCOPE)
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

    override fun getApplicableConstructorTypes(): List<String> {
        val forbiddenConstructors: MutableList<String> = mutableListOf()
        forbiddenConstructors.add("java.util.Date")
        return forbiddenConstructors
    }

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod,
    ) {
        super.visitConstructor(context, node, constructor)
        val foundClasses = context.uastFile!!.classes
        // this checks for usage of new Date(ms) which we allow
        val argTypes = node.valueArguments
        if (argTypes.size == 1) {
            val onlyArgument = argTypes[0]
            if (onlyArgument.getExpressionType()!!.equalsToText("long")) {
                return
            }
        }
        if (!LintUtils.isAnAllowedClass(foundClasses, "Time")) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                DESCRIPTION,
            )
        }
    }
}
