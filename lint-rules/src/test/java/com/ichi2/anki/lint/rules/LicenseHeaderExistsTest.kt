// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Almas Ahmad <ahmadalmas.786.aa@gmail.com>

package com.ichi2.anki.lint.rules

import com.android.tools.lint.checks.infrastructure.ProjectDescription
import com.android.tools.lint.checks.infrastructure.TestFile.JavaTestFile.create
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.intellij.lang.annotations.Language
import org.junit.Assert.assertTrue
import org.junit.Test

/** Test for [LicenseHeaderExists] */
class LicenseHeaderExistsTest {
    @Language("JAVA")
    private val fileWithLicense = """/*
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
    private val spdxGplHeader =
        "// SPDX-License-Identifier: GPL-3.0-or-later"

    @Language("JAVA")
    private val spdxLgplHeader =
        "// SPDX-License-Identifier: LGPL-3.0-or-later"

    @Language("JAVA")
    private val spdxApacheHeader =
        "// SPDX-License-Identifier: Apache-2.0"

    // invalid
    @Language("JAVA")
    private val spdxOldGplHeader =
        "// SPDX-License-Identifier: GPL-3.0"

    @Language("JAVA")
    private val spdxGplOnlyHeader =
        "// SPDX-License-Identifier: GPL-3.0-only"

    // invalid
    @Language("JAVA")
    private val spdxLgplOnlyHeader =
        "// SPDX-License-Identifier: LGPL-3.0"

    @Language("JAVA")
    private val spdxGplAndCopyrightHeader =
        """// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2025 David Allison <david@example.com>"""

    @Language("JAVA")
    private val noHeader =
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
    fun fileWithLicensePasses() {
        lint()
            .allowMissingSdk()
            .files(create(fileWithLicense))
            .issues(LicenseHeaderExists.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun fileWithSpdxLicenseHeaderPasses() {
        lint()
            .allowMissingSdk()
            .files(create(spdxGplHeader))
            .issues(LicenseHeaderExists.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun fileWithSpdxLgplLicenseHeaderPasses() {
        lint()
            .allowMissingSdk()
            .files(create(spdxLgplHeader))
            .issues(LicenseHeaderExists.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun fileWithSpdxApacheLicenseHeaderPasses() {
        lint()
            .allowMissingSdk()
            .files(create(spdxApacheHeader))
            .issues(LicenseHeaderExists.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun fileWithGplAndCopyrightHeaderPasses() {
        lint()
            .allowMissingSdk()
            .files(create(spdxGplAndCopyrightHeader))
            .issues(LicenseHeaderExists.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun fileWithSpdxGplOldHeaderFails() {
        lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(create(spdxOldGplHeader))
            .issues(LicenseHeaderExists.ISSUE)
            .run()
            .expectErrorCount(1)
    }

    @Test
    fun fileWithSpdxGplOnlyHeaderFails() {
        lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(create(spdxGplOnlyHeader))
            .issues(LicenseHeaderExists.ISSUE)
            .run()
            .expectErrorCount(1)
    }

    @Test
    fun fileWithSpdxLgplOnlyHeaderFails() {
        lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(create(spdxLgplOnlyHeader))
            .issues(LicenseHeaderExists.ISSUE)
            .run()
            .expectErrorCount(1)
    }

    @Test
    fun fileWithNoLicenseHeaderFails() {
        lint()
            .allowMissingSdk()
            .allowCompilationErrors() // import failures
            .files(create(noHeader))
            .issues(LicenseHeaderExists.ISSUE)
            .run()
            .expectErrorCount(1)
            .check({ output: String ->
                assertTrue(output.contains(LicenseHeaderExists.ID))
                assertTrue(output.contains(LicenseHeaderExists.DESCRIPTION))
            })
    }

    @Test
    fun autofixPrependsSpdxIdentifier() {
        lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(create(noHeader))
            .issues(LicenseHeaderExists.ISSUE)
            .run()
            .expectFixDiffs(
                """
                Autofix for src/com/ichi2/upgrade/Upgrade.java line 1: Add SPDX-License-Identifier: GPL-3.0-or-later:
                @@ -1 +1
                + // SPDX-License-Identifier: GPL-3.0-or-later
                +
                """.trimIndent(),
            )
    }

    @Test
    fun autofixInApiModuleUsesLgpl() {
        lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .projects(ProjectDescription(create(noHeader)).name("api"))
            .issues(LicenseHeaderExists.ISSUE)
            .run()
            .expectFixDiffs(
                """
                Autofix for src/com/ichi2/upgrade/Upgrade.java line 1: Add SPDX-License-Identifier: LGPL-3.0-or-later:
                @@ -1 +1
                + // SPDX-License-Identifier: LGPL-3.0-or-later
                +
                """.trimIndent(),
            )
    }
}
