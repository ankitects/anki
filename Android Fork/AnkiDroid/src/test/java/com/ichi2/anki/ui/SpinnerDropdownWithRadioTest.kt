// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui

import android.content.Context
import android.graphics.Rect
import android.view.LayoutInflater
import android.widget.CheckedTextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.common.utils.dp
import com.ichi2.testutils.createRtlContext
import com.ichi2.themes.Themes
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test of [R.layout.item_spinner_dropdown_with_radio]
 *
 * https://github.com/ankidroid/Anki-Android/issues/21030
 */
@RunWith(AndroidJUnit4::class)
class SpinnerDropdownWithRadioTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    /**
     * A [CheckedTextView] pads its text by exactly the check mark's intrinsic width,
     * so a gap between the text and the visible indicator can only come from padding
     * declared on the text side of the check mark drawable itself.
     */
    @Test
    fun `text does not touch the radio indicator`() {
        val view = inflateDropdownItem(context)
        val padding = Rect().also { view.checkMarkDrawable!!.getPadding(it) }
        assertThat(
            "check mark drawable should reserve space on its left (text) side",
            padding.left,
            greaterThanOrEqualTo(8.dp.toPx(context)),
        )
    }

    /** In RTL layouts, the check mark sits at the start, so the gap belongs on its right. */
    @Test
    fun `text does not touch the radio indicator in RTL`() {
        val view = inflateDropdownItem(context.createRtlContext())
        val padding = Rect().also { view.checkMarkDrawable!!.getPadding(it) }
        assertThat(
            "check mark drawable should reserve space on its right (text) side in RTL",
            padding.right,
            greaterThanOrEqualTo(8.dp.toPx(context)),
        )
    }

    private fun inflateDropdownItem(context: Context): CheckedTextView {
        Themes.setTheme(context)
        return LayoutInflater
            .from(context)
            .inflate(R.layout.item_spinner_dropdown_with_radio, null) as CheckedTextView
    }
}
