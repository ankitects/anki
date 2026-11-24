// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.lint.rules

import com.ichi2.anki.lint.testutils.assertXmlStringsHasError
import com.ichi2.anki.lint.testutils.assertXmlStringsNoIssues
import org.junit.Ignore
import org.junit.Test

/**
 * Test of [TranslationTypo]
 */
class TranslationTypoTest {
    @Test
    fun `JavaScript is valid casing`() {
        val validCasing = """<resources>
           <string name="hello">JavaScript</string>
        </resources>"""

        TranslationTypo.ISSUE.assertXmlStringsNoIssues(validCasing)
    }

    @Test
    fun `title case fails`() {
        val invalidTitleCase = """<resources>
           <string name="hello">Javascript</string>
        </resources>"""

        TranslationTypo.ISSUE.assertXmlStringsHasError(invalidTitleCase, "should be 'JavaScript'")
    }

    @Test
    fun `lowercase fails`() {
        val invalidLowerCase = """<resources>
           <string name="hello">javascript</string>
        </resources>"""

        TranslationTypo.ISSUE.assertXmlStringsHasError(invalidLowerCase, "should be 'JavaScript'")
    }

    @Test
    fun `plurals - javascript fails`() {
        val invalidLowerCase = """<resources>
           <plurals name="pl">
               <item quantity="other">javascript</item>
           </plurals>
        </resources>"""

        TranslationTypo.ISSUE.assertXmlStringsHasError(invalidLowerCase, "should be 'JavaScript'")
    }

    @Test
    fun `string array - javascript fails`() {
        val invalidLowerCase = """<resources>
           <string-array name="arr">
               <item>javascript</item>
           </string-array>
        </resources>"""

        TranslationTypo.ISSUE.assertXmlStringsHasError(invalidLowerCase, "should be 'JavaScript'")
    }

    @Test
    fun `quoted text gives error`() {
        val withQuotes = """<resources>
           <string name="ankiweb">'ankiweb'</string>
        </resources>"""

        TranslationTypo.ISSUE.assertXmlStringsHasError(withQuotes, "should be 'AnkiWeb'")
    }

    @Test
    fun `rtl text produces error`() {
        val rtl = """<resources>
            <string name="sample">فایل ankidroid فایل</string>
        </resources>"""

        TranslationTypo.ISSUE.assertXmlStringsHasError(rtl, "should be 'AnkiDroid'")
    }

    @Test
    fun `ankiweb url is not detected`() {
        val url = """<resources>
           <string name="url">https://faqs.ankiweb.net/example_for_testing</string>
        </resources>"""

        TranslationTypo.ISSUE.assertXmlStringsNoIssues(url)
    }

    @Test
    fun `vandalism fails`() {
        val stringRemoved = """<resources>
           <string name="hello"></string>
        </resources>"""

        TranslationTypo.ISSUE.assertXmlStringsHasError(stringRemoved, "should not be empty")
    }

    @Test
    fun `vandalism passes with empty_string key`() {
        val stringRemoved = """<resources>
           <string name="empty_string"></string>
        </resources>"""

        TranslationTypo.ISSUE.assertXmlStringsNoIssues(stringRemoved)
    }

    @Test
    fun `additional spacing before is flagged`() {
        val stringRemoved = """<resources>
           <string name="hello"> hello</string>
        </resources>"""

        TranslationTypo.ISSUE.assertXmlStringsHasError(
            xmlFile = stringRemoved,
            expectedError = "should not contain trailing whitespace",
            ignoreCData = true,
        )
    }

    @Test
    fun `additional spacing after is flagged`() {
        val stringRemoved = """<resources>
           <string name="hello">hello </string>
        </resources>"""

        TranslationTypo.ISSUE.assertXmlStringsHasError(
            xmlFile = stringRemoved,
            expectedError = "should not contain trailing whitespace",
            ignoreCData = true,
        )
    }

    @Test
    fun `cdata with no spaces is not flagged`() {
        val stringRemoved = """<resources>
           <string name="export_email_text"><![CDATA[
                        Hi!
                        <br/><br/>
                        This is an Anki flashcards deck sent from AnkiDroid[1].
                        Try to open it using one of the available Anki distributions[2] and enjoy easy and efficient learning!<br/><br/>
                        [1] %1${"\$s"}<br/>
                        [2] %2${"\$s"}
                ]]></string>
        </resources>"""

        TranslationTypo.ISSUE.assertXmlStringsNoIssues(stringRemoved)
    }

    @Test
    @Ignore("The ellipsis is unescaped")
    @Suppress("UNUSED_EXPRESSION")
    fun `ellipsis escaping is unchanged`() {
        """<resources>
            <string name="empty_filtered_deck">필터링된 덱을 비우는 중&#8230; </string>
        </resources>"""

        // TODO
    }

    @Test
    fun `cdata with spaces is not flagged`() {
        val stringRemoved = """<resources>
           <string name="export_email_text">
                <![CDATA[
                        Hi!
                        <br/><br/>
                        This is an Anki flashcards deck sent from AnkiDroid[1].
                        Try to open it using one of the available Anki distributions[2] and enjoy easy and efficient learning!<br/><br/>
                        [1] %1${"\$s"}<br/>
                        [2] %2${"\$s"}
                ]]>
            </string>
        </resources>"""

        TranslationTypo.ISSUE.assertXmlStringsNoIssues(stringRemoved)
    }

    /** A link to the string on Crowdin should be provided */
    @Test
    fun crowdinEditLinkIsProvided() {
        // Use links in the form: https://crowdin.com/editor/ankidroid/7290/en-af#q=create_subdeck
        // where 7290 is 01-core.xml, `en-af` is Afrikaans, and `create_subdeck` is the key

        // The actual link is https://crowdin.com/editor/ankidroid/7290/en-af#6534818, but
        // we don't have context to map from `create_subdeck` to `6534818`

        // We do not use '...', as this is not checked for RTL languages
        val xmlWithIssue = """<resources>
           <string name="create_subdeck">javascript</string>
        </resources>"""

        // 'standard' test
        TranslationTypo.ISSUE.assertXmlStringsHasError(
            xmlWithIssue,
            expectedError = "https://crowdin.com/editor/ankidroid/7290/en-af#q=create_subdeck",
            fileName = "01-core",
            androidLanguageFolder = "af",
        )

        // 02-strings -> 7291
        TranslationTypo.ISSUE.assertXmlStringsHasError(
            xmlWithIssue,
            expectedError = "https://crowdin.com/editor/ankidroid/7291/en-af#q=create_subdeck",
            fileName = "02-strings",
            androidLanguageFolder = "af",
        )

        // custom mapping: yue -> yu
        TranslationTypo.ISSUE.assertXmlStringsHasError(
            xmlWithIssue,
            expectedError = "https://crowdin.com/editor/ankidroid/7290/en-yu#q=create_subdeck",
            fileName = "01-core",
            androidLanguageFolder = "yue",
        )

        // Used region specifier: Chinese
        TranslationTypo.ISSUE.assertXmlStringsHasError(
            xmlWithIssue,
            expectedError = "https://crowdin.com/editor/ankidroid/7290/en-zhcn#q=create_subdeck",
            fileName = "01-core",
            androidLanguageFolder = "zh-rCN",
        )

        // no -> nnno
        TranslationTypo.ISSUE.assertXmlStringsHasError(
            xmlWithIssue,
            expectedError = "https://crowdin.com/editor/ankidroid/7290/en-nnno#q=create_subdeck",
            fileName = "01-core",
            androidLanguageFolder = "nn",
        )

        // ur -> urpa
        TranslationTypo.ISSUE.assertXmlStringsHasError(
            xmlWithIssue,
            expectedError = "https://crowdin.com/editor/ankidroid/7290/en-urpk#q=create_subdeck",
            fileName = "01-core",
            androidLanguageFolder = "ur",
        )
    }
}
