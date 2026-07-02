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

@file:Suppress(
    "ktlint:standard:discouraged-comment-location",
    "ktlint:standard:kdoc-wrapping",
    "ktlint:standard:kdoc", // private constructor docs
)

package com.ichi2.utils

import com.ichi2.anki.common.utils.annotation.KotlinCleanup
import org.apache.commons.collections4.list.SetUniqueList
import java.util.Arrays
import java.util.Spliterator
import java.util.TreeSet

/**
 * A collection of items that doesn't allow duplicate items, and allows fast random access, lookup, maintaining order, and sorting.
 *
 *
 * The [List] interface makes certain assumptions/requirements. This
 * implementation breaks these in certain ways, but this is merely the result of
 * rejecting duplicates. Each violation is explained in the method, but it
 * should not affect you.
 *
 * This class does also implement the [Set] interface, and as a result
 * you should bear in mind that Sets require immutable objects to function correctly.
 *
 * @implNote The implementation of this class extends [SetUniqueList] and adds the ability to define a comparator
 * to be used to judge uniqueness of elements, and allows sorting.
 *
 *
 * Data structures used internally:
 * - [ArrayList] to enable fast random access, sorting, and maintaining order of items during iteration
 * - [TreeSet] if a comparator is given or a [HashSet] otherwise.
 */
class UniqueArrayList<E> /**
 * Constructor that wraps (not copies) the List and specifies the set to use.
 *
 *
 * The set and list must both be correctly initialised to the same elements.
 *
 * @param set  the set to decorate, must not be null
 * @param list  the list to decorate, must not be null
 * @throws NullPointerException if set or list is null
 */
    private constructor(
        /**
         * Internal list used in [SetUniqueList] implementation.
         *
         *
         * This is the same list as the one used internally in [SetUniqueList]. We keep a reference to it here in
         * order to be able to sort it. [SetUniqueList] implementation needs to make sure the internal [Set]
         * and [List] don't get out of sync, and [SetUniqueList] cannot be sorted via [java.util.Collections.sort]
         * or [SetUniqueList.sort] both will throw an exception, due to a limitation on this class [java.util.ListIterator]
         *
         * Sorting can be only done via [UniqueArrayList.sort] or [UniqueArrayList.sort].
         *
         * Modification to this list reference should be done with cautious to avoid having the internal [Set] out of sync
         */
        private val list: MutableList<E>,
        set: Set<E>?,
    ) : SetUniqueList<E>(list, set),
        MutableList<E>,
        MutableSet<E> {
        /**
         * Constructs a new empty [UniqueArrayList]
         */
        constructor() : this(ArrayList<E>(), HashSet<E>())

        /**
         * Sorts the list into ascending order, according to the
         * [natural ordering][Comparable] of its elements.
         * All elements in the list must implement the [Comparable]
         * interface.  Furthermore, all elements in the list must be
         * *mutually comparable* (that is, `e1.compareTo(e2)`
         * must not throw a `ClassCastException` for any elements
         * `e1` and `e2` in the list).
         *
         * @see .sort
         */
        @KotlinCleanup("sortWith")
        fun sort() {
            sortOverride(null)
        }

        /**
         * Sorts the list according to the order induced by the specified comparator.
         * All elements in the list must be *mutually comparable* using the
         * specified comparator (that is, `c.compare(e1, e2)` must not throw
         * a [ClassCastException] for any elements `e1` and `e2`
         * in the list).
         *
         * This sort is guaranteed to be *stable*:  equal elements will
         * not be reordered as a result of the sort.
         *
         * The specified list must be modifiable, but need not be resizable.
         *
         * @implNote
         * DO NOT call [java.util.Collections.sort] using this list directly
         * this can throw due to a limitation on setting items on the [java.util.ListIterator]
         * returned by [java.util.ListIterator]
         *
         * @param c the comparator to determine the order of the list.  A
         * `null` value indicates that the elements' *natural
         * ordering* should be used.
         *
         * @see java.util.Collections.sort
         */
        @KotlinCleanup("sortWith")
        override fun sort(c: Comparator<in E>?) {
            sortOverride(c)
        }

        /** Exists temporarily: [sort] has been defined as invalid in kotlin so it can't be called internally */
        @Suppress("UNCHECKED_CAST")
        @KotlinCleanup("use sortWith")
        fun sortOverride(c: Comparator<in E>?) {
            val elements = ArrayList(list).toArray() as Array<E>
            Arrays.sort(elements, c)
            val i = list.listIterator()
            for (element in elements) {
                i.next()
                i.set(element)
            }
        }

        override fun spliterator(): Spliterator<E> = super<SetUniqueList>.spliterator()

        companion object {
            /**
             * Constructs a new [UniqueArrayList] containing the elements of the specified collection, with an optional
             * comparator to be used to judge uniqueness.
             *
             * @implNote Modified implantation of [SetUniqueList.setUniqueList] to :
             * - support using comparators to check for uniqueness.
             * - make a copy of the list passed.
             *
             * @param source the source collection that will be used to construct UniqueArrayList
             * @param comparator used to judge uniqueness
             */
            fun <E> from(
                source: Collection<E>,
                comparator: Comparator<in E>? = null,
            ): UniqueArrayList<E> {
                val set: Set<E> =
                    if (comparator == null) {
                        HashSet()
                    } else {
                        TreeSet(comparator)
                    }
                val sl = UniqueArrayList(ArrayList(), set)
                sl.addAll(source)
                return sl
            }
        }
    }
