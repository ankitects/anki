/*
 *  Copyright (c) 2026 Arthur Milchior <arthur@milchior.fr>
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

package com.ichi2.anki.deckpicker

import android.annotation.SuppressLint
import androidx.annotation.VisibleForTesting
import com.ichi2.anki.common.utils.lastIndexOfOrNull
import java.util.Locale
import kotlin.collections.filter

/**
 * Represents a deck search typed by the user, used to filter a list of decks.
 *
 * Here is the list of constraints this search tool satisfies:
 * * If the user tap a part of a name of a deck, we don't want to show its subdecks, due to constraint in space.
 *   E.g. if the user tap "Alge", we want to show the deck "Math::Algebra" but not "Math::Algebra::Group". Indeed, if "Algebra" had a dozen subdecks, this would takes the wull screen.
 *   Instead, if the user search for "group" we let them tap "alge group"
 * * The list of deck is continuously updated while the user is typing their query.
 *   E.g. if the user tap "Algebra", then adds one colon "Algebra:" we can't know whether the user wants to search for a subdeck of Algebra or for a deck whose name contains "Algebra:" with a single colon.
 *   We thus still displays "Algebra:Group" (a deck with no subdeck relation), and "Algebra".
 *   Once the user tab the second colon, we know the user is searching for a subgroup, and thus we remove "Algebra" and display instead all of its children.
 * * Apart from that, exact matching is used.
 *   E.g. "th::Alg" matches against  "Math::Algebra" but not against "Maths::Algebra" nor against "Maths::Abstract::Algebra"
 * * The filter can contains multiple parts, separated by spaces. In this case, each part must matches against some part of the deck full name, and only one part needs to match against the last name.
 *   E.g. "math group" matches against "maths::Algebra::group" but not against "math::Algebra::group::Finite" nor against "Math::Algebra".
 */
/*
 * It must be noted that if "Algebra" had no subdeck, then "Algebra:" can't be completed into a filter that would match anything.
 * So it would make sense to already remove "Math::Algebra" from the list of deck displayed. This is not done because this increase code complexity and
 * probably does not improve the user experience in any realistic way. As soon as the user tap the next letter in the search bar, "Math::Algebra" deck will disappear from the screen.
 */
class DeckFilters
    @VisibleForTesting
    constructor(
        private val filters: List<DeckFilter>,
    ) {
        /**
         * Whether the user is searching something
         */
        fun isActive() = filters.isNotEmpty()

        /**
         * Whether all filter of [filters] appear in [name]
         */
        @VisibleForTesting
        fun deckNamesMatchFilters(name: String) = filters.all { filter -> filter.deckNameMatchesFilter(name) }

        /**
         * Whether at least one of the filter matches the last name.
         * @See [DeckFilter.deckLastNameMatchesFilter] to understand the exact meaning
         */
        @VisibleForTesting
        fun deckLastNameMatchesAFilter(name: String) = filters.any { filter -> filter.deckLastNameMatchesFilter(name) }

        /**
         * Whether the deck with this full name must be kept for the current filter.
         */
        @VisibleForTesting
        fun accept(name: String) =
            !isActive() ||
                (
                    deckLastNameMatchesAFilter(name) &&
                        deckNamesMatchFilters(name)
                )

        /**
         * Represents a single filter
         * @param filter a trimmed lower case string
         */
        class DeckFilter(
            private val filter: String,
        ) {
            /**
             * Whether there is a single : at the end. This case must be treated specially in order not to remove results from the deck list
             * while the user starts typing "::subdeckName"
             */
            val endsWithSingleColon = filter.endsWith(":") && !filter.endsWith("::")

            /**
             * The filter without its last ":" if the deck name ends with exactly one ":"
             */
            val trimmedFilter = if (endsWithSingleColon) filter.trimEnd(':') else filter

            /**
             * Whether [filter] appears in [name].
             */
            @SuppressLint("LocaleRootUsage")
            fun deckNameMatchesFilter(name: String) =
                // If the filter is "foo:", two cases can occur:
                // Either we actually have a deck with a single colon, "foo:bar" and we want to find it,
                // or we have a deck "foo" with subdecks "bar" and "plop". We still want to matches against "foo" until
                // we see whether the first letter of the subdeck searched by the user.
                name.lowercase(Locale.getDefault()).contains(filter) ||
                    name.lowercase(Locale.ROOT).contains(filter) ||
                    name.lowercase(Locale.getDefault()).endsWith(trimmedFilter) ||
                    name.lowercase(Locale.ROOT).endsWith(trimmedFilter)

            /**
             * Whether [filter] matches against the last part of [name] specifically.
             * That is, if [filter] contains :: then the last suffix of the form "::foo", the name of the deck starts with "foo".
             * Otherwise, the name contains "foo".
             *
             * For example, the filter "u" would accept "buz", "foo::buz", but not "buz::foo".
             * The filters "u:" and "u::" would accept "bu", "foo::bu", but not "bu::foo".
             * The filter "o::b" would accept "foo::bar", but not "foo" nor "foo::bar::buz".
             *
             * For more examples, @see testDeckLastNameMatchesFilter
             */
            @VisibleForTesting
            fun deckLastNameMatchesFilter(name: String): Boolean {
                // if "::" does not appear in the filter. Then the filter can be anywhere in
                // the last part of the name
                val indexOfSeparatorInFilter = filter.lastIndexOfOrNull("::") ?: return deckNameMatchesFilter(name.split("::").last())
                // if "::" appears in the filter. Then it must be the same as the last "::" in the name.
                val indexOfSeparatorInName = name.lastIndexOfOrNull("::") ?: return false

                // We use trimmed filter. This way:
                // * if the filter does not ends with a :, this is similar to the filter
                // * if the filter ends with a single :, we're actually considering the parent name
                // the deck list will contains the parent.
                // * If the filter ends with ::, the last deck of the filter is empty, so this last deck is not considered, instead we just check against second to last deck name
                return containsAtPosition(
                    trimmedFilter,
                    indexOfSeparatorInFilter,
                    name,
                    indexOfSeparatorInName,
                )
            }

            companion object {
                /**
                 * Whether [containing] contains [contained] where the positions matches.
                 * Position must be less than the length of the string.
                 */
                @VisibleForTesting
                fun containsAtPosition(
                    contained: String,
                    positionContained: Int,
                    containing: String,
                    positionContaining: Int,
                ): Boolean {
                    val startOfContainingInContained = positionContaining - positionContained
                    val endOfContainingInContained = startOfContainingInContained + contained.length
                    val substringInContaining: String
                    try {
                        substringInContaining =
                            containing.substring(startOfContainingInContained, endOfContainingInContained)
                    } catch (_: IndexOutOfBoundsException) {
                        return false
                    }
                    return substringInContaining.equals(contained, ignoreCase = true) ||
                        substringInContaining.equals(
                            contained,
                            ignoreCase = true,
                        )
                }
            }
        }

        companion object {
            /**
             * Returns a [DeckFilters] for the user input [filters]
             */
            fun create(filters: CharSequence) =
                DeckFilters(
                    filters
                        .toString()
                        .lowercase()
                        .split("\\s+".toRegex())
                        .map { it.trim() }
                        .filter {
                            it.isNotEmpty()
                        }.map { DeckFilter(it) },
                )
        }
    }
