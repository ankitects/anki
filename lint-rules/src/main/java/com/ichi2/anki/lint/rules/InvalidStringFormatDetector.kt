// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 Divyansh Dwivedi <justdvnsh2208@gmail.com>

package com.ichi2.anki.lint.rules

import com.android.SdkConstants.TAG_PLURALS
import com.android.SdkConstants.TAG_STRING
import com.android.SdkConstants.TAG_STRING_ARRAY
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope.Companion.ALL_RESOURCES_SCOPE
import com.android.tools.lint.detector.api.XmlContext
import com.android.utils.forEach
import com.ichi2.anki.lint.utils.Constants.ANKI_XML_CATEGORY
import com.ichi2.anki.lint.utils.Constants.ANKI_XML_PRIORITY
import com.ichi2.anki.lint.utils.Constants.ANKI_XML_SEVERITY
import com.ichi2.anki.lint.utils.StringFormatDetector
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.util.EnumSet
import java.util.regex.Pattern

/**
 * Fix for 2 issues
 * 1. "Linting Error - String format should be valid."
 * [https://github.com/ankidroid/Anki-Android/issues/10604](https://github.com/ankidroid/Anki-Android/issues/10604)
 * 2."Check formatters like %1$D"
 * https://github.com/ankidroid/Anki-Android/issues/14079
 */
class InvalidStringFormatDetector : ResourceXmlDetector() {
    companion object {
        private val IMPLEMENTATION_XML =
            Implementation(InvalidStringFormatDetector::class.java, ALL_RESOURCES_SCOPE)

        /**
         * Whether the string or plural resource that is being used has all the translations
         * **/
        val ISSUE =
            Issue.create(
                "InvalidStringFormat",
                "The String format is invalid",
                "The String format used is invalid, Make sure to use a valid string format",
                ANKI_XML_CATEGORY,
                ANKI_XML_PRIORITY,
                ANKI_XML_SEVERITY,
                IMPLEMENTATION_XML,
            )

        private val INVALID_FORMAT_PATTERN = Pattern.compile("[^%]+%").toRegex()

        /**
         * (?<!%)% => should match %, but does not match if there's another % immediately to the left
         * [^%a-zA-Z]* => should match any characters (zero or more) that are not %, alphabetical or whitespace
         * [DFNOGC] => should match any of the capital characters that cause the errors
         * .* => can match any non whitespace characters (zero or more) following the format specifier
         */
        private val INVALID_CAPITALIZATION = Pattern.compile("(?<!%)%[^%a-zA-Z]*[DFNOGC].*").toRegex()
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean = EnumSet.of(ResourceFolderType.VALUES).contains(folderType)

    override fun getApplicableElements() = listOf(TAG_STRING, TAG_PLURALS)

    override fun visitElement(
        context: XmlContext,
        element: Element,
    ) {
        val childNodes = element.childNodes
        if (childNodes.length <= 0) return

        element.childNodes
            .forEach { child ->
                val isStringResource =
                    (child.nodeType == Node.TEXT_NODE || child.nodeType == Node.CDATA_SECTION_NODE) &&
                        TAG_STRING == element.localName
                val isStringArrayOrPlurals =
                    child.nodeType == Node.ELEMENT_NODE &&
                        (
                            TAG_STRING_ARRAY == element.localName ||
                                TAG_PLURALS == element.localName
                        )

                if (isStringResource) {
                    checkText(context, element, child.nodeValue)
                } else if (isStringArrayOrPlurals) {
                    val sb = StringBuilder()
                    StringFormatDetector.addText(sb, element)
                    if (sb.isNotEmpty()) {
                        checkText(context, element, sb.toString())
                    }
                }
            }
    }

    private fun checkText(
        context: XmlContext,
        element: Element,
        text: String,
    ) {
        text.split(" ").forEach {
            if (it.matches(INVALID_FORMAT_PATTERN) && it != "XXX%") {
                val location = context.createLocationHandle(element).resolve()
                context.report(
                    ISSUE,
                    location,
                    "You have specified the string in wrong format" +
                        "Please check that '%' sign been applied only to valid parameters. " +
                        "Your string might be having a regular word with '%' sign after it. " +
                        "eg: 'I have completed% %s cards.' ",
                )
            }

            if (it.matches(INVALID_CAPITALIZATION)) {
                val location = context.createLocationHandle(element).resolve()
                context.report(
                    ISSUE,
                    location,
                    "Formatted string should not use capital letter. " +
                        "eg: %D, %1D, %9D, %-9D, %1\$D, " +
                        "%F, %1F, %9F, %-9F, %1\$F, " +
                        "%N, %O, %G, %C " +
                        "should all be in lowercase.",
                )
            }
        }
    }
}
