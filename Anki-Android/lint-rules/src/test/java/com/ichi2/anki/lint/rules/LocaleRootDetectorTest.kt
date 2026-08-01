// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 Spencer Poisseroux <me@spoisseroux.com>

package com.ichi2.anki.lint.rules

import com.android.tools.lint.checks.infrastructure.TestFile.JavaTestFile.create
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import com.android.tools.lint.checks.infrastructure.TestMode
import org.intellij.lang.annotations.Language
import org.junit.Test

class LocaleRootDetectorTest {
    @Language("JAVA")
    private val invalidCode =
        """
        package com.ichi2.test;
        import java.util.Locale;
        import java.text.NumberFormat;
        import static java.util.Locale.ROOT;

        public class InvalidClass {
            public void invalidMethod() {
                String.format(Locale.ROOT, "Number: %d", 42); // Should be flagged
                NumberFormat.getInstance(ROOT); // Should be ignored, static import
            }
        }
        """.trimIndent()

    @Language("JAVA")
    private val suppressedCode =
        """
        package com.ichi2.test;
        import java.util.Locale;
        import androidx.annotation.SuppressLint;

        @SuppressLint("LocaleRootUsage")
        public class SuppressedClass {
            public void suppressedMethod() {
                String id = String.format(Locale.ROOT, "ID_%d", 123);
            }
        }
        """.trimIndent()

    @Test
    fun `allows suppressed Locale ROOT usage`() {
        lint()
            .testModes(TestMode.DEFAULT)
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(create(suppressedCode))
            .issues(LocaleRootDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun `detects explicit Locale ROOT usage`() {
        lint()
            .testModes(TestMode.DEFAULT)
            .allowMissingSdk()
            .files(create(invalidCode))
            .issues(LocaleRootDetector.ISSUE)
            .run()
            .expectErrorCount(1)
            .expectContains("String.format(Locale.ROOT")
    }
}
