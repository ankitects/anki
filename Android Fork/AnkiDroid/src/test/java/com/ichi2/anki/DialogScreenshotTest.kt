// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.view.MenuItem
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.RecyclerView
import org.robolectric.Robolectric.buildActivity

/** Screenshot fixtures for dialogs that can be hosted in an otherwise empty activity. */
abstract class DialogScreenshotTest : ScreenshotTest() {
    protected inline fun <T : DialogFragment> withDialog(
        fragment: T,
        block: (T) -> Unit,
    ) {
        buildActivity(AnkiActivity::class.java).use { controller ->
            val activity = controller.setup().get()
            fragment.showNow(activity.supportFragmentManager, "screenshot_dialog")
            advanceRobolectricLooper()
            block(fragment)
        }
    }

    /** Expands search, waits for a query which narrows the list, and captures the results. */
    protected fun captureSearchResults(
        name: String,
        list: RecyclerView,
        searchItem: MenuItem,
        query: String,
    ) {
        val initialCount = list.adapter!!.itemCount
        searchItem.expandActionView()
        (searchItem.actionView as SearchView).setQuery(query, false)
        advanceRobolectricLooperUntil { list.adapter!!.itemCount in 1 until initialCount }
        captureScreen("${name}_search")
    }
}
