/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *  This file incorporates work covered by the following copyright and
 *  permission notice:
 *
 *     Copyright (C) 2018 The Android Open Source Project
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 *  https://android.googlesource.com/platform/tools/base/+/studio-master-dev/lint/libs/lint-checks/src/main/java/com/android/tools/lint/checks/StringCasingDetector.kt?autodive=0%2F
 */

package com.ichi2.anki.lint.rules

import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_TRANSLATABLE
import com.android.SdkConstants.TAG_STRING
import com.android.SdkConstants.VALUE_FALSE
import com.android.resources.ResourceFolderType
import com.android.tools.lint.checks.StringCasingDetector.StringDeclaration
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope.Companion.ALL_RESOURCES_SCOPE
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.formatList
import com.android.tools.lint.detector.api.getLocale
import com.android.utils.Pair
import com.ichi2.anki.lint.utils.Constants
import com.ichi2.anki.lint.utils.StringFormatDetector
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.util.Locale

class DuplicateCrowdInStrings : ResourceXmlDetector() {
    /*
     * Map of all locale,strings in lower case, to their raw elements to ensure that there are no
     * duplicate strings.
     */
    private val allStrings: HashMap<Pair<String, String>, MutableList<StringDeclaration>> =
        HashMap<Pair<String, String>, MutableList<StringDeclaration>>()

    override fun appliesTo(folderType: ResourceFolderType): Boolean = folderType == ResourceFolderType.VALUES

    override fun getApplicableElements() = listOf(TAG_STRING)

    override fun visitElement(
        context: XmlContext,
        element: Element,
    ) {
        // Only check the golden copy - not the translated sources.
        // We currently do not have the ability to do a 'per file'
        if ("values" != context.file.parentFile.name) {
            return
        }

        // parentNode == resources, as we only apply this to <string>
        element.parentNode.attributes.getNamedItem("tools:ignore")?.let {
            if (it.nodeValue.contains(ID)) {
                return
            }
        }

        val childNodes = element.childNodes
        if (childNodes.length > 0) {
            if (childNodes.length == 1) {
                val child = childNodes.item(0)
                if (child.nodeType == Node.TEXT_NODE || child.nodeType == Node.CDATA_SECTION_NODE) {
                    checkTextNode(
                        context,
                        element,
                        StringFormatDetector.stripQuotes(child.nodeValue),
                    )
                }
            } else {
                val sb = StringBuilder()
                StringFormatDetector.addText(sb, element)
                if (sb.isNotEmpty()) {
                    checkTextNode(context, element, sb.toString())
                }
            }
        }
    }

    private fun checkTextNode(
        context: XmlContext,
        element: Element,
        text: String,
    ) {
        if (VALUE_FALSE == element.getAttribute(ATTR_TRANSLATABLE)) {
            return
        }
        val locale = getLocale(context)
        val key =
            if (locale != null) {
                Pair.of(
                    locale.full,
                    text.lowercase(Locale.forLanguageTag(locale.tag)),
                )
            } else {
                Pair.of("default", text.lowercase(Locale.US))
            }
        val handle: Location.Handle = context.createLocationHandle(element)
        handle.clientData = element
        val handleList: MutableList<StringDeclaration> = allStrings.getOrDefault(key, ArrayList<StringDeclaration>())
        handleList.add(StringDeclaration(element.getAttribute(ATTR_NAME), text, handle))
        allStrings[key] = handleList
    }

    override fun afterCheckRootProject(context: Context) {
        for (duplicates in allStrings.values) {
            if (duplicates.size <= 1) {
                continue
            }
            var firstLocation: Location? = null
            var prevLocation: Location? = null
            var prevString = ""
            var caseVaries = false
            val names: MutableList<String> = ArrayList()
            if (duplicates.all { x: StringDeclaration ->
                    val el = x.location.clientData as Element
                    el.hasAttribute("comment")
                }
            ) {
                // skipping as all instances have a comment
                continue
            }
            for (duplicate in duplicates) {
                names.add(duplicate.name)
                val string: String = duplicate.text
                val location: Location = duplicate.location.resolve()
                if (prevLocation == null) {
                    firstLocation = location
                } else {
                    prevLocation.secondary = location
                    location.message =
                        "Duplicates value in `${names[0]}`. " +
                        "Add a `comment` attribute on both strings to explain this duplication"
                    location.setSelfExplanatory(false)
                    if (string != prevString) {
                        caseVaries = true
                        location.message = location.message + " (case varies, but you can use " +
                            "`android:inputType` or `android:capitalize` in the " +
                            "presentation)"
                    }
                }
                prevLocation = location
                prevString = string
            }
            val nameValues: MutableList<String> = ArrayList()
            for (name in names) {
                nameValues.add("`$name`")
            }
            val nameList = formatList(nameValues, nameValues.size, sort = true, useConjunction = false)
            // we use both quotes and code styling here so it appears in the console quoted
            var message = "Duplicate string value \"`$prevString`\", used in $nameList"
            if (caseVaries) {
                message += ". Use `android:inputType` or `android:capitalize` " +
                    "to treat these as the same and avoid string duplication."
            }
            context.report(ISSUE, firstLocation!!, message)
        }
    }

    companion object {
        private val IMPLEMENTATION_XML = Implementation(DuplicateCrowdInStrings::class.java, ALL_RESOURCES_SCOPE)

        const val ID = "DuplicateCrowdInStrings"

        /**
         * Whether there are any duplicate strings, including capitalization adjustments.
         */
        @Suppress("ktlint:standard:property-naming")
        var ISSUE: Issue =
            Issue.create(
                ID,
                "Duplicate Strings (CrowdIn)",
                "Duplicate strings are ambiguous for translators." +
                    "This lint check looks for duplicate strings, including differences for strings" +
                    "where the only difference is in capitalization. Title casing and all uppercase can" +
                    "all be adjusted in the layout or in code. Any duplicate strings should have a comment" +
                    "attribute added if they are intentional and required for translations.",
                Constants.ANKI_CROWDIN_CATEGORY,
                Constants.ANKI_CROWDIN_PRIORITY,
                Constants.ANKI_CROWDIN_SEVERITY,
                IMPLEMENTATION_XML,
            )
    }
}
