// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.utils

import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.EmptyApplicationCategory
import com.ichi2.anki.utils.ext.DIALOG_FRAGMENT_TAG
import com.ichi2.testutils.EmptyApplication
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class DialogTest {
    @Test
    fun `dismissing a replacement dialog after recreation does not restore a dismissed dialog`() {
        Robolectric.buildActivity(FragmentActivity::class.java).setup().use { controller ->
            val manager = controller.get().supportFragmentManager
            val first = DialogFragment()
            showDialogFragmentImpl(manager, first)

            first.dismiss()
            showDialogFragmentImpl(manager, DialogFragment())

            controller.recreate()
            val restoredManager = controller.get().supportFragmentManager
            val restoredDialog = restoredManager.findFragmentByTag(DIALOG_FRAGMENT_TAG) as DialogFragment
            restoredDialog.dismiss()
            restoredManager.executePendingTransactions()

            assertEquals(0, restoredManager.backStackEntryCount)
            assertNull(restoredManager.findFragmentByTag(DIALOG_FRAGMENT_TAG))
        }
    }

    @Test
    fun `dismissing a replacement dialog does not reopen a dismissed dialog`() {
        Robolectric.buildActivity(FragmentActivity::class.java).setup().use { controller ->
            val manager = controller.get().supportFragmentManager
            val first = DialogFragment()
            showDialogFragmentImpl(manager, first)

            first.dismiss()
            val replacement = DialogFragment()
            showDialogFragmentImpl(manager, replacement)

            assertSame(replacement, manager.findFragmentByTag(DIALOG_FRAGMENT_TAG))
            replacement.dismiss()
            manager.executePendingTransactions()

            assertEquals(0, manager.backStackEntryCount)
            assertNull(manager.findFragmentByTag(DIALOG_FRAGMENT_TAG))
        }
    }

    @Test
    fun `dismissing a nested dialog after recreation restores the parent dialog`() {
        Robolectric.buildActivity(FragmentActivity::class.java).setup().use { controller ->
            val manager = controller.get().supportFragmentManager
            showDialogFragmentImpl(manager, DialogFragment())
            showDialogFragmentImpl(manager, DialogFragment())
            assertEquals(2, manager.backStackEntryCount)

            controller.recreate()
            val restoredManager = controller.get().supportFragmentManager
            val nestedDialog = restoredManager.findFragmentByTag(DIALOG_FRAGMENT_TAG) as DialogFragment
            nestedDialog.dismiss()
            restoredManager.executePendingTransactions()

            assertEquals(1, restoredManager.backStackEntryCount)
            val parentDialog = restoredManager.findFragmentByTag(DIALOG_FRAGMENT_TAG) as DialogFragment
            assertTrue(parentDialog.requireDialog().isShowing)
            parentDialog.dismiss()
            restoredManager.executePendingTransactions()

            assertEquals(0, restoredManager.backStackEntryCount)
            assertNull(restoredManager.findFragmentByTag(DIALOG_FRAGMENT_TAG))
        }
    }
}
