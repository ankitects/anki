// SPDX-License-Identifier: GPL-3.0-or-later

@file:Suppress("UnstableApiUsage")

package com.ichi2.anki.lint.rules

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope.Companion.ALL_RESOURCES_SCOPE
import com.android.tools.lint.detector.api.XmlContext
import com.ichi2.anki.lint.utils.Aapt2Util
import com.ichi2.anki.lint.utils.Aapt2Util.FormatData
import com.ichi2.anki.lint.utils.Constants
import com.ichi2.anki.lint.utils.StringFormatDetector
import com.ichi2.anki.lint.utils.childrenSequence
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Fix for "Multiple substitutions specified in non-positional format"
 * [Issue 10347](https://github.com/ankidroid/Anki-Android/issues/10347)
 * [Issue 11072: Plurals](https://github.com/ankidroid/Anki-Android/issues/11072)
 */
class NonPositionalFormatSubstitutions : ResourceXmlDetector() {
    companion object {
        private val IMPLEMENTATION_XML = Implementation(NonPositionalFormatSubstitutions::class.java, ALL_RESOURCES_SCOPE)

        /**
         * Whether there are any duplicate strings, including capitalization adjustments.
         */
        val ISSUE =
            Issue.create(
                "NonPositionalFormatSubstitutions",
                "Multiple substitutions specified in non-positional format",
                "An XML string contains ambiguous format parameters " +
                    "for example: %s %s. These should be positional (%1\$s %2\$s) to allow" +
                    "translators to select the ordering.",
                Constants.ANKI_CROWDIN_CATEGORY,
                Constants.ANKI_CROWDIN_PRIORITY,
                Constants.ANKI_CROWDIN_SEVERITY,
                IMPLEMENTATION_XML,
            )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean = folderType == ResourceFolderType.VALUES

    override fun getApplicableElements() = listOf(SdkConstants.TAG_STRING, SdkConstants.TAG_PLURALS)

    override fun visitElement(
        context: XmlContext,
        element: Element,
    ) {
        val elementsToCheck =
            when (element.tagName) {
                SdkConstants.TAG_PLURALS ->
                    element
                        .childrenSequence()
                        // skip if the item was not a plural (style, etc...)
                        .filter { it.nodeName == SdkConstants.TAG_ITEM }
                        .filter { it.nodeType == Node.ELEMENT_NODE }
                        .map { it as Element }
                        .toList()
                SdkConstants.TAG_STRING -> listOf(element)
                else -> throw IllegalStateException("Unsupported tag: ${element.tagName}")
            }

        val validFormatData =
            elementsToCheck
                .mapNotNull { getStringFromElement(it) }
                .filter { it.isNotEmpty() }
                // Filter to only string format data
                .mapNotNull {
                    Aapt2Util.verifyJavaStringFormat(it).let { formatData ->
                        when (formatData) {
                            is FormatData.DateFormatData -> null
                            is FormatData.StringFormatData -> formatData
                        }
                    }
                }

        validFormatData.filter { it.argCount > 0 }.also { formatData ->
            // for plurals: report if some have positional args, but not others.
            // This may not trigger the second check, see unit tests
            if (formatData.any { !it.hasNonPositionalArguments } && !formatData.all { !it.hasNonPositionalArguments }) {
                reportPositionalArgumentMismatch(context, element)
            }
        }

        // report the invalid nodes
        for (unused in validFormatData.filter { it.isInvalid }) {
            reportInvalidPositionalArguments(context, element)
        }
    }

    private fun getStringFromElement(element: Element): String? {
        // Check both the translated text and the "values" directory.
        val childNodes = element.childNodes
        if (childNodes.length <= 0) return null

        if (childNodes.length == 1) {
            val child = childNodes.item(0)
            return if (child.nodeType != Node.TEXT_NODE && child.nodeType != Node.CDATA_SECTION_NODE) {
                null
            } else {
                StringFormatDetector.stripQuotes(child.nodeValue)
            }
        }

        val sb = StringBuilder()
        StringFormatDetector.addText(sb, element)
        return sb.toString()
    }

    private fun reportPositionalArgumentMismatch(
        context: XmlContext,
        element: Element,
    ) {
        val location = context.createLocationHandle(element).resolve()

        // For clarity, the unescaped string is: "%s to %1$s"
        context.report(ISSUE, location, "Some, but not all plurals have positional arguments. Convert \"%s\" to \"%1\$s\"")
    }

    private fun reportInvalidPositionalArguments(
        context: XmlContext,
        element: Element,
    ) {
        val location = context.createLocationHandle(element).resolve()

        // For clarity, the unescaped string is: "%s to %1$s"
        context.report(ISSUE, location, "Multiple substitutions specified in non-positional format. Convert \"%s\" to \"%1\$s\"")
    }
}
