/*
 * Copyright (c) 2025 lukstbit <52494258+lukstbit@users.noreply.github.com>
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

package com.ichi2.anki.filtered

import android.os.Parcelable
import anki.decks.FilteredDeckForUpdate
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.sched.Scheduler
import kotlinx.parcelize.Parcelize

sealed interface FilteredDeckOptionsState

/**
 * Represents the initialization state where we are loading the initial data. If [throwable] is not
 * null it means we had an error while initializing and the view should show notify the user and
 * gracefully exit the screen as we can't continue.
 */
data class Initializing(
    val throwable: Throwable? = null,
) : FilteredDeckOptionsState

/**
 * State that indicates the successful building/rebuilding of a filtered deck. After receiving this
 * the view is expected to exit the screen.
 */
data object DeckBuilt : FilteredDeckOptionsState

/**
 * State class which encapsulates all the data required by the filtered deck options screen. This
 * class is a wrapper around the backend data object [FilteredDeckForUpdate] combined
 * with additional property to handle the views and errors.
 */
@Parcelize
data class FilteredDeckOptions(
    /**
     * If null we are creating a new filtered deck otherwise it's the [DeckId] of a filtered deck
     * which we are updating.
     */
    val id: DeckId? = null,
    /** Name of the filtered deck, initial name with the following format: "Filtered deck HH:mm" */
    val name: String = "",
    /** If not null, there's an error related to the text entered */
    val nameInputError: FilteredNameInputError? = null,
    /**
     * String used as the title of the filtered options screen. Similar to [name], this will be the
     * name we get when first loading the deck data and will not change for the lifetime of the screen.
     */
    val title: String = "",
    /**
     * Flag indicating if cards will be rescheduled based on the answers in this filtered deck. If
     * false the view will present options for custom delays.
     */
    val shouldReschedule: Boolean = true,
    /**
     * Allow creating a filtered deck even if it's empty. If false an error(see [throwable]) will be
     * thrown when building/rebuilding results in an empty deck.
     */
    val allowEmpty: Boolean = true,
    /** The list if options for cards selection, output of [Scheduler.filteredDeckOrderLabels] */
    val cardOptions: List<String> = emptyList(),
    /** The state for the first filter, which is always available */
    val filter1State: SearchTermState = SearchTermState(),
    /**
     * True if user enabled the second filter false otherwise. If this is false the data of the
     * second filter [filter2State] shouldn't be considered.
     */
    val isSecondFilterEnabled: Boolean = false,
    /** The state for an optional second filter */
    val filter2State: SearchTermState? = SearchTermState(),
    /** Custom delay for again, only available to the user if shouldReschedule is false */
    val delayAgain: String = "0",
    /** Custom delay for hard, only available to the user if shouldReschedule is false */
    val delayHard: String = "0",
    /** Custom delay for good, only available to the user if shouldReschedule is false */
    val delayGood: String = "0",
    /** Flag to indicate to the view if we are currently building/rebuilding the filtered deck. */
    val isBuildingBrowserSearch: Boolean = false,
    /**
     * Errors from model operations related to building/rebuilding the filtered deck. As these will
     * be relatively common they are offered through this property to the view to show them to the
     * user and then clear them.
     */
    val throwable: Throwable? = null,
    /**
     * If not null start the browser with [browserQuery] as the search string.
     */
    val browserQuery: String? = null,
) : Parcelable,
    FilteredDeckOptionsState

/** True if the user is allowed to build/rebuild, false otherwise */
val FilteredDeckOptions.isBuildingAllowed: Boolean
    get() {
        val hasNoInputErrors =
            filter1State.error == null &&
                !(isSecondFilterEnabled && filter2State?.error != null) &&
                nameInputError == null
        return !isBuildingBrowserSearch && hasNoInputErrors
    }

/** State for each filter (currently two with the second one being optional). */
@Parcelize
data class SearchTermState(
    val search: String = "",
    val limit: String = "100",
    val index: Int = 0,
    /** If not null, there's an error related to the text entered */
    val error: SearchInputError? = null,
) : Parcelable

enum class SearchInputError {
    Empty,
    NotANumber,
}

/** Signals errors related to the filtered deck name */
enum class FilteredNameInputError {
    Empty,
    AlreadyExists,
}

/** Type of custom delays the user can set. */
enum class RescheduleDelay {
    Again,
    Hard,
    Good,
}

/** Small abstraction over our filter indexes so we don't use simple ints. */
enum class FilterIndex {
    First,
    Second,
}
