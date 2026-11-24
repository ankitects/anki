// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.reviewer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.ActionMenuView
import androidx.compose.runtime.getValue
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CommonString
import com.ichi2.anki.DispatchKeyEventListener
import com.ichi2.anki.Flag
import com.ichi2.anki.R
import com.ichi2.anki.android.AnkiShakeDetector
import com.ichi2.anki.cardviewer.Gesture
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.common.destinations.DeckOptionsDestination
import com.ichi2.anki.common.destinations.navigate
import com.ichi2.anki.common.utils.android.isRobolectric
import com.ichi2.anki.common.utils.dp
import com.ichi2.anki.databinding.FragmentReviewerBinding
import com.ichi2.anki.dialogs.showDeckOptionsSelectionDialog
import com.ichi2.anki.dialogs.tags.TagsDialogFactory
import com.ichi2.anki.dialogs.tags.TagsDialogListener
import com.ichi2.anki.model.CardStateFilter
import com.ichi2.anki.preferences.reviewer.ViewerAction
import com.ichi2.anki.preferences.reviewer.WhiteboardAction
import com.ichi2.anki.previewer.CardViewerActivity
import com.ichi2.anki.previewer.CardViewerFragment
import com.ichi2.anki.previewer.TypeAnswer
import com.ichi2.anki.previewer.setFrameStyle
import com.ichi2.anki.previewer.stdHtml
import com.ichi2.anki.reviewer.BindingMap
import com.ichi2.anki.reviewer.ReviewerBinding
import com.ichi2.anki.scheduling.ForgetCardsDialog
import com.ichi2.anki.scheduling.SetDueDateDialog
import com.ichi2.anki.scheduling.registerOnForgetHandler
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.settings.enums.FrameStyle
import com.ichi2.anki.settings.enums.HideSystemBars
import com.ichi2.anki.settings.enums.ToolbarPosition
import com.ichi2.anki.snackbar.BaseSnackbarBuilderProvider
import com.ichi2.anki.snackbar.SnackbarBuilder
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.ui.windows.reviewer.audiorecord.CheckPronunciationFragment
import com.ichi2.anki.ui.windows.reviewer.whiteboard.WhiteboardFragment
import com.ichi2.anki.utils.CollectionPreferences
import com.ichi2.anki.utils.ext.collectInViewLifecycle
import com.ichi2.anki.utils.ext.sharedPrefs
import com.ichi2.anki.utils.ext.showDialogFragment
import com.ichi2.anki.utils.ext.window
import com.ichi2.anki.workarounds.SafeWebViewLayout
import com.ichi2.compose.theme.AnkiDroidTheme
import com.ichi2.themes.Themes
import com.ichi2.ui.TypeAnswerEditText
import com.ichi2.utils.ViewGroupUtils.setRenderWorkaround
import com.ichi2.utils.show
import com.squareup.seismic.ShakeDetector
import dev.androidbroadcast.vbpd.viewBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.reflect.jvm.jvmName
import kotlin.time.Duration.Companion.milliseconds

class ReviewerFragment :
    CardViewerFragment(R.layout.fragment_reviewer),
    BaseSnackbarBuilderProvider,
    ActionMenuView.OnMenuItemClickListener,
    DispatchKeyEventListener,
    TagsDialogListener,
    ShakeDetector.Listener {
    override val viewModel: ReviewerViewModel by viewModels()

    @VisibleForTesting
    internal val binding by viewBinding(FragmentReviewerBinding::bind)

    override val webViewLayout: SafeWebViewLayout get() = binding.webViewLayout
    private lateinit var bindingMap: BindingMap<ReviewerBinding, ViewerAction>
    private lateinit var reviewerWebViewClient: ReviewerWebViewClient
    private var shakeDetector: AnkiShakeDetector? = null
    private val whiteboardFragment get() = childFragmentManager.findFragmentByTag(WhiteboardFragment::class.jvmName) as? WhiteboardFragment
    private val isBigScreen: Boolean get() = resources.configuration.smallestScreenWidthDp >= 720

    override val baseSnackbarBuilder: SnackbarBuilder = {
        anchorView =
            when {
                binding.typeAnswerContainer.isVisible -> binding.typeAnswerContainer
                binding.answerArea.isVisible -> binding.answerArea
                Prefs.toolbarPosition == ToolbarPosition.BOTTOM -> binding.toolsLayout
                else -> null
            }
    }

    private lateinit var tagsDialogFactory: TagsDialogFactory

    override fun onLoadInitialHtml(): String =
        stdHtml(
            context = requireContext(),
            extraJsAssets = listOf("scripts/ankidroid-reviewer.js", "scripts/type-answer.js"),
            nightMode = Themes.isNightTheme,
        )

    override fun onStart() {
        super.onStart()
        shakeDetector?.start()
    }

    override fun onStop() {
        super.onStop()
        if (!requireActivity().isChangingConfigurations) {
            viewModel.stopAutoAdvance()
        }
        shakeDetector?.stop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tagsDialogFactory = TagsDialogFactory(this).attachToActivity<TagsDialogFactory>(requireActivity())
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        binding.backButton.setOnClickListener {
            requireActivity().finish()
        }

        setupBindings()
        setupImmersiveMode()
        setupTypeAnswer()
        setupAnswerButtons()
        setupCounts()
        setupMenu()
        setupToolbarPosition()
        setupAnswerTimer()
        setupMargins()
        setupResetProgress()
        setupCheckPronunciation()
        setupActions()
        setupWhiteboard()
        setupTimebox()

        val viewScope = viewLifecycleOwner.lifecycleScope

        viewModel.finishResultFlow
            .onEach { result ->
                requireActivity().run {
                    setResult(result)
                    finish()
                }
            }.launchIn(viewScope)

        viewModel.statesMutationEvalFlow
            .onEach { eval ->
                // Completion is signaled by `statesMutated`
                webViewLayout.evaluateJavascript(eval) { result ->
                    // eval failed, usually a syntax error
                    // Note: this is `"null"`, not null
                    if ("null" == result) {
                        viewModel.onStateMutationCallback()
                    }
                }
            }.launchIn(viewScope)

        viewModel.showingAnswer
            .onEach { showingAnswer ->
                // reset zoom if flipped
                if (showingAnswer) reviewerWebViewClient.resetZoom()
                // focus on the whole layout so motion controllers can be captured
                // without navigating the other View elements
                binding.rootLayout.requestFocus()
            }.launchIn(viewScope)

        viewModel.onCardUpdatedFlow
            .onEach {
                // reset zoom if the card changes (incl. bury/suspend)
                reviewerWebViewClient.resetZoom()
                binding.typeAnswerEditText.text = null
                whiteboardFragment?.resetCanvas()
            }.launchIn(viewScope)

        viewModel.navigateFlow.collectInViewLifecycle { destination ->
            if (destination is DeckOptionsDestination && destination.options.size > 1) {
                requireContext().showDeckOptionsSelectionDialog(destination.options) { selectedOption ->
                    Timber.i("Deck options target selected: ${selectedOption.deckId}")
                    navigate(
                        destination.copy(
                            deckId = selectedOption.deckId,
                            isFiltered = selectedOption.isFiltered,
                        ),
                    )
                }
                return@collectInViewLifecycle
            }
            navigate(destination)
        }

        binding.webViewContainer.setFrameStyle()

        if (Prefs.showAnswerFeedback) {
            viewModel.answerFeedbackFlow.collectInViewLifecycle { rating ->
                binding.answerFeedback.show(rating)
            }
        }

        if (Prefs.keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        setRenderWorkaround(requireActivity())
    }

    override fun onWebViewRecreated() {
        super.onWebViewRecreated()
        setRenderWorkaround(requireActivity())
    }

    private fun setupTypeAnswer() {
        binding.typeAnswerEditText.apply {
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    viewModel.onShowAnswer()
                    return@setOnEditorActionListener true
                }
                false
            }
            setOnFocusChangeListener { editTextView, hasFocus ->
                val insetsController = WindowInsetsControllerCompat(window, editTextView)
                if (hasFocus) {
                    insetsController.show(WindowInsetsCompat.Type.ime())
                } else {
                    insetsController.hide(WindowInsetsCompat.Type.ime())
                }
            }
        }

        val isHtmlTypeAnswerEnabled = Prefs.isHtmlTypeAnswerEnabled
        val autoFocusTypeAnswer = Prefs.autoFocusTypeAnswer

        /**
         * Sync keyboard suggestions and `imeHintLocales` on the answer field to match
         * [typeInAnswer]. Returns `true` if anything changed (caller should `restartInput()`).
         *
         * @see TypeAnswerEditText.noSuggest
         */
        fun TypeAnswerEditText.syncTypeAnswerProperties(typeInAnswer: TypeAnswer): Boolean {
            var changed = false
            if (noSuggest != typeInAnswer.noSuggest) {
                noSuggest = typeInAnswer.noSuggest
                changed = true
            }
            if (imeHintLocales != typeInAnswer.imeHintLocales) {
                imeHintLocales = typeInAnswer.imeHintLocales
                changed = true
            }
            return changed
        }

        viewModel.typeAnswerFlow.collectInViewLifecycle { typeInAnswer ->
            if (typeInAnswer == null) {
                binding.typeAnswerContainer.isVisible = false
                return@collectInViewLifecycle
            }

            if (isHtmlTypeAnswerEnabled) {
                if (!autoFocusTypeAnswer) return@collectInViewLifecycle
                webViewLayout.focusOnWebView()
                // `evaluateJavascript()` doesn't trigger the IME unless the WebView
                // has been touched before, so ´loadUrl()` is used instead.
                webViewLayout.loadUrl("javascript:document.getElementById('typeans')?.focus();")
                return@collectInViewLifecycle
            }

            binding.typeAnswerContainer.isVisible = true
            binding.typeAnswerEditText.apply {
                if (syncTypeAnswerProperties(typeInAnswer)) {
                    context?.getSystemService<InputMethodManager>()?.restartInput(this)
                }
                if (autoFocusTypeAnswer) {
                    requestFocus()
                }
            }
        }

        viewModel.onTypedAnswerResultFlow.collectInViewLifecycle { request ->
            if (isHtmlTypeAnswerEnabled) {
                val script = """document.getElementById("typeans").value;"""
                webViewLayout.evaluateJavascript(script) { callback ->
                    // the retuned string comes with surrounding `"`, so remove it once
                    val typedAnswer = callback.removeSurrounding("\"")
                    request.complete(typedAnswer)
                }
            } else {
                val typedAnswer = binding.typeAnswerEditText.text.toString()
                request.complete(typedAnswer)
            }
        }
    }

    @NeedsTest("Whiteboard takes priority on key events")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (
            event.action != KeyEvent.ACTION_DOWN ||
            view?.let { binding.typeAnswerEditText }?.isFocused == true
        ) {
            return false
        }
        return whiteboardFragment?.dispatchKeyEvent(event) == true || bindingMap.onKeyDown(event)
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        Timber.v("ReviewerFragment::onMenuItemClick %s", item)
        if (item.hasSubMenu()) return false
        val action = ViewerAction.fromId(item.itemId)
        viewModel.executeAction(action)
        return true
    }

    @NeedsTest("Whiteboard takes priority on shake events")
    override fun hearShake() {
        // Sensor events still arrive while a dialog or another window covers the reviewer.
        if (view?.hasWindowFocus() != true) {
            Timber.d("Ignoring shake: reviewer window does not have focus")
            return
        }

        if (whiteboardFragment?.onScreenShake() != true) {
            bindingMap.onGesture(Gesture.SHAKE)
        }
    }

    private fun setupBindings() {
        bindingMap = BindingMap(sharedPrefs(), ViewerAction.entries, viewModel)
        binding.root.setOnGenericMotionListener { _, event ->
            bindingMap.onGenericMotionEvent(event)
        }
        if (isBound(Gesture.SHAKE)) {
            shakeDetector = AnkiShakeDetector.createInstance(requireContext(), this)
        }
    }

    private fun isBound(gesture: Gesture): Boolean =
        bindingMap.isBound(gesture) || BindingMap(sharedPrefs(), WhiteboardAction.entries).isBound(gesture)

    private fun setupAnswerButtons() {
        if (!Prefs.showAnswerButtons) {
            binding.answerArea.isVisible = false
            return
        }

        val insetsController = WindowInsetsControllerCompat(window, binding.rootLayout)
        viewModel.showingAnswer
            .onEach { isAnswerShown ->
                if (isAnswerShown) {
                    insetsController.hide(WindowInsetsCompat.Type.ime())
                }
            }.launchIn(viewLifecycleOwner.lifecycleScope)

        val showHardAndEasyButtons = !Prefs.hideHardAndEasyButtons
        val buttonsHeightPercentage = Prefs.newStudyScreenAnswerButtonSize
        val useThemeColors = Prefs.answerButtonsUseThemeColors
        binding.answerArea.setContent {
            AnkiDroidTheme {
                val isAnswerShown by viewModel.showingAnswer.collectAsStateWithLifecycle()
                val nextTimes by viewModel.answerButtonsNextTimeFlow.collectAsStateWithLifecycle()
                AnswerArea(
                    isAnswerShown = isAnswerShown,
                    nextTimes = nextTimes,
                    onRatingClicked = { viewModel.answerCard(it) },
                    onShowAnswerClicked = { viewModel.onShowAnswer() },
                    showHardAndEasyButtons = showHardAndEasyButtons,
                    buttonsHeightPercentage = buttonsHeightPercentage,
                    useThemeColors = useThemeColors,
                )
            }
        }
    }

    private fun setupCounts() {
        viewModel.countsFlow.collectInViewLifecycle { counts ->
            binding.studyCounts.updateCounts(counts)
        }

        lifecycleScope.launch {
            if (!CollectionPreferences.getShowRemainingDueCounts()) {
                binding.studyCounts.isVisible = false
            }
        }
    }

    private fun setupMenu() {
        binding.reviewerMenuView.apply {
            setup(lifecycle, viewModel)
            setOnMenuItemClickListener(this@ReviewerFragment)
        }
    }

    private fun setupImmersiveMode() {
        val barsToHide =
            when (Prefs.hideSystemBars) {
                HideSystemBars.NONE -> return
                HideSystemBars.STATUS_BAR -> WindowInsetsCompat.Type.statusBars()
                HideSystemBars.NAVIGATION_BAR -> WindowInsetsCompat.Type.navigationBars()
                HideSystemBars.ALL -> WindowInsetsCompat.Type.systemBars()
            }

        val window = requireActivity().window
        with(WindowInsetsControllerCompat(window, window.decorView)) {
            hide(barsToHide)
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val minTopPadding =
            if (Prefs.frameStyle == FrameStyle.CARD && Prefs.toolbarPosition != ToolbarPosition.TOP) {
                8F.dp.toPx(requireContext())
            } else {
                0
            }
        val ignoreDisplayCutout = Prefs.ignoreDisplayCutout
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val defaultTypes = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            val typeMask =
                if (ignoreDisplayCutout) {
                    defaultTypes
                } else {
                    defaultTypes or WindowInsetsCompat.Type.displayCutout()
                }
            val bars = insets.getInsets(typeMask)
            // don't let a 'card' frame reach the top of the screen
            val topPadding = max(bars.top, minTopPadding)
            v.updatePadding(
                left = bars.left,
                top = topPadding,
                right = bars.right,
                bottom = bars.bottom,
            )
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun setupToolbarPosition() {
        when (Prefs.toolbarPosition) {
            ToolbarPosition.TOP -> return
            ToolbarPosition.NONE -> binding.toolsLayout.isVisible = false
            ToolbarPosition.BOTTOM -> {
                binding.mainLayout.removeView(binding.toolsLayout)
                binding.mainLayout.addView(binding.toolsLayout)

                // Put the answer buttons inside the toolbar on big screens
                if (!isBigScreen || !Prefs.showAnswerButtons) return
                binding.bottomLayout.removeView(binding.answerArea)
                binding.toolsLayout.addView(binding.answerArea)
                binding.answerArea.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    width = 0
                    matchConstraintPercentWidth = 0.6F
                    matchConstraintMaxWidth = 480.dp.toPx(requireContext())
                    topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                    bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                    startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                    endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                }
                binding.reviewerMenuView.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    width = 0
                    matchConstraintPercentWidth = 0.16F
                    startToEnd = ConstraintLayout.LayoutParams.UNSET
                }
            }
        }
    }

    /**
     * Updates margins based on the possible combinations
     * of [Prefs.toolbarPosition], [Prefs.frameStyle] and `Hide answer buttons`
     */
    private fun setupMargins() {
        if (Prefs.toolbarPosition == ToolbarPosition.BOTTOM && !isBigScreen) {
            binding.bottomLayout.showDividers =
                LinearLayout.SHOW_DIVIDER_MIDDLE or LinearLayout.SHOW_DIVIDER_BEGINNING
        }
    }

    private fun setupAnswerTimer() {
        lifecycle.addObserver(viewModel.answerTimer)
        viewModel.answerTimer.state
            .onEach { state ->
                binding.timer.setup(state)
            }.launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun setupResetProgress() {
        viewModel.resetProgressFlow.collectInViewLifecycle {
            showDialogFragment(ForgetCardsDialog())
        }
        // TODO handle 'Reset progress' in the ViewModel instead of the activity, once
        //  a mechanism of showing a progress bar if the operation takes too long is implemented
        registerOnForgetHandler { listOf(viewModel.getCardId()) }
    }

    private fun setupCheckPronunciation() {
        viewModel.voiceRecorderEnabledFlow.collectInViewLifecycle { isEnabled ->
            if (isEnabled && binding.checkPronunciationContainer.getFragment<CheckPronunciationFragment?>() == null) {
                childFragmentManager.commit {
                    add(binding.checkPronunciationContainer.id, CheckPronunciationFragment())
                }
            }
            binding.checkPronunciationContainer.isVisible = isEnabled
        }
    }

    private fun setupWhiteboard() {
        childFragmentManager.registerFragmentLifecycleCallbacks(
            object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentViewCreated(
                    fm: FragmentManager,
                    f: Fragment,
                    v: View,
                    savedInstanceState: Bundle?,
                ) {
                    if (f !is WhiteboardFragment) return
                    f.setOnScrollByListener { y ->
                        webViewLayout.scrollVerticallyBy(y)
                    }
                }
            },
            false,
        )

        viewModel.whiteboardEnabledFlow.collectInViewLifecycle { isEnabled ->
            val existingFragment = whiteboardFragment
            childFragmentManager.commit {
                if (isEnabled) {
                    if (existingFragment != null) {
                        show(existingFragment)
                    } else {
                        val newFragment = WhiteboardFragment()
                        newFragment.gestureFallbackListener = { gesture -> bindingMap.onGesture(gesture) }
                        add(R.id.web_view_container, newFragment, WhiteboardFragment::class.jvmName)
                    }
                } else {
                    existingFragment?.let { hide(it) }
                }
            }
        }
    }

    private fun setupTimebox() {
        viewModel.timeBoxReachedFlow.collectInViewLifecycle { timebox ->
            Timber.i("ReviewerFragment: Timebox reached (reps %d - secs %d)", timebox.reps, timebox.secs)

            viewModel.stopAutoAdvance()

            val minutes = (timebox.secs / 60f).roundToInt()
            val message = CollectionManager.TR.studyingCardStudiedIn(timebox.reps) + " " + CollectionManager.TR.studyingMinute(minutes)

            AlertDialog.Builder(requireContext()).show {
                setTitle(CommonString.timebox_reached_title)
                setMessage(message)
                setPositiveButton(CollectionManager.TR.studyingContinue()) { _, _ ->
                    Timber.i("ReviewerFragment: Timebox 'Continue'")
                    viewModel.onPageFinished(false)
                }
                setNegativeButton(CollectionManager.TR.studyingFinish()) { _, _ ->
                    Timber.i("ReviewerFragment: Timebox 'Finish'")
                    requireActivity().finish()
                }
                setCancelable(false)
            }
        }
    }

    private fun setupActions() {
        viewModel.actionFeedbackFlow.collectInViewLifecycle { message ->
            showSnackbar(message, duration = 500)
        }

        viewModel.editNoteTagsFlow.collectInViewLifecycle { noteId ->
            tagsDialogFactory.show(requireActivity(), noteIds = listOf(noteId))
        }

        viewModel.setDueDateFlow.collectInViewLifecycle { cardId ->
            SetDueDateDialog.show(requireActivity(), listOf(cardId))
        }

        viewModel.pageUpFlow.collectInViewLifecycle {
            webViewLayout.pageUp()
        }

        viewModel.pageDownFlow.collectInViewLifecycle {
            webViewLayout.pageDown()
        }

        val repository = StudyScreenRepository()

        viewModel.isMarkedFlow.collectInViewLifecycle { isMarked ->
            if (!repository.isMarkShownInToolbar) {
                binding.markIcon.isVisible = isMarked
            }
        }
        val flagView = binding.flagIcon
        viewModel.flagFlow.collectInViewLifecycle { flag ->
            if (!repository.isFlagShownInToolbar) {
                if (flag == Flag.NONE) {
                    flagView.isVisible = false
                } else {
                    flagView.setImageDrawable(ContextCompat.getDrawable(requireContext(), flag.drawableRes))
                    flagView.isVisible = true
                }
            }
        }
    }

    override fun onSelectedTags(
        selectedTags: List<String>,
        indeterminateTags: List<String>,
        stateFilter: CardStateFilter,
    ) = viewModel.onEditedTags(selectedTags)

    override fun onCreateWebViewClient(savedInstanceState: Bundle?): CardViewerWebViewClient =
        ReviewerWebViewClient(savedInstanceState).also { reviewerWebViewClient = it }

    override fun onCreateWebChromeClient(): CardViewerWebChromeClient = ReviewerWebChromeClient()

    private inner class ReviewerWebViewClient(
        savedInstanceState: Bundle?,
    ) : CardViewerWebViewClient(savedInstanceState) {
        // Capture the fresh WebView's scale before any pinch zoom; this includes screen density.
        private val initialScale: Float = if (!isRobolectric) webViewLayout.scale else 1F
        private var scale: Float = initialScale
        private var isScrolling: Boolean = false
        private var isScrollingJob: Job? = null
        private val gestureParser by lazy {
            GestureParser(
                scope = lifecycleScope,
                isDoubleTapEnabled = bindingMap.isBound(Gesture.DOUBLE_TAP),
            )
        }
        private var hasShownUnsupportedFeatureWarning = false

        /** Resets pinch zoom, leaving the viewport untouched when already at the initial scale. */
        fun resetZoom() {
            val factor = initialScale / scale
            if (abs(factor - 1f) < 0.001f) return
            // zoomBy is asynchronous. Keep _showQuestion/_showAnswer queued until the renderer
            // has reset its visual viewport, otherwise zoom completion can undo answer anchoring.
            val pageScale = initialScale / resources.displayMetrics.density
            webViewLayout.evaluateJavascript("ankidroid.waitForZoomReset($pageScale);")
            webViewLayout.zoomBy(factor.coerceIn(0.01f, 100f))
        }

        init {
            webViewLayout.setOnScrollChangeListener { _, _, _, _, _ ->
                isScrolling = true
                isScrollingJob?.cancel()
                isScrollingJob =
                    lifecycleScope.launch {
                        delay(300.milliseconds)
                        isScrolling = false
                    }
            }
        }

        override fun handleUrl(
            webView: WebView,
            url: Uri,
        ): Boolean {
            return when (url.scheme) {
                "gesture" -> {
                    if (isScrolling) return true
                    gestureParser.parse(url, scale, webView) { gesture ->
                        if (gesture == null) return@parse
                        Timber.v("ReviewerFragment::onGesture %s", gesture)
                        // Stylus mode passes finger input through to the WebView. Whiteboard
                        // bindings still take priority while the whiteboard is visible.
                        if (whiteboardFragment?.takeIf { it.isVisible }?.onGesture(gesture) != true) {
                            bindingMap.onGesture(gesture)
                        }
                    }
                    true
                }
                "ankidroid" -> {
                    when (url.host) {
                        "show-answer" -> viewModel.onShowAnswer()
                    }
                    true
                }
                "signal" -> {
                    if (hasShownUnsupportedFeatureWarning) return true
                    hasShownUnsupportedFeatureWarning = true
                    AlertDialog.Builder(requireContext()).show {
                        setMessage(CommonString.feature_not_supported_by_study_screen)
                        setPositiveButton(CommonString.dialog_ok) { _, _ -> }
                    }
                    true
                }
                else -> super.handleUrl(webView, url)
            }
        }

        override fun onScaleChanged(
            view: WebView?,
            oldScale: Float,
            newScale: Float,
        ) {
            super.onScaleChanged(view, oldScale, newScale)
            scale = newScale
        }

        override fun onPageFinished(
            view: WebView?,
            url: String?,
        ) {
            super.onPageFinished(view, url)
            Prefs.cardZoom.let {
                if (it == 100) return@let
                val scale = it / 100.0
                val script = """document.body.style.zoom = `$scale`;"""
                view?.evaluateJavascript(script, null)
            }
        }
    }

    private inner class ReviewerWebChromeClient : CardViewerWebChromeClient() {
        override fun onHideCustomView() {
            val barsToHide = Prefs.hideSystemBars
            if (barsToHide == HideSystemBars.NONE) {
                super.onHideCustomView()
            } else {
                val window = requireActivity().window
                (window.decorView as FrameLayout).removeView(paramView)

                val barsToShowBack =
                    when (barsToHide) {
                        HideSystemBars.STATUS_BAR -> WindowInsetsCompat.Type.navigationBars()
                        HideSystemBars.NAVIGATION_BAR -> WindowInsetsCompat.Type.statusBars()
                        HideSystemBars.ALL, HideSystemBars.NONE -> return
                    }
                with(WindowInsetsControllerCompat(window, window.decorView)) {
                    show(barsToShowBack)
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            }
        }
    }

    companion object {
        fun getIntent(context: Context): Intent = CardViewerActivity.getIntent(context, ReviewerFragment::class)
    }
}
