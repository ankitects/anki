// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 Divyansh Dwivedi <justdvnsh2208@gmail.com>

package com.ichi2.anki.lint.rules

import com.android.tools.lint.checks.infrastructure.TestFiles
import com.android.tools.lint.checks.infrastructure.TestLintTask
import org.intellij.lang.annotations.Language
import org.junit.Test

/** Test for [InvalidStringFormatDetectorTest] */
class InvalidStringFormatDetectorTest {
    @Language("XML")
    private val invalid =
        """<resources>
        |<string name="testString">I am a test% String</string>
        |<string name="testString">test%</string>
        |<string name="testString3">test% string</string>
        |<plurals name="pluralTestString1">
            <item quantity="other">आज%  %1$'d' मध्ये% %2$'s' कार्डांचा अभ्यास केला</item>
        </plurals>
        |</resources>
        """.trimMargin()

    @Language("XML")
    private val valid =
        """<resources>
        |<string name="testString">I am a test String</string>
        |<string name="testString">test</string>
        |<string name="testString3">test string</string>
        ||<string name="testString4">test string %s</string>
        |<string name="testString5">%%</string>
        |<string name="testString6">%1\$'d' is expected</string>
        |<plurals name="PluralTestString1">
            <item quantity="one">%1$'d' card (0 due)</item>
            <item quantity="other">%1$'d' cards (0 due)</item>
        </plurals>
        |<plurals name="pluralTestString">
            <item quantity="one">आज %1$'d' मध्ये %2$'s' कार्डचा अभ्यास केला</item>
        </plurals>
        |<string name="testString7">XXX%</string>
        |</resources>
        """.trimMargin()

    @Language("XML")
    private val invalidCapitalization =
        """<resources>
        |<string name="testString">%D</string>
        |<string name="testString2">%1D</string>
        |<string name="testString3">%9D</string>
        |<string name="testString4">%-9D</string>
        |<string name="testString5">%1${'$'}D</string>
        |<string name="testString6">%F</string>
        |<string name="testString7">%1F</string>
        |<string name="testString8">%9F</string>
        |<string name="testString9">%-9F</string>
        |<string name="testString10">%1${'$'}F</string>
        |<string name="testString11">%N</string>
        |<string name="testString12">%O</string>
        |<string name="testString13">%G</string>
        |<string name="testString14">%C</string>
        |<string name="testString15">%D %1D %9D %-9D %1${'$'}D %F %1F %9F %-9F %1${'$'}F %N %O %G %C</string>
        |<string name="testString16">% %D</string>
        |<string name="testString17">%D%</string>
        |<string name="testString18">%D%%</string>
        |<plurals name="pluralTestString1">
            <item quantity="other">%2${'$'}D</item>
        </plurals>
        |</resources>
        """.trimMargin()

    @Language("XML")
    private val validCapitalization =
        """<resources>
        |<string name="testString">%d</string>
        |<string name="testString2">%1d</string>
        |<string name="testString3">%9d</string>
        |<string name="testString4">%-9d</string>
        |<string name="testString5">%1${'$'}d</string>
        |<string name="testString6">%f</string>
        |<string name="testString7">%1f</string>
        |<string name="testString8">%9f</string>
        |<string name="testString9">%-9f</string>
        |<string name="testString10">%1${'$'}f</string>
        |<string name="testString11">%n</string>
        |<string name="testString12">%o</string>
        |<string name="testString13">%g</string>
        |<string name="testString14">%c</string>
        |<string name="testString15">%d %1d %9d %-9d %1${'$'}d %f %1f %9f %-9f %1${'$'}f %n %o %g %c</string>
        |<string name="testString16">% %d</string>
        |<string name="testString16">%%D</string>
        |<string name="testString17">% D</string>
        |<string name="testString18">%d%</string>
        |<string name="testString19">%d%%</string>
        |<string name="testString20">%abcd</string>
        |<string name="testString21">%S %X %T %E %H %B %A</string>
        |<string name="testString22">%S %1S %9S %-9S %1${'$'}S</string>
        |<string name="testString23">%1{'$'}d\nDatabase version</string>
        |<string name="testString24">%abcD</string>
        |<string name="testString25">%abcDefg</string>
        |<plurals name="pluralTestString1">
            <item quantity="other">Hello hello %2${'$'}d hello hello</item>
        </plurals>
        |</resources>
        """.trimMargin()

    @Test
    fun error_if_string_format_invalid() {
        TestLintTask
            .lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .allowDuplicates()
            .files(TestFiles.xml("res/values/string.xml", invalid))
            .issues(InvalidStringFormatDetector.ISSUE)
            .run()
            .expectErrorCount(4)
    }

    @Test
    fun no_error_if_string_format_valid() {
        TestLintTask
            .lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(TestFiles.xml("res/values/string.xml", valid))
            .issues(InvalidStringFormatDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun error_if_capitalization_invalid() {
        TestLintTask
            .lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .allowDuplicates()
            .files(TestFiles.xml("res/values/string.xml", invalidCapitalization))
            .issues(InvalidStringFormatDetector.ISSUE)
            .run()
            .expectErrorCount(19)
    }

    @Test
    fun no_error_if_capitalization_valid() {
        TestLintTask
            .lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(TestFiles.xml("res/values/string.xml", validCapitalization))
            .issues(InvalidStringFormatDetector.ISSUE)
            .run()
            .expectClean()
    }
}
