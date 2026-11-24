// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Tarek Mohamed Abdalla <tarekkma@gmail.com>

package com.ichi2.anki.dialogs.tags

import com.ichi2.anki.common.utils.UniqueArrayList
import com.ichi2.anki.common.utils.UniqueArrayList.Companion.from
import com.ichi2.utils.TagsUtil.compareTag
import com.ichi2.utils.TagsUtil.getTagAncestors
import com.ichi2.utils.TagsUtil.getTagRoot
import java.util.ArrayList
import java.util.TreeSet

/**
 * A container class that keeps track of tags and their status, handling of tags are done in a case insensitive matter.
 * For example adding tags with different letter casing (eg. TAG, tag, Tag) will only add the first one.
 * [TagsList] provides an iterator over all tags.
 * Construct a new [TagsList] with possibility of indeterminate tags. For a tag to be in indeterminate state it should be present
 * in checkedTags and also in uncheckedTags. Hierarchical tags will have their ancestors added temporarily.
 *
 * @param allTags A list of all available tags. Any duplicates will be ignored.
 * @param checkedTags a list containing the currently selected tags. Any duplicates will be ignored.
 * @param uncheckedTags a list containing the currently unselected tags. Any duplicates will be ignored.
 */
class TagsList(
    allTags: Collection<String>,
    checkedTags: Collection<String>,
    uncheckedTags: Collection<String>? = null,
) : Iterable<String> {
    /**
     * A Set containing the currently selected tags
     */
    private val checkedTags = TreeSet(java.lang.String.CASE_INSENSITIVE_ORDER)

    /**
     * Tags present on some notes, plus unchecked ancestors of selected tags.
     */
    private val indeterminateTags: MutableSet<String>

    /** Tags present on some notes whose own selection has not been changed by the user. */
    private val partiallySelectedTags = TreeSet(java.lang.String.CASE_INSENSITIVE_ORDER)

    /**
     * List of all available tags
     */
    private val allTags: UniqueArrayList<String>

    /** Tags or selection have changed since the last sort. */
    private var needsSort = true

    init {
        this.checkedTags.addAll(checkedTags)
        this.allTags = from(allTags, java.lang.String.CASE_INSENSITIVE_ORDER)
        this.allTags.addAll(this.checkedTags)
        indeterminateTags = TreeSet(java.lang.String.CASE_INSENSITIVE_ORDER)
        if (uncheckedTags != null) {
            this.allTags.addAll(uncheckedTags)
            for (tag in uncheckedTags) {
                val checkedTag = findCheckedTagIgnoringCase(tag) ?: continue
                indeterminateTags.add(checkedTag)
            }
            this.checkedTags.removeAll(indeterminateTags)
        }
        partiallySelectedTags.addAll(indeterminateTags)
        prepareTagHierarchy()
    }

    /** Returns the matching checked tag with its stored spelling, or `null` if absent. */
    private fun findCheckedTagIgnoringCase(tag: String): String? {
        // O(log n); returns "Biology" for a "biology" search or vice versa
        val candidate = checkedTags.ceiling(tag) ?: return null
        return candidate.takeIf { it.equals(tag, ignoreCase = true) }
    }

    /**
     * Return true if a tag is checked given its index in the list
     *
     * @param index index of the tag to check
     * @return whether the tag is checked or not
     * @throws IndexOutOfBoundsException if the index is out of range
     * (`index < 0 || index >= size()`)
     */
    fun isChecked(index: Int): Boolean = isChecked(allTags[index])

    /**
     * Return true if a tag is checked
     *
     * @param tag the tag to check (case-insensitive)
     * @return whether the tag is checked or not
     */
    fun isChecked(tag: String): Boolean = checkedTags.contains(tag)

    /**
     * Return true if a tag is indeterminate given its index in the list
     *
     * @param index index of the tag to check
     * @return whether the tag is indeterminate or not
     * @throws IndexOutOfBoundsException if the index is out of range
     * (`index < 0 || index >= size()`)
     */
    fun isIndeterminate(index: Int): Boolean = isIndeterminate(allTags[index])

    /**
     * Return true if a tag is indeterminate
     *
     * @param tag the tag to check (case-insensitive)
     * @return whether the tag is indeterminate or not
     */
    fun isIndeterminate(tag: String): Boolean = indeterminateTags.contains(tag)

    /** Whether the tag itself is partially selected, independently of its descendants. */
    internal fun isPartiallySelected(tag: String): Boolean = partiallySelectedTags.contains(tag)

    /** Checks the complete selection, including descendants hidden by the dialog's filter. */
    internal fun hasSelectedDescendants(tag: String): Boolean {
        val prefix = "$tag::"
        // Descendants are contiguous in these case-insensitive sets; only the first candidate is needed.
        return checkedTags.ceiling(prefix)?.startsWith(prefix, ignoreCase = true) == true ||
            partiallySelectedTags.ceiling(prefix)?.startsWith(prefix, ignoreCase = true) == true
    }

    /**
     * Adds a tag to the list if it is not already present.
     * If the tag is hierarchical, its ancestors will also be added temporarily.
     *
     * @param tag the tag to add
     * @return true if tag was added (new tag)
     */
    fun add(tag: String): Boolean {
        if (!allTags.add(tag)) {
            return false
        }
        needsSort = true
        addAncestors(tag)
        return true
    }

    /**
     * Mark a tag as checked tag.
     * Optionally mark ancestors as indeterminate
     *
     * @param tag the tag to be checked (case-insensitive)
     * @param processAncestors whether mark ancestors as indeterminate or not
     * @return true if the tag changed its check status
     * false if the tag was already checked or not in the list
     */
    fun check(
        tag: String,
        processAncestors: Boolean = true,
    ): Boolean {
        if (!allTags.contains(tag)) {
            return false
        }
        indeterminateTags.remove(tag)
        partiallySelectedTags.remove(tag)
        if (!checkedTags.add(tag)) {
            return false
        }
        needsSort = true
        if (processAncestors) {
            markAncestorsIndeterminate(tag)
        }
        return true
    }

    /**
     * Mark a tag as unchecked tag
     *
     * @param tag the tag to be checked (case-insensitive)
     * @return true if the tag changed its check status
     * false if the tag was already unchecked or not in the list
     */
    fun uncheck(tag: String): Boolean {
        partiallySelectedTags.remove(tag)
        val changed = indeterminateTags.remove(tag) || checkedTags.remove(tag)
        if (changed) {
            needsSort = true
        }
        return changed
    }

    /**
     * Mark a tag as indeterminate tag
     *
     * @param tag the tag to be turned into indeterminate (case-insensitive)
     * @return true if the tag changes into indeterminate
     * false if the tag was already indeterminate, or not in the list
     */
    fun setIndeterminate(tag: String): Boolean {
        if (!allTags.contains(tag)) {
            return false
        }
        checkedTags.remove(tag)
        val changed = indeterminateTags.add(tag)
        if (changed) {
            needsSort = true
        }
        return changed
    }

    /**
     * Toggle the status of all tags,
     * if all tags are checked, then uncheck them
     * otherwise check all tags
     *
     * @return true if this tag list changed as a result of the call
     */
    fun toggleAllCheckedStatuses(): Boolean {
        needsSort = true
        indeterminateTags.clear()
        partiallySelectedTags.clear()
        if (allTags.size == checkedTags.size) {
            checkedTags.clear()
            return true
        }
        return checkedTags.addAll(allTags)
    }

    /**
     * @return Number of tags in the list
     */
    fun size(): Int = allTags.size

    /**
     * Returns the tag at the specified position in this list.
     *
     * @param index index of the tag to return
     * @return the tag at the specified position in this list
     * @throws IndexOutOfBoundsException if the index is out of range
     * (`index < 0 || index >= size()`)
     */
    operator fun get(index: Int): String = allTags[index]

    /**
     * @return true if there is no tags in the list
     */
    val isEmpty: Boolean
        get() = allTags.isEmpty()

    /**
     * @return return a copy of checked tags
     */
    fun copyOfCheckedTagList(): List<String> = ArrayList(checkedTags)

    /**
     * @return return a copy of checked tags
     */
    fun copyOfIndeterminateTagList(): List<String> = ArrayList(indeterminateTags)

    /** Tags whose original per-note selection must be preserved when saving, excluding display-only ancestors. */
    internal fun copyOfPartiallySelectedTagList(): List<String> = ArrayList(partiallySelectedTags)

    /**
     * @return return a copy of all tags list
     */
    fun copyOfAllTagList(): List<String> = ArrayList(allTags)

    /**
     * Initialize the tag hierarchy.
     */
    private fun prepareTagHierarchy() {
        val allTags: List<String> = ArrayList(allTags)
        for (tag in allTags) {
            addAncestors(tag)
        }
        // Partially selected descendants also need their ancestors prioritized and expanded.
        // Take a snapshot because marking ancestors modifies indeterminateTags.
        for (tag in checkedTags + indeterminateTags) {
            markAncestorsIndeterminate(tag)
        }
    }

    /**
     * Add ancestors of the tag into the set of all tags.
     *
     * @param tag The tag whose ancestors will be added.
     */
    private fun addAncestors(tag: String) {
        allTags.addAll(getTagAncestors(tag))
    }

    /**
     * Mark ancestors of the tag as indeterminate (if not a checked tag).
     *
     * @param tag The tag whose ancestors will be marked as indeterminate if they are not checked.
     */
    private fun markAncestorsIndeterminate(tag: String) {
        if (!allTags.contains(tag)) {
            return
        }
        getTagAncestors(tag)
            .filterNot { isChecked(it) }
            .forEach { setIndeterminate(it) }
    }

    /**
     * Sort the tag list alphabetically ignoring the case, with priority for checked tags
     * A tag priors to another one if its root tag is checked or indeterminate while the other one's is not
     */
    fun sort() {
        if (!needsSort) {
            return
        }
        // Splitting tags and looking up root selection inside the comparator repeats this work
        // for every comparison. Compute the keys once per tag instead.
        val sortKeys =
            allTags.associateWith { tag ->
                val root = getTagRoot(tag)
                SortKey(tag.split("::"), isChecked(root) || isIndeterminate(root))
            }
        allTags.sortWith { lhs, rhs ->
            val lhsKey = sortKeys.getValue(lhs)
            val rhsKey = sortKeys.getValue(rhs)
            if (lhsKey.isSelected != rhsKey.isSelected) {
                if (lhsKey.isSelected) -1 else 1
            } else {
                compareTag(lhsKey.parts, rhsKey.parts)
            }
        }
        needsSort = false
    }

    private class SortKey(
        val parts: List<String>,
        val isSelected: Boolean,
    )

    /**
     * @return Iterator over all tags
     */
    override fun iterator(): MutableIterator<String> = allTags.iterator()
}
