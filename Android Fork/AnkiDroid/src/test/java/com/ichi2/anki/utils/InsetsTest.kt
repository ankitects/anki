// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.utils

import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.marginBottom
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InsetsTest {
    private val view =
        View(ApplicationProvider.getApplicationContext()).apply {
            setPadding(0, 0, 0, 84)
            layoutParams = ViewGroup.MarginLayoutParams(0, 0).apply { bottomMargin = 16 }
        }

    @Test
    fun `doOnApplyWindowInsets - insets add to the initial values, not the current ones`() {
        view.doOnApplyWindowInsets { v, insets, initial ->
            val bars = insets.getInsets(systemBars())
            v.updatePadding(bottom = initial.padding.bottom + bars.bottom)
            v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = initial.margins.bottom + bars.bottom
            }
        }

        repeat(2) { view.dispatchInsets(navBarBottom = 48) }

        assertThat(
            "padding is not compounded by the second dispatch",
            view.paddingBottom,
            equalTo(84 + 48),
        )
        assertThat(
            "margin is not compounded by the second dispatch",
            view.marginBottom,
            equalTo(16 + 48),
        )
    }

    private fun View.dispatchInsets(navBarBottom: Int) {
        val insets =
            WindowInsetsCompat
                .Builder()
                .setInsets(systemBars(), Insets.of(0, 0, 0, navBarBottom))
                .build()
        ViewCompat.dispatchApplyWindowInsets(this, insets)
    }
}
