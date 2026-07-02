// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Nicola Dardanis <nicdard@gmail.com>

package com.ichi2.anki.lint.rules

import com.android.tools.lint.checks.infrastructure.LintDetectorTest.java
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import com.ichi2.anki.lint.rules.VariableNamingDetector.Companion.ISSUE
import org.junit.Test

internal class VariableNamingDetectorTest {
    @Test
    @Suppress("ktlint:standard:max-line-length")
    fun reportsErrorTest() {
        lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(file)
            .issues(ISSUE)
            .run()
            .expect(
                "src/com/ichi2/anki/exception/FilteredAncestor.java:14: Error: Variable name should not use field prefixes. [VariableNamingDetector]\n" +
                    "    public void setFilteredAncestorName(String mFilteredAncestorName, String member) {\n" +
                    "                                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                    "src/com/ichi2/anki/exception/FilteredAncestor.java:22: Error: Variable name should not use field prefixes. [VariableNamingDetector]\n" +
                    "    public static setFilteredAncestorName(String sFilteredAncestorName, String staticMember) {\n" +
                    "                                          ~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                    "2 errors, 0 warnings",
            )
    }

    companion object {
        private val file =
            java(
                """
                package com.ichi2.anki.exception;

                public class FilteredAncestor extends Exception {
                    private String mFilteredAncestorName;
                    private static String sFilteredAncestorName;
                    public FilteredAncestor(String filteredAncestorName) {
                        this.mFilteredAncestorName = filteredAncestorName;
                    }

                    public String getFilteredAncestorName() {
                        return mFilteredAncestorName;
                    }
                    
                    public void setFilteredAncestorName(String mFilteredAncestorName, String member) {
                        this.mFilteredAncestorName = mFilteredAncestorName;
                    }
                    
                    public static String getStaticFilteredAncestorName() {
                        return sFilteredAncestorName;
                    }
                    
                    public static setFilteredAncestorName(String sFilteredAncestorName, String staticMember) {
                        this.sFilteredAncestorName = sFilteredAncestorName;
                    }
                }
                """.trimIndent(),
            )
    }
}
