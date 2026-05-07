/*
 *  Copyright (c) 2021 Almas Ahmad <ahmadalmas.786.aa@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.lint.rules

import com.android.tools.lint.checks.infrastructure.TestFile.JavaTestFile.create
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import com.google.common.annotations.Beta
import org.intellij.lang.annotations.Language
import org.junit.Assert.assertTrue
import org.junit.Test

/** Test for [CopyrightHeaderExists] */
@Suppress("UnstableApiUsage")
@Beta
class CopyrightHeaderExistsTest {
    @Language("JAVA")
    private val copyrightHeader = """/*
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */"""

    @Language("JAVA")
    private val spdxCopyrightHeader =
        """// SPDX-FileCopyrightText: 2025 David Allison <david@example.com>
// SPDX-License-Identifier: GPL-3.0-or-later"""

    @Language("JAVA")
    private val spdxLgplCopyrightHeader =
        """// SPDX-FileCopyrightText: 2025 David Allison <david@example.com>
// SPDX-License-Identifier: LGPL-3.0-or-later"""

    // invalid
    @Language("JAVA")
    private val spdxOldGplHeader =
        """// SPDX-FileCopyrightText: 2025 David Allison <david@example.com>
// SPDX-License-Identifier: GPL-3.0"""

    @Language("JAVA")
    private val spdxGplOnlyHeader =
        """// SPDX-FileCopyrightText: 2025 David Allison <david@example.com>
// SPDX-License-Identifier: GPL-3.0-only"""

    // invalid
    @Language("JAVA")
    private val spdxLgplOnlyHeader =
        """// SPDX-FileCopyrightText: 2025 David Allison <ddavid@example.com>
// SPDX-License-Identifier: LGPL-3.0"""

    @Language("JAVA")
    private val noCopyrightHeader =
        """
        
        package com.ichi2.upgrade;
        
        import com.ichi2.anki.libanki.Collection;
        
        import org.json.JSONException;
        import org.json.JSONObject;
        
        import timber.log.Timber;
        
        public class Upgrade {
        }
        """.trimIndent()

    @Test
    fun fileWithCopyrightHeaderPasses() {
        lint()
            .allowMissingSdk()
            .files(create(copyrightHeader))
            .issues(CopyrightHeaderExists.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun fileWithSpdxCopyrightHeaderPasses() {
        lint()
            .allowMissingSdk()
            .files(create(spdxCopyrightHeader))
            .issues(CopyrightHeaderExists.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun fileWithSpdxLgplCopyrightHeaderPasses() {
        lint()
            .allowMissingSdk()
            .files(create(spdxLgplCopyrightHeader))
            .issues(CopyrightHeaderExists.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun fileWithSpdxGplOldHeaderFails() {
        lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(create(spdxOldGplHeader))
            .issues(CopyrightHeaderExists.ISSUE)
            .run()
            .expectErrorCount(1)
    }

    @Test
    fun fileWithSpdxGplOnlyHeaderFails() {
        lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(create(spdxGplOnlyHeader))
            .issues(CopyrightHeaderExists.ISSUE)
            .run()
            .expectErrorCount(1)
    }

    @Test
    fun fileWithSpdxLgplOnlyHeaderFails() {
        lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(create(spdxLgplOnlyHeader))
            .issues(CopyrightHeaderExists.ISSUE)
            .run()
            .expectErrorCount(1)
    }

    @Test
    fun fileWithNoCopyrightHeaderFails() {
        lint()
            .allowMissingSdk()
            .allowCompilationErrors() // import failures
            .files(create(noCopyrightHeader))
            .issues(CopyrightHeaderExists.ISSUE)
            .run()
            .expectErrorCount(1)
            .check({ output: String ->
                assertTrue(output.contains(CopyrightHeaderExists.ID))
                assertTrue(output.contains(CopyrightHeaderExists.DESCRIPTION))
            })
    }
}
