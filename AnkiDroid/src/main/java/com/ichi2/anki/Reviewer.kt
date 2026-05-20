/*
 * Copyright (c) 2011 Kostas Spyropoulos <inigo.aldana@gmail.com>
 * Copyright (c) 2014 Bruno Romero de Azevedo <brunodea@inf.ufsm.br>
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

package com.ichi2.anki

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Parcelable
import android.text.SpannableString
import android.text.style.UnderlineSpan
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.SubMenu
import android.view.View
import android.webkit.WebView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.CheckResult
import androidx.annotation.DrawableRes
import androidx.annotation.IntDef
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.ThemeUtils
import androidx.appcompat.widget.Toolbar
import androidx.appcompat.widget.TooltipCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.lifecycle.lifecycleScope
import anki.frontend.SetSchedulingStatesRequest
import anki.scheduler.CardAnswer.Rating
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anim.ActivityTransitionAnimation.getInverseTransition
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.Whiteboard.Companion.createInstance
import com.ichi2.anki.Whiteboard.OnPaintColorChangeListener
import com.ichi2.anki.cardviewer.Gesture
import com.ichi2.anki.cardviewer.ViewerCommand
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.libanki.CardId
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.QueueType
import com.ichi2.anki.libanki.redoAvailable
import com.ichi2.anki.libanki.redoLabel
import com.ichi2.anki.libanki.sched.Counts
import com.ichi2.anki.libanki.sched.CurrentQueueState
import com.ichi2.anki.libanki.undoAvailable
import com.ichi2.anki.libanki.undoLabel
import com.ichi2.anki.multimedia.audio.AudioRecordingController
import com.ichi2.anki.multimedia.audio.AudioRecordingController.Companion.generateTempAudioFile
import com.ichi2.anki.multimedia.audio.AudioRecordingController.Companion.isAudioRecordingSaved
import com.ichi2.anki.multimedia.audio.AudioRecordingController.Companion.isRecording
import com.ichi2.anki.multimedia.audio.AudioRecordingController.Companion.setEditorStatus
import com.ichi2.anki.multimedia.audio.AudioRecordingController.Companion.tempAudioPath
import com.ichi2.anki.multimedia.audio.AudioRecordingController.RecordingState
import com.ichi2.anki.noteeditor.NoteEditorLauncher
import com.ichi2.anki.observability.undoableOp
import com.ichi2.anki.pages.CardInfoDestination
import com.ichi2.anki.pages.PostRequestUri
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.reviewer.ActionButtons
import com.ichi2.anki.reviewer.AnswerButtons.Companion.getBackgroundColors
import com.ichi2.anki.reviewer.AnswerButtons.Companion.getTextColors
import com.ichi2.anki.reviewer.AnswerTimer
import com.ichi2.anki.reviewer.AutomaticAnswerAction
import com.ichi2.anki.reviewer.Binding
import com.ichi2.anki.reviewer.BindingMap
import com.ichi2.anki.reviewer.BindingProcessor
import com.ichi2.anki.reviewer.CardMarker
import com.ichi2.anki.reviewer.CardSide
import com.ichi2.anki.reviewer.FullScreenMode
import com.ichi2.anki.reviewer.FullScreenMode.Companion.fromPreference
import com.ichi2.anki.reviewer.FullScreenMode.Companion.isFullScreenReview
import com.ichi2.anki.reviewer.ReviewerBinding
import com.ichi2.anki.reviewer.ReviewerUi
import com.ichi2.anki.scheduling.ForgetCardsDialog
import com.ichi2.anki.scheduling.SetDueDateDialog
import com.ichi2.anki.scheduling.registerOnForgetHandler
import com.ichi2.anki.servicelayer.NoteService.isMarked
import com.ichi2.anki.servicelayer.NoteService.toggleMark
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.settings.enums.DayTheme
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.anki.ui.windows.reviewer.ReviewerFragment
import com.ichi2.anki.utils.ext.cardStatsNoCardClean
import com.ichi2.anki.utils.ext.currentCardStudy
import com.ichi2.anki.utils.ext.flag
import com.ichi2.anki.utils.ext.getLongOrNull
import com.ichi2.anki.utils.ext.previousCardStudy
import com.ichi2.anki.utils.ext.setUserFlagForCards
import com.ichi2.anki.utils.ext.showDialogFragment
import com.ichi2.anki.utils.navBarNeedsScrim
import com.ichi2.anki.utils.remainingTime
import com.ichi2.themes.Themes
import com.ichi2.themes.Themes.currentTheme
import com.ichi2.utils.HandlerUtils.executeFunctionWithDelay
import com.ichi2.utils.HandlerUtils.getDefaultLooper
import com.ichi2.utils.Permissions.canRecordAudio
import com.ichi2.utils.ViewGroupUtils.setRenderWorkaround
import com.ichi2.utils.cancelable
import com.ichi2.utils.iconAlpha
import com.ichi2.utils.increaseHorizontalPaddingOfOverflowMenuIcons
import com.ichi2.utils.message
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import com.ichi2.utils.tintOverflowMenuIcons
import com.ichi2.utils.title
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import kotlin.coroutines.resume

@Suppress("LeakingThis")
@NeedsTest("#14709: Timebox shouldn't appear instantly when the Reviewer is opened")
open class Reviewer :
    AbstractFlashcardViewer(),
    ReviewerUi,
    BindingProcessor<ReviewerBinding, ViewerCommand> {
    private var queueState: CurrentQueueState? = null
    private val customSchedulingKey = TimeManager.time.intTimeMS().toString()
    private var hasDrawerSwipeConflicts = false
    private var showWhiteboard = true
    private var prefFullscreenReview = false
    private lateinit var colorPalette: LinearLayout
    private var toggleStylus = false
    private var isEraserMode = false
    private var previousCardId: CardId? = null

    // A flag that determines if the SchedulingStates in CurrentQueueState are
    // safe to persist in the database when answering a card. This is used to
    // ensure that the custom JS scheduler has persisted its SchedulingStates
    // back to the Reviewer before we save it to the database. If the custom
    // scheduler has not been configured, then it is safe to immediately set
    // this to true
    //
    // This flag should be set to false when we show the front of the card
    // and only set to true once we know the custom scheduler has finished its
    // execution, or set to true immediately if the custom scheduler has not
    // been configured
    private var statesMutated = false

    // TODO: Consider extracting to ViewModel
    // Card counts
    private var newCount: SpannableString? = null
    private var lrnCount: SpannableString? = null
    private var revCount: SpannableString? = null
    private lateinit var textBarNew: TextView
    private lateinit var textBarLearn: TextView
    private lateinit var textBarReview: TextView
    private lateinit var answerTimer: AnswerTimer
    private var prefHideDueCount = false

    // Whiteboard
    var prefWhiteboard = false

    @get:CheckResult
    @get:VisibleForTesting(otherwise = VisibleForTesting.NONE)
    var whiteboard: Whiteboard? = null
        protected set

    // Record Audio
    private var isMicToolBarVisible = false

    /** Controller for 'Voice Playback' feature */
    private var audioRecordingController: AudioRecordingController? = null
    private var isAudioUIInitialized = false
    private lateinit var micToolBarLayer: LinearLayout

    // ETA
    private var eta = 0
    private var prefShowETA = false

    /** Handle Mark/Flag state of cards  */
    @VisibleForTesting
    internal var cardMarker: CardMarker? = null

    // Preferences from the collection
    private var showRemainingCardCount = false
    private var stopTimerOnAnswer = false
    private val actionButtons = ActionButtons()
    private lateinit var toolbar: Toolbar

    @VisibleForTesting
    protected open lateinit var processor: BindingMap<ReviewerBinding, ViewerCommand>

    private val addNoteLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
            FlashCardViewerResultCallback(),
        )

    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        super.onCreate(savedInstanceState)
        if (!ensureStoragePermissions()) {
            return
        }
        colorPalette = findViewById(R.id.whiteboard_editor)
        answerTimer = AnswerTimer(findViewById(R.id.card_time))
        textBarNew = findViewById(R.id.new_number)
        textBarLearn = findViewById(R.id.learn_number)
        textBarReview = findViewById(R.id.review_number)
        toolbar = findViewById(R.id.toolbar)
        micToolBarLayer = findViewById(R.id.mic_tool_bar_layer)
        processor = BindingMap(sharedPrefs(), ViewerCommand.entries, this)
        if (sharedPrefs().getString("answerButtonPosition", "bottom") == "bottom" && !navBarNeedsScrim) {
            setNavigationBarColor(R.attr.showAnswerColor)
        }
        if (!sharedPrefs().getBoolean("showDeckTitle", false)) {
            // avoid showing "AnkiDroid"
            supportActionBar?.title = ""
        }
        startLoadingCollection()
        registerOnForgetHandler { listOf(currentCardId!!) }
        previousCardId = savedInstanceState?.getLongOrNull(KEY_PREVIOUS_CARD_ID)
    }

    override fun onPause() {
        answerTimer.pause()
        super.onPause()
    }

    override fun onResume() {
        when {
            stopTimerOnAnswer && isDisplayingAnswer -> {}
            else -> launchCatchingTask { answerTimer.resume() }
        }
        super.onResume()
        if (typeAnswer?.autoFocusEditText() == true) {
            answerField?.focusWithKeyboard()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        previousCardId?.let { outState.putLong(KEY_PREVIOUS_CARD_ID, it) }
    }

    protected val flagToDisplay: Flag
        get() {
            return FlagToDisplay(
                currentCard!!.flag,
                actionButtons.findMenuItem(ActionButtons.RES_FLAG)?.isActionButton ?: true,
                prefFullscreenReview,
            ).get()
        }

    override fun recreateWebView() {
        super.recreateWebView()
        setRenderWorkaround(this)
    }

    @NeedsTest("is hidden if marked is on app bar")
    @NeedsTest("is not hidden if marked is not on app bar")
    @NeedsTest("is not hidden if marked is on app bar and fullscreen is enabled")
    override fun shouldDisplayMark(): Boolean {
        val markValue = super.shouldDisplayMark()
        if (!markValue) {
            return false
        }

        // If we don't know: assume it's not shown
        val shownAsToolbarButton = actionButtons.findMenuItem(ActionButtons.RES_MARK)?.isActionButton == true
        return !shownAsToolbarButton || prefFullscreenReview
    }

    protected open fun onMark(card: Card?) {
        if (card == null) {
            return
        }
        launchCatchingTask {
            toggleMark(card.note(getColUnsafe), handler = this@Reviewer)
            refreshActionBar()
            onMarkChanged()
        }
    }

    private fun onMarkChanged() {
        if (currentCard == null) {
            return
        }
        cardMarker!!.displayMark(shouldDisplayMark())
    }

    protected open fun onFlag(
        card: Card?,
        flag: Flag,
    ) {
        if (card == null) {
            return
        }
        launchCatchingTask {
            card.setUserFlag(flag.code)
            undoableOp(this@Reviewer) {
                setUserFlagForCards(listOf(card.id), flag)
            }
            refreshActionBar()
            onFlagChanged()
        }
    }

    private fun onFlagChanged() {
        if (currentCard == null) {
            return
        }
        cardMarker!!.displayFlag(flagToDisplay)
    }

    private fun selectDeckFromExtra() {
        val extras = intent.extras
        if (extras == null || !extras.containsKey(EXTRA_DECK_ID)) {
            // deckId is not set, load default
            return
        }
        val did = extras.getLong(EXTRA_DECK_ID, Long.MIN_VALUE)
        Timber.d("selectDeckFromExtra() with deckId = %d", did)

        // deckId does not exist, load default
        if (getColUnsafe.decks.getLegacy(did) == null) {
            Timber.w("selectDeckFromExtra() deckId '%d' doesn't exist", did)
            return
        }
        // Select the deck
        getColUnsafe.decks.select(did)
    }

    override fun getContentViewAttr(fullscreenMode: FullScreenMode): Int =
        when (fullscreenMode) {
            FullScreenMode.BUTTONS_ONLY -> R.layout.activity_reviewer_fullscreen
            FullScreenMode.FULLSCREEN_ALL_GONE -> R.layout.activity_reviewer_fullscreen_noanswers
            FullScreenMode.BUTTONS_AND_MENU -> R.layout.activity_reviewer
        }

    public override fun fitsSystemWindows(): Boolean = !fullscreenMode.isFullScreenReview()

    override fun onCollectionLoaded(col: Collection) {
        super.onCollectionLoaded(col)
        if (Intent.ACTION_VIEW == intent.action) {
            Timber.d("onCreate() :: received Intent with action = %s", intent.action)
            selectDeckFromExtra()
        }
        // Load the first card and start reviewing. Uses the answer card
        // task to load a card, but since we send null
        // as the card to answer, no card will be answered.
        prefWhiteboard = MetaDB.getWhiteboardState(this, parentDid)
        if (prefWhiteboard) {
            // DEFECT: Slight inefficiency here, as we set the database using these methods
            val whiteboardVisibility = MetaDB.getWhiteboardVisibility(this, parentDid)
            setWhiteboardEnabledState(true)
            setWhiteboardVisibility(whiteboardVisibility)
            toggleStylus = MetaDB.getWhiteboardStylusState(this, parentDid)
            whiteboard!!.toggleStylus = toggleStylus
        }

        val isMicToolbarEnabled = MetaDB.getMicToolbarState(this, parentDid)
        if (isMicToolbarEnabled) {
            openMicToolbar()
        }

        launchCatchingTask {
            withCol { startTimebox() }
            updateCardAndRedraw()
        }
        disableDrawerSwipeOnConflicts()

        // Set full screen/immersive mode if needed
        if (prefFullscreenReview) {
            setFullScreen(this)
        }
        setRenderWorkaround(this)
    }

    fun redo() {
        launchCatchingTask { redoAndShowSnackbar(ACTION_SNACKBAR_TIME) }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // 100ms was not enough on my device (Honor 9 Lite -  Android Pie)
        delayedHide(1000)
        if (drawerToggle.onOptionsItemSelected(item)) {
            return true
        }

        Flag.entries.find { it.id == item.itemId }?.let { flag ->
            Timber.i("Reviewer:: onOptionItemSelected Flag - ${flag.name} clicked")
            onFlag(currentCard, flag)
            return true
        }

        when (item.itemId) {
            android.R.id.home -> {
                Timber.i("Reviewer:: Home button pressed")
                closeReviewer(RESULT_OK)
            }
            R.id.action_undo -> {
                Timber.i("Reviewer:: Undo button pressed")
                if (showWhiteboard && whiteboard != null && !whiteboard!!.undoEmpty()) {
                    whiteboard!!.undo()
                } else {
                    undo()
                }
            }
            R.id.action_redo -> {
                Timber.i("Reviewer:: Redo button pressed")
                redo()
            }
            R.id.action_reset_card_progress -> {
                Timber.i("Reviewer:: Reset progress button pressed")
                showResetCardDialog()
            }
            R.id.action_mark_card -> {
                Timber.i("Reviewer:: Mark button pressed")
                onMark(currentCard)
            }
            R.id.action_replay -> {
                Timber.i("Reviewer:: Replay media button pressed (from menu)")
                playMedia(doMediaReplay = true)
            }
            R.id.action_toggle_mic_tool_bar -> {
                Timber.i("Reviewer:: Voice playback visibility set to %b", !isMicToolBarVisible)
                // Check permission to record and request if not granted
                openOrToggleMicToolbar()
            }
            R.id.action_tag -> {
                Timber.i("Reviewer:: Tag button pressed")
                showTagsDialog()
            }
            R.id.action_edit -> {
                Timber.i("Reviewer:: Edit note button pressed")
                editCard()
            }
            R.id.action_bury_card -> buryCard()
            R.id.action_bury_note -> buryNote()
            R.id.action_suspend_card -> suspendCard()
            R.id.action_suspend_note -> suspendNote()
            R.id.action_reschedule_card -> showDueDateDialog()
            R.id.action_reset_card_progress -> showResetCardDialog()
            R.id.action_delete -> {
                Timber.i("Reviewer:: Delete note button pressed")
                showDeleteNoteDialog()
            }
            R.id.action_toggle_auto_advance -> {
                Timber.i("Reviewer:: Toggle Auto Advance button pressed")
                toggleAutoAdvance()
            }
            R.id.action_change_whiteboard_pen_color -> {
                Timber.i("Reviewer:: Pen Color button pressed")
                changeWhiteboardPenColor()
            }
            R.id.action_save_whiteboard -> {
                Timber.i("Reviewer:: Save whiteboard button pressed")
                if (whiteboard != null) {
                    try {
                        val savedWhiteboardFileName = whiteboard!!.saveWhiteboard(TimeManager.time).path
                        showSnackbar(getString(R.string.white_board_image_saved, savedWhiteboardFileName), Snackbar.LENGTH_SHORT)
                    } catch (e: Exception) {
                        Timber.w(e)
                        showSnackbar(getString(R.string.white_board_image_save_failed, e.localizedMessage), Snackbar.LENGTH_SHORT)
                    }
                }
            }
            R.id.action_clear_whiteboard -> {
                Timber.i("Reviewer:: Clear whiteboard button pressed")
                clearWhiteboard()
            }
            R.id.action_hide_whiteboard -> { // toggle whiteboard visibility
                Timber.i("Reviewer:: Whiteboard visibility set to %b", !showWhiteboard)
                setWhiteboardVisibility(!showWhiteboard)
                refreshActionBar()
            }
            R.id.action_toggle_eraser -> { // toggle eraser mode
                Timber.i("Reviewer:: Eraser button pressed")
                toggleEraser()
            }
            R.id.action_toggle_stylus -> { // toggle stylus mode
                Timber.i("Reviewer:: Stylus set to %b", !toggleStylus)
                toggleStylus = !toggleStylus
                whiteboard!!.toggleStylus = toggleStylus
                MetaDB.storeWhiteboardStylusState(this, parentDid, toggleStylus)
                refreshActionBar()
            }
            R.id.action_toggle_whiteboard -> {
                toggleWhiteboard()
            }
            R.id.action_open_deck_options -> {
                Timber.i("Reviewer:: Opening deck options")
                val i =
                    com.ichi2.anki.pages.DeckOptions
                        .getIntent(this, getColUnsafe.decks.current().id)
                deckOptionsLauncher.launch(i)
            }
            R.id.action_select_tts -> {
                Timber.i("Reviewer:: Select TTS button pressed")
                showSelectTtsDialogue()
            }
            R.id.action_add_note_reviewer -> {
                Timber.i("Reviewer:: Add note button pressed")
                addNote()
            }
            R.id.action_card_info -> {
                Timber.i("Card Viewer:: Card Info")
                openCardInfo()
            }
            R.id.action_previous_card_info -> {
                Timber.i("Card Viewer:: Previous Card Info")
                openPreviousCardInfo()
            }
            R.id.user_action_1 -> userAction(1)
            R.id.user_action_2 -> userAction(2)
            R.id.user_action_3 -> userAction(3)
            R.id.user_action_4 -> userAction(4)
            R.id.user_action_5 -> userAction(5)
            R.id.user_action_6 -> userAction(6)
            R.id.user_action_7 -> userAction(7)
            R.id.user_action_8 -> userAction(8)
            R.id.user_action_9 -> userAction(9)
            else -> {
                return super.onOptionsItemSelected(item)
            }
        }
        return true
    }

    public override fun toggleWhiteboard() {
        prefWhiteboard = !prefWhiteboard
        Timber.i("Reviewer:: Whiteboard enabled state set to %b", prefWhiteboard)
        // Even though the visibility is now stored in its own setting, we want it to be dependent
        // on the enabled status
        setWhiteboardEnabledState(prefWhiteboard)
        setWhiteboardVisibility(prefWhiteboard)
        if (!prefWhiteboard) {
            colorPalette.visibility = View.GONE
        }
        refreshActionBar()
    }

    public override fun toggleEraser() {
        val whiteboardIsShownAndHasStrokes = showWhiteboard && whiteboard?.undoEmpty() == false
        if (whiteboardIsShownAndHasStrokes) {
            Timber.i("Reviewer:: Whiteboard eraser mode set to %b", !isEraserMode)
            isEraserMode = !isEraserMode
            whiteboard?.reviewerEraserModeIsToggledOn = isEraserMode

            refreshActionBar() // Switch the eraser item's title

            // Keep ripple effect on the eraser button after the eraser mode is activated.
            toolbar.post {
                val eraserButtonView = toolbar.findViewById<View>(R.id.action_toggle_eraser)
                eraserButtonView?.apply {
                    isPressed = isEraserMode
                    isActivated = isEraserMode
                }
            }

            if (isEraserMode) {
                startMonitoringEraserButtonRipple()
                showSnackbar(getString(R.string.white_board_eraser_enabled), 1000)
            } else {
                stopMonitoringEraserButtonRipple()
                showSnackbar(getString(R.string.white_board_eraser_disabled), 1000)
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())

    /**
     * The eraser button ripple should be shown while the eraser mode is activated,
     * but the ripple gets removed after some timings
     * (e.g., when the three dot menu opens,
     *        when the side drawer opens & closes,
     *        when the button is long-pressed)
     * In such timings, this function re-press the button to re-display the ripple.
     */
    private val checkEraserButtonRippleRunnable =
        object : Runnable {
            override fun run() {
                val eraserButtonView = toolbar.findViewById<View>(R.id.action_toggle_eraser)
                if (isEraserMode && eraserButtonView?.isPressed == false) {
                    Timber.d("eraser button ripple monitoring: unpressed status detected, and re-pressed")
                    eraserButtonView.isPressed = true
                    eraserButtonView.isActivated = true
                }
                handler.postDelayed(this, 100) // monitor every 100ms
            }
        }

    private fun startMonitoringEraserButtonRipple() {
        Timber.d("eraser button ripple monitoring: started")
        handler.post(checkEraserButtonRippleRunnable)
    }

    private fun stopMonitoringEraserButtonRipple() {
        Timber.d("eraser button ripple monitoring: stopped")
        handler.removeCallbacks(checkEraserButtonRippleRunnable)
    }

    public override fun clearWhiteboard() {
        if (whiteboard != null) {
            whiteboard!!.clear()
        }
    }

    public override fun changeWhiteboardPenColor() {
        if (colorPalette.isGone) {
            colorPalette.visibility = View.VISIBLE
        } else {
            colorPalette.visibility = View.GONE
        }
        updateWhiteboardEditorPosition()
    }

    override fun replayVoice() {
        if (!openMicToolbar()) {
            return
        }
        if (isAudioRecordingSaved) {
            audioRecordingController?.playPausePlayer()
        } else {
            return
        }
    }

    override fun recordVoice() {
        if (!openMicToolbar()) {
            return
        }
        audioRecordingController?.toggleToRecorder()
    }

    override fun saveRecording() {
        if (!openMicToolbar()) {
            return
        }
        if (isRecording) {
            audioRecordingController?.toggleSave()
        } else {
            return
        }
    }

    override fun updateForNewCard() {
        super.updateForNewCard()
        if (prefWhiteboard && whiteboard != null) {
            whiteboard!!.clear()
        }
        audioRecordingController?.updateUIForNewCard()
    }

    override fun unblockControls() {
        if (prefWhiteboard && whiteboard != null) {
            whiteboard!!.isEnabled = true
        }
        super.unblockControls()
    }

    override fun closeReviewer(result: Int) {
        // Stop the mic recording if still pending
        if (isRecording) audioRecordingController?.stopAndSaveRecording()

        // Remove the temporary audio file
        tempAudioPath?.let { tempAudioPathToDelete ->
            if (tempAudioPathToDelete.exists()) {
                tempAudioPathToDelete.delete()
            }
        }
        super.closeReviewer(result)
    }

    /**
     *
     * @return Whether the mic toolbar is usable
     */
    @VisibleForTesting
    fun openMicToolbar(): Boolean {
        if (micToolBarLayer.visibility != View.VISIBLE || audioRecordingController == null) {
            openOrToggleMicToolbar()
        }
        return audioRecordingController != null
    }

    private fun openOrToggleMicToolbar() {
        if (!canRecordAudio(this)) {
            Timber.i("requesting 'RECORD_AUDIO' permission")
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_AUDIO_PERMISSION,
            )
        } else {
            toggleMicToolBar()
        }
    }

    private fun toggleMicToolBar() {
        Timber.i("toggle mic toolbar")
        tempAudioPath = generateTempAudioFile(this)
        if (isMicToolBarVisible) {
            micToolBarLayer.visibility = View.GONE
        } else {
            setEditorStatus(false)
            if (!isAudioUIInitialized) {
                try {
                    audioRecordingController = AudioRecordingController(context = this)
                    audioRecordingController?.createUI(
                        this,
                        micToolBarLayer,
                        initialState = RecordingState.ImmediatePlayback.CLEARED,
                        R.layout.activity_audio_recording_reviewer,
                    )
                } catch (e: Exception) {
                    Timber.w(e, "unable to add the audio recorder to toolbar")
                    CrashReportService.sendExceptionReport(e, "Unable to create recorder tool bar")
                    showThemedToast(
                        this,
                        this.getText(R.string.multimedia_editor_audio_view_create_failed).toString(),
                        true,
                    )
                }
                isAudioUIInitialized = true
            }
            micToolBarLayer.visibility = View.VISIBLE
        }
        isMicToolBarVisible = !isMicToolBarVisible

        MetaDB.storeMicToolbarState(this, parentDid, isMicToolBarVisible)

        refreshActionBar()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_AUDIO_PERMISSION &&
            permissions.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            // Get get audio record permission, so we can create the record tool bar
            toggleMicToolBar()
        }
    }

    private fun showDueDateDialog() =
        launchCatchingTask {
            Timber.i("showing due date dialog")
            val dialog = SetDueDateDialog.newInstance(listOf(currentCardId!!))
            showDialogFragment(dialog)
        }

    private fun showResetCardDialog() {
        Timber.i("showResetCardDialog() Reset progress button pressed")
        showDialogFragment(ForgetCardsDialog())
    }

    fun addNote(fromGesture: Gesture? = null) {
        val animation = getAnimationTransitionFromGesture(fromGesture)
        val inverseAnimation = getInverseTransition(animation)
        Timber.i("launching 'add note'")
        val intent = NoteEditorLauncher.AddNoteFromReviewer(inverseAnimation).toIntent(this)
        addNoteLauncher.launch(intent)
    }

    @NeedsTest("Starting animation from swipe is inverse to the finishing one")
    protected fun openCardInfo(fromGesture: Gesture? = null) {
        if (currentCard == null) {
            showSnackbar(getString(R.string.multimedia_editor_something_wrong), Snackbar.LENGTH_SHORT)
            return
        }
        Timber.i("opening card info")
        val intent = CardInfoDestination(currentCard!!.id, TR.currentCardStudy()).toIntent(this)
        val animation = getAnimationTransitionFromGesture(fromGesture)
        intent.putExtra(FINISH_ANIMATION_EXTRA, getInverseTransition(animation) as Parcelable)
        startActivityWithAnimation(intent, animation)
    }

    @NeedsTest("Starting animation from swipe is inverse to the finishing one")
    protected fun openPreviousCardInfo(fromGesture: Gesture? = null) {
        if (previousCardId == null) {
            showSnackbar(TR.cardStatsNoCardClean(), Snackbar.LENGTH_SHORT)
            return
        }
        Timber.i("opening previous card info")
        val intent = CardInfoDestination(previousCardId!!, TR.previousCardStudy()).toIntent(this)
        val animation = getAnimationTransitionFromGesture(fromGesture)
        intent.putExtra(FINISH_ANIMATION_EXTRA, getInverseTransition(animation) as Parcelable)
        startActivityWithAnimation(intent, animation)
    }

    // Related to https://github.com/ankidroid/Anki-Android/pull/11061#issuecomment-1107868455
    @NeedsTest("Order of operations needs Testing around Menu (Overflow) Icons and their colors.")
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        Timber.d("onCreateOptionsMenu()")
        // NOTE: This is called every time a new question is shown via invalidate options menu
        menuInflater.inflate(R.menu.reviewer, menu)
        menu.findItem(R.id.action_flag).subMenu?.let { subMenu -> setupFlags(subMenu) }
        displayIcons(menu)
        actionButtons.setCustomButtonsStatus(menu)
        val alpha = Themes.ALPHA_ICON_ENABLED_LIGHT
        val markCardIcon = menu.findItem(R.id.action_mark_card)
        if (currentCard != null && isMarked(getColUnsafe, currentCard!!.note(getColUnsafe))) {
            markCardIcon.setTitle(R.string.menu_unmark_note).setIcon(R.drawable.ic_star_white)
        } else {
            markCardIcon.setTitle(TR.sentenceCase.markNote).setIcon(R.drawable.ic_star_border_white)
        }
        markCardIcon.iconAlpha = alpha

        val flagIcon = menu.findItem(R.id.action_flag)
        if (flagIcon != null) {
            if (currentCard != null) {
                val flag = currentCard!!.flag
                flagIcon.setIcon(flag.drawableRes)
                if (flag == Flag.NONE && actionButtons.status.flagsIsOverflown()) {
                    val flagColor = ThemeUtils.getThemeAttrColor(this, android.R.attr.colorControlNormal)
                    flagIcon.icon?.mutate()?.setTint(flagColor)
                }
            }
        }

        // Anki Desktop Translations
        menu.findItem(R.id.action_reschedule_card).title = TR.sentenceCase.setDueDate
        menu.findItem(R.id.action_card_info)?.title = TR.sentenceCase.cardInfo
        menu.findItem(R.id.action_previous_card_info)?.title = TR.sentenceCase.previousCardInfo
        menu.findItem(R.id.action_delete)?.title = TR.sentenceCase.deleteNote
        // top-level (visible=false in XML) items, shown when only the card-level action is available
        menu.findItem(R.id.action_bury_card)?.title = TR.sentenceCase.buryCard
        menu.findItem(R.id.action_suspend_card)?.title = TR.sentenceCase.suspendCard
        menu.findItem(R.id.action_bury)?.let { buryItem ->
            buryItem.title = TR.studyingBury()
            buryItem.subMenu?.findItem(R.id.action_bury_card)?.title = TR.sentenceCase.buryCard
            buryItem.subMenu?.findItem(R.id.action_bury_note)?.title = TR.sentenceCase.buryNote
        }
        menu.findItem(R.id.action_suspend)?.let { suspendItem ->
            suspendItem.title = TR.studyingSuspend()
            suspendItem.subMenu?.findItem(R.id.action_suspend_card)?.title = TR.sentenceCase.suspendCard
            suspendItem.subMenu?.findItem(R.id.action_suspend_note)?.title = TR.sentenceCase.suspendNote
        }

        // Undo button
        @DrawableRes val undoIconId: Int
        val undoEnabled: Boolean
        val whiteboardIsShownAndHasStrokes = showWhiteboard && whiteboard?.undoEmpty() == false
        if (whiteboardIsShownAndHasStrokes) {
            undoIconId = R.drawable.ic_arrow_u_left_top
            undoEnabled = true
        } else {
            undoIconId = R.drawable.ic_undo_white
            undoEnabled = colIsOpenUnsafe() && getColUnsafe.undoAvailable()
            this.isEraserMode = false
        }
        val alphaUndo = Themes.ALPHA_ICON_ENABLED_LIGHT
        val undoIcon = menu.findItem(R.id.action_undo)
        undoIcon.setIcon(undoIconId)
        undoIcon.setEnabled(undoEnabled).iconAlpha = alphaUndo
        undoIcon.actionView!!.isEnabled = undoEnabled
        val toggleEraserIcon = menu.findItem((R.id.action_toggle_eraser))
        if (colIsOpenUnsafe()) { // Required mostly because there are tests where `col` is null
            if (whiteboardIsShownAndHasStrokes) {
                // Make Undo action title to whiteboard Undo specific one
                undoIcon.title = resources.getString(R.string.undo_action_whiteboard_last_stroke)

                // Show whiteboard Eraser action button
                if (!actionButtons.status.toggleEraserIsDisabled()) {
                    toggleEraserIcon.isVisible = true
                }
            } else {
                // Disable whiteboard eraser action button
                isEraserMode = false
                whiteboard?.reviewerEraserModeIsToggledOn = isEraserMode

                if (getColUnsafe.undoAvailable()) {
                    // set the undo title to a named action ('Undo Answer Card' etc...)
                    undoIcon.title = getColUnsafe.undoLabel()
                } else {
                    // In this case, there is no object word for the verb, "Undo",
                    // so in some languages such as Japanese, which have pre/post-positional particle with the object,
                    // we need to use the string for just "Undo" instead of the string for "Undo %s".
                    undoIcon.title = resources.getString(R.string.undo)
                    undoIcon.iconAlpha = Themes.ALPHA_ICON_DISABLED_LIGHT
                }
            }

            // Set the undo tooltip, only if the icon is shown in the action bar
            undoIcon.actionView?.let { undoView ->
                TooltipCompat.setTooltipText(undoView, undoIcon.title)
            }

            menu.findItem(R.id.action_redo)?.apply {
                if (getColUnsafe.redoAvailable()) {
                    title = getColUnsafe.redoLabel()
                    iconAlpha = Themes.ALPHA_ICON_ENABLED_LIGHT
                    isEnabled = true
                } else {
                    setTitle(R.string.redo)
                    iconAlpha = Themes.ALPHA_ICON_DISABLED_LIGHT
                    isEnabled = false
                }
            }
        }
        menu.findItem(R.id.action_toggle_auto_advance).apply {
            if (actionButtons.status.autoAdvanceMenuIsNeverShown()) return@apply
            // always show if enabled (to allow disabling)
            // otherwise show if it will have an effect
            isVisible = automaticAnswer.isEnabled() || automaticAnswer.isUsable()
        }

        val toggleWhiteboardIcon = menu.findItem(R.id.action_toggle_whiteboard)
        val toggleStylusIcon = menu.findItem(R.id.action_toggle_stylus)
        val hideWhiteboardIcon = menu.findItem(R.id.action_hide_whiteboard)
        val changePenColorIcon = menu.findItem(R.id.action_change_whiteboard_pen_color)
        // White board button
        if (prefWhiteboard) {
            // Configure the whiteboard related items in the action bar
            toggleWhiteboardIcon.setTitle(R.string.disable_whiteboard)
            // Always allow "Disable Whiteboard", even if "Enable Whiteboard" is disabled
            toggleWhiteboardIcon.isVisible = true
            if (!actionButtons.status.toggleStylusIsDisabled()) {
                toggleStylusIcon.isVisible = true
            }
            if (!actionButtons.status.hideWhiteboardIsDisabled()) {
                hideWhiteboardIcon.isVisible = true
            }
            if (!actionButtons.status.clearWhiteboardIsDisabled()) {
                menu.findItem(R.id.action_clear_whiteboard).isVisible = true
            }
            if (!actionButtons.status.saveWhiteboardIsDisabled()) {
                menu.findItem(R.id.action_save_whiteboard).isVisible = true
            }
            if (!actionButtons.status.whiteboardPenColorIsDisabled()) {
                changePenColorIcon.isVisible = true
            }
            val whiteboardIcon = ContextCompat.getDrawable(applicationContext, R.drawable.ic_gesture_white)!!.mutate()
            val stylusIcon = ContextCompat.getDrawable(this, R.drawable.ic_gesture_stylus)!!.mutate()
            val whiteboardColorPaletteIcon = ContextCompat.getDrawable(applicationContext, R.drawable.ic_color_lens_white_24dp)!!.mutate()
            val eraserIcon = ContextCompat.getDrawable(applicationContext, R.drawable.ic_eraser)!!.mutate()
            if (showWhiteboard) {
                // "hide whiteboard" icon
                whiteboardIcon.alpha = Themes.ALPHA_ICON_ENABLED_LIGHT
                hideWhiteboardIcon.icon = whiteboardIcon
                hideWhiteboardIcon.setTitle(R.string.hide_whiteboard)
                whiteboardColorPaletteIcon.alpha = Themes.ALPHA_ICON_ENABLED_LIGHT
                // eraser icon
                toggleEraserIcon.icon = eraserIcon
                if (isEraserMode) {
                    toggleEraserIcon.setTitle(R.string.disable_eraser)
                } else { // default
                    toggleEraserIcon.setTitle(R.string.enable_eraser)
                }
                // whiteboard editor icon
                changePenColorIcon.icon = whiteboardColorPaletteIcon
                if (toggleStylus) {
                    toggleStylusIcon.setTitle(R.string.disable_stylus)
                    stylusIcon.alpha = Themes.ALPHA_ICON_ENABLED_LIGHT
                } else {
                    toggleStylusIcon.setTitle(R.string.enable_stylus)
                    stylusIcon.alpha = Themes.ALPHA_ICON_DISABLED_LIGHT
                }
                toggleStylusIcon.icon = stylusIcon
            } else {
                whiteboardIcon.alpha = Themes.ALPHA_ICON_DISABLED_LIGHT
                hideWhiteboardIcon.icon = whiteboardIcon
                hideWhiteboardIcon.setTitle(R.string.show_whiteboard)
                whiteboardColorPaletteIcon.alpha = Themes.ALPHA_ICON_DISABLED_LIGHT
                stylusIcon.alpha = Themes.ALPHA_ICON_DISABLED_LIGHT
                toggleStylusIcon.isEnabled = false
                toggleStylusIcon.icon = stylusIcon
                changePenColorIcon.isEnabled = false
                changePenColorIcon.icon = whiteboardColorPaletteIcon
                colorPalette.visibility = View.GONE
            }
        } else {
            toggleWhiteboardIcon.setTitle(R.string.enable_whiteboard)
        }
        if (colIsOpenUnsafe() && getColUnsafe.decks.isFiltered(parentDid)) {
            menu.findItem(R.id.action_open_deck_options).isVisible = false
        }
        if (tts.enabled && !actionButtons.status.selectTtsIsDisabled()) {
            menu.findItem(R.id.action_select_tts).isVisible = true
        }
        if (!suspendNoteAvailable() && !actionButtons.status.suspendIsDisabled()) {
            menu.findItem(R.id.action_suspend).isVisible = false
            menu.findItem(R.id.action_suspend_card).isVisible = true
        }
        if (!buryNoteAvailable() && !actionButtons.status.buryIsDisabled()) {
            menu.findItem(R.id.action_bury).isVisible = false
            menu.findItem(R.id.action_bury_card).isVisible = true
        }

        val voicePlaybackIcon = menu.findItem(R.id.action_toggle_mic_tool_bar)
        if (isMicToolBarVisible) {
            voicePlaybackIcon.setTitle(R.string.menu_disable_voice_playback)
            // #18477: always show 'disable', even if 'enable' was invisible
            voicePlaybackIcon.isVisible = true
        } else {
            voicePlaybackIcon.setTitle(R.string.menu_enable_voice_playback)
        }

        increaseHorizontalPaddingOfOverflowMenuIcons(menu)
        tintOverflowMenuIcons(menu, skipIf = { isFlagItem(it) })

        return super.onCreateOptionsMenu(menu)
    }

    private fun setupFlags(subMenu: SubMenu) {
        lifecycleScope.launch {
            for ((flag, displayName) in Flag.queryDisplayNames()) {
                subMenu.findItem(flag.id).title = displayName
            }
        }
    }

    @SuppressLint("RestrictedApi")
    private fun displayIcons(menu: Menu) {
        try {
            if (menu is MenuBuilder) {
                menu.setOptionalIconsVisible(true)
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to display icons in Over flow menu")
        } catch (e: Error) {
            Timber.w(e, "Failed to display icons in Over flow menu")
        }
    }

    private fun isFlagItem(menuItem: MenuItem): Boolean = flagItemIds.contains(menuItem.itemId)

    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean {
        if (answerFieldIsFocused()) {
            return super.onKeyDown(keyCode, event)
        }
        if (processor.onKeyDown(event) || super.onKeyDown(keyCode, event)) {
            return true
        }
        return false
    }

    override fun onGenericMotionEvent(event: MotionEvent?): Boolean {
        if (processor.onGenericMotionEvent(event)) {
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    override fun canAccessScheduler(): Boolean = true

    override fun performReload() {
        launchCatchingTask { updateCardAndRedraw() }
    }

    override fun displayAnswerBottomBar() {
        super.displayAnswerBottomBar()
        // Set correct label and background resource for each button
        // Note that it's necessary to set the resource dynamically as the ease2 / ease3 buttons
        // (which libanki expects ease to be 2 and 3) can either be hard, good, or easy - depending on num buttons shown
        val background = getBackgroundColors(this)
        val textColor = getTextColors(this)
        easeButton1!!.setVisibility(View.VISIBLE)
        easeButton1!!.setColor(background[0])
        easeButton4!!.setColor(background[3])
        // Ease 2 is "hard"
        easeButton2!!.setup(background[1], textColor[1], R.string.ease_button_hard)
        easeButton2!!.requestFocus()
        // Ease 3 is good
        easeButton3!!.setup(background[2], textColor[2], R.string.ease_button_good)
        easeButton4!!.setVisibility(View.VISIBLE)
        easeButton3!!.requestFocus()

        // Show next review time
        if (shouldShowNextReviewTime()) {
            val state = queueState!!
            launchCatchingTask {
                val labels = withCol { sched.describeNextStates(state.states) }
                easeButton1!!.nextTime = labels[0]
                easeButton2!!.nextTime = labels[1]
                easeButton3!!.nextTime = labels[2]
                easeButton4!!.nextTime = labels[3]
            }
        }
    }

    override fun automaticShowQuestion(action: AutomaticAnswerAction) {
        // explicitly do not call super
        if (easeButton1!!.canPerformClick) {
            action.execute(this)
        }
    }

    override fun restorePreferences(): SharedPreferences {
        val preferences = super.restorePreferences()
        prefHideDueCount = preferences.getBoolean("hideDueCount", false)
        prefShowETA = preferences.getBoolean("showETA", false)
        prefFullscreenReview = isFullScreenReview(preferences)
        actionButtons.setup(preferences)
        return preferences
    }

    override fun updateActionBar() {
        super.updateActionBar()
        updateScreenCounts()
    }

    private fun updateWhiteboardEditorPosition() {
        answerButtonsPosition =
            this
                .sharedPrefs()
                .getString("answerButtonPosition", "bottom")
        val layoutParams: RelativeLayout.LayoutParams
        when (answerButtonsPosition) {
            "none", "top" -> {
                layoutParams = colorPalette.layoutParams as RelativeLayout.LayoutParams
                layoutParams.removeRule(RelativeLayout.ABOVE)
                layoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                colorPalette.layoutParams = layoutParams
            }

            "bottom" -> {
                layoutParams = colorPalette.layoutParams as RelativeLayout.LayoutParams
                layoutParams.removeRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                layoutParams.addRule(RelativeLayout.ABOVE, R.id.bottom_area_layout)
                colorPalette.layoutParams = layoutParams
            }
        }
    }

    private fun updateScreenCounts() {
        val queue = queueState ?: return
        super.updateActionBar()
        val actionBar = supportActionBar
        val counts = queue.counts
        if (actionBar != null) {
            if (prefShowETA) {
                launchCatchingTask {
                    eta = withCol { sched.eta(counts, false) }
                    actionBar.subtitle = remainingTime(this@Reviewer, (eta * 60).toLong())
                }
            }
        }
        newCount = SpannableString(counts.new.toString())
        lrnCount = SpannableString(counts.lrn.toString())
        revCount = SpannableString(counts.rev.toString())
        if (prefHideDueCount) {
            revCount = SpannableString("???")
        }
        // if this code is run as a card is being answered, currentCard may be non-null but
        // the queues may be empty - we can't call countIdx() in such a case
        if (counts.count() != 0) {
            when (queue.countsIndex) {
                Counts.Queue.NEW -> newCount!!.setSpan(UnderlineSpan(), 0, newCount!!.length, 0)
                Counts.Queue.LRN -> lrnCount!!.setSpan(UnderlineSpan(), 0, lrnCount!!.length, 0)
                Counts.Queue.REV -> revCount!!.setSpan(UnderlineSpan(), 0, revCount!!.length, 0)
            }
        }
        textBarNew.text = newCount
        textBarLearn.text = lrnCount
        textBarReview.text = revCount
    }

    override fun fillFlashcard() {
        super.fillFlashcard()
        if (!isDisplayingAnswer && showWhiteboard && whiteboard != null) {
            whiteboard!!.clear()
        }
    }

    override fun onPageFinished(view: WebView) {
        super.onPageFinished(view)
        onFlagChanged()
        onMarkChanged()
        if (!displayAnswer) {
            runStateMutationHook()
        }
    }

    override suspend fun updateCurrentCard() {
        val state =
            withCol {
                sched.currentQueueState()?.apply {
                    topCard.renderOutput(this@withCol, reload = true)
                }
            }

        currentCard = state?.topCard
        queueState = state
    }

    /**
     * Answer the current card, update the scheduler and checks if the timebox limit has been reached
     * and, if so, displays a dialog to the user
     * @param rating The user's rating for the card
     */
    override suspend fun answerCardInner(rating: Rating) {
        val state = queueState!!
        val cardId = currentCard!!.id
        Timber.d("answerCardInner: $cardId $rating")
        var wasLeech = false
        undoableOp(this) {
            sched.answerCard(state, rating).also {
                wasLeech = sched.stateIsLeech(state.states.again)
            }
        }.also {
            previousCardId = cardId
            if (rating == Rating.AGAIN && wasLeech) {
                state.topCard.load(getColUnsafe)
                val leechMessage: String =
                    if (state.topCard.queue.buriedOrSuspended()) {
                        resources.getString(R.string.leech_suspend_notification)
                    } else {
                        resources.getString(R.string.leech_notification)
                    }
                showSnackbar(leechMessage, Snackbar.LENGTH_SHORT)
            }
        }

        // showing the timebox reached dialog if the timebox is reached
        val timebox = withCol { timeboxReached() }
        if (timebox != null) {
            dealWithTimeBox(timebox)
        }
    }

    private suspend fun dealWithTimeBox(timebox: Collection.TimeboxReached) {
        val nCards = timebox.reps
        val nMins = timebox.secs / 60
        val mins = resources.getQuantityString(R.plurals.in_minutes, nMins, nMins)
        val timeboxMessage = resources.getQuantityString(R.plurals.timebox_reached, nCards, nCards, mins)
        suspendCancellableCoroutine { coroutines ->
            Timber.i("Showing timebox reached dialog")
            AlertDialog.Builder(this).show {
                title(R.string.timebox_reached_title)
                message(text = timeboxMessage)
                positiveButton(R.string.dialog_continue) {
                    coroutines.resume(Unit)
                }
                negativeButton(text = CollectionManager.TR.studyingFinish()) {
                    coroutines.resume(Unit)
                    finish()
                }
                cancelable(true)
                setOnCancelListener {
                    coroutines.resume(Unit)
                }
            }
        }
    }

    override fun displayCardQuestion() {
        statesMutated = false
        // show timer, if activated in the deck's preferences
        answerTimer.setupForCard(getColUnsafe, currentCard!!)
        delayedHide(100)
        super.displayCardQuestion()
    }

    @VisibleForTesting
    override fun displayCardAnswer() {
        if (queueState?.customSchedulingJs?.isEmpty() == true) {
            statesMutated = true
        }
        if (!statesMutated) {
            executeFunctionWithDelay(50) { displayCardAnswer() }
            return
        }

        delayedHide(100)
        if (stopTimerOnAnswer) {
            answerTimer.pause()
        }
        super.displayCardAnswer()
    }

    private fun runStateMutationHook() {
        val state = queueState ?: return
        if (state.customSchedulingJs.isEmpty()) {
            statesMutated = true
            return
        }
        val key = customSchedulingKey
        val js = state.customSchedulingJs
        webView?.evaluateJavascript(
            """
        anki.mutateNextCardStates('$key', async (states, customData, ctx) => { $js })
            .catch(err => { console.log(err); window.location.href = "state-mutation-error:"; });
""",
        ) { result ->
            if ("null" == result) {
                // eval failed, usually a syntax error
                // Note, we get "null" (string) and not null
                statesMutated = true
            }
        }
    }

    override fun onStateMutationError() {
        super.onStateMutationError()
        statesMutated = true
    }

    override fun initLayout() {
        super.initLayout()
        if (!showRemainingCardCount) {
            textBarNew.visibility = View.GONE
            textBarLearn.visibility = View.GONE
            textBarReview.visibility = View.GONE
        }

        // can't move this into onCreate due to mTopBarLayout
        val mark = topBarLayout!!.findViewById<ImageView>(R.id.mark_icon)
        val flag = topBarLayout!!.findViewById<ImageView>(R.id.flag_icon)
        cardMarker = CardMarker(mark, flag)
    }

    override fun switchTopBarVisibility(visible: Int) {
        super.switchTopBarVisibility(visible)
        answerTimer.setVisibility(visible)
        if (showRemainingCardCount) {
            textBarNew.visibility = visible
            textBarLearn.visibility = visible
            textBarReview.visibility = visible
        }
    }

    override fun initControls() {
        super.initControls()
        if (prefWhiteboard) {
            setWhiteboardVisibility(showWhiteboard)
        }
        if (showRemainingCardCount) {
            textBarNew.visibility = View.VISIBLE
            textBarLearn.visibility = View.VISIBLE
            textBarReview.visibility = View.VISIBLE
        }
    }

    override fun executeCommand(
        which: ViewerCommand,
        fromGesture: Gesture?,
    ): Boolean {
        when (which) {
            ViewerCommand.TOGGLE_FLAG_RED -> {
                toggleFlag(Flag.RED)
                return true
            }
            ViewerCommand.TOGGLE_FLAG_ORANGE -> {
                toggleFlag(Flag.ORANGE)
                return true
            }
            ViewerCommand.TOGGLE_FLAG_GREEN -> {
                toggleFlag(Flag.GREEN)
                return true
            }
            ViewerCommand.TOGGLE_FLAG_BLUE -> {
                toggleFlag(Flag.BLUE)
                return true
            }
            ViewerCommand.TOGGLE_FLAG_PINK -> {
                toggleFlag(Flag.PINK)
                return true
            }
            ViewerCommand.TOGGLE_FLAG_TURQUOISE -> {
                toggleFlag(Flag.TURQUOISE)
                return true
            }
            ViewerCommand.TOGGLE_FLAG_PURPLE -> {
                toggleFlag(Flag.PURPLE)
                return true
            }
            ViewerCommand.UNSET_FLAG -> {
                onFlag(currentCard, Flag.NONE)
                return true
            }
            ViewerCommand.MARK -> {
                onMark(currentCard)
                return true
            }
            ViewerCommand.REDO -> {
                redo()
                return true
            }
            ViewerCommand.ADD_NOTE -> {
                addNote(fromGesture)
                return true
            }
            ViewerCommand.CARD_INFO -> {
                openCardInfo(fromGesture)
                return true
            }
            ViewerCommand.PREVIOUS_CARD_INFO -> {
                openPreviousCardInfo(fromGesture)
                return true
            }
            ViewerCommand.RESCHEDULE_NOTE -> {
                showDueDateDialog()
                return true
            }
            ViewerCommand.TOGGLE_AUTO_ADVANCE -> {
                toggleAutoAdvance()
                return true
            }
            ViewerCommand.USER_ACTION_1 -> {
                userAction(1)
                return true
            }
            ViewerCommand.USER_ACTION_2 -> {
                userAction(2)
                return true
            }
            ViewerCommand.USER_ACTION_3 -> {
                userAction(3)
                return true
            }
            ViewerCommand.USER_ACTION_4 -> {
                userAction(4)
                return true
            }
            ViewerCommand.USER_ACTION_5 -> {
                userAction(5)
                return true
            }
            ViewerCommand.USER_ACTION_6 -> {
                userAction(6)
                return true
            }
            ViewerCommand.USER_ACTION_7 -> {
                userAction(7)
                return true
            }
            ViewerCommand.USER_ACTION_8 -> {
                userAction(8)
                return true
            }
            ViewerCommand.USER_ACTION_9 -> {
                userAction(9)
                return true
            }
            else -> return super.executeCommand(which, fromGesture)
        }
    }

    @Retention(AnnotationRetention.SOURCE)
    @IntDef(1, 2, 3, 4, 5, 6, 7, 8, 9)
    annotation class UserAction

    private fun userAction(
        @UserAction number: Int,
    ) {
        Timber.v("userAction%d", number)
        loadUrlInViewer("javascript: userAction($number);")
    }

    private fun toggleFlag(flag: Flag) {
        if (currentCard!!.flag == flag) {
            Timber.i("Toggle flag: unsetting flag")
            onFlag(currentCard, Flag.NONE)
        } else {
            Timber.i("Toggle flag: Setting flag to %d", flag.code)
            onFlag(currentCard, flag)
        }
    }

    override fun restoreCollectionPreferences(col: Collection) {
        super.restoreCollectionPreferences(col)
        showRemainingCardCount = col.config.get("dueCounts") ?: true
        stopTimerOnAnswer = col.decks.configDictForDeckId(col.decks.current().id).stopTimerOnAnswer
    }

    override fun onSingleTap(): Boolean {
        if (prefFullscreenReview && isImmersiveSystemUiVisible(this)) {
            delayedHide(INITIAL_HIDE_DELAY)
            return true
        }
        return false
    }

    override fun onFling() {
        if (prefFullscreenReview && isImmersiveSystemUiVisible(this)) {
            delayedHide(INITIAL_HIDE_DELAY)
        }
    }

    override fun onCardEdited(card: Card) {
        super.onCardEdited(card)
        if (prefWhiteboard && whiteboard != null) {
            whiteboard!!.clear()
        }
        if (!isDisplayingAnswer) {
            // Editing the card may reuse mCurrentCard. If so, the scheduler won't call startTimer() to reset the timer
            // QUESTIONABLE(legacy code): Only perform this if editing the question
            card.startTimer()
        }
    }

    private val fullScreenHandler: Handler =
        object : Handler(getDefaultLooper()) {
            override fun handleMessage(msg: Message) {
                if (prefFullscreenReview) {
                    setFullScreen(this@Reviewer)
                }
            }
        }

    /** Hide the navigation if in full-screen mode after a given period of time  */
    protected open fun delayedHide(delayMillis: Int) {
        Timber.d("Fullscreen delayed hide in %dms", delayMillis)
        fullScreenHandler.removeMessages(0)
        fullScreenHandler.sendEmptyMessageDelayed(0, delayMillis.toLong())
    }

    private fun setWhiteboardEnabledState(state: Boolean) {
        prefWhiteboard = state
        MetaDB.storeWhiteboardState(this, parentDid, state)
        if (state && whiteboard == null) {
            createWhiteboard()
        }
    }

    @Suppress("deprecation") // #9332: UI Visibility -> Insets
    private fun setFullScreen(a: AbstractFlashcardViewer) {
        // Set appropriate flags to enable Sticky Immersive mode.
        a.window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE // | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION // temporarily disabled due to #5245
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LOW_PROFILE
                or View.SYSTEM_UI_FLAG_IMMERSIVE
        )
        // Show / hide the Action bar together with the status bar
        val prefs = a.sharedPrefs()
        val fullscreenMode = fromPreference(prefs)
        a.window.statusBarColor = MaterialColors.getColor(a, R.attr.appBarColor, 0)
        val decorView = a.window.decorView
        decorView.setOnSystemUiVisibilityChangeListener { flags: Int ->
            val toolbar = a.findViewById<View>(R.id.toolbar)
            val answerButtons = a.findViewById<View>(R.id.answer_options_layout)
            val topbar = a.findViewById<View>(R.id.top_bar)
            if (toolbar == null || topbar == null || answerButtons == null) {
                return@setOnSystemUiVisibilityChangeListener
            }
            // Note that system bars will only be "visible" if none of the
            // LOW_PROFILE, HIDE_NAVIGATION, or FULLSCREEN flags are set.
            val visible = flags and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION == 0
            Timber.d("System UI visibility change. Visible: %b", visible)
            if (visible) {
                showViewWithAnimation(toolbar)
                if (fullscreenMode == FullScreenMode.FULLSCREEN_ALL_GONE) {
                    showViewWithAnimation(topbar)
                    showViewWithAnimation(answerButtons)
                }
            } else {
                hideViewWithAnimation(toolbar)
                if (fullscreenMode == FullScreenMode.FULLSCREEN_ALL_GONE) {
                    hideViewWithAnimation(topbar)
                    hideViewWithAnimation(answerButtons)
                }
            }
        }
    }

    private fun showViewWithAnimation(view: View) {
        view.alpha = 0.0f
        view.visibility = View.VISIBLE
        view
            .animate()
            .alpha(TRANSPARENCY)
            .setDuration(ANIMATION_DURATION.toLong())
            .setListener(null)
    }

    private fun hideViewWithAnimation(view: View) {
        view
            .animate()
            .alpha(0f)
            .setDuration(ANIMATION_DURATION.toLong())
            .setListener(
                object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        view.visibility = View.GONE
                    }
                },
            )
    }

    @Suppress("deprecation") // #9332: UI Visibility -> Insets
    private fun isImmersiveSystemUiVisible(
        activity: AnkiActivity,
    ): Boolean = activity.window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION == 0

    override suspend fun handlePostRequest(
        uri: PostRequestUri,
        bytes: ByteArray,
    ): ByteArray {
        uri.backendMethodName?.let { methodName ->
            return when (methodName) {
                "getSchedulingStatesWithContext" -> getSchedulingStatesWithContext()
                "setSchedulingStates" -> setSchedulingStates(bytes)
                "i18nResources" -> withCol { i18nResourcesRaw(bytes) }
                else -> throw IllegalArgumentException("Unhandled backend request: $methodName")
            }
        }

        uri.jsApiMethodName?.let { apiMethod ->
            return jsApi.handleJsApiRequest(
                apiMethod,
                bytes,
                returnDefaultValues = false,
            )
        }
        throw IllegalArgumentException("unhandled request: $uri")
    }

    private fun getSchedulingStatesWithContext(): ByteArray {
        val state = queueState ?: return ByteArray(0)
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

    private fun setSchedulingStates(bytes: ByteArray): ByteArray {
        val state = queueState
        if (state == null) {
            statesMutated = true
            return ByteArray(0)
        }
        val req = SetSchedulingStatesRequest.parseFrom(bytes)
        if (req.key == customSchedulingKey) {
            state.states = req.states
        }
        statesMutated = true
        return ByteArray(0)
    }

    private fun createWhiteboard() {
        val whiteboard =
            createInstance(this, true, this).also { whiteboard ->
                this.whiteboard = whiteboard
            }

        // We use the pen color of the selected deck at the time the whiteboard is enabled.
        // This is how all other whiteboard settings are
        val whiteboardPenColor = MetaDB.getWhiteboardPenColor(this, parentDid).fromPreferences()
        if (whiteboardPenColor != null) {
            whiteboard.penColor = whiteboardPenColor
        }
        whiteboard.onPaintColorChangeListener =
            OnPaintColorChangeListener { color ->
                MetaDB.storeWhiteboardPenColor(this@Reviewer, parentDid, currentTheme is DayTheme, color)
            }
        whiteboard.setOnTouchListener { v: View, event: MotionEvent? ->
            if (event == null) return@setOnTouchListener false
            // If the whiteboard is currently drawing, and triggers the system UI to show, we want to continue drawing.
            if (!whiteboard.isCurrentlyDrawing &&
                (
                    !showWhiteboard ||
                        (
                            prefFullscreenReview &&
                                isImmersiveSystemUiVisible(this@Reviewer)
                        )
                )
            ) {
                // Bypass whiteboard listener when it's hidden or fullscreen immersive mode is temporarily suspended
                v.performClick()
                return@setOnTouchListener gestureDetector!!.onTouchEvent(event)
            }
            whiteboard.handleTouchEvent(event)
        }
    }

    // Show or hide the whiteboard
    private fun setWhiteboardVisibility(state: Boolean) {
        showWhiteboard = state
        MetaDB.storeWhiteboardVisibility(this, parentDid, state)
        if (state) {
            whiteboard!!.visibility = View.VISIBLE
            disableDrawerSwipe()
        } else {
            whiteboard!!.visibility = View.GONE
            if (!hasDrawerSwipeConflicts) {
                enableDrawerSwipe()
            }
        }
    }

    private fun disableDrawerSwipeOnConflicts() {
        if (gestureProcessor.isBound(Gesture.SWIPE_UP, Gesture.SWIPE_DOWN, Gesture.SWIPE_RIGHT)) {
            hasDrawerSwipeConflicts = true
            super.disableDrawerSwipe()
        }
    }

    private fun toggleAutoAdvance() {
        if (automaticAnswer.isDisabled) {
            Timber.i("Re-enabling auto advance")
            automaticAnswer.reEnable(isDisplayingAnswer)
            showSnackbar(TR.actionsAutoAdvanceActivated())
        } else {
            Timber.i("Disabling auto advance")
            automaticAnswer.disable()
            showSnackbar(TR.actionsAutoAdvanceDeactivated())
        }
    }

    override val currentCardId: CardId?
        get() = currentCard!!.id

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Restore full screen once we regain focus
        if (hasFocus) {
            delayedHide(INITIAL_HIDE_DELAY)
        } else {
            fullScreenHandler.removeMessages(0)
        }
    }

    /**
     * Whether or not dismiss note is available for current card and specified DismissType
     * @return true if there is another card of same note that could be dismissed
     */
    private fun suspendNoteAvailable(): Boolean {
        return if (currentCard == null) {
            false
        } else {
            getColUnsafe.db.queryScalar(
                "select 1 from cards where nid = ? and id != ? and queue != ${QueueType.Suspended.code} limit 1",
                currentCard!!.nid,
                currentCard!!.id,
            ) == 1
        }
        // whether there exists a sibling not buried.
    }

    private fun buryNoteAvailable(): Boolean {
        return if (currentCard == null) {
            false
        } else {
            getColUnsafe.db.queryScalar(
                "select 1 from cards where nid = ? and id != ? and queue >=  ${QueueType.New.code} limit 1",
                currentCard!!.nid,
                currentCard!!.id,
            ) == 1
        }
        // Whether there exists a sibling which is neither suspended nor buried
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    fun hasDrawerSwipeConflicts(): Boolean = hasDrawerSwipeConflicts

    override fun getCardDataForJsApi(): AnkiDroidJsAPI.CardDataForJsApi {
        val cardDataForJsAPI =
            AnkiDroidJsAPI.CardDataForJsApi().apply {
                newCardCount = queueState?.counts?.new ?: -1
                lrnCardCount = queueState?.counts?.lrn ?: -1
                revCardCount = queueState?.counts?.rev ?: -1
                nextTime1 = easeButton1!!.nextTime
                nextTime2 = easeButton2!!.nextTime
                nextTime3 = easeButton3!!.nextTime
                nextTime4 = easeButton4!!.nextTime
                eta = this@Reviewer.eta
            }
        return cardDataForJsAPI
    }

    companion object {
        /**
         * Bundle key for the deck id to review.
         */
        const val EXTRA_DECK_ID = "deckId"

        private const val KEY_PREVIOUS_CARD_ID = "key_previous_card_id"

        private const val REQUEST_AUDIO_PERMISSION = 0
        private const val ANIMATION_DURATION = 200
        private const val TRANSPARENCY = 0.90f

        /** Default (500ms) time for action snackbars, such as undo, bury and suspend */
        const val ACTION_SNACKBAR_TIME = 500

        private val flagItemIds: Set<Int> = Flag.entries.map { it.id }.toSet()

        fun getIntent(context: Context): Intent =
            if (Prefs.isNewStudyScreenEnabled) {
                ReviewerFragment.getIntent(context)
            } else {
                Intent(context, Reviewer::class.java)
            }
    }

    override fun processAction(
        action: ViewerCommand,
        binding: ReviewerBinding,
    ): Boolean {
        if (binding.side != CardSide.BOTH && CardSide.fromAnswer(isDisplayingAnswer) != binding.side) return false
        val gesture = (binding.binding as? Binding.GestureInput)?.gesture
        return executeCommand(action, gesture)
    }
}
