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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.ActionMenuView
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import anki.scheduler.CardAnswer.Rating
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.DispatchKeyEventListener
import com.ichi2.anki.Flag
import com.ichi2.anki.R
import com.ichi2.anki.android.AnkiShakeDetector
import com.ichi2.anki.android.back.doubleBackPressCallback
import com.ichi2.anki.cardviewer.Gesture
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.common.utils.android.isRobolectric
import com.ichi2.anki.databinding.FragmentReviewerBinding
import com.ichi2.anki.dialogs.showDeckOptionsSelectionDialog
import com.ichi2.anki.dialogs.tags.TagsDialog
import com.ichi2.anki.dialogs.tags.TagsDialogFactory
import com.ichi2.anki.dialogs.tags.TagsDialogListener
import com.ichi2.anki.model.CardStateFilter
import com.ichi2.anki.pages.DeckOptionsDestination
import com.ichi2.anki.preferences.reviewer.ViewerAction
import com.ichi2.anki.previewer.CardViewerActivity
import com.ichi2.anki.previewer.CardViewerFragment
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
import com.ichi2.anki.utils.ext.collectIn
import com.ichi2.anki.utils.ext.collectLatestIn
import com.ichi2.anki.utils.ext.sharedPrefs
import com.ichi2.anki.utils.ext.showDialogFragment
import com.ichi2.anki.utils.ext.window
import com.ichi2.anki.workarounds.SafeWebViewLayout
import com.ichi2.themes.Themes
import com.ichi2.utils.dp
import com.ichi2.utils.show
import com.squareup.seismic.ShakeDetector
import dev.androidbroadcast.vbpd.viewBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.reflect.jvm.jvmName

class ReviewerFragment :
    CardViewerFragment(R.layout.fragment_reviewer),
    BaseSnackbarBuilderProvider,
    ActionMenuView.OnMenuItemClickListener,
    DispatchKeyEventListener,
    TagsDialogListener,
    ShakeDetector.Listener {
    override val viewModel: ReviewerViewModel by viewModels()
    private val binding by viewBinding(FragmentReviewerBinding::bind)

    override val webViewLayout: SafeWebViewLayout get() = binding.webViewLayout
    private lateinit var bindingMap: BindingMap<ReviewerBinding, ViewerAction>
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
            extraJsAssets = listOf("scripts/ankidroid-reviewer.js"),
            nightMode = Themes.isNightTheme,
        )

    override fun onStart() {
        super.onStart()
        if (!requireActivity().isChangingConfigurations) {
            shakeDetector?.start()
        }
    }

    override fun onStop() {
        super.onStop()
        if (!requireActivity().isChangingConfigurations) {
            viewModel.stopAutoAdvance()
            shakeDetector?.stop()
        }
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

        viewModel.finishResultFlow.collectIn(lifecycleScope) { result ->
            requireActivity().run {
                setResult(result)
                finish()
            }
        }

        viewModel.statesMutationEvalFlow.collectIn(lifecycleScope) { eval ->
            webViewLayout.evaluateJavascript(eval) {
                viewModel.onStateMutationCallback()
            }
        }

        viewModel.showingAnswer.collectIn(lifecycleScope) {
            resetZoom()
            // focus on the whole layout so motion controllers can be captured
            // without navigating the other View elements
            binding.rootLayout.requestFocus()
        }

        viewModel.destinationFlow.collectIn(lifecycleScope) { destination ->
            if (destination is DeckOptionsDestination && destination.options.size > 1) {
                requireContext().showDeckOptionsSelectionDialog(destination.options) { selectedOption ->
                    Timber.i("Deck options target selected: ${selectedOption.deckId}")
                    val updatedDestination =
                        destination.copy(
                            deckId = selectedOption.deckId,
                            isFiltered = selectedOption.isFiltered,
                        )
                    startActivity(updatedDestination.toIntent(requireContext()))
                }
                return@collectIn
            }
            startActivity(destination.toIntent(requireContext()))
        }

        binding.webViewContainer.setFrameStyle()

        if (Prefs.showAnswerFeedback) {
            viewModel.answerFeedbackFlow.collectIn(lifecycleScope) { ease ->
                val drawableId =
                    when (ease) {
                        Rating.AGAIN -> R.drawable.ic_ease_again
                        Rating.HARD -> R.drawable.ic_ease_hard
                        Rating.GOOD -> R.drawable.ic_ease_good
                        Rating.EASY -> R.drawable.ic_ease_easy
                        Rating.UNRECOGNIZED -> throw IllegalArgumentException("Invalid rating")
                    }
                binding.answerFeedback.apply {
                    setImageResource(drawableId)
                    toggle()
                }
            }
        }

        if (Prefs.keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
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
        lifecycleScope.launch {
            val autoFocusTypeAnswer = Prefs.autoFocusTypeAnswer
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.typeAnswerFlow.collect { typeInAnswer ->
                    if (typeInAnswer == null) {
                        binding.typeAnswerContainer.isVisible = false
                        return@collect
                    }

                    if (isHtmlTypeAnswerEnabled) {
                        if (!autoFocusTypeAnswer) return@collect
                        webViewLayout.focusOnWebView()
                        // `evaluateJavascript()` doesn't trigger the IME unless the WebView
                        // has been touched before, so ´loadUrl()` is used instead.
                        webViewLayout.loadUrl("javascript:document.getElementById('typeans')?.focus();")
                        return@collect
                    }

                    binding.typeAnswerContainer.isVisible = true
                    binding.typeAnswerEditText.apply {
                        if (imeHintLocales != typeInAnswer.imeHintLocales) {
                            imeHintLocales = typeInAnswer.imeHintLocales
                            context?.getSystemService<InputMethodManager>()?.restartInput(this)
                        }
                        if (autoFocusTypeAnswer) {
                            requestFocus()
                        }
                    }
                }
            }
        }
        viewModel.onCardUpdatedFlow.flowWithLifecycle(lifecycle).collectIn(lifecycleScope) {
            binding.typeAnswerEditText.text = null
        }

        viewModel.onTypedAnswerResultFlow
            .flowWithLifecycle(lifecycle)
            .collectIn(lifecycleScope) { request ->
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

    private fun resetZoom() {
        webViewLayout.settings.loadWithOverviewMode = false
        webViewLayout.settings.loadWithOverviewMode = true
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
        if (whiteboardFragment?.onScreenShake() != true) {
            bindingMap.onGesture(Gesture.SHAKE)
        }
    }

    private fun setupBindings() {
        bindingMap = BindingMap(sharedPrefs(), ViewerAction.entries, viewModel)
        binding.root.setOnGenericMotionListener { _, event ->
            bindingMap.onGenericMotionEvent(event)
        }
        if (bindingMap.isBound(Gesture.SHAKE)) {
            shakeDetector = AnkiShakeDetector.createInstance(requireContext(), this)
            shakeDetector?.start()
        }
    }

    private fun setupAnswerButtons() {
        if (!Prefs.showAnswerButtons) {
            binding.answerArea.isVisible = false
            return
        }

        binding.answerArea.setButtonListeners(
            onRatingClicked = { viewModel.answerCard(it) },
            onShowAnswerClicked = { viewModel.onShowAnswer() },
        )

        binding.answerArea.setRelativeHeight(Prefs.newStudyScreenAnswerButtonSize)

        viewModel.answerButtonsNextTimeFlow
            .flowWithLifecycle(lifecycle)
            .collectIn(lifecycleScope) { times ->
                binding.answerArea.setNextTimes(times)
            }

        val insetsController = WindowInsetsControllerCompat(window, binding.rootLayout)
        viewModel.showingAnswer.collectLatestIn(lifecycleScope) { isAnswerShown ->
            if (isAnswerShown) {
                insetsController.hide(WindowInsetsCompat.Type.ime())
            }
            binding.answerArea.setAnswerState(isAnswerShown)
        }

        if (Prefs.hideHardAndEasyButtons) {
            binding.answerArea.hideHardAndEasyButtons()
        }
    }

    private fun setupCounts() {
        viewModel.countsFlow
            .flowWithLifecycle(lifecycle)
            .collectLatestIn(lifecycleScope) { counts ->
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
        viewModel.answerTimer.state.collectIn(lifecycleScope) { state ->
            binding.timer.setup(state)
        }
    }

    private fun setupResetProgress() {
        viewModel.resetProgressFlow
            .flowWithLifecycle(lifecycle)
            .onEach {
                showDialogFragment(ForgetCardsDialog())
            }.launchIn(lifecycleScope)
        // TODO handle 'Reset progress' in the ViewModel instead of the activity, once
        //  a mechanism of showing a progress bar if the operation takes too long is implemented
        registerOnForgetHandler { listOf(viewModel.getCardId()) }
    }

    private fun setupCheckPronunciation() {
        viewModel.voiceRecorderEnabledFlow.flowWithLifecycle(lifecycle).collectIn(lifecycleScope) { isEnabled ->
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

        viewModel.whiteboardEnabledFlow.flowWithLifecycle(lifecycle).collectIn(lifecycleScope) { isEnabled ->
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
        viewModel.onCardUpdatedFlow.collectIn(lifecycleScope) {
            whiteboardFragment?.resetCanvas()
        }
    }

    private fun setupTimebox() {
        viewModel.timeBoxReachedFlow.flowWithLifecycle(lifecycle).collectIn(lifecycleScope) { timebox ->
            Timber.i("ReviewerFragment: Timebox reached (reps %d - secs %d)", timebox.reps, timebox.secs)

            viewModel.stopAutoAdvance()

            val minutes = (timebox.secs / 60f).roundToInt()
            val message = CollectionManager.TR.studyingCardStudiedIn(timebox.reps) + " " + CollectionManager.TR.studyingMinute(minutes)

            AlertDialog.Builder(requireContext()).show {
                setTitle(R.string.timebox_reached_title)
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
        viewModel.actionFeedbackFlow
            .flowWithLifecycle(lifecycle)
            .collectIn(lifecycleScope) { message ->
                showSnackbar(message, duration = 500)
            }

        viewModel.editNoteTagsFlow.collectIn(lifecycleScope) { noteId ->
            val dialogFragment =
                tagsDialogFactory.newTagsDialog().withArguments(
                    requireContext(),
                    TagsDialog.DialogType.EDIT_TAGS,
                    listOf(noteId),
                )
            showDialogFragment(dialogFragment)
        }

        viewModel.setDueDateFlow.collectIn(lifecycleScope) { cardId ->
            val dialogFragment = SetDueDateDialog.newInstance(listOf(cardId))
            showDialogFragment(dialogFragment)
        }

        viewModel.pageUpFlow.flowWithLifecycle(lifecycle).collectIn(lifecycleScope) {
            webViewLayout.pageUp()
        }

        viewModel.pageDownFlow.flowWithLifecycle(lifecycle).collectIn(lifecycleScope) {
            webViewLayout.pageDown()
        }

        val repository = StudyScreenRepository()

        viewModel.isMarkedFlow
            .flowWithLifecycle(lifecycle)
            .collectIn(lifecycleScope) { isMarked ->
                if (!repository.isMarkShownInToolbar) {
                    binding.markIcon.isVisible = isMarked
                }
            }
        val flagView = binding.flagIcon
        viewModel.flagFlow
            .flowWithLifecycle(lifecycle)
            .collectIn(lifecycleScope) { flag ->
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

    override fun onCreateWebViewClient(savedInstanceState: Bundle?): CardViewerWebViewClient = ReviewerWebViewClient(savedInstanceState)

    override fun onCreateWebChromeClient(): CardViewerWebChromeClient = ReviewerWebChromeClient()

    private inner class ReviewerWebViewClient(
        savedInstanceState: Bundle?,
    ) : CardViewerWebViewClient(savedInstanceState) {
        private var scale: Float = if (!isRobolectric) webViewLayout.scale else 1F
        private var isScrolling: Boolean = false
        private var isScrollingJob: Job? = null
        private val gestureParser by lazy {
            GestureParser(
                scope = lifecycleScope,
                isDoubleTapEnabled = bindingMap.isBound(Gesture.DOUBLE_TAP),
            )
        }
        private var hasShownUnsupportedFeatureWarning = false

        init {
            webViewLayout.setOnScrollChangeListener { _, _, _, _, _ ->
                isScrolling = true
                isScrollingJob?.cancel()
                isScrollingJob =
                    lifecycleScope.launch {
                        delay(300)
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
                        bindingMap.onGesture(gesture)
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
                        setMessage(R.string.feature_not_supported_by_study_screen)
                        setPositiveButton(R.string.dialog_ok) { _, _ -> }
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
