// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2020 lukstbit <52494258+lukstbit@users.noreply.github.com>

package com.ichi2.anki.lint.rules

import com.android.tools.lint.checks.infrastructure.TestFile.JavaTestFile
import com.android.tools.lint.checks.infrastructure.TestLintTask
import org.intellij.lang.annotations.Language
import org.junit.Assert
import org.junit.Test

class DirectDateInstantiationTest {
    @Language("JAVA")
    private val stubDate = """                         
package java.util;                               
                                                 
public class Date {                              
                                                 
    public Date() {                              
                                                 
    }                                            
    public Date(long time) {                     
                                                 
    }                                            
}                                                
"""

    @Language("JAVA")
    private val javaFileToBeTested = """               
package com.ichi2.anki.lint.rules;               
                                                 
import java.util.Date;                           
                                                 
public class TestJavaClass {                     
                                                 
    public static void main(String[] args) {     
        Date d = new Date();                     
    }                                            
}                                                
"""

    @Language("JAVA")
    private val javaFileWithTime = """                 
package com.ichi2.anki.lint.rules;               
                                                 
import java.util.Date;                           
                                                 
public abstract class Time {                     
                                                 
    public static void main(String[] args) {     
        Date d = new Date();                     
    }                                            
}                                                
"""

    @Language("JAVA")
    private val javaFileUsingDateWithLong = """        
package com.ichi2.anki.lint.rules;               
                                                 
import java.util.Date;                           
                                                 
public class TestJavaClass {                     
                                                 
    public static void main(String[] args) {     
        Date d = new Date(1L);                   
    }                                            
}                                                
"""

    @Test
    fun showsErrorsForInvalidUsage() {
        TestLintTask
            .lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(JavaTestFile.create(stubDate), JavaTestFile.create(javaFileToBeTested))
            .issues(DirectDateInstantiation.ISSUE)
            .run()
            .expectErrorCount(1)
            .check({ output: String ->
                Assert.assertTrue(output.contains(DirectDateInstantiation.ID))
                Assert.assertTrue(output.contains(DirectDateInstantiation.DESCRIPTION))
            })
    }

    @Test
    fun allowsUsageInTimeClass() {
        TestLintTask
            .lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(JavaTestFile.create(stubDate), JavaTestFile.create(javaFileWithTime))
            .issues(DirectDateInstantiation.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun allowsUsageWithLongValue() {
        TestLintTask
            .lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(JavaTestFile.create(stubDate), JavaTestFile.create(javaFileUsingDateWithLong))
            .issues(DirectDateInstantiation.ISSUE)
            .run()
            .expectClean()
    }
}
