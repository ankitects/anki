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

package com.ichi2.anki.browser.search

import anki.search.SearchNode
import anki.search.SearchNodeKt.field
import anki.search.copy
import anki.search.searchNode
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.Field
import com.ichi2.anki.model.ResultType
import com.ichi2.anki.utils.ext.buildSearchString

/**
 * The **Fields** tab of [AdvancedSearchFragment]
 *
 * See: https://docs.ankiweb.net/searching.html#limiting-to-a-field
 *
 * @see AdvancedSearchFragment.OptionType.SelectField
 */
@NeedsTest("'Add Reverse' with '*text*' - asterisks are inside brackets")
object AdvancedSearchFieldsTab {
    /**
     * Examples of advanced syntax which can be used to search for/inside a field
     *
     * @see FieldSearch
     */
    val options: Map<ResultType, FieldSearch> =
        listOf(
            // Unusually, searching on field content performs an exact match by default
            // use *text* to match a field containing 'texting'
            "field" to
                object : FieldSearch("Search a named field", "field:*text*", { fieldName ->
                    field {
                        this.fieldName = fieldName
                        this.text = "text" // should be *text*, but broken by the backend
                    }
                }) {
                    override suspend fun buildSearchString(fieldName: String): String =
                        buildSearchString(fieldName, appendBefore = "*", appendAfter = "*")
                },
            "field_exact_match" to
                FieldSearch("Search a named field (exact text match)", "field:text") { fieldName ->
                    field {
                        this.fieldName = fieldName
                        this.text = "text"
                    }
                },
            "field_empty" to
                FieldSearch("Notes with an empty field", "field:") { fieldName ->
                    field {
                        this.fieldName = fieldName
                        this.text = ""
                    }
                },
            "field_non_empty" to
                object : FieldSearch("Notes with a non-empty field", "field:_*", { fieldName ->
                    field {
                        this.fieldName = fieldName
                        this.text = "" // should be '_*', but broken by the backend
                    }
                }) {
                    override suspend fun buildSearchString(fieldName: String): String =
                        super.buildSearchString(fieldName, appendBefore = "_*", appendAfter = "")
                },
            "field_either_empty" to
                object : FieldSearch("Notes with a field (empty or non-empty)", "field:*", { fieldName ->
                    field {
                        this.fieldName = fieldName
                        this.text = "" // should be '*', but broken by the backend
                    }
                }) {
                    override suspend fun buildSearchString(fieldName: String): String =
                        super.buildSearchString(fieldName, appendBefore = "*", appendAfter = "")
                },
        ).associate { ResultType(it.first) to it.second }

    /**
     * Represents an advanced search which searches the content of a named [Field]
     *
     * ```
     * front:*dog*  // any note with a front field containing the word "dog"
     * front:_*     // non-empty field
     * front:*      // any note type with a front field
     * ```
     */
    open class FieldSearch(
        val title: String,
        val example: String,
        // TODO: handle user-supplied text in 2 cases:
        //  - *text*
        //  - text
        //  OR have the UI move the cursor to a position to insert the text
        val buildFieldNode: (fieldName: String) -> SearchNode.Field,
    ) {
        open suspend fun buildSearchString(fieldName: String): String {
            val searchNode = searchNode { this.field = buildFieldNode(fieldName) }
            return withCol { buildSearchString(searchNode) }
        }

        /**
         * Optional extension to [buildSearchString], where unescaped values are required
         *
         * For example: if we want to apply "*text*", we want all but the asterisks to be escaped
         *  by [Collection.buildSearchString]
         */
        protected suspend fun buildSearchString(
            fieldName: String,
            appendBefore: String = "",
            appendAfter: String = "",
        ): String {
            val beforePlaceholder = "!!BEFORE!!"
            val afterPlaceholder = "!!AFTER!!"

            val fieldNode = buildFieldNode(fieldName)
            val updatedFieldName =
                fieldNode.copy {
                    text = "$beforePlaceholder${fieldNode.text}$afterPlaceholder"
                }
            val searchNode = searchNode { this.field = updatedFieldName }
            val searchString = withCol { buildSearchString(searchNode) }
            return searchString
                .replace(beforePlaceholder, appendBefore)
                .replace(afterPlaceholder, appendAfter)
        }
    }
}
