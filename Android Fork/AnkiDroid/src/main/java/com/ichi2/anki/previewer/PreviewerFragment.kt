// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.previewer

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.annotation.VisibleForTesting
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.slider.Slider
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CommonString
import com.ichi2.anki.DispatchKeyEventListener
import com.ichi2.anki.Flag
import com.ichi2.anki.R
import com.ichi2.anki.browser.IdsFile
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.common.destinations.navigate
import com.ichi2.anki.common.utils.android.showThemedToast
import com.ichi2.anki.databinding.FragmentPreviewerBinding
import com.ichi2.anki.reviewer.BindingMap
import com.ichi2.anki.reviewer.BindingProcessor
import com.ichi2.anki.reviewer.MappableBinding
import com.ichi2.anki.snackbar.BaseSnackbarBuilderProvider
import com.ichi2.anki.snackbar.SnackbarBuilder
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.anki.utils.ext.collectInViewLifecycle
import com.ichi2.anki.utils.ext.getIntOrNull
import com.ichi2.anki.utils.ext.requireParcelable
import com.ichi2.anki.utils.ext.setIconRes
import com.ichi2.anki.utils.ext.sharedPrefs
import com.ichi2.anki.utils.savedStateViewModelFactory
import com.ichi2.anki.workarounds.SafeWebViewLayout
import com.ichi2.utils.performClickIfEnabled
import dev.androidbroadcast.vbpd.viewBinding
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.IOException

class PreviewerFragment :
    CardViewerFragment(R.layout.fragment_previewer),
    Toolbar.OnMenuItemClickListener,
    BaseSnackbarBuilderProvider,
    DispatchKeyEventListener,
    BindingProcessor<MappableBinding, PreviewerAction> {
    override val viewModel: PreviewerViewModel by viewModels {
        savedStateViewModelFactory { handle ->
            val arguments = requireArguments()
            // Read before constructing the ViewModel, which immediately loads the first card.
            val ids =
                try {
                    arguments.requireParcelable<IdsFile>(ARG_CARD_IDS_FILE).getIdsOrNull()
                } catch (e: IOException) {
                    throw UnavailableSelectionException(e)
                }
            if (ids.isNullOrEmpty()) throw UnavailableSelectionException()
            PreviewerViewModel(handle, ids, requireNotNull(arguments.getIntOrNull(ARG_CURRENT_INDEX)))
        }
    }

    @VisibleForTesting
    internal val binding by viewBinding(FragmentPreviewerBinding::bind)

    override val webViewLayout: SafeWebViewLayout get() = binding.webViewLayout

    override val baseSnackbarBuilder: SnackbarBuilder
        get() = {
            anchorView =
                if (binding.slider.isVisible) {
                    binding.slider
                } else {
                    binding.nextButton
                }
        }

    private lateinit var bindingMap: BindingMap<MappableBinding, PreviewerAction>

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        try {
            viewModel
        } catch (e: UnavailableSelectionException) {
            Timber.w(e, "Failed to read previewer IDs")
            showThemedToast(requireContext(), CommonString.something_wrong, false)
            // The activity may still lay out this view before finishing. Its slider has no range yet.
            view.isVisible = false
            requireActivity().finish()
            return
        }
        super.onViewCreated(view, savedInstanceState)
        val cardsCount = viewModel.cardsCount()

        viewModel.currentIndex.collectInViewLifecycle { currentIndex ->
            val displayIndex = currentIndex + 1
            binding.slider.value = displayIndex.toFloat()
            binding.progressIndicator.text =
                getString(CommonString.preview_progress_bar_text, displayIndex, cardsCount)
        }
        // ************************************* Menu items *************************************
        val menu = binding.toolbar.menu
        setupFlagMenu(menu)

        viewModel.backSideOnly.collectInViewLifecycle { isBackSideOnly ->
            setBackSideOnlyButtonIcon(menu, isBackSideOnly)
        }

        viewModel.isMarked.collectInViewLifecycle { isMarked ->
            with(menu.findItem(R.id.action_mark)) {
                if (isMarked) {
                    setIcon(R.drawable.ic_star)
                    setTitle(CommonString.menu_unmark_note)
                } else {
                    setIcon(R.drawable.ic_star_border_white)
                    title = TR.sentenceCase.markNote
                }
            }
        }

        // handle selection of a new flag
        viewModel.flag.collectInViewLifecycle { flag ->
            menu.findItem(R.id.action_flag).setIcon(flag.drawableRes)
        }

        @NeedsTest("webview don't vanish when only one card is in the list")
        if (cardsCount == 1) {
            binding.slider.visibility = View.GONE
            binding.progressIndicator.visibility = View.GONE
        }

        binding.slider.apply {
            valueTo = cardsCount.toFloat()
            doOnLayout {
                updateSliderGestureExclusion(this)
            }
            addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                updateSliderGestureExclusion(this)
            }
            addOnSliderTouchListener(
                object : Slider.OnSliderTouchListener {
                    override fun onStartTrackingTouch(slider: Slider) = Unit

                    override fun onStopTrackingTouch(slider: Slider) {
                        viewModel.onSliderChange(slider.value.toInt())
                    }
                },
            )
        }

        viewModel.isNextButtonEnabled.collectInViewLifecycle {
            binding.nextButton.isEnabled = it
        }

        binding.nextButton.setOnClickListener {
            viewModel.onNextButtonClick()
        }

        viewModel.isBackButtonEnabled.collectInViewLifecycle {
            binding.previousButton.isEnabled = it
        }

        binding.previousButton.setOnClickListener {
            viewModel.onPreviousButtonClick()
        }

        view.setOnGenericMotionListener { _, event ->
            bindingMap.onGenericMotionEvent(event)
        }

        viewModel.showingAnswer.collectInViewLifecycle {
            // focus on the whole layout so motion controllers can be captured
            // without navigating the other View elements
            binding.root.requestFocus()
        }

        binding.toolbar.apply {
            setOnMenuItemClickListener(this@PreviewerFragment)
            setNavigationOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
        }

        binding.webviewContainer.setFrameStyle()

        bindingMap = BindingMap(sharedPrefs(), PreviewerAction.entries, this)
    }

    private fun updateSliderGestureExclusion(slider: Slider) {
        ViewCompat.setSystemGestureExclusionRects(
            slider,
            listOf(Rect(0, 0, slider.width, slider.height)),
        )
    }

    private fun setupFlagMenu(menu: Menu) {
        menu.findItem(R.id.action_flag).title = TR.sentenceCase.flagCard
        val submenu = menu.findItem(R.id.action_flag).subMenu
        lifecycleScope.launch {
            for ((flag, name) in Flag.queryDisplayNames(requireContext())) {
                submenu
                    ?.add(Menu.NONE, flag.id, Menu.NONE, name)
                    ?.setIcon(flag.drawableRes)
            }
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_edit -> editCard()
            R.id.action_mark -> viewModel.toggleMark()
            R.id.action_back_side_only -> viewModel.toggleBackSideOnly()
            R.id.flag_none -> viewModel.setFlag(Flag.NONE)
            R.id.flag_red -> viewModel.setFlag(Flag.RED)
            R.id.flag_orange -> viewModel.setFlag(Flag.ORANGE)
            R.id.flag_green -> viewModel.setFlag(Flag.GREEN)
            R.id.flag_blue -> viewModel.setFlag(Flag.BLUE)
            R.id.flag_pink -> viewModel.setFlag(Flag.PINK)
            R.id.flag_turquoise -> viewModel.setFlag(Flag.TURQUOISE)
            R.id.flag_purple -> viewModel.setFlag(Flag.PURPLE)
        }
        return true
    }

    override fun processAction(
        action: PreviewerAction,
        binding: MappableBinding,
    ): Boolean {
        when (action) {
            PreviewerAction.MARK -> viewModel.toggleMark()
            PreviewerAction.EDIT -> editCard()
            PreviewerAction.TOGGLE_BACKSIDE_ONLY -> viewModel.toggleBackSideOnly()
            PreviewerAction.REPLAY_AUDIO -> viewModel.replayMedia()
            PreviewerAction.TOGGLE_FLAG_RED -> viewModel.toggleFlag(Flag.RED)
            PreviewerAction.TOGGLE_FLAG_ORANGE -> viewModel.toggleFlag(Flag.ORANGE)
            PreviewerAction.TOGGLE_FLAG_GREEN -> viewModel.toggleFlag(Flag.GREEN)
            PreviewerAction.TOGGLE_FLAG_BLUE -> viewModel.toggleFlag(Flag.BLUE)
            PreviewerAction.TOGGLE_FLAG_PINK -> viewModel.toggleFlag(Flag.PINK)
            PreviewerAction.TOGGLE_FLAG_TURQUOISE -> viewModel.toggleFlag(Flag.TURQUOISE)
            PreviewerAction.TOGGLE_FLAG_PURPLE -> viewModel.toggleFlag(Flag.PURPLE)
            PreviewerAction.UNSET_FLAG -> viewModel.setFlag(Flag.NONE)
            PreviewerAction.BACK -> this.binding.previousButton.performClickIfEnabled()
            PreviewerAction.NEXT -> this.binding.nextButton.performClickIfEnabled()
        }
        return true
    }

    private fun setBackSideOnlyButtonIcon(
        menu: Menu,
        isBackSideOnly: Boolean,
    ) {
        menu.findItem(R.id.action_back_side_only).apply {
            if (isBackSideOnly) {
                setIcon(R.drawable.ic_card_answer)
                setTitle(CommonString.card_side_answer)
            } else {
                setIconRes(requireContext(), R.drawable.ic_card_question)
                setTitle(CommonString.card_side_both)
            }
        }
    }

    private fun editCard() {
        lifecycleScope.launch {
            navigate(viewModel.getNoteEditorDestination())
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN || !::bindingMap.isInitialized) return false
        return bindingMap.onKeyDown(event)
    }

    /** Distinguishes selection failures from I/O failures during ViewModel construction. */
    private class UnavailableSelectionException(
        cause: IOException? = null,
    ) : Exception("Unable to load preview selection", cause)

    companion object {
        /** Index of the card to be first displayed among the IDs provided by [ARG_CARD_IDS_FILE] */
        const val ARG_CURRENT_INDEX = "currentIndex"

        /** Argument key to a [IdsFile] with the IDs of the cards to be displayed */
        const val ARG_CARD_IDS_FILE = "cardIdsFile"

        fun getIntent(
            context: Context,
            idsFile: IdsFile,
            currentIndex: Int,
        ): Intent {
            val arguments =
                Bundle().apply {
                    putInt(ARG_CURRENT_INDEX, currentIndex)
                    putParcelable(ARG_CARD_IDS_FILE, idsFile)
                }
            return CardViewerActivity.getIntent(context, PreviewerFragment::class, arguments)
        }
    }
}
