// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.lint.testutils

import com.android.tools.lint.checks.infrastructure.TestFiles
import com.android.tools.lint.checks.infrastructure.TestLintTask
import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.detector.api.Issue
import com.intellij.util.applyIf
import org.intellij.lang.annotations.Language
import org.junit.Assert.assertTrue

fun Issue.assertXmlStringsNoIssues(
    @Language("XML") xmlFile: String,
) {
    TestLintTask
        .lint()
        .allowMissingSdk()
        .allowCompilationErrors()
        .files(TestFiles.xml("res/values/constants.xml", xmlFile))
        .issues(this)
        .run()
        .expectClean()
}

fun Issue.assertXmlStringsHasErrorCount(
    @Language("XML") xmlFile: String,
    expectedErrorCount: Int,
) {
    assert(expectedErrorCount > 0) { "Use assertXmlStringsNoIssues" }
    TestLintTask
        .lint()
        .allowMissingSdk()
        .allowCompilationErrors()
        .files(TestFiles.xml("res/values/constants.xml", xmlFile))
        .issues(this)
        .run()
        .expectErrorCount(expectedErrorCount)
}

/**
 * @param androidLanguageFolder the code used in the Android `values-XX` folder.
 *  Cantonese: `yue`, not `yu`
 * @param fileName The name of the xml file without extension: `01-core` etc...
 */
fun Issue.assertXmlStringsHasError(
    @Language("XML") xmlFile: String,
    expectedError: String,
    androidLanguageFolder: String? = null,
    fileName: String? = null,
    ignoreCData: Boolean = false,
) {
    val languageQualifier = if (androidLanguageFolder != null) "-$androidLanguageFolder" else ""
    val resourceFileName = fileName ?: "constants"
    TestLintTask
        .lint()
        .allowMissingSdk()
        .applyIf(ignoreCData) { skipTestModes(TestMode.CDATA) }
        .allowCompilationErrors()
        .files(TestFiles.xml("res/values$languageQualifier/$resourceFileName.xml", xmlFile))
        .issues(this)
        .run()
        .expectErrorCount(1)
        .check({ output: String ->
            assertTrue(
                "check should fail with '$expectedError', but was '$output'",
                output.contains(expectedError),
            )
        })
}
