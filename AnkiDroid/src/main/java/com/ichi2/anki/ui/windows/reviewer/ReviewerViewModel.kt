/*
 *  Copyright (c) 2024 Brayan Oliveira <brayandso.dev@gmail.com>
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
package com.ichi2.anki.ui.windows.reviewer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import anki.collection.OpChanges
import anki.frontend.SetSchedulingStatesRequest
import anki.scheduler.CardAnswer.Rating
import com.ichi2.anki.AbstractFlashcardViewer
import com.ichi2.anki.AbstractFlashcardViewer.Companion.RESULT_NO_MORE_CARDS
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.Flag
import com.ichi2.anki.Reviewer
import com.ichi2.anki.asyncIO
import com.ichi2.anki.cardviewer.SingleCardSide
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.common.destinations.BrowserDestination
import com.ichi2.anki.common.destinations.CardInfoDestination
import com.ichi2.anki.common.destinations.CardInfoDestination.EntryPoint
import com.ichi2.anki.launchCatchingIO
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.libanki.CardId
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.NoteId
import com.ichi2.anki.libanki.redoLabel
import com.ichi2.anki.libanki.sched.CurrentQueueState
import com.ichi2.anki.libanki.undoLabel
import com.ichi2.anki.noteeditor.NoteEditorLauncher
import com.ichi2.anki.observability.ChangeManager
import com.ichi2.anki.observability.undoableOp
import com.ichi2.anki.pages.AnkiServer
import com.ichi2.anki.pages.DeckOptionsDestination
import com.ichi2.anki.pages.DeckOptionsEntry
import com.ichi2.anki.pages.PostRequestUri
import com.ichi2.anki.pages.StatisticsDestination
import com.ichi2.anki.preferences.reviewer.ViewerAction
import com.ichi2.anki.previewer.CardViewerViewModel
import com.ichi2.anki.previewer.TypeAnswer
import com.ichi2.anki.previewer.typeAnsRe
import com.ichi2.anki.reviewer.BindingProcessor
import com.ichi2.anki.reviewer.CardSide
import com.ichi2.anki.reviewer.ReviewerBinding
import com.ichi2.anki.servicelayer.MARKED_TAG
import com.ichi2.anki.servicelayer.NoteService
import com.ichi2.anki.servicelayer.isBuryNoteAvailable
import com.ichi2.anki.servicelayer.isSuspendNoteAvailable
import com.ichi2.anki.tryRedo
import com.ichi2.anki.tryUndo
import com.ichi2.anki.ui.windows.reviewer.autoadvance.AnswerAction
import com.ichi2.anki.ui.windows.reviewer.autoadvance.AutoAdvance
import com.ichi2.anki.ui.windows.reviewer.autoadvance.AutoAdvanceAction
import com.ichi2.anki.ui.windows.reviewer.autoadvance.QuestionAction
import com.ichi2.anki.utils.Destination
import com.ichi2.anki.utils.ext.answerCard
import com.ichi2.anki.utils.ext.cardStatsNoCardClean
import com.ichi2.anki.utils.ext.flag
import com.ichi2.anki.utils.ext.getLongOrNull
import com.ichi2.anki.utils.ext.setUserFlagForCards
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.intellij.lang.annotations.Language
import timber.log.Timber
import com.ichi2.anki.common.destinations.Destination as NavigateDestination

class ReviewerViewModel(
    savedStateHandle: SavedStateHandle,
) : CardViewerViewModel(savedStateHandle),
    ChangeManager.Subscriber,
    BindingProcessor<ReviewerBinding, ViewerAction>,
    AutoAdvance.ActionListener {
    private var queueState: Deferred<CurrentQueueState?> =
        asyncIO {
            withCol { sched.currentQueueState() }
        }
    override var currentCard =
        asyncIO {
            queueState.await()?.topCard
                ?: Card(anki.cards.Card.getDefaultInstance())
        }
    private val repository = StudyScreenRepository()
    private var isInputFocused = false
    val finishResultFlow = MutableSharedFlow<Int>()
    val isMarkedFlow = MutableStateFlow(false)
    val flagFlow = MutableStateFlow(Flag.NONE)
    val actionFeedbackFlow = MutableSharedFlow<String>()
    val canBuryNoteFlow = MutableStateFlow(true)
    val canSuspendNoteFlow = MutableStateFlow(true)
    val undoLabelFlow = MutableStateFlow<String?>(null)
    val redoLabelFlow = MutableStateFlow<String?>(null)
    val countsFlow = savedStateHandle.getMutableStateFlow(KEY_COUNTS, StudyCounts())
    val typeAnswerFlow = MutableStateFlow<TypeAnswer?>(null)
    val onTypedAnswerResultFlow = MutableSharedFlow<CompletableDeferred<String>>()
    val onCardUpdatedFlow = MutableSharedFlow<Unit>()
    val destinationFlow = MutableSharedFlow<Destination>()
    val navigateFlow = MutableSharedFlow<NavigateDestination>()
    val editNoteTagsFlow = MutableSharedFlow<NoteId>()
    val setDueDateFlow = MutableSharedFlow<CardId>()
    val resetProgressFlow = MutableSharedFlow<Unit>()
    val answerFeedbackFlow = MutableSharedFlow<Rating>()
    val voiceRecorderEnabledFlow = MutableStateFlow(repository.isRecordVoiceEnabled)
    val whiteboardEnabledFlow = MutableStateFlow(repository.isWhiteboardEnabled)
    val replayVoiceFlow = MutableSharedFlow<Unit>()
    val timeBoxReachedFlow = MutableSharedFlow<Collection.TimeboxReached>()
    val pageUpFlow = MutableSharedFlow<Unit>()
    val pageDownFlow = MutableSharedFlow<Unit>()
    val statesMutationEvalFlow = MutableSharedFlow<String>()

    override val server: AnkiServer = AnkiServer(this, repository.getServerPort()).also { it.start() }
    private val stateMutationKey = repository.generateStateMutationKey()
    private val stateMutationJs: Deferred<String> = asyncIO { repository.getCustomSchedulingJs() }
    private var typedAnswer = ""

    private val autoAdvance = AutoAdvance(viewModelScope, this, currentCard)
    private val isHtmlTypeAnswerEnabled = repository.isHtmlTypeAnswerEnabled
    val answerTimer = AnswerTimer()
    private val actionsMutex = Mutex()

    /**
     * A flag that determines if the SchedulingStates in CurrentQueueState are
     * safe to persist in the database when answering a card. This is used to
     * ensure that the custom JS scheduler has persisted its SchedulingStates
     * back to the Reviewer before we save it to the database.
     *
     * This flag should be reset when we show the front of the card
     * and only complete once we know the custom scheduler has finished its
     * execution, or complete immediately if the custom scheduler has not
     * been configured.
     */
    private var mutationSignal = CompletableDeferred(Unit)

    val isAutoAdvanceEnabledFlow = MutableStateFlow(autoAdvance.isEnabled)
    val answerButtonsNextTimeFlow: MutableStateFlow<AnswerButtonsNextTime?> = MutableStateFlow(null)
    private val shouldShowNextTimes = asyncIO { repository.getShouldShowNextTimes() }

    init {
        ChangeManager.subscribe(this)
        launchCatchingIO {
            withCol { startTimebox() }
            updateUndoAndRedoLabels()
            // The height of the answer buttons may increase if `Show button time` is enabled.
            // To ensure consistent height, load the times to match the height of the `Show answer`
            // button with the answer buttons.
            updateNextTimes()
        }
        cardMediaPlayer.setOnMediaGroupCompletedListener {
            if (!autoAdvance.shouldWaitForAudio()) return@setOnMediaGroupCompletedListener

            if (showingAnswer.value) {
                autoAdvance.onShowAnswer()
            } else {
                autoAdvance.onShowQuestion()
            }
        }
    }

    override fun onPageFinished(isAfterRecreation: Boolean) {
        Timber.v("ReviewerViewModel::onPageFinished %b", isAfterRecreation)
        if (isAfterRecreation) {
            launchCatchingIO {
                // TODO handle "Don't keep activities"
                if (showingAnswer.value) showAnswer() else showQuestion()
            }
        } else {
            launchCatchingIO {
                updateCurrentCard()
            }
        }
    }

    fun onShowAnswer() {
        executeAction(ViewerAction.SHOW_ANSWER)
    }

    fun answerCard(rating: Rating) {
        val action =
            when (rating) {
                Rating.AGAIN -> ViewerAction.ANSWER_AGAIN
                Rating.HARD -> ViewerAction.ANSWER_HARD
                Rating.GOOD -> ViewerAction.ANSWER_GOOD
                Rating.EASY -> ViewerAction.ANSWER_EASY
                Rating.UNRECOGNIZED -> return
            }
        executeAction(action)
    }

    suspend fun getCardId() = currentCard.await().id

    /**
     * Sends an [eval] request to load the card answer, and updates components
     * with behavior specific to the `Answer` card side.
     *
     * @see showAnswer
     */
    private suspend fun showAnswerInternal() {
        Timber.v("ReviewerViewModel::onShowAnswer")
        mutationSignal.await()

        val typedAnswerResult = CompletableDeferred<String>()
        if (typeAnswerFlow.value != null) {
            onTypedAnswerResultFlow.emit(typedAnswerResult)
        } else {
            typedAnswerResult.complete("")
        }
        typedAnswer = withTimeoutOrNull(1000L) {
            typedAnswerResult.await()
        } ?: ""

        updateNextTimes()
        showAnswer()
        loadAndPlayMedia(CardSide.ANSWER)
        if (!autoAdvance.shouldWaitForAudio()) {
            autoAdvance.onShowAnswer()
        } // else wait for onMediaGroupCompleted

        if (answerTimer.state.value == AnswerTimerState.Hidden) return
        val did = currentCard.await().currentDeckId()
        val stopTimerOnAnswer = withCol { decks.configDictForDeckId(did) }.stopTimerOnAnswer
        if (stopTimerOnAnswer) {
            answerTimer.stop()
        }
    }

    private suspend fun toggleMark() {
        Timber.v("ReviewerViewModel::toggleMark")
        val card = currentCard.await()
        val note = withCol { card.note(this@withCol) }
        NoteService.toggleMark(note)
        isMarkedFlow.emit(NoteService.isMarked(note))
    }

    private suspend fun setFlag(flag: Flag) {
        Timber.v("ReviewerViewModel::setFlag")
        val card = currentCard.await()
        undoableOp {
            setUserFlagForCards(listOf(card.id), flag)
        }
        flagFlow.emit(flag)
    }

    private suspend fun toggleFlag(flag: Flag) {
        Timber.v("ReviewerViewModel::toggleFlag")
        if (flag == flagFlow.value) {
            setFlag(Flag.NONE)
        } else {
            setFlag(flag)
        }
    }

    fun onStateMutationCallback() {
        mutationSignal.complete(Unit)
    }

    private suspend fun emitEditNoteDestination() {
        val cardId = currentCard.await().id
        val destination = NoteEditorLauncher.EditNoteFromPreviewer(cardId)
        Timber.i("Opening 'edit note' for card %d", cardId)
        destinationFlow.emit(destination)
    }

    private suspend fun emitAddNoteDestination() {
        Timber.i("Launching 'add note'")
        destinationFlow.emit(NoteEditorLauncher.AddNoteFromReviewer())
    }

    private suspend fun emitCardInfoDestination() {
        val cardId = currentCard.await().id
        val destination = CardInfoDestination(cardId, EntryPoint.CURRENT_CARD_STUDY)
        Timber.i("Launching 'card info' for card %d", cardId)
        navigateFlow.emit(destination)
    }

    private suspend fun emitPreviousCardInfoDestination() {
        val previousCardId: CardId? = savedStateHandle.getLongOrNull(KEY_PREVIOUS_CARD_ID)
        if (previousCardId == null) {
            Timber.i("No previous answered card found, ignoring request for 'previous card info'")
            actionFeedbackFlow.emit(TR.cardStatsNoCardClean())
            return
        }
        val destination = CardInfoDestination(previousCardId, EntryPoint.PREVIOUS_CARD_STUDY)
        Timber.i("Launching 'previous card info' for card %d", previousCardId)
        navigateFlow.emit(destination)
    }

    @NeedsTest("verify that we show the proper deck option targets for the current card")
    private suspend fun emitDeckOptionsDestination() {
        val deckId = withCol { decks.getCurrentId() }
        val card = currentCard.await()
        val options = getDeckOptionsTargets(deckId, card)
        val isFiltered = options.first { it.deckId == deckId }.isFiltered
        val destination = DeckOptionsDestination(deckId, isFiltered, options)
        Timber.i("Launching 'deck options' for deck %d", deckId)
        destinationFlow.emit(destination)
    }

    /**
     *  Builds the valid(all [DeckOptionsEntry] that have a proper deck name) list of
     *  [DeckOptionsEntry] from which the user can select one to see its deck options.
     *  @param currentDeckId the [DeckId] of the currently selected deck
     *  @param card current card shown in the study screen
     *  See https://github.com/ankitects/anki/blob/b8884bac72aa50fa1189fe0a5079a71574bc5043/qt/aqt/deckoptions.py#L83-L100
     *  for backend implementation and ordering of the entries.
     */
    private suspend fun getDeckOptionsTargets(
        currentDeckId: DeckId,
        card: Card,
    ): List<DeckOptionsEntry> {
        val extraDeckIds = mutableListOf(currentDeckId)
        if (card.oDid != 0L && card.oDid != currentDeckId) {
            extraDeckIds.add(card.oDid)
        }
        if (card.did != currentDeckId) {
            extraDeckIds.add(card.did)
        }
        return withCol {
            extraDeckIds
                .map { deckId ->
                    DeckOptionsEntry(
                        deckId = deckId,
                        name = decks.nameIfExists(deckId),
                        isFiltered = decks.isFiltered(deckId),
                    )
                }.filter { it.name != null }
                .sortedBy { it.isFiltered }
        }
    }

    private suspend fun emitBrowseDestination() {
        val deckId = withCol { decks.getCurrentId() }
        val cardId = currentCard.await().id
        val destination = BrowserDestination.ScrollToCard(deckId, cardId)
        Timber.i("Launching 'browse options' for deck %d", deckId)
        navigateFlow.emit(destination)
    }

    private suspend fun deleteNote() {
        val cardId = currentCard.await().id
        val noteCount =
            undoableOp {
                removeNotes(cardIds = listOf(cardId))
            }.count
        actionFeedbackFlow.emit(TR.browsingCardsDeleted(noteCount))
        updateCurrentCard()
    }

    private suspend fun buryCard() {
        val cardId = currentCard.await().id
        val noteCount =
            undoableOp {
                sched.buryCards(cids = listOf(cardId))
            }.count
        actionFeedbackFlow.emit(TR.studyingCardsBuried(noteCount))
        updateCurrentCard()
    }

    private suspend fun buryNote() {
        val noteId = currentCard.await().nid
        val noteCount =
            undoableOp {
                sched.buryNotes(nids = listOf(noteId))
            }.count
        actionFeedbackFlow.emit(TR.studyingCardsBuried(noteCount))
        updateCurrentCard()
    }

    private suspend fun suspendCard() {
        val cardId = currentCard.await().id
        undoableOp {
            sched.suspendCards(ids = listOf(cardId))
        }.count
        actionFeedbackFlow.emit(TR.studyingCardSuspended())
        updateCurrentCard()
    }

    private suspend fun suspendNote() {
        val noteId = currentCard.await().nid
        undoableOp {
            sched.suspendNotes(ids = listOf(noteId))
        }
        actionFeedbackFlow.emit(TR.studyingNoteSuspended())
        updateCurrentCard()
    }

    private suspend fun undo() {
        Timber.v("ReviewerViewModel::undo")
        actionFeedbackFlow.emit(tryUndo())
    }

    private suspend fun redo() {
        Timber.v("ReviewerViewModel::redo")
        actionFeedbackFlow.emit(tryRedo())
    }

    private suspend fun userAction(
        @Reviewer.UserAction number: Int,
    ) {
        eval.emit("ankidroid.userAction($number);")
    }

    fun stopAutoAdvance() {
        Timber.v("ReviewerViewModel::stopAutoAdvance")
        autoAdvance.cancelQuestionAndAnswerActionJobs()
    }

    private suspend fun toggleAutoAdvance() {
        Timber.v("ReviewerViewModel::toggleAutoAdvance")
        autoAdvance.isEnabled = !autoAdvance.isEnabled
        isAutoAdvanceEnabledFlow.value = autoAdvance.isEnabled
        val message =
            if (autoAdvance.isEnabled) {
                TR.actionsAutoAdvanceActivated()
            } else {
                TR.actionsAutoAdvanceDeactivated()
            }
        actionFeedbackFlow.emit(message)

        if (autoAdvance.shouldWaitForAudio() && cardMediaPlayer.isPlaying) return

        if (showingAnswer.value) {
            autoAdvance.onShowAnswer()
        } else {
            autoAdvance.onShowQuestion()
        }
    }

    override suspend fun handlePostRequest(
        uri: PostRequestUri,
        bytes: ByteArray,
    ): ByteArray {
        when (uri.ankidroidMethodName) {
            "focusin" -> {
                isInputFocused = true
                return byteArrayOf()
            }
            "focusout" -> {
                isInputFocused = false
                return byteArrayOf()
            }
        }
        return when (uri.backendMethodName) {
            "getSchedulingStatesWithContext" -> getSchedulingStatesWithContext()
            "setSchedulingStates" -> setSchedulingStates(bytes)
            else -> super.handlePostRequest(uri, bytes)
        }
    }

    override suspend fun showQuestion() {
        Timber.v("ReviewerViewModel::showQuestion")
        super.showQuestion()
        runStateMutationHook()
        updateMarkIcon()
        updateFlagIcon()
        if (!autoAdvance.shouldWaitForAudio()) {
            autoAdvance.onShowQuestion()
        } // else run in onMediaGroupCompleted
    }

    private suspend fun runStateMutationHook() {
        val js = stateMutationJs.await()
        if (js.isEmpty()) {
            if (!mutationSignal.isCompleted) {
                mutationSignal.complete(Unit)
            }
            return
        }
        mutationSignal = CompletableDeferred()
        statesMutationEvalFlow.emit(
            "anki.mutateNextCardStates('$stateMutationKey', async (states, customData, ctx) => { $js });",
        )
    }

    private suspend fun getSchedulingStatesWithContext(): ByteArray {
        val state = queueState.await() ?: return ByteArray(0)
        return state
            .schedulingStatesWithContext()
            .toBuilder()
            .mergeStates(
                state.states
                    .toBuilder()
                    .mergeCurrent(
                        state.states.current
                            .toBuilder()
                            .setCustomData(state.topCard.customData)
                            .build(),
                    ).build(),
            ).build()
            .toByteArray()
    }

    private suspend fun setSchedulingStates(bytes: ByteArray): ByteArray {
        val state = queueState.await() ?: return ByteArray(0)
        val req = SetSchedulingStatesRequest.parseFrom(bytes)
        if (req.key == stateMutationKey) {
            state.states = req.states
        }
        return ByteArray(0)
    }

    private suspend fun answerCardInternal(rating: Rating) {
        Timber.v("ReviewerViewModel::answerCard")
        val state = queueState.await() ?: return
        val card = currentCard.await()
        val answer =
            withCol {
                sched.answerCard(
                    card = card,
                    states = state.states,
                    rating,
                )
            }

        undoableOp(handler = this) { sched.answerCard(answer) }
        answerFeedbackFlow.emit(rating)
        savedStateHandle[KEY_PREVIOUS_CARD_ID] = card.id

        val wasLeech = withCol { sched.stateIsLeech(answer.newState) }
        if (wasLeech) {
            withCol { card.load(this) }
            val isSuspended = card.queue.code < 0
            onLeech(isSuspended)
        }
        updateCurrentCard()
    }

    // https://github.com/ankitects/anki/blob/da907053460e2b78c31199f97bbea3cf3600f0c2/qt/aqt/reviewer.py#L954
    private suspend fun onLeech(isSuspended: Boolean) {
        Timber.i("ReviewerViewModel::onLeech (isSuspended = %b)", isSuspended)
        val message = StringBuilder(TR.studyingCardWasALeech())
        if (isSuspended) {
            message.append(" ")
            message.append(TR.studyingItHasBeenSuspended())
        }
        actionFeedbackFlow.emit(message.toString())
    }

    private suspend fun loadAndPlayMedia(side: CardSide) {
        Timber.v("ReviewerViewModel::loadAndPlaySounds")
        cardMediaPlayer.loadCardAvTags(currentCard.await())
        cardMediaPlayer.autoplayAllForSide(side)
    }

    private suspend fun updateMarkIcon() {
        Timber.v("ReviewerViewModel::updateMarkIcon")
        val card = currentCard.await()
        val isMarkedValue = withCol { card.note(this@withCol).hasTag(this@withCol, MARKED_TAG) }
        isMarkedFlow.emit(isMarkedValue)
    }

    private suspend fun updateFlagIcon() {
        Timber.v("ReviewerViewModel::updateFlagIcon")
        val card = currentCard.await()
        flagFlow.emit(card.flag)
    }

    private suspend fun updateCurrentCard() {
        Timber.v("ReviewerViewModel::updateCurrentCard")
        queueState =
            asyncIO {
                withCol {
                    sched.currentQueueState()
                }
            }
        val state = queueState.await()
        if (state == null) {
            finishResultFlow.emit(RESULT_NO_MORE_CARDS)
            return
        }
        state.timeboxReached?.let {
            timeBoxReachedFlow.emit(it)
            return
        }

        val card = state.topCard
        currentCard = CompletableDeferred(card)
        setupAnswerTimer(card)
        autoAdvance.onCardChange(card)
        onCardUpdatedFlow.emit(Unit) // must be before showQuestion()
        showQuestion()
        loadAndPlayMedia(CardSide.QUESTION)
        canBuryNoteFlow.emit(isBuryNoteAvailable(card))
        canSuspendNoteFlow.emit(isSuspendNoteAvailable(card))
        countsFlow.emit(StudyCounts(state))
    }

    override suspend fun typeAnsFilter(text: String): String {
        Timber.v("ReviewerViewModel::typeAnsFilter")
        val typeAnswer = TypeAnswer.getInstance(currentCard.await(), text)
        return if (showingAnswer.value) {
            typeAnswerFlow.emit(null)
            typeAnswer?.answerFilter(typedAnswer) ?: text
        } else {
            typeAnswerFlow.emit(typeAnswer)
            if (isHtmlTypeAnswerEnabled) {
                typeAnswer?.let { typeAnsQuestionFilter(text, it) } ?: text
            } else {
                TypeAnswer.removeTags(text)
            }
        }
    }

    // https://github.com/ankitects/anki/blob/da907053460e2b78c31199f97bbea3cf3600f0c2/qt/aqt/reviewer.py#L704
    private fun typeAnsQuestionFilter(
        text: String,
        typeAnswer: TypeAnswer,
    ): String {
        @Suppress("XmlDeprecatedElement") // upstream uses `<center>`
        @Language("HTML")
        val repl =
            """
            <center>
            <input type="text" id="typeans" onkeydown="ankidroid.onTypeAnswerKeyDown(event);" 
               style="font-family: '${typeAnswer.font}'; font-size: ${typeAnswer.fontSize}px;">
            </center>
            """.trimIndent()
        return typeAnsRe.replace(text, repl)
    }

    private suspend fun updateUndoAndRedoLabels() {
        Timber.v("ReviewerViewModel::updateUndoAndRedoLabels")
        undoLabelFlow.emit(withCol { undoLabel() })
        redoLabelFlow.emit(withCol { redoLabel() })
    }

    private suspend fun updateNextTimes() {
        Timber.v("ReviewerViewModel::updateNextTimes")
        if (!shouldShowNextTimes.await()) return
        val state = queueState.await() ?: return

        val nextTimes = AnswerButtonsNextTime.from(state)
        answerButtonsNextTimeFlow.emit(nextTimes)
    }

    private suspend fun editNoteTags() {
        val noteId = currentCard.await().nid
        editNoteTagsFlow.emit(noteId)
    }

    fun onEditedTags(selectedTags: List<String>) {
        launchCatchingIO {
            val card = currentCard.await()
            val note = withCol { card.note(this@withCol) }
            if (note.tags == selectedTags) {
                Timber.d("No changed tags")
                return@launchCatchingIO
            }

            val tagsString = selectedTags.joinToString(" ")
            withCol { note.setTagsFromStr(this@withCol, tagsString) }
            undoableOp {
                updateNote(note)
            }
        }
    }

    private suspend fun launchSetDueDate() {
        val cardId = currentCard.await().id
        setDueDateFlow.emit(cardId)
    }

    private suspend fun launchResetProgress() = resetProgressFlow.emit(Unit)

    private suspend fun setupAnswerTimer(card: Card) {
        val shouldShowTimer = withCol { card.shouldShowTimer(this@withCol) }
        val limitMs = withCol { card.timeLimit(this@withCol) }
        answerTimer.configureForCard(shouldShowTimer, limitMs)
    }

    private suspend fun replayMedia() {
        val side = if (showingAnswer.value) SingleCardSide.BACK else SingleCardSide.FRONT
        cardMediaPlayer.replayAll(side)
    }

    private fun toggleWhiteboard() {
        val newValue = !whiteboardEnabledFlow.value
        whiteboardEnabledFlow.value = newValue
        repository.isWhiteboardEnabled = newValue
    }

    private fun toggleRecordVoice() {
        val newValue = !voiceRecorderEnabledFlow.value
        voiceRecorderEnabledFlow.value = newValue
        repository.isRecordVoiceEnabled = newValue
    }

    fun executeAction(action: ViewerAction) {
        Timber.v("ReviewerViewModel::executeAction %s", action.name)
        launchCatchingIO {
            actionsMutex.withLock {
                when (action) {
                    ViewerAction.ADD_NOTE -> emitAddNoteDestination()
                    ViewerAction.CARD_INFO -> emitCardInfoDestination()
                    ViewerAction.PREVIOUS_CARD_INFO -> emitPreviousCardInfoDestination()
                    ViewerAction.DECK_OPTIONS -> emitDeckOptionsDestination()
                    ViewerAction.EDIT -> emitEditNoteDestination()
                    ViewerAction.TAG -> editNoteTags()
                    ViewerAction.DELETE -> deleteNote()
                    ViewerAction.MARK -> toggleMark()
                    ViewerAction.REDO -> redo()
                    ViewerAction.UNDO -> undo()
                    ViewerAction.RESCHEDULE_NOTE -> launchSetDueDate()
                    ViewerAction.RESET_PROGRESS -> launchResetProgress()
                    ViewerAction.TOGGLE_AUTO_ADVANCE -> toggleAutoAdvance()
                    ViewerAction.BURY_NOTE -> buryNote()
                    ViewerAction.BURY_CARD -> buryCard()
                    ViewerAction.SUSPEND_NOTE -> suspendNote()
                    ViewerAction.SUSPEND_CARD -> suspendCard()
                    ViewerAction.UNSET_FLAG -> setFlag(Flag.NONE)
                    ViewerAction.FLAG_RED -> setFlag(Flag.RED)
                    ViewerAction.FLAG_ORANGE -> setFlag(Flag.ORANGE)
                    ViewerAction.FLAG_BLUE -> setFlag(Flag.BLUE)
                    ViewerAction.FLAG_GREEN -> setFlag(Flag.GREEN)
                    ViewerAction.FLAG_PINK -> setFlag(Flag.PINK)
                    ViewerAction.FLAG_TURQUOISE -> setFlag(Flag.TURQUOISE)
                    ViewerAction.FLAG_PURPLE -> setFlag(Flag.PURPLE)
                    ViewerAction.TOGGLE_FLAG_RED -> toggleFlag(Flag.RED)
                    ViewerAction.TOGGLE_FLAG_ORANGE -> toggleFlag(Flag.ORANGE)
                    ViewerAction.TOGGLE_FLAG_BLUE -> toggleFlag(Flag.BLUE)
                    ViewerAction.TOGGLE_FLAG_GREEN -> toggleFlag(Flag.GREEN)
                    ViewerAction.TOGGLE_FLAG_PINK -> toggleFlag(Flag.PINK)
                    ViewerAction.TOGGLE_FLAG_TURQUOISE -> toggleFlag(Flag.TURQUOISE)
                    ViewerAction.TOGGLE_FLAG_PURPLE -> toggleFlag(Flag.PURPLE)
                    ViewerAction.SHOW_ANSWER -> if (!showingAnswer.value) showAnswerInternal()
                    ViewerAction.ANSWER_AGAIN -> answerCardInternal(Rating.AGAIN)
                    ViewerAction.ANSWER_HARD -> answerCardInternal(Rating.HARD)
                    ViewerAction.ANSWER_GOOD -> answerCardInternal(Rating.GOOD)
                    ViewerAction.ANSWER_EASY -> answerCardInternal(Rating.EASY)
                    ViewerAction.SHOW_HINT -> eval.emit("ankidroid.showHint()")
                    ViewerAction.SHOW_ALL_HINTS -> eval.emit("ankidroid.showAllHints()")
                    ViewerAction.TOGGLE_WHITEBOARD -> toggleWhiteboard()
                    ViewerAction.RECORD_VOICE -> toggleRecordVoice()
                    ViewerAction.REPLAY_VOICE -> replayVoiceFlow.emit(Unit)
                    ViewerAction.PAGE_UP -> pageUpFlow.emit(Unit)
                    ViewerAction.PAGE_DOWN -> pageDownFlow.emit(Unit)
                    ViewerAction.EXIT -> finishResultFlow.emit(AbstractFlashcardViewer.RESULT_DEFAULT)
                    ViewerAction.USER_ACTION_1 -> userAction(1)
                    ViewerAction.USER_ACTION_2 -> userAction(2)
                    ViewerAction.USER_ACTION_3 -> userAction(3)
                    ViewerAction.USER_ACTION_4 -> userAction(4)
                    ViewerAction.USER_ACTION_5 -> userAction(5)
                    ViewerAction.USER_ACTION_6 -> userAction(6)
                    ViewerAction.USER_ACTION_7 -> userAction(7)
                    ViewerAction.USER_ACTION_8 -> userAction(8)
                    ViewerAction.USER_ACTION_9 -> userAction(9)
                    ViewerAction.SUSPEND_MENU -> suspendCard()
                    ViewerAction.BURY_MENU -> buryCard()
                    ViewerAction.STATISTICS -> destinationFlow.emit(StatisticsDestination())
                    ViewerAction.BROWSE -> emitBrowseDestination()
                    ViewerAction.PLAY_MEDIA -> replayMedia()
                    ViewerAction.FLAG_MENU -> {}
                }
            }
        }
    }

    override fun processAction(
        action: ViewerAction,
        binding: ReviewerBinding,
    ): Boolean {
        Timber.v("ReviewerViewModel::processAction")
        if ((binding.side != CardSide.BOTH && CardSide.fromAnswer(showingAnswer.value) != binding.side) ||
            (binding.isKey && isInputFocused)
        ) {
            return false
        }
        executeAction(action)
        return true
    }

    override suspend fun onAutoAdvanceAction(action: AutoAdvanceAction) {
        when (action) {
            QuestionAction.SHOW_ANSWER -> executeAction(ViewerAction.SHOW_ANSWER)
            QuestionAction.SHOW_REMINDER -> actionFeedbackFlow.emit(TR.studyingQuestionTimeElapsed())
            AnswerAction.BURY_CARD -> executeAction(ViewerAction.BURY_CARD)
            AnswerAction.ANSWER_AGAIN -> executeAction(ViewerAction.ANSWER_AGAIN)
            AnswerAction.ANSWER_GOOD -> executeAction(ViewerAction.ANSWER_GOOD)
            AnswerAction.ANSWER_HARD -> executeAction(ViewerAction.ANSWER_HARD)
            AnswerAction.SHOW_REMINDER -> actionFeedbackFlow.emit(TR.studyingAnswerTimeElapsed())
        }
    }

    // Based in https://github.com/ankitects/anki/blob/1f95d030bbc7ebcc004ffe1e2be2a320c9fe1e94/qt/aqt/reviewer.py#L201
    // and https://github.com/ankitects/anki/blob/1f95d030bbc7ebcc004ffe1e2be2a320c9fe1e94/qt/aqt/reviewer.py#L219
    override fun opExecuted(
        changes: OpChanges,
        handler: Any?,
    ) {
        launchCatchingIO {
            updateUndoAndRedoLabels()

            if (handler == this) return@launchCatchingIO

            when {
                changes.studyQueues -> updateCurrentCard()
                changes.noteText -> {
                    val card = currentCard.await()
                    withCol { card.load(this) }
                    cardMediaPlayer.loadCardAvTags(card)
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
        private const val KEY_PREVIOUS_CARD_ID = "key_previous_card_id"
        private const val KEY_COUNTS = "counts"
    }
}
