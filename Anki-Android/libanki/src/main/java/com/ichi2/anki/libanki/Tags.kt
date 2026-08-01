/*
 * Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com>
 * Copyright (c) 2012 Kostas Spyropoulos <inigo.aldana@gmail.com>
 * Copyright (c) 2014 Houssam Salem <houssam.salem.au@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.libanki

import androidx.annotation.CheckResult
import androidx.annotation.WorkerThread
import anki.collection.OpChanges
import anki.collection.OpChangesWithCount
import anki.tags.TagTreeNode
import com.ichi2.anki.libanki.utils.LibAnkiAlias
import com.ichi2.anki.libanki.utils.join
import java.util.AbstractSet

/**
 * Anki maintains a cache of used tags so it can quickly present a list of tags
 * for autocomplete and in the browser. For efficiency, deletions are not
 * tracked, so unused tags can only be removed from the list with a DB check.
 *
 * This module manages the tag cache and tags for notes.
 *
 */
@WorkerThread
class Tags(
    private val col: Collection,
) {
    /** all tags */
    fun all(): List<String> = col.backend.allTags()

    @LibAnkiAlias("tree")
    fun tree(): TagTreeNode = col.backend.tagTree()

    /*
     * Registering and fetching tags
     * ***********************************************************
     */

    @LibAnkiAlias("clear_unused_tags")
    fun clearUnusedTags(): OpChangesWithCount = col.backend.clearUnusedTags()

    /** Set browser expansion state for tag, registering the tag if missing. */
    @LibAnkiAlias("set_collapsed")
    fun setCollapsed(
        tag: String,
        collapsed: Boolean,
    ): OpChanges = col.backend.setTagCollapsed(name = tag, collapsed = collapsed)

    /*
     * Bulk addition/removal from specific notes
     * ***********************************************************
     */

    /** Add space-separate tags to provided notes. */
    fun bulkAdd(
        noteIds: List<NoteId>,
        tags: String,
    ): OpChangesWithCount = col.backend.addNoteTags(noteIds = noteIds, tags = tags)

    // Remove space-separated tags from provided notes.
    fun bulkRemove(
        noteIds: List<Long>,
        tags: String,
    ): OpChangesWithCount =
        col.backend.removeNoteTags(
            noteIds = noteIds,
            tags = tags,
        )

    /*
     * Bulk addition/removal based on tag
     * ***********************************************************
     */

    /**
     * Rename a given tag and its children on all notes that reference it.
     *
     * @return [OpChangesWithCount] containing the number of affected notes.
     */
    fun rename(
        old: String,
        new: String,
    ): OpChangesWithCount = col.backend.renameTags(currentPrefix = old, newPrefix = new)

    /**
     * Remove the provided tag(s) and their children from notes and the tag list.
     *
     * @return [OpChangesWithCount] containing the number of affected notes.
     */
    fun remove(spaceSeparatedTags: String): OpChangesWithCount = col.backend.removeTags(`val` = spaceSeparatedTags)

    /**
     * Change the parent of the provided tags.
     * If new_parent is empty, tags will be reparented to the top-level.
     */
    fun reparent(
        tags: Iterable<String>,
        newParent: String,
    ): OpChangesWithCount = col.backend.reparentTags(tags = tags, newParent = newParent)

    /*
     * String-based utilities
     * ***********************************************************
     */

    /** Parse a string and return a list of tags. */
    fun split(tags: String): MutableList<String> =
        tags
            .replace('\u3000', ' ')
            .split("\\s".toRegex())
            .filter { it.isNotEmpty() }
            .toMutableList()

    /** Join tags into a single string, with leading and trailing spaces. */
    fun join(tags: kotlin.collections.Collection<String>): String {
        if (tags.isEmpty()) {
            return ""
        }
        return " ${" ".join(tags)} "
    }

    /*
     * List-based utilities
     * ***********************************************************
     */

    /** {@inheritDoc}  */
    fun canonify(tagList: List<String>): AbstractSet<String> {
        // this is now a no-op - the tags are canonified when the note is saved

        // libAnki difference: tagList was returned directly
        return HashSet(tagList)
    }

    /** True if TAG is in TAGS. Ignore case.*/
    fun inList(
        tag: String,
        tags: Iterable<String>,
    ): Boolean = tags.map { it.lowercase() }.contains(tag.lowercase())

    /**
     * Replace occurrences of a search with a new value in tags.
     * [https://github.com/ankitects/anki/blob/main/pylib/anki/tags.py#L73](https://github.com/ankitects/anki/blob/main/pylib/anki/tags.py#L73)
     *
     * @return An [OpChangesWithCount] representing the number of affected notes
     */
    @LibAnkiAlias("find_and_replace")
    @CheckResult
    fun findAndReplace(
        noteIds: List<Long>,
        search: String,
        replacement: String,
        regex: Boolean,
        matchCase: Boolean,
    ) = col.backend.findAndReplaceTag(
        noteIds = noteIds,
        search = search,
        replacement = replacement,
        regex = regex,
        matchCase = matchCase,
    )
}

fun Collection.completeTagRaw(input: ByteArray): ByteArray = backend.completeTagRaw(input)
