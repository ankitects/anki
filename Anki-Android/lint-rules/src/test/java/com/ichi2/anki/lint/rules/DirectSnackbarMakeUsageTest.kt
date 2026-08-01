// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Mrudul Tora <mrudultora@gmail.com>

package com.ichi2.anki.lint.rules

import com.android.tools.lint.checks.infrastructure.TestFile.JavaTestFile
import com.android.tools.lint.checks.infrastructure.TestLintTask
import org.intellij.lang.annotations.Language
import org.junit.Assert
import org.junit.Test

class DirectSnackbarMakeUsageTest {
    @Language("JAVA")
    private val stubSnackbar = """                                      
package com.google.android.material.snackbar;                     
public class Snackbar {                                           
                                                                  
    public static Snackbar make(View view,                        
                                CharSequence text,                
                                int duration) {                   
         // Stub                                                  
    }                                                             
}                                                                 
"""

    @Language("JAVA")
    private val javaFileToBeTested = """                             
package com.ichi2.anki.lint.rules;                             
                                                               
import com.google.android.material.snackbar.Snackbar;          
                                                               
public class TestJavaClass {                                   
                                                               
    public static void main(String[] args) {                   
        Snackbar snackbar = Snackbar.make();                   
        snackbar.show();                                       
    }                                                          
}                                                              
"""

    @Language("JAVA")
    private val javaFileWithSnackbarsKt = """                            
package com.ichi2.anki.lint.rules;                             
                                                               
import com.google.android.material.snackbar.Snackbar;          
                                                               
public class SnackbarsKt {                                         
                                                               
    public static void main(String[] args) {                   
        Snackbar snackbar = Snackbar.make();                   
        snackbar.show();                                       
    }                                                          
}                                                              
"""

    @Test
    fun showsErrorsForInvalidUsage() {
        TestLintTask
            .lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(JavaTestFile.create(stubSnackbar), JavaTestFile.create(javaFileToBeTested))
            .issues(DirectSnackbarMakeUsage.ISSUE)
            .run()
            .expectErrorCount(1)
            .check({ output: String ->
                Assert.assertTrue(output.contains(DirectSnackbarMakeUsage.ID))
                Assert.assertTrue(output.contains(DirectSnackbarMakeUsage.DESCRIPTION))
            })
    }

    @Test
    fun allowsUsageForSnackbarsKt() {
        TestLintTask
            .lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(JavaTestFile.create(stubSnackbar), JavaTestFile.create(javaFileWithSnackbarsKt))
            .issues(DirectSnackbarMakeUsage.ISSUE)
            .run()
            .expectClean()
    }
}
