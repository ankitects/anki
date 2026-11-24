// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.tags

import anki.collection.OpChangesWithCount
import anki.collection.copy
import anki.collection.opChangesWithCount
import com.ichi2.anki.libanki.Collection

/** The backend can only rename a subtree referenced by at least one note. */
internal fun Collection.canRenameTagTree(tag: TagName): Boolean = findNotes(tag.literalSubtreeSearch()).isNotEmpty()

/**
 * Renames tags on notes, repairing duplicates when subtrees merge.
 *
 * Works around [Anki issue 5857](https://github.com/ankitects/anki/issues/5857).
 *
 * Follows Anki's backend behavior: an entirely unused subtree is left unchanged. When
 * renaming a subtree used by notes, unused cached descendants are removed, not renamed.
 * See https://github.com/ankitects/anki/blob/main/rslib/src/tags/rename.rs.
 */
internal fun Collection.renameTagTree(
    oldName: TagName,
    newName: TagName,
): OpChangesWithCount {
    val affectedNotes = findNotes(oldName.literalSubtreeSearch())
    val result = tags.rename(oldName, newName)
    if (result.count == 0) return result

    // Renaming into an existing tag can leave duplicate tags on a note. Use the backend's
    // tag identity to deduplicate: its pinned unicase 2.6.0 has a folding bug that equates
    // "ﬃ" with "iff", but keeps "ffi" distinct. Standard Unicode folding disagrees, so
    // client-side cleanup can remove valid tags or miss duplicates.
    // The rename already marked these notes modified; no card generation is needed.
    val undoStep = undoStatus().lastStep
    afterNoteUpdates(affectedNotes, markModified = false, generateCards = false)
    return result.copy { changes = mergeUndoEntries(undoStep) }
}

/** Move tags on notes, merging overlapping subtrees with the same semantics as [renameTagTree]. */
internal fun Collection.moveTagTree(
    tag: TagName,
    newParent: TagName?,
): OpChangesWithCount {
    require(newParent == null || !newParent.isSameOrDescendantOf(tag)) { "A tag cannot be moved under itself" }
    val destination = tag.withParent(newParent)
    if (destination == tag) return opChangesWithCount { }
    // Moving one subtree is a prefix rename. The backend reparent operation also rejects
    // valid sibling prefixes (e.g. parent::a beneath parent::ab).
    return renameTagTree(tag, destination)
}

/** A literal regex avoids tag-search wildcards and reserved names such as "none". */
private fun TagName.literalSubtreeSearch(): String {
    val escaped =
        buildString {
            for (char in value) {
                if (char in "\\.^$|?*+()[]{}") append('\\')
                append(char)
            }
        }.replace("\"", "\\\"")
    return "\"tag:re:^$escaped(?:::|$)\""
}
