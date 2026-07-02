// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2020 lukstbit <52494258+lukstbit@users.noreply.github.com>

package com.ichi2.anki.lint.rules

import com.android.tools.lint.checks.infrastructure.TestFile.JavaTestFile.create
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.intellij.lang.annotations.Language
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectSystemTimeInstantiationTest {
    @Language("JAVA")
    private val stubTime = """                         
package com.ichi2.anki.libanki.utils;                 
                                                 
public abstract class Time {                     
                                                 
}                                                
"""

    @Language("JAVA")
    private val stubSystemTime = """                   
package com.ichi2.anki.libanki.utils;                 
                                                 
public class SystemTime extends Time {           
                                                 
    public SystemTime() {                        
    }                                            
                                                 
}                                                
"""

    @Language("JAVA")
    private val javaFileToBeTested = """               
package com.ichi2.anki.lint.rules;               
                                                 
import com.ichi2.anki.libanki.utils.SystemTime;       
                                                 
public class TestJavaClass {                     
                                                 
    public static void main(String[] args) {     
        SystemTime st = new SystemTime();        
    }                                            
}                                                
"""

    @Language("JAVA")
    private val javaFileWithStorage = """              
package com.ichi2.anki.lint.rules;               
                                                 
import com.ichi2.anki.libanki.utils.SystemTime;       
                                                 
public class Storage {                           
                                                 
    public static void main(String[] args) {     
        SystemTime st = new SystemTime();        
    }                                            
}                                                
"""

    @Language("JAVA")
    private val javaFileWithCollectionHelper = """     
package com.ichi2.anki.lint.rules;               
                                                 
import com.ichi2.anki.libanki.utils.SystemTime;       
                                                 
public class CollectionHelper {                  
                                                 
    public static void main(String[] args) {     
        SystemTime st = new SystemTime();        
    }                                            
}                                                
"""

    @Test
    fun showsErrorsForInvalidUsage() {
        lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(
                create(stubTime),
                create(stubSystemTime),
                create(javaFileToBeTested),
            ).issues(DirectSystemTimeInstantiation.ISSUE)
            .run()
            .expectErrorCount(1)
            .check({ output: String ->
                assertTrue(output.contains(DirectSystemTimeInstantiation.ID))
                assertTrue(output.contains(DirectSystemTimeInstantiation.DESCRIPTION))
            })
    }

    @Test
    fun doesNotShowErrorsWhenUsedInStorage() {
        lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(
                create(stubTime),
                create(stubSystemTime),
                create(javaFileWithStorage),
            ).issues(DirectSystemTimeInstantiation.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun doesNotShowErrorsWhenUsedInCollectionHelper() {
        lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(
                create(stubTime),
                create(stubSystemTime),
                create(javaFileWithCollectionHelper),
            ).issues(DirectSystemTimeInstantiation.ISSUE)
            .run()
            .expectClean()
    }
}
