/*
 *  Copyright (c) 2023 Brayan Oliveira <brayandso.dev@gmail.com>
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
package com.ichi2.anki.previewer

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.SavedStateHandle
import anki.collection.OpChanges
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.Flag
import com.ichi2.anki.asyncIO
import com.ichi2.anki.browser.IdsFile
import com.ichi2.anki.cardviewer.SingleCardSide
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.launchCatchingIO
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.noteeditor.NoteEditorLauncher
import com.ichi2.anki.observability.ChangeManager
import com.ichi2.anki.observability.undoableOp
import com.ichi2.anki.pages.AnkiServer
import com.ichi2.anki.reviewer.CardSide
import com.ichi2.anki.servicelayer.MARKED_TAG
import com.ichi2.anki.servicelayer.NoteService
import com.ichi2.anki.utils.ext.flag
import com.ichi2.anki.utils.ext.require
import com.ichi2.anki.utils.ext.setUserFlagForCards
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import timber.log.Timber

class PreviewerViewModel(
    savedStateHandle: SavedStateHandle,
) : CardViewerViewModel(savedStateHandle),
    ChangeManager.Subscriber {
    val currentIndex =
        savedStateHandle.getMutableStateFlow(
            KEY_CURRENT_INDEX,
            initialValue = savedStateHandle.require<Int>(PreviewerFragment.CURRENT_INDEX_ARG),
        )
    val backSideOnly = savedStateHandle.getMutableStateFlow(KEY_BACKSIDE_ONLY, false)
    val isMarked = MutableStateFlow(false)
    val flag: MutableStateFlow<Flag> = MutableStateFlow(Flag.NONE)

    @VisibleForTesting
    val selectedCardIds: List<Long> = savedStateHandle.require<IdsFile>(PreviewerFragment.CARD_IDS_FILE_ARG).getIds()

    val isBackButtonEnabled =
        combine(currentIndex, showingAnswer, backSideOnly) { index, showingAnswer, isBackSideOnly ->
            index != 0 || (showingAnswer && !isBackSideOnly)
        }
    val isNextButtonEnabled =
        combine(currentIndex, showingAnswer) { index, showingAnswer ->
            index != selectedCardIds.lastIndex || !showingAnswer
        }

    private val showAnswerOnReload get() = showingAnswer.value || backSideOnly.value

    override var currentCard: Deferred<Card> =
        asyncIO {
            withCol { getCard(selectedCardIds[savedStateHandle.require(PreviewerFragment.CURRENT_INDEX_ARG)]) }
        }
    override val server = AnkiServer(this).also { it.start() }

    init {
        ChangeManager.subscribe(this)
    }

    /* *********************************************************************************************
     ************************ Public methods: meant to be used by the View **************************
     ********************************************************************************************* */

    /** Call this after the webView has finished loading the page */
    @NeedsTest("16302 - a sound-only card on the back/flipped with 'don't keep activities'")
    @NeedsTest("16302 - on config changes, sound continues to play")
    override fun onPageFinished(isAfterRecreation: Boolean) {
        launchCatchingIO {
            if (isAfterRecreation) {
                showCard(showAnswerOnReload)
                // isAfterRecreation can either mean:
                // * after config change (ViewModel exists)
                // * after recreation (ViewModel did not exist)
                // if the ViewModel existed, we want to continue playing audio
                // if not, we want to setup the sound player
                cardMediaPlayer.ensureAvTagsLoaded(currentCard.await())
            } else {
                // re-render the current card
                updateCurrentIndex { it }
            }
        }
    }

    fun toggleBackSideOnly() {
        Timber.v("toggleBackSideOnly() %b", !backSideOnly.value)
        launchCatchingIO {
            backSideOnly.emit(!backSideOnly.value)
            if (!backSideOnly.value && showingAnswer.value) {
                showQuestion()
                cardMediaPlayer.autoplayAllForSide(CardSide.QUESTION)
            } else if (backSideOnly.value && !showingAnswer.value) {
                showAnswer()
                cardMediaPlayer.autoplayAllForSide(CardSide.ANSWER)
            }
        }
    }

    fun toggleMark() {
        launchCatchingIO {
            val card = currentCard.await()
            val note = withCol { card.note(this@withCol) }
            NoteService.toggleMark(note)
            isMarked.emit(NoteService.isMarked(note))
        }
    }

    fun setFlag(flag: Flag) {
        launchCatchingIO {
            val card = currentCard.await()
            undoableOp {
                setUserFlagForCards(cids = listOf(card.id), flag = flag)
            }
            this.flag.emit(flag)
        }
    }

    fun toggleFlag(flag: Flag) {
        if (this@PreviewerViewModel.flag.value == flag) {
            setFlag(Flag.NONE)
        } else {
            setFlag(flag)
        }
    }

    /**
     * Shows the current card's answer
     * or the next question if the answer is already being shown
     */
    fun onNextButtonClick() {
        launchCatchingIO {
            if (!showingAnswer.value && !backSideOnly.value) {
                showAnswer()
                cardMediaPlayer.autoplayAllForSide(CardSide.ANSWER)
            } else {
                updateCurrentIndex { it + 1 }
            }
        }
    }

    /**
     * Shows the previous' card question
     * or hides the current answer if the first card is being shown
     */
    fun onPreviousButtonClick() {
        launchCatchingIO {
            if (currentIndex.value > 0) {
                updateCurrentIndex { it - 1 }
            } else if (showingAnswer.value && !backSideOnly.value) {
                showQuestion()
            }
        }
    }

    suspend fun getNoteEditorDestination() = NoteEditorLauncher.EditNoteFromPreviewer(currentCard.await().id)

    fun replayMedia() {
        launchCatchingIO {
            val side = if (showingAnswer.value) SingleCardSide.BACK else SingleCardSide.FRONT
            cardMediaPlayer.replayAll(side)
        }
    }

    fun cardsCount() = selectedCardIds.count()

    /**
     * @param sliderPosition the value of the slider (i.e. Slider::value). It's NOT the card index.
     */
    fun onSliderChange(sliderPosition: Int) {
        val index = sliderPosition - 1
        if (index !in selectedCardIds.indices) return
        launchCatchingIO {
            updateCurrentIndex { index }
        }
    }

    /* *********************************************************************************************
     *************************************** Internal methods ***************************************
     ********************************************************************************************* */

    /** Applies [update] to [currentIndex] and re-renders the resulting card. */
    private suspend fun updateCurrentIndex(update: (Int) -> Int) {
        currentIndex.update(update)
        showCard(showAnswer = backSideOnly.value)
        loadAndPlaySounds()
    }

    private suspend fun showCard(showAnswer: Boolean) {
        currentCard =
            asyncIO {
                withCol { getCard(selectedCardIds[currentIndex.value]) }
            }
        if (showAnswer) showAnswer() else showQuestion()
        updateFlagIcon()
        updateMarkIcon()
    }

    private suspend fun updateFlagIcon() {
        flag.emit(currentCard.await().flag)
    }

    private suspend fun updateMarkIcon() {
        val card = currentCard.await()
        val isMarkedValue = withCol { card.note(this@withCol).hasTag(this@withCol, MARKED_TAG) }
        isMarked.emit(isMarkedValue)
    }

    private suspend fun loadAndPlaySounds() {
        val side: CardSide =
            when {
                backSideOnly.value -> CardSide.BOTH
                showingAnswer.value -> CardSide.ANSWER
                else -> CardSide.QUESTION
            }
        cardMediaPlayer.loadCardAvTags(currentCard.await())
        cardMediaPlayer.autoplayAllForSide(side)
    }

    /** From the [desktop code](https://github.com/ankitects/anki/blob/1ff55475b93ac43748d513794bcaabd5d7df6d9d/qt/aqt/reviewer.py#L671) */
    override suspend fun typeAnsFilter(text: String): String =
        if (showingAnswer.value) {
            val typeAnswer = TypeAnswer.getInstance(currentCard.await(), text)
            typeAnswer?.answerFilter() ?: text
        } else {
            TypeAnswer.removeTags(text)
        }

    override fun opExecuted(
        changes: OpChanges,
        handler: Any?,
    ) {
        launchCatchingIO {
            when {
                changes.noteText -> {
                    val card = currentCard.await()
                    withCol { card.load(this) }
                    updateMarkIcon()
                    if (showingAnswer.value) {
                        showAnswer()
                    } else {
                        showQuestion()
                    }
                }
                changes.card -> {
                    val card = currentCard.await()
                    withCol { card.load(this) }
                    updateFlagIcon()
                }
            }
        }
    }

    companion object {
        private const val KEY_BACKSIDE_ONLY = "backsideOnly"
        private const val KEY_CURRENT_INDEX = "currentIndex"
    }
}
