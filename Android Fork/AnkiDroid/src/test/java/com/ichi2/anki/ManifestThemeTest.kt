// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.compat.CompatHelper.Companion.getPackageInfoCompat
import com.ichi2.anki.compat.GET_ACTIVITIES_L
import com.ichi2.anki.compat.PackageInfoFlagsCompat
import com.ichi2.testutils.AndroidTest
import com.ichi2.testutils.EmptyApplication
import com.ichi2.testutils.targetContext
import com.ichi2.themes.Themes
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.empty
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * [Themes.setTheme] applies the user's theme in `onCreate`, so activities should not set
 * `android:theme` in the manifest. A manifest theme which differs from the application theme:
 *
 * - briefly displays the wrong theme in the starting window
 * - resolves `android:windowBackground` if the window is accessed before [Themes.setTheme]
 *   the stale background is then visible behind transparent content
 */
@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class ManifestThemeTest : AndroidTest {
    /**
     * Manifest themes which are intentional. Additions to this list should explain why the
     * activity cannot use the application theme.
     */
    private val allowedThemes: Map<String, Int> =
        mapOf(
            // launcher splash background; replaced by Themes.setTheme (`hadLauncherSplash`)
            "com.ichi2.anki.DeckPicker" to R.style.Theme_AnkiDroid_Launcher,
            "com.ichi2.anki.CardBrowser" to R.style.Theme_AnkiDroid_Launcher,
            "com.ichi2.anki.Reviewer" to R.style.Theme_AnkiDroid_Launcher,
            "com.ichi2.anki.IntentHandler2" to R.style.Theme_AnkiDroid_Launcher,
            // invisible trampoline: forwards intents without showing UI
            "com.ichi2.anki.IntentHandler" to android.R.style.Theme_Translucent_NoTitleBar,
            // ACRA crash report dialog
            "com.ichi2.anki.analytics.AnkiDroidCrashReportDialog" to android.R.style.Theme_DeviceDefault_Dialog,
            // transparent window: the editor is displayed as a dialog over the caller
            "com.ichi2.anki.instantnoteeditor.InstantNoteEditorActivity" to R.style.Theme_AppCompat_Transparent_NoActionBar,
        )

    @Test
    fun `activities use the application theme`() {
        val flags = PackageInfoFlagsCompat.of(GET_ACTIVITIES_L)
        val packageInfo =
            targetContext.getPackageInfoCompat(targetContext.packageName, flags)
                ?: throw IllegalStateException("getPackageInfo failed")
        val activities = packageInfo.activities ?: throw IllegalStateException("activity list")

        val violations =
            activities
                .filter { it.name.startsWith("com.ichi2") }
                // an <activity-alias> takes its theme from its target
                .filter { it.targetActivity == null }
                .filter { it.theme != (allowedThemes[it.name] ?: 0) }
                .map { "${it.name}: expected ${themeName(allowedThemes[it.name] ?: 0)}, was ${themeName(it.theme)}" }

        assertThat(
            "Activities should not set android:theme in the manifest: Themes.setTheme " +
                "applies the user's theme (see the class documentation of this test). " +
                "If a manifest theme is genuinely required, add it to allowedThemes with an explanation",
            violations,
            empty(),
        )
    }

    private fun themeName(resId: Int): String =
        if (resId == 0) {
            "no manifest theme"
        } else {
            runCatching { targetContext.resources.getResourceName(resId) }
                .getOrDefault("0x%08x".format(resId))
        }
}
