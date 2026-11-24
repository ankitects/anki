// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.gradle

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

// see .editorconfig for ktlint-related rules
fun Project.configureAndroidLint() {
    extensions.configure<CommonExtension> {
        lint.apply {
            abortOnError = true
            checkReleaseBuilds = true
            checkTestSources = true
            explainIssues = false
            lintConfig = rootProject.file("lint-release.xml")
            showAll = true
            // To output the lint report to stdout set textReport=true, and leave textOutput unset.
            textReport = true
            warningsAsErrors = true

            // Only relevant to lint implementations; these modules do not use UAST/PSI APIs.
            disable += "UElementAsPsi"

            if (System.getenv("CI") == "true") {
                // 14853: we want this to appear in the IDE, but it adds noise to CI
                disable += "WrongThread"
                disable += "ThreadConstraint"
            }
        }
    }
}
