// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.lint.rules

import com.android.tools.lint.checks.infrastructure.TestFiles
import com.android.tools.lint.checks.infrastructure.TestLintResult
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.MatcherAssert.assertThat
import org.intellij.lang.annotations.Language
import org.junit.Test

class SentenceCaseConventionsTest {
    @Test
    fun `valid file has no error`() {
        checkSentenceCase(
            """<resources>
           |<string name="sentence_valid">Valid string</string>
           |</resources>
            """.trimMargin(),
        ).expectClean()
    }

    @Test
    fun `missing 'sentence_' prefix emits error`() {
        checkSentenceCase(
            """<resources>
            |<string name="invalid_prefix">missing 'sentence_' prefix</string>
            |</resources>
            """.trimMargin(),
        ).expectErrorCount(1)
            .check({ output: String ->
                assertThat("ID", output, containsString(SentenceCaseConventions.ID))
                assertThat("message", output, containsString("the 'name' attribute: 'invalid_prefix' should be prefixed with 'sentence_'"))
            })
    }

    private fun checkSentenceCase(
        @Language("XML") input: String,
    ): TestLintResult =
        lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(TestFiles.xml("res/values/sentence-case.xml", input))
            .issues(SentenceCaseConventions.ISSUE)
            .run()
}
