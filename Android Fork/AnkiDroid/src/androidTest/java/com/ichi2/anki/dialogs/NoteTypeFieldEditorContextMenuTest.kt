// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs

import android.os.Bundle
import androidx.fragment.app.testing.launchFragment
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.dialogs.NoteTypeFieldEditorContextMenu.NoteTypeFieldEditorContextMenuAction
import com.ichi2.anki.tests.InstrumentedTest
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import com.ichi2.anki.common.android.R as CommonR

/** Tests [NoteTypeFieldEditorContextMenu] */
@RunWith(AndroidJUnit4::class)
class NoteTypeFieldEditorContextMenuTest : InstrumentedTest() {
    private val testDialogTitle = "test editor title"

    @Test
    @Ignore("flaky")
    fun showsAllOptions() {
        launchFragment(
            fragmentArgs = Bundle().apply { putString(NoteTypeFieldEditorContextMenu.ARG_LABEL, testDialogTitle) },
            themeResId = CommonR.style.Theme_AnkiDroid,
        ) { NoteTypeFieldEditorContextMenu() }
        onView(withText(testDialogTitle))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
        NoteTypeFieldEditorContextMenuAction.entries.forEach {
            onView(withText(it.actionTextId)).check(matches(isDisplayed()))
        }
    }
}
