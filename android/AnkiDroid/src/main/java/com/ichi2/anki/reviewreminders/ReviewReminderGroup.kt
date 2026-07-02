/*
 *  Copyright (c) 2026 Eric Li <ericli3690@gmail.com>
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

package com.ichi2.anki.reviewreminders

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * A group of review reminders, all for the same [ReviewReminderScope],
 * persisted as a single JSON string in SharedPreferences.
 *
 * Essentially a wrapper around a HashMap of [ReviewReminderId] to [ReviewReminder],
 * explicitly defined to restrict what can be done with the interface and to eliminate the
 * need to verbosely type out "HashMap<ReviewReminderId, ReviewReminder>" everywhere.
 *
 * A HashMap is used to allow for O(1) access to individual reminders by [ReviewReminderId].
 */
class ReviewReminderGroup(
    private val underlyingMap: HashMap<ReviewReminderId, ReviewReminder>,
) {
    constructor() : this(HashMap())

    constructor(map: Map<ReviewReminderId, ReviewReminder>) : this(HashMap(map))

    /**
     * Manually construct a [ReviewReminderGroup] from key-value pairs.
     */
    constructor(vararg pairs: Pair<ReviewReminderId, ReviewReminder>) : this(
        buildMap { pairs.forEach { put(it.first, it.second) } },
    )

    /**
     * Merge multiple [ReviewReminderGroup]s into one.
     * [ReviewReminderId] should be unique, so there should be no collisions.
     */
    constructor(vararg groups: ReviewReminderGroup) : this(
        buildMap { groups.forEach { putAll(it.underlyingMap) } },
    )

    /**
     * Constructs a [ReviewReminderGroup] from a serialized JSON string.
     *
     * @throws SerializationException If the stored string is not a valid JSON string.
     * @throws IllegalArgumentException If the decoded reminders map is not a HashMap<[ReviewReminderId], [ReviewReminder]>.
     */
    constructor(serializedString: String) : this(
        Json.decodeFromString<HashMap<ReviewReminderId, ReviewReminder>>(serializedString),
    )

    /**
     * Serializes this [ReviewReminderGroup] to a JSON string for storage.
     */
    fun serializeToString(): String = Json.encodeToString(underlyingMap)

    /**
     * Merge two [ReviewReminderGroup]s into one.
     * [ReviewReminderId] should be unique, so there should be no collisions.
     */
    operator fun plus(other: ReviewReminderGroup): ReviewReminderGroup = ReviewReminderGroup(underlyingMap + other.underlyingMap)

    operator fun get(id: ReviewReminderId) = underlyingMap[id]

    operator fun set(
        id: ReviewReminderId,
        reminder: ReviewReminder,
    ) {
        underlyingMap[id] = reminder
    }

    fun isEmpty(): Boolean = underlyingMap.isEmpty()

    fun remove(id: ReviewReminderId) {
        underlyingMap.remove(id)
    }

    fun forEach(action: (ReviewReminderId, ReviewReminder) -> Unit) {
        underlyingMap.forEach { (id, reminder) -> action(id, reminder) }
    }

    /**
     * Get the values of the underlying map, removing the [ReviewReminderId] keys.
     */
    fun getRemindersList(): List<ReviewReminder> = underlyingMap.values.toList()

    /**
     * Toggles whether a [ReviewReminder] is enabled.
     */
    fun toggleEnabled(id: ReviewReminderId) {
        underlyingMap[id]?.apply { enabled = !enabled }
    }

    override fun equals(other: Any?): Boolean = other is ReviewReminderGroup && other.underlyingMap == this.underlyingMap

    override fun hashCode(): Int = underlyingMap.hashCode()
}

/**
 * Convenience extension constructor for merging a list of [ReviewReminderGroup]s into one.
 */
fun List<ReviewReminderGroup>.mergeAll() = ReviewReminderGroup(*this.toTypedArray())

/**
 * Convenience typealias for the mutation functions passed to editors of [ReviewReminderGroup].
 */
typealias ReviewReminderGroupEditor = (ReviewReminderGroup) -> ReviewReminderGroup
