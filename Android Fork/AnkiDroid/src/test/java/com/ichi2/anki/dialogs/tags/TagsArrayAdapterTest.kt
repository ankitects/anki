// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 capthunder19 <anirudhpatwal19@gmail.com>
package com.ichi2.anki.dialogs.tags

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.ichi2.anki.dialogs.tags.TagTree.Node as TagTreeNode

class TagsArrayAdapterTest {
    private fun leaf(
        tag: String,
        parent: TagTreeNode,
    ): TagTreeNode =
        TagTreeNode(
            tag = tag,
            parent = parent,
            children = ArrayList(),
            level = parent.level + 1,
            subtreeSize = 1,
            isExpanded = true,
        )

    private fun root(): TagTreeNode =
        TagTreeNode(
            tag = "",
            parent = null,
            children = ArrayList(),
            level = -1,
            subtreeSize = 0,
            isExpanded = true,
        )

    @Test
    fun `getContributeSize returns 1 when collapsed`() {
        val node =
            leaf("a", root()).apply {
                subtreeSize = 5
                isExpanded = false
            }

        assertEquals(1, node.getContributeSize())
    }

    @Test
    fun `getContributeSize returns subtreeSize when expanded`() {
        val node =
            leaf("a", root()).apply {
                subtreeSize = 5
                isExpanded = true
            }

        assertEquals(5, node.getContributeSize())
    }

    @Test
    fun `isNotLeaf is false for a node without children`() {
        val node = leaf("a", root())

        assertFalse(node.isNotLeaf())
    }

    @Test
    fun `isNotLeaf is true for a node with children`() {
        val node = leaf("a", root())
        node.children.add(leaf("a::b", node))

        assertTrue(node.isNotLeaf())
    }

    @Test
    fun `iterateAncestorsOf walks from a node up to the root, excluding itself`() {
        val root = root()
        val a = leaf("a", root)
        val b = leaf("a::b", a)

        val ancestors = TagTreeNode.iterateAncestorsOf(b).asSequence().toList()

        assertEquals(listOf(a, root), ancestors)
    }

    @Test
    fun `toggleIsExpanded collapsing propagates the size delta to all expanded ancestors`() {
        val root = root()
        val a = leaf("a", root).apply { subtreeSize = 3 }
        val b = leaf("a::b", a).apply { subtreeSize = 2 }
        val c = leaf("a::b::c", b)
        b.children.add(c)
        a.children.add(b)
        root.children.add(a)
        root.subtreeSize = a.getContributeSize()

        a.toggleIsExpanded()

        assertFalse(a.isExpanded)
        assertEquals(1, a.getContributeSize())
        assertEquals(1, root.subtreeSize)
    }

    @Test
    fun `toggleIsExpanded stops propagating once a collapsed ancestor is reached`() {
        val root = root()
        val a = leaf("a", root)
        val b = leaf("a::b", a).apply { isExpanded = false }
        val c = leaf("a::b::c", b).apply { subtreeSize = 2 }
        val d = leaf("a::b::c::d", c)
        c.children.add(d)
        b.children.add(c)
        a.children.add(b)
        root.children.add(a)
        b.subtreeSize = 1 + c.getContributeSize()
        a.subtreeSize = 1 + b.getContributeSize()
        root.subtreeSize = a.getContributeSize()

        val aSizeBefore = a.subtreeSize
        val rootSizeBefore = root.subtreeSize
        val bSizeBefore = b.subtreeSize

        c.toggleIsExpanded()

        assertFalse(c.isExpanded)
        assertEquals(bSizeBefore - 1, b.subtreeSize)
        assertEquals(aSizeBefore, a.subtreeSize)
        assertEquals(rootSizeBefore, root.subtreeSize)
    }
}
