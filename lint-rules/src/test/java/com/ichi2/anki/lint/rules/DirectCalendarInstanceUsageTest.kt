// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2020 lukstbit <52494258+lukstbit@users.noreply.github.com>

package com.ichi2.anki.lint.rules

import com.android.tools.lint.checks.infrastructure.TestFile.JavaTestFile
import com.android.tools.lint.checks.infrastructure.TestLintTask
import com.google.common.annotations.Beta
import org.intellij.lang.annotations.Language
import org.junit.Assert
import org.junit.Test

@Suppress("UnstableApiUsage")
@Beta
class DirectCalendarInstanceUsageTest {
    @Language("JAVA")
    private val stubCalendar = """                                   
package java.util;                                             
                                                               
public class Calendar {                                        
                                                               
    public static Calendar getInstance() {                     
       return null;                                            
    }                                                          
}                                                              
"""

    @Language("JAVA")
    private val javaFileToBeTested = """                             
package com.ichi2.anki.lint.rules;                             
                                                               
import java.util.Calendar;                                     
                                                               
public class TestJavaClass {                                   
                                                               
    public static void main(String[] args) {                   
        Calendar c = Calendar.getInstance();                   
        c.clear();                                             
    }                                                          
}                                                              
"""

    @Language("JAVA")
    private val javaFileWithTime = """                               
package com.ichi2.anki.lint.rules;                             
                                                               
import java.util.Calendar;                                     
                                                               
public abstract class Time {                                   
                                                               
    public static void main(String[] args) {                   
        Calendar c = Calendar.getInstance();                   
        c.clear();                                             
    }                                                          
}                                                              
"""

    @Test
    fun showsErrorForInvalidUsage() {
        TestLintTask
            .lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(JavaTestFile.create(stubCalendar), JavaTestFile.create(javaFileToBeTested))
            .issues(DirectCalendarInstanceUsage.ISSUE)
            .run()
            .expectErrorCount(1)
            .check({ output: String ->
                Assert.assertTrue(output.contains(DirectCalendarInstanceUsage.ID))
                Assert.assertTrue(output.contains(DirectCalendarInstanceUsage.DESCRIPTION))
            })
    }

    @Test
    fun allowsUsageInTimeClass() {
        TestLintTask
            .lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(JavaTestFile.create(stubCalendar), JavaTestFile.create(javaFileWithTime))
            .issues(DirectCalendarInstanceUsage.ISSUE)
            .run()
            .expectClean()
    }
}
