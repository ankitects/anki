// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Alok Srivastava <alok020505@gmail.com>

package com.ichi2.anki.browser.search

import android.os.Looper
import android.widget.CheckBox
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.browser.withCardBrowserFragment
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** Test of [CardStateBottomSheetFragment] */
@RunWith(AndroidJUnit4::class)
class CardStateBottomSheetFragmentTest : RobolectricTest() {
    // TalkBack users must hear which card state a checkbox toggles, not just "checked"/"not checked"
    @Test
    fun `card state checkbox exposes its label to TalkBack`() =
        withCardBrowserFragment {
            CardStateBottomSheetFragment.show(childFragmentManager)
            shadowOf(Looper.getMainLooper()).idle()

            onView(withId(R.id.list)).inRoot(isDialog()).check { view, _ ->
                val recyclerView = view as RecyclerView
                val holder = requireNotNull(recyclerView.findViewHolderForAdapterPosition(0))
                val checkbox = holder.itemView.findViewById<CheckBox>(R.id.checkbox)
                // position 0 is CardState.New
                assertThat(checkbox.contentDescription, equalTo("New" as CharSequence))
            }
        }
}
