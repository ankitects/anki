// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.lint.rules

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.utils.forEach
import com.google.common.annotations.VisibleForTesting
import com.ichi2.anki.lint.rules.TranslationTypo.CheckStringCase.Companion.create
import com.ichi2.anki.lint.utils.Constants
import com.ichi2.anki.lint.utils.CrowdinContext.Companion.toCrowdinContext
import com.ichi2.anki.lint.utils.ext.isRightToLeftLanguage
import org.w3c.dom.CDATASection
import org.w3c.dom.Element

/**
 * Checks for a variety of errors commonly made in CrowdIn
 *
 * `JavaScript`, not `Javascript`, ellipses, empty strings, etc
 */
class TranslationTypo :
    ResourceXmlDetector(),
    XmlScanner {
    companion object {
        @VisibleForTesting
        val ID_TRANSLATION_TYPO = "TranslationTypo"

        @VisibleForTesting
        val EXPLANATION_TRANSLATION_TYPO = "Typo in translation"

        private val implementation = Implementation(TranslationTypo::class.java, Scope.RESOURCE_FILE_SCOPE)
        val ISSUE: Issue =
            Issue.create(
                ID_TRANSLATION_TYPO,
                EXPLANATION_TRANSLATION_TYPO,
                EXPLANATION_TRANSLATION_TYPO,
                Constants.ANKI_XML_CATEGORY,
                Constants.ANKI_XML_PRIORITY,
                Constants.ANKI_XML_SEVERITY,
                implementation,
            )

        // copied from tools/localization/src/constants.ts
        // excludes marketdescription as it is .txt
        private val I18N_FILES =
            listOf(
                "01-core",
                "02-strings",
                "03-dialogs",
                "04-network",
                "05-feedback",
                "06-statistics",
                "07-cardbrowser",
                "08-widget",
                "09-backup",
                "10-preferences",
                "11-arrays",
                "16-multimedia-editor",
                "17-model-manager",
                "18-standard-models",
                "20-search-preference",
            ).map { "$it.xml" }

        // CrowdIn strings+ additional string XML which are not translated
        val STRING_XML_FILES = I18N_FILES + listOf("constants.xml", "sentence-case.xml")

        val javascriptCasingCheck = create("JavaScript")
        val ankiWebCasingCheck = create("AnkiWeb")
        val ankiDroidCasingCheck = create("AnkiDroid")
        val ankiCasingCheck = create("Anki")
        val ankiMobileCasingCheck = create("AnkiMobile")
    }

    override fun getApplicableElements(): Collection<String>? = ALL

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.XML || folderType == ResourceFolderType.VALUES

    override fun visitElement(
        context: XmlContext,
        element: Element,
    ) {
        // ignore files not containing strings
        if (!STRING_XML_FILES.contains(context.file.name)) {
            return
        }

        // Ignore container tags: visitElement already handles visiting sub-tags (<item>/<string>)
        if (element.tagName in listOf("resources", "plurals", "string-array")) {
            return
        }

        val crowdinContext = context.toCrowdinContext()

        /** Helper function to report 'TranslationTypo' issues */
        fun Element.reportIssue(message: String) {
            val elementToReport = this
            val crowdinEditUrl =
                crowdinContext
                    ?.getEditUrl(elementToReport)
                    ?.let { url -> "\n$url" } ?: ""
            context.report(
                issue = ISSUE,
                location = context.getElementLocation(elementToReport),
                message = message + crowdinEditUrl,
            )
        }

        fun CheckStringCase.reportIfIssue() {
            if (!this.shouldReport(element)) return
            element.reportIssue("should be '${this.expectedCasing}'")
        }

        // use the unicode character instead of three dots for ellipsis
        // ignore RTL to reduce maintenance: GitHub incorrectly displays ellipsis, so hard to review
        if (element.textContent.contains("...") && !context.isRightToLeftLanguage()) {
            element.reportIssue("should use unicode '&#8230;' for ellipsis not three dots; RTL languages best viewed on crowdin")
        }

        javascriptCasingCheck.reportIfIssue()
        ankiWebCasingCheck.reportIfIssue()
        ankiDroidCasingCheck.reportIfIssue()
        // anki = current in Turkish
        if (context.file.parentFile.name != "values-tr") {
            ankiCasingCheck.reportIfIssue()
        }
        ankiMobileCasingCheck.reportIfIssue()

        // remove empty strings
        if (element.textContent.isEmpty() && element.getAttribute("name") != "empty_string") {
            element.reportIssue("should not be empty")
        }

        if (element.textContent.trim() != element.textContent) {
            var isValid = true
            element.childNodes.forEach {
                if (it is CDATASection) {
                    isValid = false
                }
            }

            if (isValid) {
                element.reportIssue("should not contain trailing whitespace")
            }
        }
    }

    /**
     * Performs case-sensitive validation of a string: `"ankidroid"` should be `"AnkiDroid"`
     *
     * @param regex Used to check for the presence of a badly cased string. See [create]
     * @param expectedCasing String in the expected casing. e.g. `"JavaScript"`
     */
    data class CheckStringCase(
        val regex: Regex,
        val expectedCasing: String,
    ) {
        /** Flags 'javascript' when 'JavaScript' should be used */
        fun shouldReport(element: Element): Boolean {
            // if it's a link, don't check it - www.ankiweb.net matches the below
            if (element.textContent.contains("://")) return false

            // if it contains 'javascript', but casing is not the correct 'JavaScript'
            return regex.containsMatchIn(element.textContent) && !element.textContent.contains(expectedCasing)
        }

        companion object {
            /**
             * Creates a [CheckStringCase] for case-sensitive validation of a string in `values`
             * @param expectedCasing The string in the expected casing. e.g. `"JavaScript"`
             */
            fun create(expectedCasing: String): CheckStringCase =
                CheckStringCase(
                    // (?:^|[\p{P}\s]) = (start of string | punctuation | space)
                    // (?:$|[\p{P}\s]) = (end of string | punctuation | space)
                    regex = Regex("""(?:^|[\p{P}\s])$expectedCasing(?:$|[\p{P}\s])""", RegexOption.IGNORE_CASE),
                    expectedCasing = expectedCasing,
                )
        }
    }
}
