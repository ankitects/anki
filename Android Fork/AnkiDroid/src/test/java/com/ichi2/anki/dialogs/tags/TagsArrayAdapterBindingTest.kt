// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki.dialogs.tags

import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.testutils.RecyclerViewUtils
import com.ichi2.ui.CheckBoxTriStates.State.CHECKED
import com.ichi2.ui.CheckBoxTriStates.State.UNCHECKED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import com.ichi2.anki.common.android.R as CommonR

@RunWith(AndroidJUnit4::class)
class TagsArrayAdapterBindingTest : RobolectricTest() {
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
    fun `recycled parent holder does not change an unrelated checkbox`() {
        withTags(TagsList(listOf("B::child", "Z"), listOf("B::child", "Z"))) {
            val parent = holder("B")
            adapter.onViewRecycled(parent)
            adapter.onBindViewHolder(parent, 2)
            assertEquals("Z", parent.text)
            assertEquals(CHECKED, parent.checkboxState)
            click("B::child")
            assertTrue(tags.isChecked("Z"))
            assertEquals(CHECKED, parent.checkboxState)
        }
    }

    @Test
    fun `rebinding without recycling detaches the old parent holder`() {
        withTags(TagsList(listOf("B::child", "Z"), listOf("B::child", "Z"))) {
            val parent = holder("B")
            adapter.onBindViewHolder(parent, 2)
            assertEquals("Z", parent.text)
            click("B::child")
            assertEquals(CHECKED, parent.checkboxState)
        }
    }

    @Test
    fun `recycling an old holder preserves the replacement binding`() {
        withTags(TagsList(listOf("B::child"), listOf("B::child"))) {
            val parent = holder("B")
            val replacement = adapter.onCreateViewHolder(recycler, 0)
            adapter.onBindViewHolder(replacement, 0)
            adapter.onViewRecycled(parent)
            click("B::child")
            assertEquals(UNCHECKED, replacement.checkboxState)
        }
    }
}
