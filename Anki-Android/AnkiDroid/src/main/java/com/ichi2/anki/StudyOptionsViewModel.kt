// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import anki.collection.OpChanges
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.observability.undoableOp
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File

class StudyOptionsViewModel : ViewModel() {
    val flowOfState: StateFlow<StudyOptionsState>
        field = MutableStateFlow<StudyOptionsState>(StudyOptionsState.Loading)
    val state: StudyOptionsState get() = flowOfState.value

    val flowOfIsFilteredDeck: StateFlow<Boolean>
        field = MutableStateFlow(false)
    val isFilteredDeck: Boolean get() = flowOfIsFilteredDeck.value

    val flowOfHaveBuried: StateFlow<Boolean>
        field = MutableStateFlow(false)
    val haveBuried: Boolean get() = flowOfHaveBuried.value

    val flowOfSelectedDeckId: StateFlow<DeckId>
        field = MutableStateFlow(0L)
    val selectedDeckId: DeckId get() = flowOfSelectedDeckId.value

    /** The collection's media directory, used to resolve images in deck descriptions. */
    val flowOfMediaDir: StateFlow<File?>
        field = MutableStateFlow<File?>(null)
    val mediaDir: File? get() = flowOfMediaDir.value

    /**
     * Refreshes the deck statistics, deck name, description, and menu-state flags from
     * the collection.
     */
    fun refreshData(): Job =
        viewModelScope.launch {
            if (!CollectionManager.isOpenUnsafe()) return@launch
            withCol { updateStateFromCollection() }
        }

    suspend fun rebuildCram() {
        Timber.d("doInBackground - RebuildCram")
        undoableOp { sched.rebuildFilteredDeck(decks.selected()) }
        withCol { updateStateFromCollection() }
    }

    suspend fun emptyCram() {
        Timber.d("doInBackgroundEmptyCram")
        undoableOp { sched.emptyFilteredDeck(decks.selected()) }
        withCol { updateStateFromCollection() }
    }

    fun unbury(): Job =
        viewModelScope.launch {
            undoableOp<OpChanges> { sched.unburyDeck(decks.getCurrentId()) }
        }

    /**
     * See https://github.com/ankitects/anki/blob/b05c9d15986ab4e33daa2a47a947efb066bb69b6/qt/aqt/overview.py#L226-L272
     */
    private fun Collection.updateStateFromCollection() {
        val deck = decks.current()
        val deckId = deck.id
        val counts = sched.counts()
        var buriedNew = 0
        var buriedLearning = 0
        var buriedReview = 0
        val tree = sched.deckDueTree(deckId)
        if (tree != null) {
            buriedNew = tree.newCount - counts.new
            buriedLearning = tree.learnCount - counts.lrn
            buriedReview = tree.reviewCount - counts.rev
        }
        val isDynamic = deck.isFiltered
        val fullName = deck.getString("name")
        val description =
            if (isDynamic) {
                null
            } else {
                if (deck.descriptionAsMarkdown) {
                    @Suppress("DEPRECATION")
                    renderMarkdown(deck.description, sanitize = true)
                } else {
                    deck.description
                }
            }
        val data =
            DeckStudyData(
                newCardsToday = counts.new,
                lrnCardsToday = counts.lrn,
                revCardsToday = counts.rev,
                buriedNew = buriedNew,
                buriedLearning = buriedLearning,
                buriedReview = buriedReview,
                totalNewCards = sched.totalNewForCurrentDeck(),
                numberOfCardsInDeck = decks.cardCount(deckId, includeSubdecks = true),
            )

        flowOfIsFilteredDeck.value = isDynamic
        flowOfHaveBuried.value = sched.haveBuried()
        flowOfSelectedDeckId.value = decks.selected()
        // Only resolve the media directory when a description will render it: an in-memory
        // collection has no media folder and `media.dir` would throw.
        flowOfMediaDir.value = if (!description.isNullOrEmpty()) media.dir else null

        val totalDue = data.newCardsToday + data.lrnCardsToday + data.revCardsToday
        flowOfState.value =
            when {
                data.numberOfCardsInDeck == 0 && !isDynamic ->
                    StudyOptionsState.Empty(
                        deckName = fullName,
                        data = data,
                    )
                totalDue == 0 ->
                    StudyOptionsState.Congrats(
                        deckName = fullName,
                        isDynamic = isDynamic,
                        data = data,
                    )
                else ->
                    StudyOptionsState.StudyOptions(
                        deckName = fullName,
                        deckDescription = description,
                        isDynamic = isDynamic,
                        data = data,
                    )
            }
    }
}

class DeckStudyData(
    val newCardsToday: Int,
    val lrnCardsToday: Int,
    val revCardsToday: Int,
    val buriedNew: Int,
    val buriedLearning: Int,
    val buriedReview: Int,
    val totalNewCards: Int,
    val numberOfCardsInDeck: Int,
) {
    /** True if any cards (new, learning, or review) are buried. */
    val hasBuriedCards: Boolean get() = buriedNew > 0 || buriedLearning > 0 || buriedReview > 0
}

sealed interface StudyOptionsState {
    data object Loading : StudyOptionsState

    data class StudyOptions(
        val deckName: String,
        val deckDescription: String?,
        val isDynamic: Boolean,
        val data: DeckStudyData,
    ) : StudyOptionsState

    data class Congrats(
        val deckName: String,
        val isDynamic: Boolean,
        val data: DeckStudyData,
    ) : StudyOptionsState

    data class Empty(
        val deckName: String,
        val data: DeckStudyData,
    ) : StudyOptionsState
}

/** The [DeckStudyData] for any non-[StudyOptionsState.Loading] state, or `null` while loading. */
fun StudyOptionsState.dataOrNull(): DeckStudyData? =
    when (this) {
        is StudyOptionsState.StudyOptions -> data
        is StudyOptionsState.Congrats -> data
        is StudyOptionsState.Empty -> data
        is StudyOptionsState.Loading -> null
    }

/** The deck name for any non-[StudyOptionsState.Loading] state, or `null` while loading. */
fun StudyOptionsState.deckNameOrNull(): String? =
    when (this) {
        is StudyOptionsState.StudyOptions -> deckName
        is StudyOptionsState.Congrats -> deckName
        is StudyOptionsState.Empty -> deckName
        is StudyOptionsState.Loading -> null
    }
