// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.lint.rules

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.google.common.annotations.VisibleForTesting
import com.ichi2.anki.lint.utils.Constants
import org.w3c.dom.Element

/**
 * Checks for issues in sentence-case.xml
 */
class SentenceCaseConventions :
    ResourceXmlDetector(),
    XmlScanner {
    companion object {
        @VisibleForTesting
        val ID = "SentenceCaseConventions"

        @VisibleForTesting
        val EXPLANATION = "Sentence-case style guide"

        private val implementation =
            Implementation(SentenceCaseConventions::class.java, Scope.RESOURCE_FILE_SCOPE)
        val ISSUE: Issue =
            Issue.create(
                ID,
                EXPLANATION,
                EXPLANATION,
                Constants.ANKI_XML_CATEGORY,
                Constants.ANKI_XML_PRIORITY,
                Constants.ANKI_XML_SEVERITY,
                implementation,
            )
    }

    override fun getApplicableElements(): Collection<String> = listOf("string")

    override fun appliesTo(folderType: ResourceFolderType): Boolean = folderType == ResourceFolderType.VALUES

    override fun visitElement(
        context: XmlContext,
        element: Element,
    ) {
        if (context.file.name != "sentence-case.xml") {
            return
        }

        // ensure 'name' starts with 'sentence_'
        // Convention: avoids reviewers needing to check strings against 'GeneratedTranslations'
        val elementName = element.getAttribute("name")
        if (!elementName.startsWith("sentence_")) {
            context.report(
                issue = ISSUE,
                location = context.getElementLocation(element),
                message = "the 'name' attribute: '$elementName' should be prefixed with 'sentence_'",
            )
        }
    }
}
