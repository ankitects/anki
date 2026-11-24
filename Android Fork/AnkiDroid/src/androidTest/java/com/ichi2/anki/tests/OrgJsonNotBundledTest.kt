// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.tests

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dalvik.system.DexFile
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.empty
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression test for https://github.com/ankidroid/Anki-Android/issues/20852
 */
@RunWith(AndroidJUnit4::class)
class OrgJsonNotBundledTest {
    @Test
    fun orgJsonIsNotBundledInApk() {
        val apkPath =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext
                .applicationInfo
                .sourceDir

        // DexFile is deprecated since API 26 but still functional
        // Reflection on BaseDexClassLoader.pathList is possible, but requires multiple lint
        // suppressions on private APIs.
        // We could pin this code to work on an earlier emulator if/when it's removed
        @Suppress("DEPRECATION")
        val orgJsonClasses =
            DexFile(apkPath)
                .entries()
                .toList()
                .filter { it.startsWith("org.json.") }

        assertThat(
            "`org.json` must come from android.jar, not be bundled in the APK. " +
                "Use `compileOnly(libs.json)` rather than `implementation(libs.json)`.",
            orgJsonClasses,
            empty(),
        )
    }
}
