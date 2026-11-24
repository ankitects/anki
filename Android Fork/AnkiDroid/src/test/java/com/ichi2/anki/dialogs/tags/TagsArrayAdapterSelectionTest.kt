// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki.dialogs.tags

import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.testutils.RecyclerViewUtils
import com.ichi2.ui.CheckBoxTriStates.State.INDETERMINATE
import com.ichi2.ui.CheckBoxTriStates.State.UNCHECKED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import com.ichi2.anki.common.android.R as CommonR

@RunWith(AndroidJUnit4::class)
class TagsArrayAdapterSelectionTest : RobolectricTest() {
    private inner class TagSelection(
        val tags: TagsList,
        val recycler: RecyclerView,
        val adapter: TagsArrayAdapter,
    ) {
        fun holder(tag: String): TagsArrayAdapter.ViewHolder {
            recycler.measure(0, 0)
            recycler.layout(0, 0, 800, 2000)
            return (0 until adapter.itemCount)
                .map { RecyclerViewUtils.viewHolderAt<TagsArrayAdapter.ViewHolder>(recycler, it) }
                .first { it.text == tag }
        }

        fun click(tag: String) = holder(tag).checkBoxView.performClick()

        fun filter(text: String) {
            adapter.filter.filter(text)
            advanceRobolectricLooper()
        }
    }

    private fun withTags(
        tags: TagsList,
        block: TagSelection.() -> Unit,
    ) {
        Robolectric.buildActivity(FragmentActivity::class.java).use { controller ->
            controller.get().setTheme(CommonR.style.Theme_AnkiDroid)
            val activity = controller.setup().get()
            val recycler = RecyclerView(activity)
            recycler.layoutManager = LinearLayoutManager(activity)
            val adapter = TagsArrayAdapter(tags) {}
            recycler.adapter = adapter
            activity.setContentView(recycler)
            TagSelection(tags, recycler, adapter).block()
        }
    }

    @Test
    fun `filtering does not hide partially selected siblings from parent state`() {
        withTags(TagsList(listOf("B::one", "B::two"), listOf("B::one", "B::two"), listOf("B::one", "B::two"))) {
            filter("one")
            click("B::one")
            assertEquals(INDETERMINATE, holder("B").checkboxState)
            filter("")
            assertEquals(INDETERMINATE, holder("B").checkboxState)
            assertEquals(INDETERMINATE, holder("B::two").checkboxState)
        }
    }

    @Test
    fun `unbound parents update their model state`() {
        withTags(TagsList(listOf("B::child"), listOf("B::child"), listOf("B::child"))) {
            val parent = holder("B")
            adapter.onViewRecycled(parent)
            click("B::child")
            assertFalse(tags.isIndeterminate("B"))
            adapter.onBindViewHolder(parent, 0)
            assertEquals(UNCHECKED, parent.checkboxState)
        }
    }
}
