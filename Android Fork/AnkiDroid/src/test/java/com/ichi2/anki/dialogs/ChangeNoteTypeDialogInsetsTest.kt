// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs

import android.os.Bundle
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.testing.launchFragment
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.utils.dp
import com.ichi2.anki.dialogs.ChangeNoteTypeDialog.Companion.ARG_NOTE_IDS
import com.ichi2.anki.settings.enums.DayTheme
import com.ichi2.anki.settings.enums.NightTheme
import com.ichi2.anki.settings.enums.Theme
import com.ichi2.testutils.windowInsetsOf
import com.ichi2.themes.Themes
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChangeNoteTypeDialogInsetsTest : RobolectricTest() {
    @Test
    fun `dialog clears system bars and cutouts`() =
        withDialog { dialog ->
            val insets =
                with(targetContext) {
                    windowInsetsOf(navBarBottom = 48.dp, navBarRight = 48.dp, cutoutLeft = 32.dp)
                }
            ViewCompat.dispatchApplyWindowInsets(dialog.requireDialog().window!!.decorView, insets)

            val root = dialog.requireView()
            assertThat(root.paddingLeft, equalTo(32.dp.toPx(targetContext)))
            assertThat(root.paddingTop, equalTo(24.dp.toPx(targetContext)))
            assertThat(root.paddingRight, equalTo(48.dp.toPx(targetContext)))
            assertThat(root.paddingBottom, equalTo(48.dp.toPx(targetContext)))
        }

    @Test
    fun `status bar icons are dark over a light theme`() =
        withDialog(DayTheme.LIGHT) { dialog ->
            assertThat(dialog.hasLightStatusBars, equalTo(true))
        }

    @Test
    fun `status bar icons stay light over a dark theme`() =
        withDialog(NightTheme.DARK) { dialog ->
            assertThat(dialog.hasLightStatusBars, equalTo(false))
        }

    private val ChangeNoteTypeDialog.hasLightStatusBars: Boolean
        get() {
            val window = requireDialog().window!!
            return WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars
        }

    private fun withDialog(
        theme: Theme = DayTheme.LIGHT,
        block: (ChangeNoteTypeDialog) -> Unit,
    ) {
        val previousTheme = Themes.currentTheme
        Themes.currentTheme = theme
        try {
            launchFragment<ChangeNoteTypeDialog>(
                fragmentArgs = Bundle().apply { putLongArray(ARG_NOTE_IDS, longArrayOf(addBasicNote().id)) },
                themeResId = theme.styleResId,
            ).use { scenario -> scenario.onFragment(block) }
        } finally {
            Themes.currentTheme = previousTheme
        }
    }
}
