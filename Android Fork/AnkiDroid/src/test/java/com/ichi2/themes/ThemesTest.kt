// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.themes

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.TypedValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.StudyOptionsActivity
import com.ichi2.anki.settings.PrefsRepository
import com.ichi2.anki.settings.enums.AppTheme
import com.ichi2.anki.settings.enums.DayTheme
import com.ichi2.anki.settings.enums.NightTheme
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import kotlin.test.assertFailsWith
import com.ichi2.testutils.Robolectric as RobolectricActivities

@RunWith(AndroidJUnit4::class)
class ThemesTest : RobolectricTest() {
    /**
     * If the decor view is created before [Themes.setTheme] applies the user's theme, the theme's
     * windowBackground is not applied and night mode renders a white background (issue 21520).
     * Ensure this fails fast in debug builds rather than rendering incorrectly.
     */
    @Test
    fun `decor view access before setTheme fails fast`() {
        // create() throws before the controller leaves INITIAL, so ActivityController.close() would
        // no-op and leave the activity registered — register for tearDown's fallback cleanup.
        val controller = Robolectric.buildActivity(EarlyDecorViewInitActivity::class.java)
        saveControllerForCleanup(controller)
        val exception =
            assertFailsWith<IllegalStateException> {
                controller.create()
            }
        assertThat(exception.message, containsString("setTheme"))
    }

    @Test
    fun `window background follows the night theme - issue 21520`() {
        // Check for leftovers before applying the night theme, which can recreate retained
        // AppCompat activities.
        RobolectricActivities.assertNoLiveActivities("ThemesTest before setQualifiers(+night)")
        RuntimeEnvironment.setQualifiers("+night")
        PrefsRepository(targetContext).apply {
            appTheme = AppTheme.NIGHT
            nightTheme = NightTheme.DARK
        }
        val activity =
            startActivityNormallyOpenCollectionWithIntent(
                StudyOptionsActivity::class.java,
                Intent(),
            )

        val tv = TypedValue()
        activity.theme.resolveAttribute(android.R.attr.windowBackground, tv, true)
        assertThat(
            "the theme's windowBackground is expected to be a plain color",
            tv.type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT,
            equalTo(true),
        )
        val decorBackground = activity.window.decorView.background
        assertThat((decorBackground as ColorDrawable).color, equalTo(tv.data))
    }

    /**
     * When an activity is relaunched (e.g. after a day/night theme change), the framework may
     * preserve the window: the decor view already exists before `super.onCreate`, and the
     * framework refreshes its `windowBackground` itself, so [Themes.setTheme] should not fail
     * (issue 21548).
     */
    @Test
    fun `recreated activity with an existing decor view does not fail - issue 21548`() {
        Robolectric.buildActivity(EarlyDecorViewInitActivity::class.java).use { controller ->
            controller.create(Bundle())
        }
    }

    @Test
    fun `Material You developer option applies the dynamic colors`() {
        PrefsRepository(targetContext).devMaterialYouEnabled = true
        assertThat(primaryColorResourceName(), containsString("dynamic"))
    }

    @Test
    fun `Material You developer option is not applied to E-Ink`() {
        PrefsRepository(targetContext).apply {
            devMaterialYouEnabled = true
            appTheme = AppTheme.DAY
            dayTheme = DayTheme.EINK
        }
        assertThat(primaryColorResourceName(), not(containsString("dynamic")))
    }

    private fun primaryColorResourceName(): String =
        Robolectric.buildActivity(AnkiActivity::class.java).use { controller ->
            val activity = controller.create().get()
            val tv = TypedValue()
            activity.theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, tv, false)
            activity.resources.getResourceEntryName(tv.data)
        }

    /** simulates e.g. an [androidx.activity.enableEdgeToEdge] call before `super.onCreate` */
    class EarlyDecorViewInitActivity : AnkiActivity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            window.decorView
            super.onCreate(savedInstanceState)
        }
    }
}
