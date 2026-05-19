// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.lint.rules

import com.android.tools.lint.checks.infrastructure.TestFile.JavaTestFile.create
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.intellij.lang.annotations.Language
import org.junit.Assert.assertTrue
import org.junit.Test

@Suppress("UnstableApiUsage") // .issues() is unstable
class PrintStackTraceUsageTest {
    @Suppress("EmptyTryBlock") // in code samples
    companion object {
        @Language("JAVA")
        private val printStackTraceUsage = """import java.io.IOException;          
public class Test {                  
    public Test() {                  
        try {                        
        } catch (IOException ex) {   
            ex.printStackTrace();    
        }                            
    }                                
}"""

        @Language("JAVA")
        private val printStackTraceWithMethodArgument =
            """import java.io.IOException;                            
import java.io.PrintWriter;                            
public class Test {                                    
    public Test() {                                    
        try {                                          
        } catch (IOException ex) {                     
            ex.printStackTrace(new PrintWriter(sw));   
        }                                              
    }                                                  
}"""
    }

    @Test
    fun showsErrorForUsageWithNoParam() {
        lint()
            .allowMissingSdk()
            .files(create(printStackTraceUsage))
            .issues(PrintStackTraceUsage.ISSUE)
            .run()
            .expectErrorCount(1)
            .check({ output: String -> assertTrue(output.contains(PrintStackTraceUsage.ID)) })
    }

    @Test
    fun noErrorIfParamUsage() {
        // .check() is not required for the code to execute
        // If we have a parameter, we're not writing to stdout, so it's OK
        lint()
            .allowMissingSdk()
            .files(create(printStackTraceWithMethodArgument))
            .issues(PrintStackTraceUsage.ISSUE)
            .run()
            .expectErrorCount(0)
    }
}
