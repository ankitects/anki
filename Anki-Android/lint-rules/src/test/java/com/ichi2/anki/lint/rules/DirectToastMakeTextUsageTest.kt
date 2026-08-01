// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Nicola Dardanis <nicdard@gmail.com>

package com.ichi2.anki.lint.rules

import com.android.tools.lint.checks.infrastructure.TestFile.JavaTestFile.create
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.intellij.lang.annotations.Language
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectToastMakeTextUsageTest {
    @Language("JAVA")
    private val stubToast = """                                      
package android.widget;                                        
public class Toast {                                           
                                                               
    public static Toast makeText(Context context,              
                                String text,                   
                                int duration) {                
         // Stub                                               
    }                                                          
}                                                              
"""

    @Language("JAVA")
    private val javaFileToBeTested = """                             
package com.ichi2.anki.lint.rules;                             
                                                               
import android.widget.Toast;                                   
                                                               
public class TestJavaClass {                                   
                                                               
    public static void main(String[] args) {                   
        Toast.makeText();                                      
    }                                                          
}                                                              
"""

    @Language("JAVA")
    private val javaFileWithToastWrapper = """
package com.ichi2.anki.lint.rules;

import android.widget.Toast;

public class ToastKt {

    public static void main(String[] args) {
        Toast.makeText();
    }
}
"""

    @Test
    fun showsErrorsForInvalidUsage() {
        lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(create(stubToast), create(javaFileToBeTested))
            .issues(DirectToastMakeTextUsage.ISSUE)
            .run()
            .expectErrorCount(1)
            .check({ output: String ->
                assertTrue(output.contains(DirectToastMakeTextUsage.ID))
                assertTrue(output.contains(DirectToastMakeTextUsage.DESCRIPTION))
            })
    }

    @Test
    fun allowsUsageForToastWrapper() {
        lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(create(stubToast), create(javaFileWithToastWrapper))
            .issues(DirectToastMakeTextUsage.ISSUE)
            .run()
            .expectClean()
    }
}
