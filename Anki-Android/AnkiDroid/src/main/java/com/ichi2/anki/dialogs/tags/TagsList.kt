/*
 Copyright (c) 2021 Tarek Mohamed Abdalla <tarekkma@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.dialogs.tags

import com.ichi2.utils.TagsUtil.compareTag
import com.ichi2.utils.TagsUtil.getTagAncestors
import com.ichi2.utils.TagsUtil.getTagRoot
import com.ichi2.utils.UniqueArrayList
import com.ichi2.utils.UniqueArrayList.Companion.from
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
    private val checkedTags: MutableSet<String> = TreeSet(java.lang.String.CASE_INSENSITIVE_ORDER)

    /**
     * A Set containing the tags with indeterminate state.
     * For a tag to be in indeterminate state it should be present in checkedTags and also in uncheckedTags.
     */
    private val indeterminateTags: MutableSet<String>

    /**
     * List of all available tags
     */
    private val allTags: UniqueArrayList<String>

    init {
        this.checkedTags.addAll(checkedTags)
        this.allTags = from(allTags, java.lang.String.CASE_INSENSITIVE_ORDER)
        this.allTags.addAll(this.checkedTags)
        indeterminateTags = TreeSet(java.lang.String.CASE_INSENSITIVE_ORDER)
        if (uncheckedTags != null) {
            this.allTags.addAll(uncheckedTags)
            // intersection between mCheckedTags and uncheckedTags
            indeterminateTags.addAll(this.checkedTags)
            val uncheckedSet: MutableSet<String> = TreeSet(java.lang.String.CASE_INSENSITIVE_ORDER)
            uncheckedSet.addAll(uncheckedTags)
            indeterminateTags.retainAll(uncheckedSet)
            this.checkedTags.removeAll(indeterminateTags)
        }
        prepareTagHierarchy()
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
        if (!checkedTags.add(tag)) {
            return false
        }
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
    fun uncheck(tag: String): Boolean = indeterminateTags.remove(tag) || checkedTags.remove(tag)

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
        return indeterminateTags.add(tag)
    }

    /**
     * Toggle the status of all tags,
     * if all tags are checked, then uncheck them
     * otherwise check all tags
     *
     * @return true if this tag list changed as a result of the call
     */
    fun toggleAllCheckedStatuses(): Boolean {
        indeterminateTags.clear()
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
        for (tag in checkedTags) {
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
        val sortedList =
            allTags.toList().sortedWith { lhs: String?, rhs: String? ->
                val lhsRoot = getTagRoot(lhs!!)
                val rhsRoot = getTagRoot(rhs!!)
                val lhsChecked = isChecked(lhsRoot) || isIndeterminate(lhsRoot)
                val rhsChecked = isChecked(rhsRoot) || isIndeterminate(rhsRoot)
                if (lhsChecked != rhsChecked) {
                    if (lhsChecked) -1 else 1
                } else {
                    compareTag(lhs, rhs)
                }
            }
        allTags.clear()
        allTags.addAll(sortedList)
    }

    /**
     * @return Iterator over all tags
     */
    override fun iterator(): MutableIterator<String> = allTags.iterator()
}
