// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Tarek Mohamed <tarekkma@gmail.com>

package com.ichi2.anki.dialogs.tags

import com.ichi2.anki.dialogs.tags.TagTree.State.CHECKED
import com.ichi2.anki.dialogs.tags.TagTree.State.INDETERMINATE
import com.ichi2.anki.dialogs.tags.TagTree.State.UNCHECKED
import com.ichi2.utils.TagsUtil
import com.ichi2.utils.TagsUtil.getUpdatedTags
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TagTreeTest {
    private class Selection(
        val tags: TagsList,
    ) {
        val tree = TagTree(tags)
        val visibleTags: List<String> get() = (0 until tree.size).map { tree[it]!!.tag }

        fun node(tag: String) = (0 until tree.size).map { tree[it]!! }.first { it.tag == tag }

        fun state(tag: String) = tree.state(node(tag))

        fun click(tag: String) = tree.toggleChecked(node(tag))

        fun expand(tag: String) = tree.toggleExpanded(node(tag))

        fun filter(
            text: String,
            expandTarget: String = "",
        ) {
            tree.publishResults(tree.filterResults(text, tags.toList()), expandTarget)
        }

        fun addTag(rawTag: String) {
            val tag = TagsUtil.getUniformedTag(rawTag)
            tags.add(tag)
            tags.check(tag)
            tags.sort()
            filter("", expandTarget = tag)
        }

        fun bulkToggle() {
            tags.toggleAllCheckedStatuses()
            tree.refreshSelection()
        }

        fun saved(previous: List<String>) =
            getUpdatedTags(previous, tags.copyOfCheckedTagList(), tags.copyOfPartiallySelectedTagList()).sorted()

        fun assertStates(vararg expected: Pair<String, TagTree.State>) {
            assertEquals(expected.toList(), visibleTags.map { it to state(it) })
        }
    }

    private fun withTags(
        all: List<String>,
        checked: List<String> = emptyList(),
        block: Selection.() -> Unit,
    ) = Selection(TagsList(all, checked)).block()

    private fun withPartialTags(
        first: String,
        second: String = "",
        block: Selection.() -> Unit,
    ) {
        val firstTags = first.split(' ').filter { it.isNotEmpty() }
        val secondTags = second.split(' ').filter { it.isNotEmpty() }
        val all = firstTags + secondTags
        Selection(TagsList(all, all, all.filter { it !in firstTags || it !in secondTags })).block()
    }

    @Test
    fun `adding a new tag selects and sorts it before unchecked tags`() =
        withTags(listOf("a", "b", "d", "e"), listOf("a", "b")) {
            addTag("zzzz")
            assertEquals(listOf("a", "b", "zzzz", "d", "e"), visibleTags)
            assertEquals(CHECKED, state("zzzz"))
            assertEquals(UNCHECKED, state("e"))
        }

    @Test
    fun `adding an existing tag selects and sorts it`() =
        withTags(listOf("a", "b", "d", "e"), listOf("a", "b")) {
            addTag("e")
            assertEquals(listOf("a", "b", "e", "d"), visibleTags)
            assertEquals(CHECKED, state("e"))
            assertEquals(UNCHECKED, state("d"))
        }

    @Test
    fun `unchecking parent after check all keeps it indeterminate`() =
        withTags(listOf("1", "1::2"), listOf("1")) {
            bulkToggle()
            click("1")
            assertEquals(INDETERMINATE, state("1"))
            assertEquals(CHECKED, state("1::2"))
            click("1::2")
            assertEquals(UNCHECKED, state("1"))
        }

    @Test
    fun `uncheck all clears the indeterminate cycle for parents`() =
        withTags(listOf("1", "1::2"), listOf("1", "1::2")) {
            bulkToggle()
            click("1")
            click("1")
            assertEquals(UNCHECKED, state("1"))
            assertEquals(UNCHECKED, state("1::2"))
        }

    @Test
    fun `partially selected hierarchical tags are visible at the top`() {
        val selected = listOf("B::B", "C::child::leaf")
        Selection(TagsList(selected + "aaa", selected, selected)).apply {
            assertStates(
                "B" to INDETERMINATE,
                "B::B" to INDETERMINATE,
                "C" to INDETERMINATE,
                "C::child" to INDETERMINATE,
                "C::child::leaf" to INDETERMINATE,
                "aaa" to UNCHECKED,
            )
        }
    }

    @Test
    fun `unchecking a partially selected child clears its parent and allows rechecking`() =
        withPartialTags("B::B") {
            click("B::B")
            assertEquals(UNCHECKED, state("B::B"))
            assertEquals(UNCHECKED, state("B"))
            click("B::B")
            assertEquals(CHECKED, state("B::B"))
            assertEquals(INDETERMINATE, state("B"))
            click("B::B")
            assertEquals(UNCHECKED, state("B"))
        }

    @Test
    fun `parent stays indeterminate until all partially selected children are unchecked`() =
        withPartialTags("B::one B::two") {
            click("B::one")
            assertEquals(INDETERMINATE, state("B"))
            click("B::two")
            assertEquals(UNCHECKED, state("B"))
        }

    @Test
    fun `unchecking a partially selected descendant clears every ancestor`() =
        withPartialTags("B::child::leaf") {
            click("B::child::leaf")
            assertEquals(UNCHECKED, state("B::child"))
            assertEquals(UNCHECKED, state("B"))
        }

    @Test
    fun `unchecking a child preserves a parent tag present on some notes`() =
        withPartialTags("B B::child") {
            click("B::child")
            assertEquals(INDETERMINATE, state("B"))
            click("B")
            assertEquals(UNCHECKED, state("B"))
        }

    @Test
    fun `explicitly changing a partially selected parent replaces its original selection`() =
        withPartialTags("B B::child") {
            click("B")
            assertEquals(CHECKED, state("B"))
            click("B")
            assertEquals(INDETERMINATE, state("B"))
            click("B::child")
            assertEquals(UNCHECKED, state("B"))
        }

    @Test
    fun `unchecking a fully selected child preserves partially selected siblings`() =
        withPartialTags("B::one B::two", "B::one") {
            click("B::one")
            assertEquals(INDETERMINATE, state("B"))
            assertEquals(INDETERMINATE, state("B::two"))
            click("B::two")
            assertEquals(UNCHECKED, state("B"))
        }

    @Test
    fun `checked unchecked and indeterminate states are exposed for each row`() =
        withTags(listOf("a", "b", "d", "e"), listOf("a", "b")) {
            assertStates("a" to CHECKED, "b" to CHECKED, "d" to UNCHECKED, "e" to UNCHECKED)
        }

    @Test
    fun `paths to checked tags are expanded upon opening`() =
        withTags(
            listOf("fruit::apple", "fruit::pear", "fruit::pear::big", "sport::football", "sport::tennis", "book"),
            listOf("fruit::pear::big", "sport::tennis"),
        ) {
            assertEquals(
                listOf("fruit", "fruit::apple", "fruit::pear", "fruit::pear::big", "sport", "sport::football", "sport::tennis", "book"),
                visibleTags,
            )
        }

    @Test
    fun `adding a hierarchical tag expands its path`() =
        withTags(
            listOf("common::speak", "common::speak::daily", "common::sport::tennis", "common::sport::football"),
            listOf("common::speak::daily", "common::sport::tennis"),
        ) {
            addTag("common::sport::football::small")
            assertStates(
                "common" to INDETERMINATE,
                "common::speak" to INDETERMINATE,
                "common::speak::daily" to CHECKED,
                "common::sport" to INDETERMINATE,
                "common::sport::football" to INDETERMINATE,
                "common::sport::football::small" to CHECKED,
                "common::sport::tennis" to CHECKED,
            )
        }

    @Test
    fun `adding a hierarchical tag normalizes empty parts`() =
        withTags(listOf("common"), listOf("common")) {
            addTag("common::::careless")
            assertStates("common" to CHECKED, "common::blank" to INDETERMINATE, "common::blank::careless" to CHECKED)
        }

    @Test
    fun `search shows matching tags and their ancestors`() =
        withTags(
            listOf(
                "common::speak",
                "common::speak::tennis",
                "common::sport::tennis",
                "common::sport::football",
                "common::sport::football::small",
            ),
            listOf("common::speak::tennis", "common::sport::tennis", "common::sport::football::small"),
        ) {
            filter("tennis")
            assertEquals(listOf("common", "common::speak", "common::speak::tennis", "common::sport", "common::sport::tennis"), visibleTags)
        }

    @Test
    fun `clearing the search inherits expansion state`() =
        withTags(listOf("common::speak", "common::sport::tennis")) {
            filter("sport")
            assertEquals(listOf("common", "common::sport", "common::sport::tennis"), visibleTags)
            filter("")
            assertEquals(listOf("common", "common::speak", "common::sport", "common::sport::tennis"), visibleTags)
        }

    @Test
    fun `intermediate tags toggle dynamically as descendants change`() =
        withTags(
            listOf(
                "common::speak",
                "common::speak::tennis",
                "common::sport::tennis",
                "common::sport::football",
                "common::sport::football::small",
            ),
        ) {
            expand("common")
            expand("common::speak")
            expand("common::sport")
            expand("common::sport::football")
            click("common::speak::tennis")
            click("common::sport::football::small")
            assertStates(
                "common" to INDETERMINATE,
                "common::speak" to INDETERMINATE,
                "common::speak::tennis" to CHECKED,
                "common::sport" to INDETERMINATE,
                "common::sport::football" to INDETERMINATE,
                "common::sport::football::small" to CHECKED,
                "common::sport::tennis" to UNCHECKED,
            )
            click("common::speak::tennis")
            click("common::sport::football::small")
            assertTrue(visibleTags.all { state(it) == UNCHECKED })
            click("common::sport::football::small")
            assertStates(
                "common" to INDETERMINATE,
                "common::speak" to UNCHECKED,
                "common::speak::tennis" to UNCHECKED,
                "common::sport" to INDETERMINATE,
                "common::sport::football" to INDETERMINATE,
                "common::sport::football::small" to CHECKED,
                "common::sport::tennis" to UNCHECKED,
            )
            click("common::sport")
            click("common::sport::football::small")
            assertStates(
                "common" to INDETERMINATE,
                "common::speak" to UNCHECKED,
                "common::speak::tennis" to UNCHECKED,
                "common::sport" to CHECKED,
                "common::sport::football" to UNCHECKED,
                "common::sport::football::small" to UNCHECKED,
                "common::sport::tennis" to UNCHECKED,
            )
        }

    @Test
    fun `filtering does not hide checked siblings from parent state`() {
        Selection(TagsList(listOf("B::one", "B::two"), listOf("B::one", "B::two"))).apply {
            filter("one")
            click("B::one")
            assertEquals(INDETERMINATE, state("B"))
        }
    }

    @Test
    fun `clearing a child preserves partially selected parent tags when saving`() {
        Selection(TagsList(listOf("B", "B::child"), listOf("B", "B::child"), listOf("B", "B::child"))).apply {
            click("B::child")
            assertEquals(listOf("B"), saved(listOf("B", "B::child")))
            assertEquals(emptyList<String>(), saved(emptyList()))
        }
    }

    @Test
    fun `synthetic parent states do not add tags when saving`() {
        Selection(TagsList(listOf("B::child"), listOf("B::child"), listOf("B::child"))).apply {
            assertEquals(listOf("B::child"), saved(listOf("B::child")))
            assertEquals(emptyList<String>(), saved(emptyList()))
            click("B::child")
            assertEquals(emptyList<String>(), saved(listOf("B::child")))
        }
    }

    @Test
    fun `bulk selection replaces original partial parent selection`() {
        Selection(TagsList(listOf("B", "B::child"), listOf("B", "B::child"), listOf("B", "B::child"))).apply {
            bulkToggle()
            bulkToggle()
            click("B::child")
            assertEquals(INDETERMINATE, state("B"))
            click("B::child")
            assertEquals(UNCHECKED, state("B"))
            assertEquals(emptyList<String>(), saved(listOf("B", "B::child")))
        }
    }

    @Test
    fun `clearing a partially selected child preserves checked parents`() {
        Selection(TagsList(listOf("B", "B::child"), listOf("B", "B::child"), listOf("B::child"))).apply {
            click("B::child")
            assertEquals(CHECKED, state("B"))
            assertEquals(listOf("B"), saved(listOf("B", "B::child")))
        }
    }

    @Test
    fun `clearing a child updates parents ignoring case`() {
        Selection(TagsList(listOf("b", "B::child"), listOf("B::CHILD"), listOf("b::child"))).apply {
            click("B::child")
            assertEquals(UNCHECKED, state("b"))
        }
    }

    @Test
    fun `parent tags are case-insensitive`() {
        val firstTag = "B::first"
        val secondTag = "b::second"
        val tagList = listOf(firstTag, secondTag)
        Selection(TagsList(tagList, tagList)).apply {
            val firstParentTag = node(firstTag).parent?.tag
            val secondParentTag = node(secondTag).parent?.tag
            assertEquals(firstParentTag, secondParentTag)
            assertEquals(1, node(firstTag).level)
            assertEquals(1, node(secondTag).level)
        }
    }
}
