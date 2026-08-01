// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.lint.rules

import com.android.tools.lint.checks.infrastructure.TestFiles
import com.android.tools.lint.checks.infrastructure.TestLintTask
import org.intellij.lang.annotations.Language
import org.junit.Test

/** Test for [NonPositionalFormatSubstitutions] */
class NonPositionalFormatSubstitutionsTest {
    /** One substitution is unambiguous  */
    @Language("XML")
    private val valid = """<resources>
       <string name="hello">%s</string>
    </resources>"""

    /** Easiest test case: Two exact duplicates in the same file  */
    @Language("XML")
    private val invalid = """<resources>
       <string name="hello">%s %s</string>
    </resources>"""

    @Language("XML")
    private val unambiguous = "<resources><string name=\"hello\">%1\$s %2\$s</string></resources>"

    /** %% is an encoded percentage */
    @Language("XML")
    private val encoded = "<resources><string name=\"hello\">%%</string></resources>"

    @Language("XML")
    private val pluralPass =
        """
        <resources>
            <plurals name="import_complete_message">
                <item quantity="one">Cards imported: %1${'$'}d</item>
                <item quantity="other">Files imported :%1${'$'}d" Total cards imported: %2${'$'}d</item>
            </plurals>
        </resources>
        """.trimIndent()

    @Language("XML")
    private val pluralPartial =
        """
        <resources>
            <plurals name="import_complete_message">
                <item quantity="one">Cards imported: %d</item>
                <item quantity="other">Files imported :%1${'$'}d" Total cards imported: %2${'$'}d</item>
            </plurals>
        </resources>
        """.trimIndent()

    @Language("XML")
    private val pluralFail =
        """
        <resources>
            <plurals name="import_complete_message">
                <item quantity="one">Cards imported: %d</item>
                <item quantity="other">Files imported: %d\nTotal cards imported: %d</item>
            </plurals>
        </resources>
        """.trimIndent()

    @Language("XML")
    private val pluralMultiple =
        """
        <plurals name="reschedule_card_dialog_interval">
            <item quantity="one">Current interval: %d day</item>
            <item quantity="few">Keisti Kortelių mokymosi dieną</item>
            <item quantity="many">Current interval: %d days</item>
            <item quantity="other">Current interval: %d days</item>
        </plurals>
        """.trimIndent()

    @Language("XML")
    private val pluralMultipleTwo =
        """
        <plurals name="in_minutes">
            <item quantity="one">%1${'$'}d मिनट</item>
            <item quantity="other">मिनट</item>
        </plurals>
        """.trimIndent()

    @Test
    fun errors_if_ambiguous() {
        TestLintTask
            .lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(TestFiles.xml("res/values/string.xml", invalid))
            .issues(NonPositionalFormatSubstitutions.ISSUE)
            .run()
            .expectErrorCount(1)
    }

    @Test
    fun no_errors_if_valid() {
        TestLintTask
            .lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(TestFiles.xml("res/values/string.xml", valid))
            .issues(NonPositionalFormatSubstitutions.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun no_errors_if_unambiguous() {
        TestLintTask
            .lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(TestFiles.xml("res/values/string.xml", unambiguous))
            .issues(NonPositionalFormatSubstitutions.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun no_errors_if_encoded() {
        TestLintTask
            .lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(TestFiles.xml("res/values/string.xml", encoded))
            .issues(NonPositionalFormatSubstitutions.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun valid_plural_passed() {
        TestLintTask
            .lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(TestFiles.xml("res/values/string.xml", pluralPass))
            .issues(NonPositionalFormatSubstitutions.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun plural_partial_flags() {
        // If one plural has $1, $2 etc... the other should as well
        TestLintTask
            .lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(TestFiles.xml("res/values/string.xml", pluralPartial))
            .issues(NonPositionalFormatSubstitutions.ISSUE)
            .run()
            .expectErrorCount(1)
    }

    @Test
    fun errors_on_plural_issue() {
        TestLintTask
            .lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(TestFiles.xml("res/values/string.xml", pluralFail))
            .issues(NonPositionalFormatSubstitutions.ISSUE)
            .run()
            .expectErrorCount(1)
    }

    @Test
    fun plural_integration_test() {
        TestLintTask
            .lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(TestFiles.xml("res/values/string.xml", pluralMultiple))
            .issues(NonPositionalFormatSubstitutions.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun plural_integration_test_positional_and_nothing() {
        TestLintTask
            .lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(TestFiles.xml("res/values/string.xml", pluralMultipleTwo))
            .issues(NonPositionalFormatSubstitutions.ISSUE)
            .run()
            .expectClean()
    }
}
