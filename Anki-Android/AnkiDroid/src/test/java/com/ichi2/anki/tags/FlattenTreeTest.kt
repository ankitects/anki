/*
 *  Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.tags

import anki.tags.tagTreeNode
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.Test

/** Unit tests for [ManageTagsViewModel.flattenTree] (no collection needed) */
class FlattenTreeTest {
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
