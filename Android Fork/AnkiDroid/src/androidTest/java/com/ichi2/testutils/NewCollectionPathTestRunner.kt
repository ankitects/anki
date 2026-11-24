// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/**
 * A test runner which sets [com.ichi2.anki.common.utils.android.isInstrumentationTest] to true
 * so a test collection path is used
 */
@Suppress("unused") // referenced by build.gradle
class NewCollectionPathTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader?,
        className: String?,
        context: Context?,
    ): Application = super.newApplication(cl, TestingApplication::class.java.name, context)
}
