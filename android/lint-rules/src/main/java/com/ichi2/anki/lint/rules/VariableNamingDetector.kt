// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Nicola Dardanis <nicdard@gmail.com>

package com.ichi2.anki.lint.rules

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope.Companion.JAVA_FILE_SCOPE
import com.android.tools.lint.detector.api.TextFormat
import com.ichi2.anki.lint.utils.Constants
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UField
import org.jetbrains.uast.UVariable

class VariableNamingDetector :
    Detector(),
    Detector.UastScanner {
    override fun getApplicableUastTypes() = listOf(UVariable::class.java)

    private fun reportVariable(
        context: JavaContext,
        node: UVariable,
    ) {
        context.report(
            ISSUE,
            context.getLocation(node as UElement),
            ISSUE.getBriefDescription(TextFormat.TEXT),
        )
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            private val pattern = Regex("""^[ms][A-Z].*""")

            override fun visitVariable(node: UVariable) {
                if (node is UField) {
                    // Do not check for field names here, just looking for variables and assure that
                    // those are not written as fields, that would be confusing.
                    return
                }
                node.name?.let {
                    if (pattern.containsMatchIn(it)) {
                        reportVariable(context, node)
                    }
                }
            }
        }
    }

    companion object {
        private val IMPLEMENTATION = Implementation(VariableNamingDetector::class.java, JAVA_FILE_SCOPE)
        val ISSUE =
            Issue.create(
                id = "VariableNamingDetector",
                briefDescription = "Variable name should not use field prefixes.",
                explanation =
                    """
                    Variable name should not use any field prefix to make clear to who is reading which one is a field
                    and which one is a variable.
                    """.trimIndent(),
                category = Constants.ANKI_CODE_STYLE_CATEGORY,
                priority = Constants.ANKI_CODE_STYLE_PRIORITY,
                severity = Constants.ANKI_CODE_STYLE_SEVERITY,
                implementation = IMPLEMENTATION,
            )
    }
}
