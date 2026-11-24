// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.tags

import anki.tags.tagTreeNode
import com.ichi2.testutils.rules.IcuCaseFoldingRule
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.Rule
import org.junit.Test

/** Unit tests for [ManageTagsViewModel.flattenTree] (no collection needed) */
class FlattenTreeTest {
    @get:Rule
    val caseFolding = IcuCaseFoldingRule()

    /** Composes flatten + filter to match the old two-arg flattenTree behavior */
    private fun flattenAndFilter(
        root: anki.tags.TagTreeNode,
        searchQuery: String,
    ): List<TagListItemState> {
        val all = ManageTagsViewModel.flattenTree(root)
        return if (searchQuery.isBlank()) {
            ManageTagsViewModel.applyCollapsedVisibility(all)
        } else {
            ManageTagsViewModel.applySearchFilter(all, searchQuery)
        }
    }

    @Test
    fun `flattenTree with flat tags`() {
        val root =
            tagTreeNode {
                level = 0
                children.add(
                    tagTreeNode {
                        name = "biology"
                        level = 1
                    },
                )
                children.add(
                    tagTreeNode {
                        name = "chemistry"
                        level = 1
                    },
                )
                children.add(
                    tagTreeNode {
                        name = "physics"
                        level = 1
                    },
                )
            }
        val result = flattenAndFilter(root, "")
        MatcherAssert.assertThat(result, Matchers.hasSize(3))
        MatcherAssert.assertThat(result[0].fullTagName, Matchers.equalTo("biology"))
        MatcherAssert.assertThat(result[0].level, Matchers.equalTo(0))
        MatcherAssert.assertThat(result[0].hasChildren, Matchers.equalTo(false))
    }

    @Test
    fun `flattenTree with nested tags - collapsed`() {
        val root =
            tagTreeNode {
                level = 0
                children.add(
                    tagTreeNode {
                        name = "science"
                        level = 1
                        collapsed = true
                        children.add(
                            tagTreeNode {
                                name = "biology"
                                level = 2
                            },
                        )
                        children.add(
                            tagTreeNode {
                                name = "chemistry"
                                level = 2
                            },
                        )
                    },
                )
            }
        val result = flattenAndFilter(root, "")
        // only "science" visible since it's collapsed
        MatcherAssert.assertThat(result, Matchers.hasSize(1))
        MatcherAssert.assertThat(result[0].fullTagName, Matchers.equalTo("science"))
        MatcherAssert.assertThat(result[0].hasChildren, Matchers.equalTo(true))
        MatcherAssert.assertThat(result[0].collapsed, Matchers.equalTo(true))
    }

    @Test
    fun `flattenTree with nested tags - expanded`() {
        val root =
            tagTreeNode {
                level = 0
                children.add(
                    tagTreeNode {
                        name = "science"
                        level = 1
                        collapsed = false
                        children.add(
                            tagTreeNode {
                                name = "biology"
                                level = 2
                            },
                        )
                        children.add(
                            tagTreeNode {
                                name = "chemistry"
                                level = 2
                            },
                        )
                    },
                )
            }
        val result = flattenAndFilter(root, "")
        MatcherAssert.assertThat(result, Matchers.hasSize(3))
        MatcherAssert.assertThat(result[0].fullTagName, Matchers.equalTo("science"))
        MatcherAssert.assertThat(result[0].collapsed, Matchers.equalTo(false))
        MatcherAssert.assertThat(result[1].fullTagName, Matchers.equalTo("science::biology"))
        MatcherAssert.assertThat(result[1].level, Matchers.equalTo(1))
        MatcherAssert.assertThat(result[2].fullTagName, Matchers.equalTo("science::chemistry"))
    }

    @Test
    fun `flattenTree with search query filters and shows ancestors`() {
        val root =
            tagTreeNode {
                level = 0
                children.add(
                    tagTreeNode {
                        name = "science"
                        level = 1
                        collapsed = true
                        children.add(
                            tagTreeNode {
                                name = "biology"
                                level = 2
                            },
                        )
                        children.add(
                            tagTreeNode {
                                name = "chemistry"
                                level = 2
                            },
                        )
                    },
                )
                children.add(
                    tagTreeNode {
                        name = "history"
                        level = 1
                    },
                )
            }
        val result = flattenAndFilter(root, "bio")
        // should show "science" (ancestor) and "science::biology" (match)
        MatcherAssert.assertThat(result, Matchers.hasSize(2))
        MatcherAssert.assertThat(result[0].fullTagName, Matchers.equalTo("science"))
        MatcherAssert.assertThat(result[1].fullTagName, Matchers.equalTo("science::biology"))
    }

    @Test
    fun `flattenTree deeply nested - partial expansion`() {
        val root =
            tagTreeNode {
                level = 0
                children.add(
                    tagTreeNode {
                        name = "a"
                        level = 1
                        collapsed = false
                        children.add(
                            tagTreeNode {
                                name = "b"
                                level = 2
                                collapsed = true
                                children.add(
                                    tagTreeNode {
                                        name = "c"
                                        level = 3
                                        children.add(
                                            tagTreeNode {
                                                name = "d"
                                                level = 4
                                            },
                                        )
                                    },
                                )
                            },
                        )
                    },
                )
            }
        // "a" expanded, "a::b" collapsed
        val result = flattenAndFilter(root, "")
        MatcherAssert.assertThat(result, Matchers.hasSize(2))
        MatcherAssert.assertThat(result[0].fullTagName, Matchers.equalTo("a"))
        MatcherAssert.assertThat(result[1].fullTagName, Matchers.equalTo("a::b"))
        MatcherAssert.assertThat(result[1].hasChildren, Matchers.equalTo(true))
        MatcherAssert.assertThat(result[1].collapsed, Matchers.equalTo(true))
    }

    @Test
    fun `flattenTree deeply nested - fully expanded`() {
        val root =
            tagTreeNode {
                level = 0
                children.add(
                    tagTreeNode {
                        name = "a"
                        level = 1
                        collapsed = false
                        children.add(
                            tagTreeNode {
                                name = "b"
                                level = 2
                                collapsed = false
                                children.add(
                                    tagTreeNode {
                                        name = "c"
                                        level = 3
                                    },
                                )
                            },
                        )
                    },
                )
            }
        val result = flattenAndFilter(root, "")
        MatcherAssert.assertThat(result, Matchers.hasSize(3))
        MatcherAssert.assertThat(result[2].fullTagName, Matchers.equalTo("a::b::c"))
        MatcherAssert.assertThat(result[2].level, Matchers.equalTo(2))
        MatcherAssert.assertThat(result[2].hasChildren, Matchers.equalTo(false))
    }

    @Test
    fun `flattenTree empty root`() {
        val root = tagTreeNode { level = 0 }
        val result = flattenAndFilter(root, "")
        MatcherAssert.assertThat(result, Matchers.hasSize(0))
    }

    @Test
    fun `flattenTree search with no matches`() {
        val root =
            tagTreeNode {
                level = 0
                children.add(
                    tagTreeNode {
                        name = "science"
                        level = 1
                    },
                )
                children.add(
                    tagTreeNode {
                        name = "history"
                        level = 1
                    },
                )
            }
        val result = flattenAndFilter(root, "zzz")
        MatcherAssert.assertThat(result, Matchers.hasSize(0))
    }

    @Test
    fun `flattenTree multiple root tags with children - expanded`() {
        val root =
            tagTreeNode {
                level = 0
                children.add(
                    tagTreeNode {
                        name = "math"
                        level = 1
                        collapsed = false
                        children.add(
                            tagTreeNode {
                                name = "algebra"
                                level = 2
                            },
                        )
                    },
                )
                children.add(
                    tagTreeNode {
                        name = "science"
                        level = 1
                        collapsed = false
                        children.add(
                            tagTreeNode {
                                name = "biology"
                                level = 2
                            },
                        )
                    },
                )
            }
        val result = flattenAndFilter(root, "")
        MatcherAssert.assertThat(result, Matchers.hasSize(4))
        MatcherAssert.assertThat(result[0].fullTagName, Matchers.equalTo("math"))
        MatcherAssert.assertThat(result[1].fullTagName, Matchers.equalTo("math::algebra"))
        MatcherAssert.assertThat(result[2].fullTagName, Matchers.equalTo("science"))
        MatcherAssert.assertThat(result[3].fullTagName, Matchers.equalTo("science::biology"))
    }

    @Test
    fun `flattenTree produces all nodes regardless of collapsed state`() {
        val root =
            tagTreeNode {
                level = 0
                children.add(
                    tagTreeNode {
                        name = "a"
                        level = 1
                        collapsed = false
                        children.add(
                            tagTreeNode {
                                name = "b"
                                level = 2
                                collapsed = true
                                children.add(
                                    tagTreeNode {
                                        name = "c"
                                        level = 3
                                        children.add(
                                            tagTreeNode {
                                                name = "d"
                                                level = 4
                                            },
                                        )
                                    },
                                )
                            },
                        )
                    },
                )
            }
        val result = ManageTagsViewModel.flattenTree(root)
        MatcherAssert.assertThat(result, Matchers.hasSize(4))
        MatcherAssert.assertThat(result[0].fullTagName, Matchers.equalTo("a"))
        MatcherAssert.assertThat(result[1].fullTagName, Matchers.equalTo("a::b"))
        MatcherAssert.assertThat(result[1].collapsed, Matchers.equalTo(true))
        MatcherAssert.assertThat(result[2].fullTagName, Matchers.equalTo("a::b::c"))
        MatcherAssert.assertThat(result[3].fullTagName, Matchers.equalTo("a::b::c::d"))
    }

    @Test
    fun `applyCollapsedVisibility skips children of collapsed nodes`() {
        val items =
            listOf(
                tagListItem("a", "a", level = 0, hasChildren = true, collapsed = true),
                tagListItem("a::b", "b", level = 1, hasChildren = true, collapsed = false),
                tagListItem("a::b::c", "c", level = 2, hasChildren = false, collapsed = false),
                tagListItem("d", "d", level = 0, hasChildren = false, collapsed = false),
            )
        val result = ManageTagsViewModel.applyCollapsedVisibility(items)
        MatcherAssert.assertThat(result, Matchers.hasSize(2))
        MatcherAssert.assertThat(result[0].fullTag.value, Matchers.equalTo("a"))
        MatcherAssert.assertThat(result[1].fullTag.value, Matchers.equalTo("d"))
    }

    @Test
    fun `search only expands ancestors needed to reach the matching component`() {
        val items =
            listOf(
                tagListItem("a", "a", level = 0, hasChildren = true, collapsed = true),
                tagListItem("a::b", "b", level = 1, hasChildren = true, collapsed = true),
                tagListItem("a::b::c", "c", level = 2, hasChildren = true, collapsed = true),
                tagListItem("a::b::c::d", "d", level = 3, hasChildren = false, collapsed = false),
            )
        for ((query, expectedNames) in listOf(
            "a" to listOf("a"),
            "b" to listOf("a", "a::b"),
            "A::B" to listOf("a", "a::b"),
            "a::" to listOf("a", "a::b"),
            "c" to listOf("a", "a::b", "a::b::c"),
            "::" to items.map { it.fullTagName },
        )) {
            val result = ManageTagsViewModel.applySearchFilter(items, query)
            MatcherAssert.assertThat("Query: $query", result.map { it.fullTagName }, Matchers.equalTo(expectedNames))
            MatcherAssert.assertThat(result.dropLast(1).all { !it.collapsed }, Matchers.equalTo(true))
            MatcherAssert.assertThat(result.last().collapsed, Matchers.equalTo(result.last().hasChildren))
        }
    }

    @Test
    fun `a match in both parent and child names reveals the child`() {
        val items =
            listOf(
                tagListItem("a", "a", level = 0, hasChildren = true, collapsed = true),
                tagListItem("a::cat", "cat", level = 1, hasChildren = true, collapsed = true),
                tagListItem("a::cat::dog", "dog", level = 2, hasChildren = false, collapsed = false),
            )
        val result = ManageTagsViewModel.applySearchFilter(items, "a")
        MatcherAssert.assertThat(result.map { it.fullTagName }, Matchers.equalTo(listOf("a", "a::cat")))
        MatcherAssert.assertThat(result[0].collapsed, Matchers.equalTo(false))
        MatcherAssert.assertThat(result[1].collapsed, Matchers.equalTo(true))
    }

    @Test
    fun `applySearchFilter includes ancestors`() {
        val items =
            listOf(
                tagListItem("science", "science", level = 0, hasChildren = true, collapsed = true),
                tagListItem("science::biology", "biology", level = 1, hasChildren = false, collapsed = false),
                tagListItem("science::chemistry", "chemistry", level = 1, hasChildren = false, collapsed = false),
                tagListItem("history", "history", level = 0, hasChildren = false, collapsed = false),
            )
        val result = ManageTagsViewModel.applySearchFilter(items, "bio")
        MatcherAssert.assertThat(result, Matchers.hasSize(2))
        MatcherAssert.assertThat(result[0].fullTag.value, Matchers.equalTo("science"))
        MatcherAssert.assertThat(result[0].collapsed, Matchers.equalTo(false))
        MatcherAssert.assertThat(result[1].fullTag.value, Matchers.equalTo("science::biology"))
    }

    @Test
    fun `applyCollapsedVisibility - collapsed parent hides children`() {
        val items =
            listOf(
                tagListItem("aa", "aa", level = 0, hasChildren = true, collapsed = true),
                tagListItem("aa::bb", "bb", level = 1, hasChildren = false, collapsed = false),
                tagListItem("aa::cc", "cc", level = 1, hasChildren = false, collapsed = false),
            )
        val result = ManageTagsViewModel.applyCollapsedVisibility(items)
        MatcherAssert.assertThat(result, Matchers.hasSize(1))
        MatcherAssert.assertThat(result[0].fullTag.value, Matchers.equalTo("aa"))
        MatcherAssert.assertThat(result[0].collapsed, Matchers.equalTo(true))
    }

    @Test
    fun `applySearchFilter - expands collapsed node when children are visible`() {
        val items =
            listOf(
                tagListItem("bb", "bb", level = 0, hasChildren = true, collapsed = true),
                tagListItem("bb::aa", "aa", level = 1, hasChildren = false, collapsed = false),
            )
        val result = ManageTagsViewModel.applySearchFilter(items, "aa")
        MatcherAssert.assertThat(result, Matchers.hasSize(2))
        MatcherAssert.assertThat(result[0].fullTag.value, Matchers.equalTo("bb"))
        MatcherAssert.assertThat(result[0].collapsed, Matchers.equalTo(false))
        MatcherAssert.assertThat(result[1].fullTag.value, Matchers.equalTo("bb::aa"))
    }
}
