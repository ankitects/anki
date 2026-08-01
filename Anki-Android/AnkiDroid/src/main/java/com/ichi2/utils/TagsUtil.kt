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

package com.ichi2.utils

import java.util.stream.Collectors

object TagsUtil {
    fun getUpdatedTags(
        previous: List<String>,
        selected: List<String>,
        indeterminate: List<String>,
    ): List<String> {
        if (indeterminate.isEmpty()) {
            return selected
        }
        val updated: MutableList<String> = ArrayList()
        val previousSet: Set<String> = HashSet(previous)
        updated.addAll(selected)
        updated.addAll(indeterminate.stream().filter { o: String -> previousSet.contains(o) }.collect(Collectors.toList()))
        return updated
    }

    private const val BLANK_SUBSTITUENT = "blank"

    /**
     * Utility method that decomposes a hierarchy tag into several parts.
     * Replace empty parts to "blank".
     */
    fun getTagParts(tag: String): List<String> {
        val parts = tag.split("::").asSequence()
        return parts
            .map {
                // same as the way Anki Desktop deals with an empty tag subpart
                it.ifEmpty { BLANK_SUBSTITUENT }
            }.toList()
    }

    /**
     * Utility that uniforms a hierarchy tag.
     * Remove trailing '::'.
     */
    fun getUniformedTag(tag: String): String {
        val parts = getTagParts(tag)
        return if (tag.endsWith("::") && parts.last() == BLANK_SUBSTITUENT) {
            parts.subList(0, parts.size - 1)
        } else {
            parts
        }.joinToString("::")
    }

    /**
     * Utility method that gets the root part of a tag.
     */
    fun getTagRoot(tag: String): String {
        val parts = tag.split("::").asSequence()
        return parts
            .map {
                // same as the way Anki Desktop deals with an empty tag subpart
                it.ifEmpty { "blank" }
            }.take(1)
            .first()
    }

    /**
     * Utility method that gets the ancestors of a tag.
     */
    fun getTagAncestors(tag: String): List<String> {
        val parts = getTagParts(tag)
        return (0..parts.size - 2)
            .asSequence()
            .map {
                parts.subList(0, it + 1).joinToString("::")
            }.toList()
    }

    /**
     * Compare two tags with hierarchy comparison
     * Used to sort all tags firstly in DFN order, secondly in dictionary order
     * Both lhs and rhs must be uniformed.
     */
    fun compareTag(
        lhs: String,
        rhs: String,
    ): Int {
        val lhsIt = lhs.split("::").asSequence().iterator()
        val rhsIt = rhs.split("::").asSequence().iterator()
        while (lhsIt.hasNext() && rhsIt.hasNext()) {
            val cmp = lhsIt.next().compareTo(rhsIt.next(), true)
            if (cmp != 0) {
                return cmp
            }
        }
        if (!lhsIt.hasNext() && !rhsIt.hasNext()) {
            return 0
        }
        return if (lhsIt.hasNext()) 1 else -1
    }
}
