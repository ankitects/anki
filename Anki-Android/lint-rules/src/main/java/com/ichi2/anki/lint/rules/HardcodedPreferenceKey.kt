// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 Brayan Oliveira <brayandso.dev@gmail.com>

package com.ichi2.anki.lint.rules

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.XmlContext
import com.google.common.annotations.VisibleForTesting
import com.ichi2.anki.lint.utils.Constants
import org.w3c.dom.Element

/**
 * A Lint check to prevent using hardcoded strings on preferences keys
 */
class HardcodedPreferenceKey : ResourceXmlDetector() {
    companion object {
        @VisibleForTesting
        val ID = "HardcodedPreferenceKey"

        @VisibleForTesting
        val DESCRIPTION = "Preference key should not be hardcoded"
        private const val EXPLANATION = "Extract the key to a resources XML so it can be reused"
        private val implementation = Implementation(HardcodedPreferenceKey::class.java, Scope.RESOURCE_FILE_SCOPE)
        val ISSUE: Issue =
            Issue.create(
                ID,
                DESCRIPTION,
                EXPLANATION,
                Constants.ANKI_XML_CATEGORY,
                Constants.ANKI_XML_PRIORITY,
                Constants.ANKI_XML_SEVERITY,
                implementation,
            )
    }

    override fun getApplicableElements(): Collection<String>? = ALL

    override fun visitElement(
        context: XmlContext,
        element: Element,
    ) {
        reportAttributeIfHardcoded(context, element, "android:key")
        reportAttributeIfHardcoded(context, element, "android:dependency")
    }

    private fun reportAttributeIfHardcoded(
        context: XmlContext,
        element: Element,
        attributeName: String,
    ) {
        val attrNode = element.getAttributeNode(attributeName) ?: return

        if (isHardcodedString(attrNode.value)) {
            context.report(ISSUE, element, context.getLocation(attrNode), DESCRIPTION)
        }
    }

    private fun isHardcodedString(string: String): Boolean {
        // resources start with a `@`, and attributes start with a `?`
        return string.isNotEmpty() && !string.startsWith("@") && !string.startsWith("?")
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean = folderType == ResourceFolderType.XML
}
