/* **************************************************************************************
 * Copyright (c) 2011 Kostas Spyropoulos <inigo.aldana@gmail.com>                       *
 * Copyright (c) 2014 Bruno Romero de Azevedo <brunodea@inf.ufsm.br>                    *
 * Copyright (c) 2014–15 Roland Sieker <ospalh@gmail.com>                               *
 * Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>                          *
 * Copyright (c) 2016 Mark Carter <mark@marcardar.com>                                  *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

// TODO: implement own menu? http://www.codeproject.com/Articles/173121/Android-Menus-My-Way
package com.ichi2.anki

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.ViewGroup
import android.view.ViewParent
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebView.HitTestResult
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RelativeLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.CheckResult
import androidx.annotation.IdRes
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle.State.RESUMED
import anki.collection.OpChanges
import anki.scheduler.CardAnswer.Rating
import com.drakeet.drawer.FullDraggableContainer
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anim.ActivityTransitionAnimation
import com.ichi2.anki.AbstractFlashcardViewer.Signal.Companion.toSignal
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.android.AnkiShakeDetector
import com.ichi2.anki.android.back.exitViaDoubleTapBackCallback
import com.ichi2.anki.backend.stripHTMLAndSpecialFields
import com.ichi2.anki.cardviewer.AndroidCardRenderContext
import com.ichi2.anki.cardviewer.AndroidCardRenderContext.Companion.createInstance
import com.ichi2.anki.cardviewer.CardMediaPlayer
import com.ichi2.anki.cardviewer.Gesture
import com.ichi2.anki.cardviewer.GestureProcessor
import com.ichi2.anki.cardviewer.JavascriptEvaluator
import com.ichi2.anki.cardviewer.MediaErrorBehavior
import com.ichi2.anki.cardviewer.MediaErrorBehavior.CONTINUE_MEDIA
import com.ichi2.anki.cardviewer.MediaErrorBehavior.RETRY_MEDIA
import com.ichi2.anki.cardviewer.MediaErrorHandler
import com.ichi2.anki.cardviewer.MediaErrorListener
import com.ichi2.anki.cardviewer.OnRenderProcessGoneDelegate
import com.ichi2.anki.cardviewer.RenderedCard
import com.ichi2.anki.cardviewer.SingleCardSide
import com.ichi2.anki.cardviewer.TTS
import com.ichi2.anki.cardviewer.TypeAnswer
import com.ichi2.anki.cardviewer.TypeAnswer.Companion.createInstance
import com.ichi2.anki.cardviewer.ViewerCommand
import com.ichi2.anki.cardviewer.ViewerRefresh
import com.ichi2.anki.cardviewer.handledGamepadKeyDown
import com.ichi2.anki.cardviewer.handledGamepadKeyUp
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.common.utils.android.HandlerUtils.newHandler
import com.ichi2.anki.compat.CompatHelper.Companion.resolveActivityCompat
import com.ichi2.anki.compat.ResolveInfoFlagsCompat
import com.ichi2.anki.dialogs.TtsPlaybackErrorDialog
import com.ichi2.anki.dialogs.TtsVoicesDialogFragment
import com.ichi2.anki.dialogs.tags.TagsDialog
import com.ichi2.anki.dialogs.tags.TagsDialogFactory
import com.ichi2.anki.dialogs.tags.TagsDialogListener
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.libanki.CardId
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.Decks
import com.ichi2.anki.libanki.SoundOrVideoTag
import com.ichi2.anki.libanki.TTSTag
import com.ichi2.anki.libanki.TtsPlayer
import com.ichi2.anki.model.CardStateFilter
import com.ichi2.anki.multimedia.getAvTag
import com.ichi2.anki.noteeditor.NoteEditorLauncher
import com.ichi2.anki.observability.ChangeManager
import com.ichi2.anki.observability.undoableOp
import com.ichi2.anki.pages.AnkiServer
import com.ichi2.anki.pages.CongratsPage
import com.ichi2.anki.pages.PostRequestHandler
import com.ichi2.anki.pages.PostRequestUri
import com.ichi2.anki.preferences.AccessibilitySettingsFragment
import com.ichi2.anki.preferences.PreferencesActivity
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.reviewer.AutomaticAnswer
import com.ichi2.anki.reviewer.AutomaticAnswer.AutomaticallyAnswered
import com.ichi2.anki.reviewer.AutomaticAnswerAction
import com.ichi2.anki.reviewer.CardSide
import com.ichi2.anki.reviewer.EaseButton
import com.ichi2.anki.reviewer.FullScreenMode
import com.ichi2.anki.reviewer.FullScreenMode.Companion.DEFAULT
import com.ichi2.anki.reviewer.FullScreenMode.Companion.fromPreference
import com.ichi2.anki.reviewer.PreviousAnswerIndicator
import com.ichi2.anki.servicelayer.LanguageHintService.applyLanguageHint
import com.ichi2.anki.servicelayer.NoteService.isMarked
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.snackbar.BaseSnackbarBuilderProvider
import com.ichi2.anki.snackbar.SnackbarBuilder
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.ui.windows.reviewer.StudyScreenRepository
import com.ichi2.anki.utils.OnlyOnce.Method.ANSWER_CARD
import com.ichi2.anki.utils.OnlyOnce.preventSimultaneousExecutions
import com.ichi2.anki.utils.ext.isTouchWithinBounds
import com.ichi2.anki.utils.ext.showDialogFragment
import com.ichi2.themes.Themes
import com.ichi2.themes.Themes.getResFromAttr
import com.ichi2.ui.FixedEditText
import com.ichi2.utils.HashUtil.hashSetInit
import com.ichi2.utils.Stopwatch
import com.ichi2.utils.message
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import com.ichi2.utils.title
import com.squareup.seismic.ShakeDetector
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.io.File
import java.io.UnsupportedEncodingException
import java.net.URLDecoder
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.function.Consumer
import java.util.function.Function
import kotlin.math.abs

abstract class AbstractFlashcardViewer :
    NavigationDrawerActivity(),
    ViewerCommand.CommandProcessor,
    TagsDialogListener,
    WhiteboardMultiTouchMethods,
    AutomaticallyAnswered,
    OnPageFinishedCallback,
    BaseSnackbarBuilderProvider,
    ChangeManager.Subscriber,
    PostRequestHandler {
    private var ttsInitialized = false
    private var replayOnTtsInit = false

    @VisibleForTesting
    val jsApi by lazy { AnkiDroidJsAPI(this) }

    private var tagsDialogFactory: TagsDialogFactory? = null

    /**
     * Variables to hold preferences
     */
    internal var prefShowTopbar = false
    protected var fullscreenMode = DEFAULT
        private set
    private var relativeButtonSize = 0
    private var minimalClickSpeed = 0
    private var doubleScrolling = false
    private var gesturesEnabled = false
    private var largeAnswerButtons = false
    protected var answerButtonsPosition: String? = "bottom"
    private var doubleTapTimeInterval = DEFAULT_DOUBLE_TAP_TIME_INTERVAL

    // Android WebView
    var automaticAnswer = AutomaticAnswer.defaultInstance(this)

    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    internal var typeAnswer: TypeAnswer? = null

    /** Generates HTML content  */
    private var cardRenderContext: AndroidCardRenderContext? = null

    // Default short animation duration, provided by Android framework
    private var shortAnimDuration = 0
    private var backButtonPressedToReturn = false

    // Preferences from the collection
    private var showNextReviewTime = false
    private var isSelecting = false
    private var inAnswer = false

    /**
     * Variables to hold layout objects that we need to update or handle events for
     */
    var webView: WebView? = null
        private set

    /** Accessor for [WebView.getWebViewClient] before API 26 */
    var webViewClient: CardViewerWebClient? = null

    private var cardFrame: FrameLayout? = null
    private var touchLayer: FrameLayout? = null
    protected var answerField: FixedEditText? = null
    protected var flipCardLayout: FrameLayout? = null
    private var easeButtonsLayout: LinearLayout? = null

    internal var easeButton1: EaseButton? = null
    internal var easeButton2: EaseButton? = null
    internal var easeButton3: EaseButton? = null
    internal var easeButton4: EaseButton? = null
    protected var topBarLayout: RelativeLayout? = null
    private var previousAnswerIndicator: PreviousAnswerIndicator? = null

    private var currentEase: Rating? = null
    private var initialFlipCardHeight = 0
    private var buttonHeightSet = false

    /**
     * A record of the last time the "show answer" or ease buttons were pressed. We keep track
     * of this time to ignore accidental button presses.
     */
    @VisibleForTesting
    protected var lastClickTime: Long = 0

    /**
     * Swipe Detection
     */
    var gestureDetector: GestureDetector? = null
        private set
    private lateinit var gestureDetectorImpl: MyGestureDetector
    private var isXScrolling = false
    private var isYScrolling = false

    /**
     * Gesture Allocation
     */
    protected val gestureProcessor = GestureProcessor(this)

    // needs to be lateinit due to a reliance on Context

    lateinit var server: AnkiServer

    @get:VisibleForTesting
    var cardContent: String? = null
        private set

    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    internal lateinit var cardMediaPlayer: CardMediaPlayer

    /** Reference to the parent of the cardFrame to allow regeneration of the cardFrame in case of crash  */
    private var cardFrameParent: ViewGroup? = null

    /** Lock to allow thread-safe regeneration of mCard  */
    private val cardLock: ReadWriteLock = ReentrantReadWriteLock()

    @VisibleForTesting
    val onRenderProcessGoneDelegate = OnRenderProcessGoneDelegate(this)
    protected val tts = TTS()

    // ----------------------------------------------------------------------------
    // LISTENERS
    // ----------------------------------------------------------------------------
    // Handler for the "show answer" button
    private val flipCardListener =
        View.OnClickListener {
            Timber.i("AbstractFlashcardViewer:: Show answer button pressed")
            // Ignore what is most likely an accidental double-tap.
            if (elapsedRealTime - lastClickTime < doubleTapTimeInterval) {
                return@OnClickListener
            }
            lastClickTime = elapsedRealTime
            automaticAnswer.onShowAnswer()
            displayCardAnswer()
        }

    /**
     * Changes which were received when the viewer was in the background
     * which should be executed once the viewer is visible again
     * @see opExecuted
     * @see refreshIfRequired
     */
    @VisibleForTesting
    internal var refreshRequired: ViewerRefresh? = null

    private val editCurrentCardLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
            FlashCardViewerResultCallback { result, reloadRequired ->
                if (result.resultCode == RESULT_OK) {
                    Timber.i("AbstractFlashcardViewer:: card edited...")
                    onEditedNoteChanged()
                } else if (result.resultCode == RESULT_CANCELED && !reloadRequired) {
                    // nothing was changed by the note editor so just redraw the card
                    redrawCard()
                }
            },
        )

    private val defaultOnBackCallback =
        object : OnBackPressedCallback(enabled = true) {
            override fun handleOnBackPressed() {
                // TODO: This should be improved now we're using callbacks
                closeReviewer(RESULT_DEFAULT)
            }
        }

    protected inner class FlashCardViewerResultCallback(
        private val callback: (result: ActivityResult, reloadRequired: Boolean) -> Unit = { _, _ -> },
    ) : ActivityResultCallback<ActivityResult> {
        override fun onActivityResult(result: ActivityResult) {
            if (result.resultCode == DeckPicker.RESULT_MEDIA_EJECTED) {
                finishNoStorageAvailable()
            }

            /* Reset the schedule and reload the latest card off the top of the stack if required.
               The card could have been rescheduled, the deck could have changed, or a change of
               note type could have lead to the card being deleted */
            val reloadRequired =
                result.data?.getBooleanExtra(NoteEditorFragment.RELOAD_REQUIRED_EXTRA_KEY, false) == true
            if (reloadRequired) {
                performReload()
            }

            callback(result, reloadRequired)
        }
    }

    init {
        ChangeManager.subscribe(this)
    }

    // Event handler for eases (answer buttons)
    inner class SelectEaseHandler :
        View.OnClickListener,
        OnTouchListener {
        private var prevCard: Card? = null
        private var hasBeenTouched = false
        private var touchX = 0f
        private var touchY = 0f

        override fun onTouch(
            view: View,
            event: MotionEvent,
        ): Boolean {
            if (event.action == MotionEvent.ACTION_DOWN) {
                // Save states when button pressed
                prevCard = currentCard
                hasBeenTouched = true
                // We will need to check if a touch is followed by a click
                // Since onTouch always come before onClick, we should check if
                // the touch is going to be a click by storing the start coordinates
                // and comparing with the end coordinates of the touch
                touchX = event.rawX
                touchY = event.rawY
            } else if (event.action == MotionEvent.ACTION_UP) {
                val diffX = abs(event.rawX - touchX)
                val diffY = abs(event.rawY - touchY)
                // If a click is not coming then we reset the touch
                if (diffX > CLICK_ACTION_THRESHOLD || diffY > CLICK_ACTION_THRESHOLD) {
                    hasBeenTouched = false
                }
            }
            return false
        }

        override fun onClick(view: View) {
            // Try to perform intended action only if the button has been pressed for current card,
            // or if the button was not touched,
            if (prevCard === currentCard || !hasBeenTouched) {
                // Only perform if the click was not an accidental double-tap
                if (elapsedRealTime - lastClickTime >= doubleTapTimeInterval) {
                    // For whatever reason, performClick does not return a visual feedback anymore
                    if (!hasBeenTouched) {
                        view.isPressed = true
                    }
                    lastClickTime = elapsedRealTime
                    automaticAnswer.onSelectEase()
                    when (view.id) {
                        R.id.flashcard_layout_ease1 -> {
                            Timber.i("AbstractFlashcardViewer:: Ease_1 pressed")
                            answerCard(Rating.AGAIN)
                        }

                        R.id.flashcard_layout_ease2 -> {
                            Timber.i("AbstractFlashcardViewer:: Ease_2 pressed")
                            answerCard(Rating.HARD)
                        }

                        R.id.flashcard_layout_ease3 -> {
                            Timber.i("AbstractFlashcardViewer:: Ease_3 pressed")
                            answerCard(Rating.GOOD)
                        }

                        R.id.flashcard_layout_ease4 -> {
                            Timber.i("AbstractFlashcardViewer:: Ease_4 pressed")
                            answerCard(Rating.EASY)
                        }

                        else -> currentEase = null
                    }
                    if (!hasBeenTouched) {
                        view.isPressed = false
                    }
                }
            }
            // We will have to reset the touch after every onClick event
            // Do not return early without considering this
            hasBeenTouched = false
        }
    }

    private val easeHandler = SelectEaseHandler()

    @get:VisibleForTesting
    protected open val elapsedRealTime: Long
        get() = SystemClock.elapsedRealtime()
    private val gestureListener =
        OnTouchListener { _, event ->
            if (gestureDetector!!.onTouchEvent(event)) {
                return@OnTouchListener true
            }
            if (!gestureDetectorImpl.eventCanBeSentToWebView(event)) {
                return@OnTouchListener false
            }
            // Gesture listener is added before mCard is set
            processCardAction { cardWebView: WebView? ->
                if (cardWebView == null) return@processCardAction
                cardWebView.dispatchTouchEvent(event)
            }
            false
        }

    // This is intentionally package-private as it removes the need for synthetic accessors
    @SuppressLint("CheckResult")
    fun processCardAction(cardConsumer: Consumer<WebView?>) {
        processCardFunction { cardWebView: WebView? ->
            cardConsumer.accept(cardWebView)
            true
        }
    }

    @CheckResult
    private fun <T> processCardFunction(cardFunction: Function<WebView?, T>): T {
        val readLock = cardLock.readLock()
        return try {
            readLock.lock()
            cardFunction.apply(webView)
        } finally {
            readLock.unlock()
        }
    }

    /** Operation after a card has been updated due to being edited. Called before display[Question/Answer]  */
    protected open fun onCardEdited(card: Card) {
        // intentionally blank
    }

    /** Invoked by [CardViewerWebClient.onPageFinished] */
    override fun onPageFinished(view: WebView) {
        // intentionally blank
    }

    /** Called after an undo or undoable operation takes place. * Should set currentCard to the current card to display. */
    open suspend fun updateCurrentCard() {
        // Legacy tests assume the current card will be grabbed from the collection,
        // despite that making no sense outside of Reviewer.kt
        currentCard =
            withCol {
                sched.card?.apply {
                    renderOutput(this@withCol, reload = false, browser = false)
                }
            }
    }

    internal suspend fun updateCardAndRedraw() {
        Timber.d("updateCardAndRedraw")
        refreshRequired = null // this method is called on refresh

        updateCurrentCard()

        if (currentCard == null) {
            closeReviewer(RESULT_NO_MORE_CARDS)
            // When launched with a shortcut, we want to display a message when finishing
            if (intent.getBooleanExtra(EXTRA_STARTED_WITH_SHORTCUT, false)) {
                CongratsPage.display(this)
            }
            return
        }

        // Start reviewing next card
        hideProgressBar()
        unblockControls()
        displayCardQuestion()
        // set the correct mark/unmark icon on action bar
        refreshActionBar()
        focusDefaultLayout()
    }

    private fun focusDefaultLayout() {
        findViewById<View>(R.id.root_layout).requestFocus()
    }

    // ----------------------------------------------------------------------------
    // ANDROID METHODS
    // ----------------------------------------------------------------------------
    override fun onCreate(savedInstanceState: Bundle?) {
        restorePreferences()
        tagsDialogFactory = TagsDialogFactory(this).attachToActivity<TagsDialogFactory>(this)
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(automaticAnswer)

        // Issue 14142: The reviewer had a focus highlight after answering using a keyboard.
        // This theme removes the highlight, but there is likely a better way.
        this.setTheme(R.style.ThemeOverlay_DisableKeyboardHighlight)

        setContentView(getContentViewAttr(fullscreenMode))

        val port = StudyScreenRepository().getServerPort()
        server = AnkiServer(this, port).also { it.start() }
        // Make ACTION_PROCESS_TEXT for in-app searching possible on > Android 4.0
        delegate.isHandleNativeActionModesEnabled = true

        initNavigationDrawer()
        previousAnswerIndicator = PreviousAnswerIndicator(findViewById(R.id.chosen_answer))
        shortAnimDuration = resources.getInteger(android.R.integer.config_shortAnimTime)
        gestureDetectorImpl = LinkDetectingGestureDetector()
        TtsVoicesFieldFilter.ensureApplied()
    }

    override fun setupBackPressedCallbacks() {
        onBackPressedDispatcher.addCallback(this, defaultOnBackCallback)
        onBackPressedDispatcher.addCallback(this, exitViaDoubleTapBackCallback())
        super.setupBackPressedCallbacks()
    }

    protected open fun getContentViewAttr(fullscreenMode: FullScreenMode): Int = R.layout.activity_reviewer

    @get:VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    val isFullscreen: Boolean
        get() = !supportActionBar!!.isShowing

    override fun onConfigurationChanged(newConfig: Configuration) {
        // called when screen rotated, etc, since recreating the Webview is too expensive
        super.onConfigurationChanged(newConfig)
        refreshActionBar()
    }

    // Finish initializing the activity after the collection has been correctly loaded
    public override fun onCollectionLoaded(col: Collection) {
        super.onCollectionLoaded(col)
        cardMediaPlayer = getCardMediaPlayerInstance(this)
        registerReceiver()
        restoreCollectionPreferences(col)
        initLayout()
        cardRenderContext = createInstance(this, col, typeAnswer!!)

        // Initialize text-to-speech. This is an asynchronous operation.
        tts.initialize(this, ReadTextListener())
        updateActionBar()
        invalidateOptionsMenu()
    }

    // Saves deck each time Reviewer activity loses focus
    override fun onPause() {
        super.onPause()
        gestureDetectorImpl.stopShakeDetector()
        if (this::cardMediaPlayer.isInitialized) {
            launchCatchingTask {
                cardMediaPlayer.setEnabled(false)
            }
            ReadText.stopTts()
        }
        // Prevent loss of data in Cookies
        CookieManager.getInstance().flush()
    }

    override fun onResume() {
        super.onResume()
        gestureDetectorImpl.startShakeDetector()
        if (this::cardMediaPlayer.isInitialized) {
            launchCatchingTask {
                cardMediaPlayer.setEnabled(true)
            }
        }
        // Reset the activity title
        updateActionBar()
        selectNavigationItem(-1)
        refreshIfRequired(isResuming = true)
    }

    /**
     * If the activity is [RESUMED], or is called from [onResume] then execute the pending
     * operations in [refreshRequired].
     *
     * If the activity is NOT [RESUMED], wait until [onResume]
     */
    @VisibleForTesting
    internal fun refreshIfRequired(isResuming: Boolean = false) {
        // Defer the execution of `opExecuted` until the user is looking at the screen.
        // This ensures that audio/timers are not accidentally started
        if (isResuming || lifecycle.currentState.isAtLeast(RESUMED)) {
            refreshRequired?.let {
                Timber.d("refreshIfRequired: redraw")
                // if changing code, re-evaluate `refreshRequired = null` in `updateCardAndRedraw`
                launchCatchingTask { updateCardAndRedraw() }
                refreshRequired = null
            }
        } else if (refreshRequired != null) {
            // onResume() will execute this method
            Timber.d("deferred refresh as activity was not STARTED")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (this::server.isInitialized) {
            server.stop()
        }
        tts.releaseTts(this)
        // WebView.destroy() should be called after the end of use
        // http://developer.android.com/reference/android/webkit/WebView.html#destroy()
        if (cardFrame != null) {
            cardFrame!!.removeAllViews()
        }
        destroyWebView(webView) // OK to do without a lock
        if (this::cardMediaPlayer.isInitialized) {
            cardMediaPlayer.close()
        }
    }

    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean {
        if (processCardFunction { cardWebView: WebView? ->
                processHardwareButtonScroll(
                    keyCode,
                    cardWebView,
                )
            }
        ) {
            return true
        }

        // Subclasses other than 'Reviewer' have not been setup with Gestures/KeyPresses
        // so hardcode this functionality for now.
        // This is in onKeyDown to match the gesture processor in the Reviewer
        if (!displayAnswer && !answerFieldIsFocused()) {
            if (keyCode == KeyEvent.KEYCODE_SPACE || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                displayCardAnswer()
                return true
            }
        }

        if (webView.handledGamepadKeyDown(keyCode, event)) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean {
        if (webView.handledGamepadKeyUp(keyCode, event)) {
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    public override val currentCardId: CardId? get() = currentCard?.id

    private fun processHardwareButtonScroll(
        keyCode: Int,
        card: WebView?,
    ): Boolean {
        if (keyCode == KeyEvent.KEYCODE_PAGE_UP) {
            card!!.pageUp(false)
            if (doubleScrolling) {
                card.pageUp(false)
            }
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_PAGE_DOWN) {
            card!!.pageDown(false)
            if (doubleScrolling) {
                card.pageDown(false)
            }
            return true
        }
        return false
    }

    protected open fun answerFieldIsFocused(): Boolean = answerField != null && answerField!!.isFocused

    val deckOptionsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
            Timber.i("Returned from deck options -> Restarting activity")
            performReload()
        }

    /**
     * Whether the class should use collection.getSched() when performing tasks.
     * The aim of this method is to completely distinguish FlashcardViewer from Reviewer
     *
     * This is partially implemented, the end goal is that the FlashcardViewer will not have any coupling to getSched
     *
     * Currently, this is used for note edits - in a reviewing context, this should show the next card.
     * In a previewing context, the card should not change.
     */
    open fun canAccessScheduler(): Boolean = false

    protected open fun onEditedNoteChanged() {}

    /** An action which may invalidate the current list of cards has been performed  */
    protected abstract fun performReload()

    // ----------------------------------------------------------------------------
    // CUSTOM METHODS
    // ----------------------------------------------------------------------------
    // Get the did of the parent deck (ignoring any subdecks)
    protected val parentDid: DeckId
        get() = getColUnsafe.decks.selected()

    private fun redrawCard() {
        // #3654 We can call this from ActivityResult, which could mean that the card content hasn't yet been set
        // if the activity was destroyed. In this case, just wait until onCollectionLoaded callback succeeds.
        if (hasLoadedCardContent()) {
            fillFlashcard()
        } else {
            Timber.i("Skipping card redraw - card still initialising.")
        }
    }

    /** Whether the callback to onCollectionLoaded has loaded card content  */
    private fun hasLoadedCardContent(): Boolean = cardContent != null

    open fun undo(): Job =
        launchCatchingTask {
            undoAndShowSnackbar(duration = Reviewer.ACTION_SNACKBAR_TIME)
        }

    private fun finishNoStorageAvailable() {
        this@AbstractFlashcardViewer.setResult(DeckPicker.RESULT_MEDIA_EJECTED)
        finish()
    }

    protected open fun editCard(fromGesture: Gesture? = null) {
        if (currentCard == null) {
            // This should never occurs. It means the review button was pressed while there is no more card in the reviewer.
            return
        }
        val animation = fromGesture.toAnimationTransition().invert()
        Timber.i("Launching 'edit card'")
        val editCardIntent = NoteEditorLauncher.EditSelection(listOf(currentCard!!.id), animation).toIntent(this)
        editCurrentCardLauncher.launch(editCardIntent)
    }

    protected fun showDeleteNoteDialog() {
        Timber.i("Displaying 'delete note' dialog")
        AlertDialog.Builder(this).show {
            title(R.string.delete_card_title)
            setIcon(R.drawable.ic_warning)
            message(
                text =
                    resources.getString(
                        R.string.delete_note_message,
                        stripHTMLAndSpecialFields(currentCard!!.question(getColUnsafe, true)).trim(),
                    ),
            )
            positiveButton(R.string.dialog_positive_delete) {
                Timber.i(
                    "AbstractFlashcardViewer:: OK button pressed to delete note %d",
                    currentCard!!.nid,
                )
                launchCatchingTask { stopCardMediaPlayer() }
                deleteNoteWithoutConfirmation()
            }
            negativeButton(R.string.dialog_cancel)
        }
    }

    /** Consumers should use [.showDeleteNoteDialog]   */
    private fun deleteNoteWithoutConfirmation() {
        val cardId = currentCard!!.id
        launchCatchingTask {
            val noteCount =
                withProgress {
                    undoableOp {
                        removeNotes(cardIds = listOf(cardId))
                    }.count
                }
            val deletedMessage =
                resources.getQuantityString(
                    R.plurals.card_browser_cards_deleted,
                    noteCount,
                    noteCount,
                )
            showSnackbar(deletedMessage, Snackbar.LENGTH_LONG) {
                setAction(R.string.undo) { launchCatchingTask { undoAndShowSnackbar() } }
            }
        }
    }

    open fun answerCard(rating: Rating) =
        preventSimultaneousExecutions(ANSWER_CARD) {
            launchCatchingTask {
                if (inAnswer) {
                    return@launchCatchingTask
                }
                isSelecting = false
                if (previousAnswerIndicator == null) {
                    // workaround for a broken ReviewerKeyboardInputTest
                    return@launchCatchingTask
                }
                // Temporarily sets the answer indicator dots appearing below the toolbar
                previousAnswerIndicator?.displayAnswerIndicator(rating)
                stopCardMediaPlayer()
                currentEase = rating

                answerCardInner(rating)
                updateCardAndRedraw()
            }
        }

    open suspend fun answerCardInner(rating: Rating) {
        // Legacy tests assume they can call answerCard() even outside of Reviewer
        withCol {
            sched.answerCard(currentCard!!, rating)
        }
    }

    // Set the content view to the one provided and initialize accessors.
    protected open fun initLayout() {
        topBarLayout = findViewById(R.id.top_bar)
        cardFrame =
            findViewById<FrameLayout>(R.id.flashcard).apply {
                // Force the WebView's container onto its own GPU texture so it isn't dropped from
                // composition when the overlapping Whiteboard sibling invalidates each touch frame.
                // Without this, Samsung WebView (since a recent update) hides the card mid-stroke
                // until the next full hierarchy invalidation. (#19364)
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
            }
        cardFrameParent = cardFrame!!.parent as ViewGroup
        touchLayer =
            findViewById<FrameLayout>(R.id.touch_layer).apply { setOnTouchListener(gestureListener) }
        cardFrame!!.removeAllViews()

        // Initialize swipe
        gestureDetector = GestureDetector(this, gestureDetectorImpl)
        easeButtonsLayout = findViewById(R.id.ease_buttons)
        easeButton1 =
            EaseButton(
                Rating.AGAIN,
                findViewById(R.id.flashcard_layout_ease1),
                findViewById(R.id.ease1),
                findViewById(R.id.nextTime1),
            ).apply { setListeners(easeHandler) }
        easeButton2 =
            EaseButton(
                Rating.HARD,
                findViewById(R.id.flashcard_layout_ease2),
                findViewById(R.id.ease2),
                findViewById(R.id.nextTime2),
            ).apply { setListeners(easeHandler) }
        easeButton3 =
            EaseButton(
                Rating.GOOD,
                findViewById(R.id.flashcard_layout_ease3),
                findViewById(R.id.ease3),
                findViewById(R.id.nextTime3),
            ).apply { setListeners(easeHandler) }
        easeButton4 =
            EaseButton(
                Rating.EASY,
                findViewById(R.id.flashcard_layout_ease4),
                findViewById(R.id.ease4),
                findViewById(R.id.nextTime4),
            ).apply { setListeners(easeHandler) }
        if (!showNextReviewTime) {
            easeButton1!!.hideNextReviewTime()
            easeButton2!!.hideNextReviewTime()
            easeButton3!!.hideNextReviewTime()
            easeButton4!!.hideNextReviewTime()
        }
        flipCardLayout = findViewById(R.id.flashcard_layout_flip)
        flipCardLayout?.let { layout ->
            if (minimalClickSpeed == 0) {
                layout.setOnClickListener(flipCardListener)
            } else {
                val handler = Handler(Looper.getMainLooper())
                layout.setOnTouchListener { view, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            handler.postDelayed({
                                flipCardListener.onClick(layout)
                            }, minimalClickSpeed.toLong())

                            showMinimalClickHint()
                            false
                        }

                        MotionEvent.ACTION_MOVE -> {
                            if (!view.isTouchWithinBounds(event)) {
                                handler.removeCallbacksAndMessages(null)
                            }
                            false
                        }

                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_HOVER_ENTER -> {
                            handler.removeCallbacksAndMessages(null)
                            false
                        }

                        else -> false
                    }
                }
            }
        }
        if (animationEnabled()) {
            flipCardLayout?.setBackgroundResource(getResFromAttr(this, R.attr.hardButtonRippleRef))
        }
        if (!buttonHeightSet && relativeButtonSize != 100) {
            val params = flipCardLayout!!.layoutParams
            params.height = params.height * relativeButtonSize / 100
            easeButton1!!.setButtonScale(relativeButtonSize)
            easeButton2!!.setButtonScale(relativeButtonSize)
            easeButton3!!.setButtonScale(relativeButtonSize)
            easeButton4!!.setButtonScale(relativeButtonSize)
            buttonHeightSet = true
        }
        initialFlipCardHeight = flipCardLayout!!.layoutParams.height
        if (largeAnswerButtons) {
            val params = flipCardLayout!!.layoutParams
            params.height = initialFlipCardHeight * 2
        }
        answerField = findViewById(R.id.answer_field)
        initControls()

        // Position answer buttons
        val answerButtonsPosition =
            this.sharedPrefs().getString(
                getString(R.string.answer_buttons_position_preference),
                "bottom",
            )
        this.answerButtonsPosition = answerButtonsPosition
        val answerArea = findViewById<LinearLayout>(R.id.bottom_area_layout)
        val answerAreaParams = answerArea.layoutParams as RelativeLayout.LayoutParams
        val whiteboardContainer = findViewById<FrameLayout>(R.id.whiteboard)
        val whiteboardContainerParams =
            whiteboardContainer.layoutParams as RelativeLayout.LayoutParams
        val flashcardContainerParams = cardFrame!!.layoutParams as RelativeLayout.LayoutParams
        val touchLayerContainerParams = touchLayer!!.layoutParams as RelativeLayout.LayoutParams
        when (answerButtonsPosition) {
            "top" -> {
                whiteboardContainerParams.addRule(RelativeLayout.BELOW, R.id.bottom_area_layout)
                flashcardContainerParams.addRule(RelativeLayout.BELOW, R.id.bottom_area_layout)
                touchLayerContainerParams.addRule(RelativeLayout.BELOW, R.id.bottom_area_layout)
                answerAreaParams.addRule(RelativeLayout.BELOW, R.id.mic_tool_bar_layer)
                answerArea.removeView(answerField)
                answerArea.addView(answerField, 1)
            }

            "bottom",
            "none",
            -> {
                whiteboardContainerParams.addRule(RelativeLayout.ABOVE, R.id.bottom_area_layout)
                whiteboardContainerParams.addRule(RelativeLayout.BELOW, R.id.mic_tool_bar_layer)
                flashcardContainerParams.addRule(RelativeLayout.ABOVE, R.id.bottom_area_layout)
                flashcardContainerParams.addRule(RelativeLayout.BELOW, R.id.mic_tool_bar_layer)
                touchLayerContainerParams.addRule(RelativeLayout.ABOVE, R.id.bottom_area_layout)
                touchLayerContainerParams.addRule(RelativeLayout.BELOW, R.id.mic_tool_bar_layer)
                answerAreaParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
            }

            else -> Timber.w("Unknown answerButtonsPosition: %s", answerButtonsPosition)
        }
        answerArea.visibility = if (answerButtonsPosition == "none") View.GONE else View.VISIBLE
        // workaround for #14419, iterate over the bottom area children and manually enable the
        // answer field while still hiding the other children
        if (answerButtonsPosition == "none") {
            answerArea.visibility = View.VISIBLE
            answerArea.children.forEach {
                it.visibility = if (it.id == R.id.answer_field) View.VISIBLE else View.GONE
            }
        }
        answerArea.layoutParams = answerAreaParams
        whiteboardContainer.layoutParams = whiteboardContainerParams
        cardFrame!!.layoutParams = flashcardContainerParams
        touchLayer!!.layoutParams = touchLayerContainerParams
    }

    protected open fun createWebView(): WebView {
        val resourceHandler = ViewerResourceHandler(this)
        val webView: WebView =
            MyWebView(this).apply {
                scrollBarStyle = View.SCROLLBARS_OUTSIDE_OVERLAY
                with(settings) {
                    displayZoomControls = false
                    builtInZoomControls = true
                    setSupportZoom(true)
                    loadWithOverviewMode = true
                    javaScriptEnabled = true
                    allowFileAccess = true
                    // enable dom storage so that sessionStorage & localStorage can be used in webview
                    domStorageEnabled = true
                }
                webChromeClient = AnkiDroidWebChromeClient()
                isFocusableInTouchMode = typeAnswer!!.useInputTag
                isScrollbarFadingEnabled = true
                // Set transparent color to prevent flashing white when night mode enabled
                setBackgroundColor(Color.argb(1, 0, 0, 0))
                CardViewerWebClient(resourceHandler, this@AbstractFlashcardViewer).apply {
                    webViewClient = this
                    this@AbstractFlashcardViewer.webViewClient = this
                }
            }
        Timber.d(
            "Focusable = %s, Focusable in touch mode = %s",
            webView.isFocusable,
            webView.isFocusableInTouchMode,
        )

        // enable third party cookies so that cookies can be used in webview
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        return webView
    }

    /** If a card is displaying the question, flip it, otherwise answer it  */
    internal open fun flipOrAnswerCard(cardOrdinal: Rating) {
        if (!displayAnswer) {
            displayCardAnswer()
            return
        }
        performClickWithVisualFeedback(cardOrdinal)
    }

    // #5780 - Users could OOM the WebView Renderer. This triggers the same symptoms
    @VisibleForTesting
    @Suppress("unused")
    fun crashWebViewRenderer() {
        loadUrlInViewer("chrome://crash")
    }

    /** Used to set the "javascript:" URIs for IPC  */
    fun loadUrlInViewer(url: String) {
        processCardAction { cardWebView: WebView? -> cardWebView!!.loadUrl(url) }
    }

    private fun <T : View?> inflateNewView(
        @IdRes id: Int,
    ): T {
        val layoutId = getContentViewAttr(fullscreenMode)
        val content =
            LayoutInflater
                .from(this@AbstractFlashcardViewer)
                .inflate(layoutId, null, false) as ViewGroup
        val ret: T = content.findViewById(id)
        (ret!!.parent as ViewGroup).removeView(ret) // detach the view from its parent
        content.removeAllViews()
        return ret
    }

    private fun destroyWebView(webView: WebView?) {
        try {
            if (webView != null) {
                webView.stopLoading()
                webView.webChromeClient = null
                webView.destroy()
            }
        } catch (npe: NullPointerException) {
            Timber.e(npe, "WebView became null on destruction")
        }
    }

    protected fun shouldShowNextReviewTime(): Boolean = showNextReviewTime

    protected open fun displayAnswerBottomBar() {
        flipCardLayout!!.isClickable = false
        easeButtonsLayout!!.visibility = View.VISIBLE
        if (largeAnswerButtons) {
            easeButtonsLayout!!.orientation = LinearLayout.VERTICAL
            easeButtonsLayout!!.removeAllViewsInLayout()
            easeButton1!!.detachFromParent()
            easeButton2!!.detachFromParent()
            easeButton3!!.detachFromParent()
            easeButton4!!.detachFromParent()
            val row1 = LinearLayout(baseContext)
            row1.orientation = LinearLayout.HORIZONTAL
            val row2 = LinearLayout(baseContext)
            row2.orientation = LinearLayout.HORIZONTAL
            easeButton2!!.addTo(row1)
            easeButton4!!.addTo(row1)
            easeButton1!!.addTo(row2)
            easeButton3!!.addTo(row2)
            easeButtonsLayout!!.addView(row1)
            easeButtonsLayout!!.addView(row2)
        }
        val after = Runnable { flipCardLayout!!.visibility = View.GONE }

        // hide "Show Answer" button
        if (animationDisabled()) {
            after.run()
        } else {
            flipCardLayout!!.alpha = 1f
            flipCardLayout!!
                .animate()
                .alpha(0f)
                .setDuration(shortAnimDuration.toLong())
                .withEndAction(after)
        }
    }

    protected open fun hideEaseButtons() {
        val after = Runnable { actualHideEaseButtons() }
        val easeButtonsVisible = easeButtonsLayout?.visibility == View.VISIBLE
        flipCardLayout?.isClickable = true
        flipCardLayout?.visibility = View.VISIBLE
        if (animationDisabled() || !easeButtonsVisible) {
            after.run()
        } else {
            flipCardLayout?.alpha = 0f
            flipCardLayout
                ?.animate()
                ?.alpha(1f)
                ?.setDuration(shortAnimDuration.toLong())
                ?.withEndAction(after)
        }
        focusAnswerCompletionField()
    }

    private fun actualHideEaseButtons() {
        easeButtonsLayout?.visibility = View.GONE
        easeButton1?.hide()
        easeButton2?.hide()
        easeButton3?.hide()
        easeButton4?.hide()
    }

    /**
     * Focuses the appropriate field for an answer
     * And allows keyboard shortcuts to go to the default handlers.
     */
    private fun focusAnswerCompletionField() =
        runOnUiThread {
            // This does not handle mUseInputTag (the WebView contains an input field with a typable answer).
            // In this case, the user can use touch to focus the field if necessary.
            if (typeAnswer?.autoFocusEditText() == true) {
                answerField?.focusWithKeyboard()
            } else {
                flipCardLayout?.requestFocus()
            }
        }

    protected open fun switchTopBarVisibility(visible: Int) {
        previousAnswerIndicator!!.setVisibility(visible)
    }

    protected open fun initControls() {
        cardFrame!!.visibility = View.VISIBLE
        previousAnswerIndicator!!.setVisibility(View.VISIBLE)
        flipCardLayout!!.visibility = View.VISIBLE
        answerField!!.visibility = if (typeAnswer!!.validForEditText()) View.VISIBLE else View.GONE
        answerField!!.setOnEditorActionListener { _, actionId: Int, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                displayCardAnswer()
                return@setOnEditorActionListener true
            }
            false
        }
        answerField!!.setOnKeyListener { _, keyCode: Int, event: KeyEvent ->
            if (event.action == KeyEvent.ACTION_UP &&
                (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER)
            ) {
                displayCardAnswer()
                return@setOnKeyListener true
            }
            false
        }
    }

    protected open fun restorePreferences(): SharedPreferences {
        val preferences = baseContext.sharedPrefs()
        typeAnswer = createInstance(preferences)
        // mDeckFilename = preferences.getString("deckFilename", "");
        minimalClickSpeed = preferences.getInt("showCardAnswerButtonTime", 0)
        fullscreenMode = fromPreference(preferences)
        relativeButtonSize = Prefs.answerButtonsSize
        tts.enabled = preferences.getBoolean("tts", false)
        doubleScrolling = preferences.getBoolean("double_scrolling", false)
        prefShowTopbar = preferences.getBoolean("showTopbar", true)
        largeAnswerButtons = preferences.getBoolean("showLargeAnswerButtons", false)
        doubleTapTimeInterval = Prefs.doubleTapInterval
        gesturesEnabled = preferences.getBoolean(GestureProcessor.PREF_KEY, false)
        if (gesturesEnabled) {
            gestureProcessor.init(preferences)
        }
        if (preferences.getBoolean("timeoutAnswer", false) ||
            preferences.getBoolean(
                "keepScreenOn",
                false,
            )
        ) {
            this.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        return preferences
    }

    protected open fun restoreCollectionPreferences(col: Collection) {
        // These are preferences we pull out of the collection instead of SharedPreferences
        try {
            lifecycle.removeObserver(automaticAnswer)
            showNextReviewTime = col.config.get("estTimes") ?: true
            automaticAnswer = AutomaticAnswer.createInstance(this, col)
            lifecycle.addObserver(automaticAnswer)
        } catch (ex: Exception) {
            Timber.w(ex)
            onCollectionLoadError()
        }
    }

    private fun setInterface() {
        if (currentCard == null) {
            return
        }
        recreateWebView()
    }

    protected open fun recreateWebView() {
        if (webView == null) {
            webView = createWebView()
            cardFrame!!.addView(webView)
            gestureDetectorImpl.onWebViewCreated(webView!!)
        }
        if (webView!!.visibility != View.VISIBLE) {
            webView!!.visibility = View.VISIBLE
        }
    }

    /** A new card has been loaded into the Viewer, or the question has been re-shown  */
    protected open fun updateForNewCard() {
        updateActionBar()

        // Clean answer field
        if (typeAnswer!!.validForEditText()) {
            answerField!!.setText("")
        }
    }

    protected open fun updateActionBar() {
        updateDeckName()
    }

    private fun updateDeckName() {
        if (currentCard == null) return
        if (sharedPrefs().getBoolean("showDeckTitle", false)) {
            supportActionBar?.title = Decks.basename(getColUnsafe.decks.name(currentCard!!.did))
        }
        if (!prefShowTopbar) {
            topBarLayout!!.visibility = View.GONE
        }
    }

    override fun automaticShowQuestion(action: AutomaticAnswerAction) {
        // Assume hitting the "Again" button when auto next question
        easeButton1!!.performSafeClick()
    }

    override fun automaticShowAnswer() {
        if (flipCardLayout!!.isEnabled && flipCardLayout!!.isVisible) {
            flipCardLayout!!.performClick()
        }
    }

    private suspend fun automaticAnswerShouldWaitForMedia(): Boolean =
        withCol {
            decks.configDictForDeckId(currentCard!!.did).waitForAudio
        }

    internal inner class ReadTextListener : ReadText.ReadTextListener {
        override fun onDone(playedSide: CardSide?) {
            Timber.d("done reading text")
            this@AbstractFlashcardViewer.onMediaGroupCompleted()
        }
    }

    open fun displayCardQuestion() {
        Timber.d("displayCardQuestion()")
        displayAnswer = false
        backButtonPressedToReturn = false
        setInterface()
        typeAnswer?.input = ""
        typeAnswer?.updateInfo(getColUnsafe, currentCard!!, resources)
        if (typeAnswer?.validForEditText() == true) {
            // Show text entry based on if the user wants to write the answer
            answerField?.visibility = View.VISIBLE
            answerField?.applyLanguageHint(typeAnswer?.languageHint)
        } else {
            answerField?.visibility = View.GONE
        }
        val content = cardRenderContext!!.renderCard(getColUnsafe, currentCard!!, SingleCardSide.FRONT)
        automaticAnswer.onDisplayQuestion()
        launchCatchingTask {
            if (!automaticAnswerShouldWaitForMedia()) {
                automaticAnswer.scheduleAutomaticDisplayAnswer()
            }
        }
        updateCard(content)
        hideEaseButtons()
        // If Card-based TTS is enabled, we "automatic display" after the TTS has finished as we don't know the duration
        Timber.i(
            "AbstractFlashcardViewer:: Question successfully shown for card id %d",
            currentCard!!.id,
        )
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    open fun displayCardAnswer() {
        // #7294 Required in case the animation end action does not fire:
        actualHideEaseButtons()
        Timber.d("displayCardAnswer()")
        mediaErrorHandler.onCardSideChange()
        backButtonPressedToReturn = false

        // prevent answering (by e.g. gestures) before card is loaded
        if (currentCard == null) {
            return
        }

        // TODO needs testing: changing a card's model without flipping it back to the front
        //  (such as editing a card, then editing the card template)
        typeAnswer!!.updateInfo(getColUnsafe, currentCard!!, resources)

        // Explicitly hide the soft keyboard. It *should* be hiding itself automatically,
        // but sometimes failed to do so (e.g. if an OnKeyListener is attached).
        if (typeAnswer!!.validForEditText()) {
            val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            inputMethodManager.hideSoftInputFromWindow(answerField!!.windowToken, 0)
        }
        displayAnswer = true
        answerField!!.visibility = View.GONE
        // Clean up the user answer and the correct answer
        if (!typeAnswer!!.useInputTag) {
            typeAnswer!!.input = answerField!!.text.toString()
        }
        isSelecting = false
        val answerContent = cardRenderContext!!.renderCard(getColUnsafe, currentCard!!, SingleCardSide.BACK)
        automaticAnswer.onDisplayAnswer()
        launchCatchingTask {
            if (!automaticAnswerShouldWaitForMedia()) {
                automaticAnswer.scheduleAutomaticDisplayQuestion()
            }
        }
        updateCard(answerContent)
        displayAnswerBottomBar()
    }

    override fun scrollCurrentCardBy(dy: Int) {
        processCardAction { cardWebView: WebView? ->
            if (dy != 0 && cardWebView!!.canScrollVertically(dy)) {
                cardWebView.scrollBy(0, dy)
            }
        }
    }

    override fun tapOnCurrentCard(
        x: Int,
        y: Int,
    ) {
        // assemble suitable ACTION_DOWN and ACTION_UP events and forward them to the card's handler
        val eDown =
            MotionEvent.obtain(
                SystemClock.uptimeMillis(),
                SystemClock.uptimeMillis(),
                MotionEvent.ACTION_DOWN,
                x.toFloat(),
                y.toFloat(),
                1f,
                1f,
                0,
                1f,
                1f,
                0,
                0,
            )
        processCardAction { cardWebView: WebView? -> cardWebView!!.dispatchTouchEvent(eDown) }
        val eUp =
            MotionEvent.obtain(
                eDown.downTime,
                SystemClock.uptimeMillis(),
                MotionEvent.ACTION_UP,
                x.toFloat(),
                y.toFloat(),
                1f,
                1f,
                0,
                1f,
                1f,
                0,
                0,
            )
        processCardAction { cardWebView: WebView? -> cardWebView!!.dispatchTouchEvent(eUp) }
    }

    internal val isInNightMode: Boolean
        get() = Themes.isNightTheme

    private fun updateCard(content: RenderedCard) {
        Timber.d("updateCard()")
        // TODO: This doesn't need to be blocking
        runBlocking {
            cardMediaPlayer.loadCardAvTags(currentCard!!)
        }
        cardContent = content.html
        fillFlashcard()
        playMedia(false) // Play media if appropriate
    }

    /**
     * Plays media (or TTS, if configured) for currently shown side of card.
     *
     * @param doMediaReplay indicates an anki desktop-like replay call is desired, whose behavior is identical to
     * pressing the keyboard shortcut R on the desktop
     */
    @NeedsTest("media is not played if opExecuted occurs when viewer is in the background")
    protected open fun playMedia(doMediaReplay: Boolean) {
        // this can occur due to OpChanges when the viewer is on another screen
        if (!this.lifecycle.currentState.isAtLeast(RESUMED)) {
            Timber.w("media is not played as the activity is inactive")
            return
        }
        if (cardMediaPlayer.config?.autoplay != true && !doMediaReplay) return
        // Use TTS if TTS preference enabled and no other media source
        val useTTS = tts.enabled && !cardMediaPlayer.hasMedia(displayAnswer)
        // We need to play the media from the proper side of the card
        if (!useTTS) {
            launchCatchingTask {
                val side = if (displayAnswer) SingleCardSide.BACK else SingleCardSide.FRONT
                when (doMediaReplay) {
                    true -> cardMediaPlayer.replayAll(side)
                    false -> cardMediaPlayer.playAllForSide(side.toCardSide())
                }
            }
            return
        }

        val replayQuestion = cardMediaPlayer.config?.replayQuestion == true
        // Text to speech is in effect here
        // If the question is displayed or if the question should be replayed, read the question
        if (ttsInitialized) {
            if (!displayAnswer || (doMediaReplay && replayQuestion)) {
                readCardTts(SingleCardSide.FRONT)
            }
            if (displayAnswer) {
                readCardTts(SingleCardSide.BACK)
            }
        } else {
            replayOnTtsInit = true
        }
    }

    @VisibleForTesting
    fun readCardTts(side: SingleCardSide) {
        val tags = legacyGetTtsTags(getColUnsafe, currentCard!!, side)
        tts.readCardText(getColUnsafe, tags, currentCard!!, side.toCardSide())
    }

    /**
     * @see CardMediaPlayer.onMediaGroupCompleted
     */
    open fun onMediaGroupCompleted() {
        Timber.v("onMediaGroupCompleted")
        launchCatchingTask {
            if (automaticAnswerShouldWaitForMedia()) {
                if (isDisplayingAnswer) {
                    automaticAnswer.scheduleAutomaticDisplayQuestion()
                } else {
                    automaticAnswer.scheduleAutomaticDisplayAnswer()
                }
            }
        }
    }

    /**
     * Shows the dialogue for selecting TTS for the current card and cardside.
     */
    protected fun showSelectTtsDialogue() {
        if (ttsInitialized) {
            tts.selectTts(
                getColUnsafe,
                currentCard!!,
                if (displayAnswer) CardSide.ANSWER else CardSide.QUESTION,
            )
        }
    }

    open fun fillFlashcard() {
        Timber.d("fillFlashcard()")
        if (cardContent == null) {
            Timber.w("fillFlashCard() called with no card content")
            return
        }
        processCardAction { cardWebView: WebView? -> loadContentIntoCard(cardWebView, cardContent!!) }
        gestureDetectorImpl.onFillFlashcard()
        if (!displayAnswer) {
            updateForNewCard()
        }
    }

    private fun loadContentIntoCard(
        card: WebView?,
        content: String,
    ) {
        if (card != null) {
            card.settings.mediaPlaybackRequiresUserGesture = cardMediaPlayer.config?.autoplay != true
            card.loadDataWithBaseURL(
                server.baseUrl(),
                content,
                "text/html",
                null,
                null,
            )
        }
    }

    protected open fun unblockControls() {
        cardFrame!!.isEnabled = true
        flipCardLayout?.isEnabled = true
        easeButton1?.unblockBasedOnEase(currentEase)
        easeButton2?.unblockBasedOnEase(currentEase)
        easeButton3?.unblockBasedOnEase(currentEase)
        easeButton4?.unblockBasedOnEase(currentEase)
        if (typeAnswer?.validForEditText() == true) {
            answerField?.isEnabled = true
        }
        touchLayer?.visibility = View.VISIBLE
        inAnswer = false
        invalidateOptionsMenu()
    }

    fun buryCard(): Boolean {
        launchCatchingTask {
            withProgress {
                undoableOp {
                    sched.buryCards(listOf(currentCard!!.id))
                }
            }
            stopCardMediaPlayer()
            showSnackbar(R.string.card_buried, Reviewer.ACTION_SNACKBAR_TIME)
        }
        return true
    }

    @VisibleForTesting
    open fun suspendCard(): Boolean {
        launchCatchingTask {
            withProgress {
                undoableOp {
                    sched.suspendCards(listOf(currentCard!!.id))
                }
            }
            stopCardMediaPlayer()
            showSnackbar(TR.studyingCardSuspended(), Reviewer.ACTION_SNACKBAR_TIME)
        }
        return true
    }

    @VisibleForTesting
    open fun suspendNote(): Boolean {
        launchCatchingTask {
            val changed =
                withProgress {
                    undoableOp {
                        sched.suspendNotes(listOf(currentCard!!.nid))
                    }
                }
            val count = changed.count
            val noteSuspended = resources.getQuantityString(R.plurals.note_suspended, count, count)
            stopCardMediaPlayer()
            showSnackbar(noteSuspended, Reviewer.ACTION_SNACKBAR_TIME)
        }
        return true
    }

    @VisibleForTesting
    open fun buryNote(): Boolean {
        launchCatchingTask {
            val changed =
                withProgress {
                    undoableOp {
                        sched.buryNotes(listOf(currentCard!!.nid))
                    }
                }
            stopCardMediaPlayer()
            showSnackbar(TR.studyingCardsBuried(changed.count), Reviewer.ACTION_SNACKBAR_TIME)
        }
        return true
    }

    private suspend fun stopCardMediaPlayer() {
        cardMediaPlayer.stop()
        ReadText.stopTts()
    }

    override fun executeCommand(
        which: ViewerCommand,
        fromGesture: Gesture?,
    ): Boolean {
        return when (which) {
            ViewerCommand.SHOW_ANSWER -> {
                if (displayAnswer) {
                    return false
                }
                displayCardAnswer()
                true
            }

            ViewerCommand.ANSWER_AGAIN -> {
                flipOrAnswerCard(Rating.AGAIN)
                true
            }

            ViewerCommand.ANSWER_HARD -> {
                flipOrAnswerCard(Rating.HARD)
                true
            }

            ViewerCommand.ANSWER_GOOD -> {
                flipOrAnswerCard(Rating.GOOD)
                true
            }

            ViewerCommand.ANSWER_EASY -> {
                flipOrAnswerCard(Rating.EASY)
                true
            }

            ViewerCommand.EXIT -> {
                closeReviewer(RESULT_DEFAULT)
                true
            }

            ViewerCommand.UNDO -> {
                undo()
                true
            }

            ViewerCommand.EDIT -> {
                editCard(fromGesture)
                true
            }

            ViewerCommand.TAG -> {
                showTagsDialog()
                true
            }

            ViewerCommand.BURY_CARD -> buryCard()
            ViewerCommand.BURY_NOTE -> buryNote()
            ViewerCommand.SUSPEND_CARD -> suspendCard()
            ViewerCommand.SUSPEND_NOTE -> suspendNote()
            ViewerCommand.DELETE -> {
                showDeleteNoteDialog()
                true
            }

            ViewerCommand.PLAY_MEDIA -> {
                playMedia(true)
                true
            }

            ViewerCommand.PAGE_UP -> {
                onPageUp()
                true
            }

            ViewerCommand.PAGE_DOWN -> {
                onPageDown()
                true
            }

            ViewerCommand.RECORD_VOICE -> {
                recordVoice()
                true
            }

            ViewerCommand.SAVE_VOICE -> {
                saveRecording()
                true
            }

            ViewerCommand.REPLAY_VOICE -> {
                replayVoice()
                true
            }

            ViewerCommand.TOGGLE_WHITEBOARD -> {
                toggleWhiteboard()
                true
            }

            ViewerCommand.TOGGLE_ERASER -> {
                toggleEraser()
                true
            }

            ViewerCommand.CLEAR_WHITEBOARD -> {
                clearWhiteboard()
                true
            }

            ViewerCommand.CHANGE_WHITEBOARD_PEN_COLOR -> {
                changeWhiteboardPenColor()
                true
            }

            ViewerCommand.SHOW_HINT -> {
                loadUrlInViewer("javascript: showHint();")
                true
            }

            ViewerCommand.SHOW_ALL_HINTS -> {
                loadUrlInViewer("javascript: showAllHints();")
                true
            }
            ViewerCommand.REDO,
            ViewerCommand.MARK,
            ViewerCommand.TOGGLE_FLAG_RED,
            ViewerCommand.TOGGLE_FLAG_ORANGE,
            ViewerCommand.TOGGLE_FLAG_GREEN,
            ViewerCommand.TOGGLE_FLAG_BLUE,
            ViewerCommand.TOGGLE_FLAG_PINK,
            ViewerCommand.TOGGLE_FLAG_TURQUOISE,
            ViewerCommand.TOGGLE_FLAG_PURPLE,
            ViewerCommand.UNSET_FLAG,
            ViewerCommand.CARD_INFO,
            ViewerCommand.PREVIOUS_CARD_INFO,
            ViewerCommand.ADD_NOTE,
            ViewerCommand.RESCHEDULE_NOTE,
            ViewerCommand.TOGGLE_AUTO_ADVANCE,
            ViewerCommand.USER_ACTION_1,
            ViewerCommand.USER_ACTION_2,
            ViewerCommand.USER_ACTION_3,
            ViewerCommand.USER_ACTION_4,
            ViewerCommand.USER_ACTION_5,
            ViewerCommand.USER_ACTION_6,
            ViewerCommand.USER_ACTION_7,
            ViewerCommand.USER_ACTION_8,
            ViewerCommand.USER_ACTION_9,
            -> {
                Timber.w("Unknown command requested: %s", which)
                false
            }
        }
    }

    fun executeCommand(which: ViewerCommand): Boolean = executeCommand(which, fromGesture = null)

    protected open fun replayVoice() {
        // intentionally blank
    }

    protected open fun saveRecording() {
        // intentionally blank
    }

    protected open fun recordVoice() {
        // intentionally blank
    }

    protected open fun toggleWhiteboard() {
        // intentionally blank
    }

    protected open fun toggleEraser() {
        // intentionally blank
    }

    protected open fun clearWhiteboard() {
        // intentionally blank
    }

    protected open fun changeWhiteboardPenColor() {
        // intentionally blank
    }

    override val baseSnackbarBuilder: SnackbarBuilder = {
        // Configure the snackbar to avoid the bottom answer buttons.
        // The answer buttons are animated to GONE in fullscreen mode (see Reviewer.hideViewWithAnimation),
        // so check visibility to avoid anchoring the snackbar to a hidden view (#20946).
        if (answerButtonsPosition == "bottom") {
            anchorView = findViewById<View>(R.id.answer_options_layout)?.takeIf { it.isVisible }
        }
    }

    private fun onPageUp() {
        // pageUp performs a half scroll, we want a full page
        processCardAction { cardWebView: WebView? ->
            cardWebView!!.pageUp(false)
            cardWebView.pageUp(false)
        }
    }

    private fun onPageDown() {
        processCardAction { cardWebView: WebView? ->
            cardWebView!!.pageDown(false)
            cardWebView.pageDown(false)
        }
    }

    protected open fun performClickWithVisualFeedback(rating: Rating) {
        // Delay could potentially be lower - testing with 20 left a visible "click"
        when (rating) {
            Rating.AGAIN -> easeButton1!!.performClickWithVisualFeedback()
            Rating.HARD -> easeButton2!!.performClickWithVisualFeedback()
            Rating.GOOD -> easeButton3!!.performClickWithVisualFeedback()
            Rating.EASY -> easeButton4!!.performClickWithVisualFeedback()
            Rating.UNRECOGNIZED -> {}
        }
    }

    private fun showMinimalClickHint() {
        if (minimalClickPrefHintShown) {
            return
        }

        minimalClickPrefHintShown = true

        showSnackbar(
            getString(
                R.string.show_answer_hint_long_press,
                getString(R.string.pref_show_answer_long_press_time),
            ),
            minimalClickSpeed + Reviewer.ACTION_SNACKBAR_TIME,
        ) {
            setAction(R.string.settings) {
                val settingsIntent =
                    PreferencesActivity.getIntent(
                        this@AbstractFlashcardViewer,
                        AccessibilitySettingsFragment::class,
                    )
                startActivity(settingsIntent)
            }
        }
    }

    // ----------------------------------------------------------------------------
    // INNER CLASSES
    // ----------------------------------------------------------------------------

    /**
     * Provides a hook for calling "alert" from javascript. Useful for debugging your javascript.
     */
    inner class AnkiDroidWebChromeClient : WebChromeClient() {
        override fun onJsAlert(
            view: WebView,
            url: String,
            message: String,
            result: JsResult,
        ): Boolean {
            Timber.i("AbstractFlashcardViewer:: onJsAlert: %s", message)
            result.confirm()
            return true
        }

        private lateinit var customView: View

        override fun onPermissionRequest(request: PermissionRequest) {
            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE in request.resources) {
                Timber.i("Granting audio capture permission to WebView")
                request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
            } else {
                Timber.i("Denying permissions to WebView")
                request.deny()
            }
        }

        // used for displaying `<video>` in fullscreen.
        // This implementation requires configChanges="orientation" in the manifest
        // to avoid destroying the View if the device is rotated
        override fun onShowCustomView(
            paramView: View,
            paramCustomViewCallback: CustomViewCallback?,
        ) {
            customView = paramView
            (window.decorView as FrameLayout).addView(
                customView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
            // hide system bars
            with(WindowInsetsControllerCompat(window, window.decorView)) {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }

        override fun onHideCustomView() {
            (window.decorView as FrameLayout).removeView(customView)
            // show system bars back
            with(WindowInsetsControllerCompat(window, window.decorView)) {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    protected open fun closeReviewer(result: Int) {
        automaticAnswer.disable()
        previousAnswerIndicator!!.stopAutomaticHide()
        this@AbstractFlashcardViewer.setResult(result)
        finish()
    }

    protected fun refreshActionBar() {
        invalidateOptionsMenu()
    }

    /**
     * Re-renders the content inside the WebView, retaining the side of the card to render
     *
     * To be used if card/note data has changed
     *
     * @see updateCardAndRedraw - also calls [updateCurrentCard] and resets the side
     * @see refreshIfRequired - calls through to [updateCurrentCard]
     */
    private fun reloadWebViewContent() {
        currentCard?.renderOutput(getColUnsafe, reload = true, browser = false)
        if (!isDisplayingAnswer) {
            Timber.d("displayCardQuestion()")
            displayAnswer = false
            backButtonPressedToReturn = false
            setInterface()
            typeAnswer?.input = ""
            typeAnswer?.updateInfo(getColUnsafe, currentCard!!, resources)
            if (typeAnswer?.validForEditText() == true) {
                // Show text entry based on if the user wants to write the answer
                answerField?.visibility = View.VISIBLE
                answerField?.applyLanguageHint(typeAnswer?.languageHint)
            } else {
                answerField?.visibility = View.GONE
            }
            val content = cardRenderContext!!.renderCard(getColUnsafe, currentCard!!, SingleCardSide.FRONT)
            automaticAnswer.onDisplayQuestion()
            updateCard(content)
            hideEaseButtons()
            Timber.i(
                "AbstractFlashcardViewer:: Question successfully shown for card id %d",
                currentCard!!.id,
            )
        } else {
            displayCardAnswer()
        }
    }

    /** Fixing bug 720: <input></input> focus, thanks to pablomouzo on android issue 7189  */
    internal inner class MyWebView(
        context: Context?,
    ) : WebView(context!!) {
        override fun loadDataWithBaseURL(
            baseUrl: String?,
            data: String,
            mimeType: String?,
            encoding: String?,
            historyUrl: String?,
        ) {
            if (!this@AbstractFlashcardViewer.isDestroyed) {
                super.loadDataWithBaseURL(baseUrl, data, mimeType, encoding, historyUrl)
            } else {
                Timber.w("Not loading card - Activity is in the process of being destroyed.")
            }
        }

        override fun onScrollChanged(
            horiz: Int,
            vert: Int,
            oldHoriz: Int,
            oldVert: Int,
        ) {
            super.onScrollChanged(horiz, vert, oldHoriz, oldVert)
            if (abs(horiz - oldHoriz) > abs(vert - oldVert)) {
                isXScrolling = true
                scrollHandler.removeCallbacks(scrollXRunnable)
                scrollHandler.postDelayed(scrollXRunnable, 300)
            } else {
                isYScrolling = true
                scrollHandler.removeCallbacks(scrollYRunnable)
                scrollHandler.postDelayed(scrollYRunnable, 300)
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.action == MotionEvent.ACTION_DOWN) {
                val scrollParent = findScrollParent(this)
                scrollParent?.requestDisallowInterceptTouchEvent(true)
            }
            return super.onTouchEvent(event)
        }

        override fun onOverScrolled(
            scrollX: Int,
            scrollY: Int,
            clampedX: Boolean,
            clampedY: Boolean,
        ) {
            if (clampedX) {
                val scrollParent = findScrollParent(this)
                scrollParent?.requestDisallowInterceptTouchEvent(false)
            }
            super.onOverScrolled(scrollX, scrollY, clampedX, clampedY)
        }

        private fun findScrollParent(current: View): ViewParent? {
            val parent = current.parent ?: return null
            if (parent is FullDraggableContainer) {
                return parent
            } else if (parent is View) {
                return findScrollParent(parent as View)
            }
            return null
        }

        private val scrollHandler = newHandler()
        private val scrollXRunnable = Runnable { isXScrolling = false }
        private val scrollYRunnable = Runnable { isYScrolling = false }
    }

    internal open inner class MyGestureDetector : SimpleOnGestureListener() {
        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float,
        ): Boolean {
            Timber.d("onFling")

            // #5741 - A swipe from the top caused delayedHide to be triggered,
            // accepting a gesture and quickly disabling the status bar, which wasn't ideal.
            // it would be lovely to use e1.getEdgeFlags(), but alas, it doesn't work.
            if (e1 != null && isTouchingEdge(e1)) {
                Timber.d("ignoring edge fling")
                return false
            }

            // Go back to immersive mode if the user had temporarily exited it (and then execute swipe gesture)
            this@AbstractFlashcardViewer.onFling()
            if (e1 != null && gesturesEnabled) {
                try {
                    val dy = e2.y - e1.y
                    val dx = e2.x - e1.x
                    gestureProcessor.onFling(
                        dx,
                        dy,
                        velocityX,
                        velocityY,
                        isSelecting,
                        isXScrolling,
                        isYScrolling,
                    )
                } catch (e: Exception) {
                    Timber.e(e, "onFling Exception")
                }
            }
            return false
        }

        private fun isTouchingEdge(e1: MotionEvent): Boolean {
            val height = touchLayer!!.height
            val width = touchLayer!!.width
            val margin = NO_GESTURE_BORDER_DIP * resources.displayMetrics.density + 0.5f
            return e1.x < margin || e1.y < margin || height - e1.y < margin || width - e1.x < margin
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (gesturesEnabled) {
                gestureProcessor.onDoubleTap()
            }
            return true
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean = false

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            // Go back to immersive mode if the user had temporarily exited it (and ignore the tap gesture)
            if (onSingleTap()) {
                return true
            }
            executeTouchCommand(e)
            return false
        }

        protected open fun executeTouchCommand(e: MotionEvent) {
            if (gesturesEnabled && !isSelecting) {
                val height = touchLayer!!.height
                val width = touchLayer!!.width
                val posX = e.x
                val posY = e.y
                gestureProcessor.onTap(height, width, posX, posY)
            }
            isSelecting = false
        }

        open fun onWebViewCreated(webView: WebView) {
            // intentionally blank
        }

        open fun onFillFlashcard() {
            // intentionally blank
        }

        open fun eventCanBeSentToWebView(event: MotionEvent): Boolean = true

        open fun startShakeDetector() {
            // intentionally blank
        }

        open fun stopShakeDetector() {
            // intentionally blank
        }
    }

    protected open fun onSingleTap(): Boolean = false

    protected open fun onFling() {}

    /** #6141 - blocks clicking links from executing "touch" gestures.
     * COULD_BE_BETTER: Make base class static and move this out of the CardViewer  */
    internal inner class LinkDetectingGestureDetector :
        MyGestureDetector(),
        ShakeDetector.Listener {
        private var shakeDetector: AnkiShakeDetector? = null

        init {
            initShakeDetector()
        }

        private fun initShakeDetector() {
            Timber.d("Initializing shake detector")
            if (gestureProcessor.isBound(Gesture.SHAKE)) {
                shakeDetector =
                    AnkiShakeDetector
                        .createInstance(
                            context = this@AbstractFlashcardViewer,
                            listener = this@LinkDetectingGestureDetector,
                        )?.apply {
                            start()
                        }
            }
        }

        override fun stopShakeDetector() {
            shakeDetector?.stop()
            shakeDetector = null
        }

        override fun startShakeDetector() {
            if (shakeDetector == null) {
                initShakeDetector()
            }
        }

        /** A list of events to process when listening to WebView touches   */
        private val desiredTouchEvents = hashSetInit<MotionEvent>(2)

        /** A list of events we sent to the WebView (to block double-processing)  */
        private val dispatchedTouchEvents = hashSetInit<MotionEvent>(2)

        override fun hearShake() {
            gestureProcessor.onShake()
        }

        override fun onFillFlashcard() {
            Timber.d("Removing pending touch events for gestures")
            desiredTouchEvents.clear()
            dispatchedTouchEvents.clear()
        }

        override fun eventCanBeSentToWebView(event: MotionEvent): Boolean {
            // if we processed the event, we don't want to perform it again
            return !dispatchedTouchEvents.remove(event)
        }

        override fun executeTouchCommand(e: MotionEvent) {
            e.action = MotionEvent.ACTION_DOWN
            val upEvent = MotionEvent.obtainNoHistory(e)
            upEvent.action = MotionEvent.ACTION_UP

            // mark the events we want to process
            desiredTouchEvents.add(e)
            desiredTouchEvents.add(upEvent)

            // mark the events to can guard against double-processing
            dispatchedTouchEvents.add(e)
            dispatchedTouchEvents.add(upEvent)
            Timber.d("Dispatching touch events")
            processCardAction { cardWebView: WebView? ->
                if (cardWebView != null) {
                    cardWebView.dispatchTouchEvent(e)
                    cardWebView.dispatchTouchEvent(upEvent)
                } else {
                    Timber.w("AbstractFlashcardViewer:: cardWebView is null")
                }
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onWebViewCreated(webView: WebView) {
            Timber.d("Initializing WebView touch handler")
            webView.setOnTouchListener { webViewAsView: View, motionEvent: MotionEvent ->
                if (!desiredTouchEvents.remove(motionEvent)) {
                    return@setOnTouchListener false
                }

                // We need an associated up event so the WebView doesn't keep a selection
                // But we don't want to handle this as a touch event.
                if (motionEvent.action == MotionEvent.ACTION_UP) {
                    return@setOnTouchListener true
                }
                val cardWebView = webViewAsView as WebView
                val result: HitTestResult =
                    try {
                        cardWebView.hitTestResult
                    } catch (e: Exception) {
                        Timber.w(e, "Cannot obtain HitTest result")
                        return@setOnTouchListener true
                    }
                if (isLinkClick(result)) {
                    Timber.v("Detected link click - ignoring gesture dispatch")
                    return@setOnTouchListener true
                }
                Timber.v("Executing continuation for click type: %d", result.type)
                super.executeTouchCommand(motionEvent)
                true
            }
        }

        private fun isLinkClick(result: HitTestResult?): Boolean {
            if (result == null) {
                return false
            }
            val type = result.type
            return (
                type == HitTestResult.SRC_ANCHOR_TYPE ||
                    type == HitTestResult.SRC_IMAGE_ANCHOR_TYPE
            )
        }
    }

    /** Callback for when TTS has been initialized.  */
    fun ttsInitialized() {
        ttsInitialized = true
        if (replayOnTtsInit) {
            playMedia(true)
        }
    }

    protected open fun shouldDisplayMark(): Boolean = isMarked(getColUnsafe, currentCard!!.note(getColUnsafe))

    val writeLock: Lock
        get() = cardLock.writeLock()
    open var currentCard: Card? = null

    /** Refreshes the WebView after a crash  */
    fun destroyWebViewFrame() {
        // Destroy the current WebView (to ensure WebView is GCed).
        // Otherwise, we get the following error:
        // "crash wasn't handled by all associated webviews, triggering application crash"
        cardFrame!!.removeAllViews()
        cardFrameParent!!.removeView(cardFrame)
        // destroy after removal from the view - produces logcat warnings otherwise
        destroyWebView(webView)
        webView = null
        // inflate a new instance of mCardFrame
        cardFrame =
            inflateNewView<FrameLayout>(R.id.flashcard).apply {
                // 'recreateWebView' applies setRenderWorkaround so the hardware renderer remains
                // disabled if a user requests it
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
            }
        // Even with the above, I occasionally saw the above error. Manually trigger the GC.
        // I'll keep this line unless I see another crash, which would point to another underlying issue.
        System.gc()
    }

    fun recreateWebViewFrame() {
        // we need to add at index 0 so gestures still go through.
        cardFrameParent!!.addView(cardFrame, 0)
        recreateWebView()
    }

    /** Signals from a WebView represent actions with no parameters  */
    enum class Signal {
        /** A signal which we did not know how to handle  */
        SIGNAL_UNHANDLED,

        /** A known signal which should perform a noop  */
        SIGNAL_NOOP,
        TYPE_FOCUS,

        /** Tell the app that we no longer want to focus the WebView and should instead return keyboard focus to a
         * native answer input method.  */
        RELINQUISH_FOCUS,
        SHOW_ANSWER,
        ANSWER_ORDINAL_1,
        ANSWER_ORDINAL_2,
        ANSWER_ORDINAL_3,
        ANSWER_ORDINAL_4,
        ;

        companion object {
            fun String.toSignal(): Signal {
                when (this) {
                    "signal:typefocus" -> return TYPE_FOCUS
                    "signal:relinquishFocus" -> return RELINQUISH_FOCUS
                    "signal:show_answer" -> return SHOW_ANSWER
                    "signal:answer_ease1" -> return ANSWER_ORDINAL_1
                    "signal:answer_ease2" -> return ANSWER_ORDINAL_2
                    "signal:answer_ease3" -> return ANSWER_ORDINAL_3
                    "signal:answer_ease4" -> return ANSWER_ORDINAL_4
                    else -> {}
                }
                if (this.startsWith("signal:answer_ease")) {
                    Timber.w("Unhandled signal: ease value: %s", this)
                    return SIGNAL_NOOP
                }
                return SIGNAL_UNHANDLED // unknown, or not a signal.
            }
        }
    }

    inner class CardViewerWebClient internal constructor(
        private val resourceHandler: ViewerResourceHandler,
        private val onPageFinishedCallback: OnPageFinishedCallback? = null,
    ) : WebViewClient(),
        JavascriptEvaluator {
        private var pageFinishedFired = true
        private val pageRenderStopwatch = Stopwatch.init("page render")

        @Deprecated("Deprecated in Java") // still needed for API 23
        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
            Timber.d("Obtained URL from card: '%s'", url)
            return filterUrl(url)
        }

        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest,
        ): Boolean {
            val url = request.url.toString()
            Timber.d("Obtained URL from card: '%s'", url)
            return filterUrl(url)
        }

        override fun onPageStarted(
            view: WebView?,
            url: String?,
            favicon: Bitmap?,
        ) {
            pageRenderStopwatch.reset()
            pageFinishedFired = false
        }

        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest,
        ): WebResourceResponse? {
            resourceHandler.shouldInterceptRequest(request)?.let { return it }
            return null
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError,
        ) {
            super.onReceivedError(view, request, error)
            mediaErrorHandler.processFailure(request) { filename: String ->
                displayCouldNotFindMediaSnackbar(
                    filename,
                )
            }
        }

        override fun onReceivedHttpError(
            view: WebView,
            request: WebResourceRequest,
            errorResponse: WebResourceResponse,
        ) {
            super.onReceivedHttpError(view, request, errorResponse)
            mediaErrorHandler.processFailure(request) { filename: String ->
                displayCouldNotFindMediaSnackbar(
                    filename,
                )
            }
        }

        // Filter any links using the custom "playsound" protocol defined in Sound.java.
        // We play sounds through these links when a user taps the sound icon.
        @NeedsTest("integration test with typechangetext")
        fun filterUrl(url: String): Boolean {
            if (url.startsWith("playsound:")) {
                launchCatchingTask {
                    controlMedia(url)
                }
                return true
            }
            if (url.startsWith("missing-user-action:")) {
                val actionNumber = url.substringAfter(":")
                val message = getString(R.string.missing_user_action_dialog_message, actionNumber)
                Timber.i("showing 'missing user action' dialog")
                AlertDialog.Builder(this@AbstractFlashcardViewer).show {
                    setTitle(R.string.vague_error)
                    setMessage(message)
                    setPositiveButton(R.string.dialog_ok) { _, _ -> }
                    setNeutralButton(R.string.help) { _, _ ->
                        openUrl(R.string.link_user_actions_help)
                    }
                }
                return true
            }
            if (url.startsWith("videoended:")) {
                // note: 'q:0' is provided
                cardMediaPlayer.onVideoFinished()
                return true
            }
            if (url.startsWith("videopause:")) {
                // note: 'q:0' is provided
                cardMediaPlayer.onVideoPaused()
                return true
            }
            if (url.startsWith("state-mutation-error:")) {
                onStateMutationError()
                return true
            }
            if (url.startsWith("tts-voices:")) {
                Timber.i("showing TTS Voices fragment")
                showDialogFragment(TtsVoicesDialogFragment())
                return true
            }
            if (url.startsWith("file") || url.startsWith("data:")) {
                return false // Let the webview load files, i.e. local images.
            }
            if (url.startsWith("typechangetext:")) {
                // Store the text the javascript has sent us…
                typeAnswer!!.input = decodeUrl(url.replaceFirst("typechangetext:".toRegex(), ""))
                return true
            }
            if (url.startsWith("typeentertext:")) {
                // Store the text the javascript has send us…
                typeAnswer!!.input = decodeUrl(url.replaceFirst("typeentertext:".toRegex(), ""))
                // … and show the answer.
                flipCardLayout!!.performClick()
                return true
            }

            // card.html reload
            if (url.startsWith("signal:reload_card_html")) {
                redrawCard()
                return true
            }

            when (url.toSignal()) {
                Signal.SIGNAL_UNHANDLED -> {}
                Signal.SIGNAL_NOOP -> return true
                Signal.TYPE_FOCUS -> return true

                Signal.RELINQUISH_FOCUS -> {
                    // #5811 - The WebView could be focused via mouse. Allow components to return focus to Android.
                    focusAnswerCompletionField()
                    return true
                }

                Signal.SHOW_ANSWER -> {
                    // display answer when showAnswer() called from card.js
                    if (!displayAnswer) {
                        displayCardAnswer()
                    }
                    return true
                }

                Signal.ANSWER_ORDINAL_1 -> {
                    flipOrAnswerCard(Rating.AGAIN)
                    return true
                }

                Signal.ANSWER_ORDINAL_2 -> {
                    flipOrAnswerCard(Rating.HARD)
                    return true
                }

                Signal.ANSWER_ORDINAL_3 -> {
                    flipOrAnswerCard(Rating.GOOD)
                    return true
                }

                Signal.ANSWER_ORDINAL_4 -> {
                    flipOrAnswerCard(Rating.EASY)
                    return true
                }
            }
            var intent: Intent? = null
            try {
                if (url.startsWith("intent:")) {
                    intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                } else if (url.startsWith("android-app:")) {
                    intent = Intent.parseUri(url, Intent.URI_ANDROID_APP_SCHEME)
                }
                if (intent != null) {
                    Timber.i("Launching user-defined intent")
                    if (packageManager.resolveActivityCompat(
                            intent,
                            ResolveInfoFlagsCompat.EMPTY,
                        ) == null
                    ) {
                        val packageName = intent.getPackage()
                        if (packageName == null) {
                            Timber.d(
                                "Not using resolved intent uri because not available: %s",
                                intent,
                            )
                            intent = null
                        } else {
                            Timber.d(
                                "Resolving intent uri to market uri because not available: %s",
                                intent,
                            )
                            intent =
                                Intent(
                                    Intent.ACTION_VIEW,
                                    "market://details?id=$packageName".toUri(),
                                )
                            if (packageManager.resolveActivityCompat(
                                    intent,
                                    ResolveInfoFlagsCompat.EMPTY,
                                ) == null
                            ) {
                                intent = null
                            }
                        }
                    } else {
                        // https://developer.chrome.com/multidevice/android/intents says that we should remove this
                        intent.addCategory(Intent.CATEGORY_BROWSABLE)
                    }
                }
            } catch (t: Throwable) {
                Timber.w("Unable to parse intent uri: %s because: %s", url, t.message)
            }
            if (intent == null) {
                Timber.d("Opening external link \"%s\" with an Intent", url)
                intent = Intent(Intent.ACTION_VIEW, url.toUri())
            } else {
                Timber.d("Opening resolved external link \"%s\" with an Intent: %s", url, intent)
            }
            try {
                startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                Timber.w("No app found to handle open external url from AbstractFlashcardViewer")
                showSnackbar(R.string.activity_start_failed)
            }
            return true
        }

        /**
         * Check if the user clicked on another audio icon or the audio itself finished
         * Also, Check if the user clicked on the running audio icon
         * @param url
         */
        @NeedsTest("14221: 'playsound' should play the sound from the start")
        private suspend fun controlMedia(url: String) {
            val avTag =
                when (val tag = currentCard?.let { getAvTag(it, url) }) {
                    is SoundOrVideoTag -> tag
                    is TTSTag -> tag
                    // not currently supported
                    null -> return
                }
            cardMediaPlayer.playOne(avTag)
        }

        // Run any post-load events in javascript that rely on the window being completely loaded.
        override fun onPageFinished(
            view: WebView,
            url: String,
        ) {
            if (pageFinishedFired) {
                return
            }
            pageFinishedFired = true
            pageRenderStopwatch.logElapsed()
            Timber.d("Java onPageFinished triggered: %s", url)
            // onPageFinished will be called multiple times if the WebView redirects by setting window.location.href
            onPageFinishedCallback?.onPageFinished(view)
            view.loadUrl("javascript:onPageFinished();")
            // focus keyboard automatically only when if it has inputTag and focus set to true
            val autoFocus = typeAnswer!!.useInputTag && typeAnswer!!.autoFocus
            if (autoFocus) {
                view.requestFocus()
            }
        }

        @RequiresApi(Build.VERSION_CODES.O)
        override fun onRenderProcessGone(
            view: WebView,
            detail: RenderProcessGoneDetail,
        ): Boolean = onRenderProcessGoneDelegate.onRenderProcessGone(view, detail)

        override fun eval(js: String) {
            // WARNING: it is not guaranteed that card.js has loaded at this point
            // even if `evaluateAfterDOMContentLoaded` is called
            runOnUiThread { webView!!.evaluateJavascript(js, null) }
        }
    }

    fun decodeUrl(url: String): String {
        try {
            return URLDecoder.decode(url, "UTF-8")
        } catch (e: UnsupportedEncodingException) {
            Timber.e(e, "UTF-8 isn't supported as an encoding?")
        } catch (e: Exception) {
            Timber.e(e, "Exception decoding: '%s'", url)
            showThemedToast(
                this@AbstractFlashcardViewer,
                getString(R.string.card_viewer_url_decode_error),
                true,
            )
        }
        return ""
    }

    protected open fun onStateMutationError() {
        Timber.w("state mutation error, see console log")
    }

    internal fun displayCouldNotFindMediaSnackbar(filename: String?) {
        showSnackbar(getString(R.string.card_viewer_could_not_find_image, filename)) {
            setAction(R.string.help) { openUrl(R.string.link_faq_missing_media) }
        }
    }

    @SuppressLint("WebViewApiAvailability")
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    fun handleUrlFromJavascript(url: String) {
        webViewClient?.filterUrl(url) ?: throw IllegalStateException("Couldn't obtain WebView - maybe it wasn't created yet")
    }

    val isDisplayingAnswer
        get() = displayAnswer

    internal fun showTagsDialog() {
        Timber.i("opening tags dialog")
        val noteId = currentCard!!.note(getColUnsafe).id
        val dialog =
            tagsDialogFactory!!
                .newTagsDialog()
                .withArguments(this, TagsDialog.DialogType.EDIT_TAGS, noteIds = listOf(noteId))
        showDialogFragment(dialog)
    }

    override fun onSelectedTags(
        selectedTags: List<String>,
        indeterminateTags: List<String>,
        stateFilter: CardStateFilter,
    ) {
        launchCatchingTask {
            val note = withCol { currentCard!!.note(this@withCol) }
            if (note.tags == selectedTags) return@launchCatchingTask

            withCol { note.setTagsFromStr(this@withCol, selectedTags.joinToString(" ")) }
            undoableOp { updateNote(note) }
            // Reload current card to reflect tag changes
            reloadWebViewContent()
        }
    }

    override fun opExecuted(
        changes: OpChanges,
        handler: Any?,
    ) {
        if (handler === this) return
        refreshRequired = ViewerRefresh.updateState(refreshRequired, changes)
        refreshIfRequired()
    }

    open fun getCardDataForJsApi(): AnkiDroidJsAPI.CardDataForJsApi = AnkiDroidJsAPI.CardDataForJsApi()

    override suspend fun handlePostRequest(
        uri: PostRequestUri,
        bytes: ByteArray,
    ): ByteArray =
        uri.jsApiMethodName?.let { methodName ->
            jsApi.handleJsApiRequest(
                methodName,
                bytes,
                returnDefaultValues = true,
            )
        } ?: throw IllegalArgumentException("unhandled request: $uri")

    companion object {
        /**
         * Result codes that are returned when this activity finishes.
         */
        const val RESULT_DEFAULT = 50
        const val RESULT_NO_MORE_CARDS = 52

        /**
         * Time to wait in milliseconds before resuming fullscreen mode
         *
         * Should be protected, using non-JVM static members protected in the superclass companion is unsupported yet
         */
        const val INITIAL_HIDE_DELAY = 200
        internal var displayAnswer = false
        const val DEFAULT_DOUBLE_TAP_TIME_INTERVAL = 200

        /** Handle providing help for "Image Not Found"  */
        internal val mediaErrorHandler = MediaErrorHandler()

        // Android design spec for the size of the status bar.
        private const val NO_GESTURE_BORDER_DIP = 24

        // maximum screen distance from initial touch where we will consider a click related to the touch
        private const val CLICK_ACTION_THRESHOLD = 200

        private var minimalClickPrefHintShown = false

        /**
         * @return if [gesture] is a swipe, a transition to the same direction of the swipe
         * else return [ActivityTransitionAnimation.Direction.FADE]
         */
        fun getAnimationTransitionFromGesture(gesture: Gesture?): ActivityTransitionAnimation.Direction =
            when (gesture) {
                Gesture.SWIPE_UP -> ActivityTransitionAnimation.Direction.UP
                Gesture.SWIPE_DOWN -> ActivityTransitionAnimation.Direction.DOWN
                Gesture.SWIPE_RIGHT -> ActivityTransitionAnimation.Direction.RIGHT
                Gesture.SWIPE_LEFT -> ActivityTransitionAnimation.Direction.LEFT
                else -> ActivityTransitionAnimation.Direction.FADE
            }

        fun Gesture?.toAnimationTransition() = getAnimationTransitionFromGesture(this)

        /**
         * @param mediaDir media directory path on SD card
         * @return path converted to file URL, properly UTF-8 URL encoded
         */
        fun getMediaBaseUrl(mediaDir: File): String {
            // Use android.net.Uri class to ensure whole path is properly encoded
            // File.toURL() does not work here, and URLEncoder class is not directly usable
            // with existing slashes
            if (mediaDir.absolutePath.isNotEmpty()) {
                val mediaDirUri = Uri.fromFile(mediaDir)
                return "$mediaDirUri/"
            }
            return ""
        }

        fun getCardMediaPlayerInstance(viewer: AbstractFlashcardViewer): CardMediaPlayer {
            val soundErrorListener = viewer.createMediaErrorListener()

            return CardMediaPlayer(
                javascriptEvaluator = { viewer.webViewClient?.eval(it) },
                mediaErrorListener = soundErrorListener,
            ).apply {
                setOnMediaGroupCompletedListener(viewer::onMediaGroupCompleted)
            }
        }

        fun AbstractFlashcardViewer.createMediaErrorListener(): MediaErrorListener {
            val activity = this
            return object : MediaErrorListener {
                override fun onMediaPlayerError(
                    mp: MediaPlayer?,
                    which: Int,
                    extra: Int,
                    uri: Uri,
                ): MediaErrorBehavior {
                    Timber.w("Media Error: (%d, %d)", which, extra)
                    return onError(uri)
                }

                override fun onTtsError(
                    error: TtsPlayer.TtsError,
                    isAutomaticPlayback: Boolean,
                ) {
                    mediaErrorHandler.processTtsFailure(error, isAutomaticPlayback) {
                        when (error) {
                            is AndroidTtsError.MissingVoiceError ->
                                TtsPlaybackErrorDialog.ttsPlaybackErrorDialog(activity, supportFragmentManager, error.tag)
                            is AndroidTtsError.InvalidVoiceError ->
                                activity.showSnackbar(getString(R.string.voice_not_supported))
                            else -> activity.showSnackbar(error.localizedErrorMessage(activity))
                        }
                    }
                }

                override fun onError(uri: Uri): MediaErrorBehavior {
                    if (uri.scheme != "file") {
                        return CONTINUE_MEDIA
                    }

                    try {
                        val file = uri.toFile()
                        // There is a multitude of transient issues with the MediaPlayer. (1, -1001) for example
                        // Retrying fixes most of these
                        if (file.exists()) return RETRY_MEDIA
                        // just doesn't exist - process the error
                        mediaErrorHandler.processMissingMedia(
                            file,
                        ) { filename: String? -> displayCouldNotFindMediaSnackbar(filename) }
                        return CONTINUE_MEDIA
                    } catch (e: Exception) {
                        Timber.w(e)
                        return CONTINUE_MEDIA
                    }
                }
            }
        }
    }
}
