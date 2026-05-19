// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2020 lukstbit <52494258+lukstbit@users.noreply.github.com>

package com.ichi2.anki.lint.rules

import com.android.tools.lint.checks.infrastructure.TestFile.JavaTestFile
import com.android.tools.lint.checks.infrastructure.TestLintTask
import org.intellij.lang.annotations.Language
import org.junit.Assert
import org.junit.Test

class DirectGregorianInstantiationTest {
    @Language("JAVA")
    private val stubZoned = """                                      
package java.time;                                             
                                                               
public class ZonedDateTime {                                   
                                                               
                                                               
     public ZonedDateTime() {                                  
                                                               
     }                                                         
}                                                              
"""

    @Language("JAVA")
    private val stubGregorian = """                                  
package java.util;                                             
                                                               
import java.time.ZonedDateTime;                                
                                                               
public class GregorianCalendar {                               
                                                               
    public GregorianCalendar() {                               
    }                                                          
                                                               
                                                               
    public static GregorianCalendar from(ZonedDateTime z) {    
        return null;                                           
    }                                                          
}                                                              
"""

    @Language("JAVA")
    private val javaFileWithFromCall = """                           
package com.ichi2.anki.lint.rules;                             
                                                               
import java.util.GregorianCalendar;                            
                                                               
public class TestJavaClass {                                   
                                                               
    public static void main(String[] args) {                   
        GregorianCalendar gc = GregorianCalendar.from(null);   
    }                                                          
}                                                              
"""

    @Language("JAVA")
    private val javaFileWithConstructorInvocation = """              
package com.ichi2.anki.lint.rules;                             
                                                               
import java.util.GregorianCalendar;                            
                                                               
public class TestJavaClass {                                   
                                                               
    public static void main(String[] args) {                   
        GregorianCalendar gc = new GregorianCalendar();        
    }                                                          
}                                                              
"""

    @Language("JAVA")
    private val javaFileWithTime = """                               
package com.ichi2.anki.lint.rules;                             
                                                               
import java.util.GregorianCalendar;                            
                                                               
public class Time {                                            
                                                               
    public static void main(String[] args) {                   
        GregorianCalendar gc = new GregorianCalendar();        
    }                                                          
}                                                              
"""

    @Test
    fun showsErrorForInvalidUsage() {
        TestLintTask
            .lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(
                JavaTestFile.create(stubZoned),
                JavaTestFile.create(stubGregorian),
                JavaTestFile.create(javaFileWithFromCall),
            ).issues(DirectGregorianInstantiation.ISSUE)
            .run()
            .expectErrorCount(1)
            .check({ output: String ->
                Assert.assertTrue(output.contains(DirectGregorianInstantiation.ID))
                Assert.assertTrue(output.contains(DirectGregorianInstantiation.DESCRIPTION))
            })
        TestLintTask
            .lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(
                JavaTestFile.create(stubZoned),
                JavaTestFile.create(stubGregorian),
                JavaTestFile.create(javaFileWithConstructorInvocation),
            ).issues(DirectGregorianInstantiation.ISSUE)
            .run()
            .expectErrorCount(1)
            .check({ output: String ->
                Assert.assertTrue(output.contains(DirectGregorianInstantiation.ID))
                Assert.assertTrue(output.contains(DirectGregorianInstantiation.DESCRIPTION))
            })
    }

    @Test
    fun doesNotShowErrorsWhenUsedInTime() {
        TestLintTask
            .lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(JavaTestFile.create(stubGregorian), JavaTestFile.create(javaFileWithTime))
            .issues(DirectGregorianInstantiation.ISSUE)
            .run()
            .expectClean()
    }
}
