// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import anki.collection.OpChanges
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.CollectionManager.withOpenColOrNull
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.observability.undoableOp
import com.ichi2.anki.progress.HasProgress
import com.ichi2.anki.progress.ProgressManager
import com.ichi2.anki.study.DeckKind
import com.ichi2.anki.study.StudyAvailability
import com.ichi2.anki.study.currentDeckStudyState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File

class StudyOptionsViewModel :
    ViewModel(),
    HasProgress<StudyOptionsProgress> {
    override val progressManager = ProgressManager<StudyOptionsProgress>()

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
    suspend fun refreshData() {
        withOpenColOrNull { updateStateFromCollection() }
    }

    suspend fun rebuildCram(deckId: DeckId) {
        Timber.d("doInBackground - RebuildCram")
        progressManager.withProgress(message = StudyOptionsProgress.REBUILDING_FILTERED_DECK) {
            undoableOp { sched.rebuildFilteredDeck(deckId) }
            withCol { updateStateFromCollection() }
        }
    }

    suspend fun emptyCram(deckId: DeckId) {
        Timber.d("doInBackgroundEmptyCram")
        progressManager.withProgress(message = StudyOptionsProgress.EMPTYING_FILTERED_DECK) {
            undoableOp { sched.emptyFilteredDeck(deckId) }
            withCol { updateStateFromCollection() }
        }
    }

    fun unbury(deckId: DeckId): Job =
        viewModelScope.launch {
            undoableOp<OpChanges> { sched.unburyDeck(deckId) }
        }

    /**
     * See https://github.com/ankitects/anki/blob/b05c9d15986ab4e33daa2a47a947efb066bb69b6/qt/aqt/overview.py#L226-L272
     */
    private fun Collection.updateStateFromCollection() {
        val deck = decks.current()
        val study = currentDeckStudyState()
        val deckId = study.deckId
        var buriedNew = 0
        var buriedLearning = 0
        var buriedReview = 0
        val tree = sched.deckDueTree(deckId)
        if (tree != null) {
            buriedNew = tree.newCount - study.newCardsAvailable
            buriedLearning = tree.learnCount - study.learningCardsAvailable
            buriedReview = tree.reviewCount - study.reviewCardsAvailable
        }
        val isDynamic = study.kind == DeckKind.Filtered
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
                newCardsToday = study.newCardsAvailable,
                lrnCardsToday = study.learningCardsAvailable,
                revCardsToday = study.reviewCardsAvailable,
                buriedNew = buriedNew,
                buriedLearning = buriedLearning,
                buriedReview = buriedReview,
                totalNewCards = sched.totalNewForCurrentDeck(),
                numberOfCardsInDeck = study.cardCount,
            )

        flowOfIsFilteredDeck.value = isDynamic
        flowOfHaveBuried.value = sched.haveBuried()
        flowOfSelectedDeckId.value = study.deckId
        // Only resolve the media directory when a description will render it: an in-memory
        // collection has no media folder and `media.dir` would throw.
        flowOfMediaDir.value = if (!description.isNullOrEmpty()) media.dir else null

        flowOfState.value =
            when {
                study.isEmptyRegularDeck ->
                    StudyOptionsState.Empty(
                        deckId = deckId,
                        deckName = deck.name,
                        data = data,
                    )
                study.availability == StudyAvailability.NoCardsAvailable ->
                    StudyOptionsState.Congrats(
                        deckId = deckId,
                        deckName = deck.name,
                        isDynamic = isDynamic,
                        data = data,
                    )
                else ->
                    StudyOptionsState.StudyOptions(
                        deckId = deckId,
                        deckName = deck.name,
                        deckDescription = description,
                        isDynamic = isDynamic,
                        data = data,
                    )
            }
    }
}

enum class StudyOptionsProgress {
    REBUILDING_FILTERED_DECK,
    EMPTYING_FILTERED_DECK,
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
        val deckId: DeckId,
        val deckName: String,
        val deckDescription: String?,
        val isDynamic: Boolean,
        val data: DeckStudyData,
    ) : StudyOptionsState

    data class Congrats(
        val deckId: DeckId,
        val deckName: String,
        val isDynamic: Boolean,
        val data: DeckStudyData,
    ) : StudyOptionsState

    data class Empty(
        val deckId: DeckId,
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

fun StudyOptionsState.isFiltered(): Boolean =
    when (this) {
        is StudyOptionsState.StudyOptions -> isDynamic
        is StudyOptionsState.Congrats -> isDynamic
        is StudyOptionsState.Empty -> false
        is StudyOptionsState.Loading -> false
    }

fun StudyOptionsState.deckIdOrNull(): DeckId? =
    when (this) {
        is StudyOptionsState.StudyOptions -> deckId
        is StudyOptionsState.Congrats -> deckId
        is StudyOptionsState.Empty -> deckId
        is StudyOptionsState.Loading -> null
    }
