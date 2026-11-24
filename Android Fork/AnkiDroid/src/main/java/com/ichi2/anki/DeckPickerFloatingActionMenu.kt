// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Akshay Jadhav <jadhavakshay0701@gmail.com>

package com.ichi2.anki

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.content.res.ColorStateList
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.color.MaterialColors
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.common.android.animationEnabled
import com.ichi2.anki.databinding.ActivityHomescreenBinding
import com.ichi2.anki.databinding.IncludeFloatingAddButtonBinding
import com.ichi2.anki.ui.DoubleTapListener
import com.ichi2.anki.ui.internationalization.sentenceCase
import timber.log.Timber
import com.ichi2.anki.common.android.R as CommonR

class DeckPickerFloatingActionMenu(
    private val context: Context,
    homescreenBinding: ActivityHomescreenBinding,
    private val deckPicker: DeckPicker,
    private val toggleListener: FloatingActionBarToggleListener,
    initiallyOpen: Boolean,
) {
    // TODO: refactor this to decouple with Homescreen & DeckPicker
    private val binding: IncludeFloatingAddButtonBinding = homescreenBinding.deckPickerPane.floatingActionButton
    private val extendedFabHorizontalPadding = binding.fabMain.paddingStart + binding.fabMain.paddingEnd

    /** Layout deck_picker.xml is attached here */
    private val linearLayout: LinearLayout = homescreenBinding.deckPickerPane.deckpickerView
    private val studyOptionsFrame: View? = homescreenBinding.studyoptionsFrame

    // Colors values obtained from attributes
    private val fabNormalColor = MaterialColors.getColor(binding.fabMain, CommonR.attr.fabNormalColor)
    private val fabPressedColor = MaterialColors.getColor(binding.fabMain, CommonR.attr.fabPressedColor)

    // Add Note Drawable Icon
    private val addNoteIcon: Int = R.drawable.ic_add_note

    // Add White Icon
    private val addWhiteIcon: Int = CommonR.drawable.ic_add

    var isFABOpen = false
        private set

    private var pendingMenuReset = false

    @Suppress("unused")
    val isFragmented: Boolean
        get() = studyOptionsFrame != null

    @VisibleForTesting
    fun showFloatingActionMenu() {
        if (pendingMenuReset) return
        toggleListener.onBeginToggle(isOpening = true)
        deckPicker.activeSnackBar?.dismiss()
        linearLayout.alpha = 0.5f
        studyOptionsFrame?.let { it.alpha = 0.5f }
        isFABOpen = true

        setCreateDeckButtonLabel()

        if (deckPicker.animationEnabled()) {
            // Show with animation
            binding.addSharedButton.visibility = View.VISIBLE
            binding.addDeckButton.visibility = View.VISIBLE
            binding.addFilteredDeckButton.visibility = View.VISIBLE
            binding.fabBGLayout.visibility = View.VISIBLE
            binding.fabMain.backgroundTintList = ColorStateList.valueOf(fabPressedColor)
            binding.fabMain.setIconResource(addNoteIcon)
            extendMainButton()

            with(binding) {
                // Dismissal listeners must not run when these opening animations finish.
                addDeckButton.animate().setListener(null)
                addFilteredDeckButton.animate().setListener(null)
                addSharedButton.animate().translationY(0f).duration = 100
                addDeckButton.animate().translationY(0f).duration = 70
                addFilteredDeckButton.animate().translationY(0f).duration = 100
                addSharedButton.animate().alpha(1f).duration = 100
                addDeckButton.animate().alpha(1f).duration = 70
                addFilteredDeckButton.animate().alpha(1f).duration = 100
            }
        } else {
            // Show without animation
            binding.addSharedButton.visibility = View.VISIBLE
            binding.addDeckButton.visibility = View.VISIBLE
            binding.addFilteredDeckButton.visibility = View.VISIBLE
            binding.fabBGLayout.visibility = View.VISIBLE
            binding.addSharedButton.alpha = 1f
            binding.addDeckButton.alpha = 1f
            binding.addFilteredDeckButton.alpha = 1f
            binding.addSharedButton.translationY = 0f
            binding.addDeckButton.translationY = 0f
            binding.addFilteredDeckButton.translationY = 0f
            binding.fabMain.isExtended = true

            // During without animation maintain the original color of FAB
            binding.fabMain.apply {
                backgroundTintList = ColorStateList.valueOf(fabNormalColor)
                setIconResource(addNoteIcon)
            }
        }
    }

    /** Extends Add without overshooting its final width, preserving the styled minimum width. */
    private fun extendMainButton() =
        with(binding.fabMain) {
            val originalMinWidth = minWidth
            val originalMinimumWidth = minimumWidth
            // Material measures with collapsed padding, then adds the extra extended padding.
            // Discount that padding from the minimum during measurement so it isn't counted twice.
            val extraPadding = extendedFabHorizontalPadding - paddingStart - paddingEnd
            // Both TextView's minWidth and View's minimumWidth constrain this measurement.
            minWidth = (originalMinWidth - extraPadding).coerceAtLeast(0)
            minimumWidth = (originalMinimumWidth - extraPadding).coerceAtLeast(0)
            try {
                extend()
            } finally {
                // Preserve the styled minimum width for the final wrap_content layout.
                minWidth = originalMinWidth
                minimumWidth = originalMinimumWidth
            }
        }

    /**
     * Dismisses the menu, or resets it off-screen after opening the editor.
     * [style] selects the closing animation for Back/outside taps or menu actions.
     * [animate] is false for the off-screen reset.
     */
    fun closeFloatingActionMenu(
        style: CloseAnimation,
        animate: Boolean = deckPicker.animationEnabled(),
    ) {
        toggleListener.onBeginToggle(isOpening = false)
        linearLayout.alpha = 1f
        studyOptionsFrame?.let { it.alpha = 1f }
        isFABOpen = false
        with(binding) {
            fabBGLayout.visibility = View.GONE
            fabMain.backgroundTintList = ColorStateList.valueOf(fabNormalColor)
            fabMain.setIconResource(addWhiteIcon)

            if (!animate) {
                addSharedButton.animate().cancel()
                addDeckButton.animate().cancel()
                addFilteredDeckButton.animate().cancel()
                hideMenuButtons()
                fabMain.isExtended = false
                return
            }

            fabMain.shrink()
            val hideButtons =
                object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        if (!isFABOpen) {
                            hideMenuButtons()
                        }
                    }
                }
            addSharedButton.animateClose(style.otherOffset, 100)
            addDeckButton.animateClose(style.deckOffset, 50).setListener(hideButtons)
            addFilteredDeckButton.animateClose(style.otherOffset, 100).setListener(hideButtons)
        }
    }

    private fun hideMenuButtons() =
        with(binding) {
            addSharedButton.visibility = View.GONE
            addDeckButton.visibility = View.GONE
            addFilteredDeckButton.visibility = View.GONE
        }

    private fun View.animateClose(
        offset: Float,
        duration: Long,
    ) = animate()
        .alpha(0f)
        .translationY(offset)
        .setDuration(duration)

    fun showFloatingActionButton() {
        if (!binding.fabMain.isShown) {
            Timber.i("DeckPicker:: showFloatingActionButton()")
            binding.fabMain.visibility = View.VISIBLE
        }
    }

    fun hideFloatingActionButton() {
        if (binding.fabMain.isShown) {
            // close the menu if it's open so the other menu items are hidden as well
            closeFloatingActionMenu(style = CloseAnimation.LONG)
            Timber.i("DeckPicker:: hideFloatingActionButton()")
            binding.fabMain.visibility = View.GONE
        }
    }

    private fun createActivationKeyListener(
        logMessage: String,
        action: () -> Unit,
    ): View.OnKeyListener =
        View.OnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN &&
                (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_CENTER)
            ) {
                Timber.d(logMessage)
                action()
                return@OnKeyListener true
            }
            false
        }

    init {
        deckPicker.lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStop(owner: LifecycleOwner) = resetMenuAfterAdding()

                // In multi-window mode the deck screen may remain visible behind the editor.
                override fun onResume(owner: LifecycleOwner) = resetMenuAfterAdding()
            },
        )
        binding.fabMain.isExtended = false
        binding.fabMain.setOnTouchListener(
            object : DoubleTapListener(context) {
                override fun onDoubleTap(e: MotionEvent?) {
                    addNote()
                }

                override fun onUnconfirmedSingleTap(e: MotionEvent?) {
                    // we use an unconfirmed tap as we don't want any visual delay in tapping the +
                    // and opening the menu.
                    if (!isFABOpen) {
                        showFloatingActionMenu()
                    } else {
                        addNote()
                    }
                }
            },
        )

        // Enable keyboard activation for Enter/DPAD_CENTER/ESC keys
        binding.fabMain.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> {
                        Timber.d("FAB main button: ENTER key pressed")
                        if (!isFABOpen) {
                            showFloatingActionMenu()
                        } else {
                            addNote()
                        }
                        return@setOnKeyListener true
                    }
                    KeyEvent.KEYCODE_ESCAPE -> {
                        if (isFABOpen) {
                            Timber.d("FAB main button: ESC key pressed - closing menu")
                            closeFloatingActionMenu(style = CloseAnimation.SHORT)
                            return@setOnKeyListener true
                        }
                    }
                }
            }
            false
        }

        // Close the FAB menu when clicking outside the menu container
        binding.fabBGLayout.setOnClickListener { closeFloatingActionMenu(style = CloseAnimation.SHORT) }

        // Close the FAB menu when clicking the background inside the menu container
        // (e.g., when tapping to the left of narrower buttons such as the "Add" button)
        binding.fabLinearLayout.setOnClickListener {
            if (isFABOpen) {
                closeFloatingActionMenu(style = CloseAnimation.SHORT)
            }
        }

        val addDeckListener =
            View.OnClickListener {
                if (isFABOpen) {
                    closeFloatingActionMenu(style = CloseAnimation.LONG)
                    deckPicker.showCreateDeckDialog()
                }
            }
        setCreateDeckButtonLabel()
        binding.addDeckButton.setOnClickListener(addDeckListener)

        // Enable keyboard activation for Enter/DPAD_CENTER keys
        val addDeckKeyListener =
            createActivationKeyListener("Add Deck button: ENTER key pressed") {
                if (isFABOpen) {
                    closeFloatingActionMenu(style = CloseAnimation.LONG)
                    deckPicker.showCreateDeckDialog()
                }
            }
        binding.addDeckButton.setOnKeyListener(addDeckKeyListener)
        val addFilteredDeckListener =
            View.OnClickListener {
                if (isFABOpen) {
                    closeFloatingActionMenu(style = CloseAnimation.LONG)
                    deckPicker.showCreateFilteredDeckDialog()
                }
            }
        binding.addFilteredDeckButton.setOnClickListener(addFilteredDeckListener)

        // Enable keyboard activation for Enter/DPAD_CENTER keys
        val addFilteredDeckKeyListener =
            createActivationKeyListener("Add Filtered Deck button: ENTER key pressed") {
                if (isFABOpen) {
                    closeFloatingActionMenu(style = CloseAnimation.LONG)
                    deckPicker.showCreateFilteredDeckDialog()
                }
            }
        binding.addFilteredDeckButton.setOnKeyListener(addFilteredDeckKeyListener)
        val addSharedListener =
            View.OnClickListener {
                if (isFABOpen) {
                    closeFloatingActionMenu(style = CloseAnimation.LONG)
                    Timber.d("configureFloatingActionsMenu::addSharedButton::onClickListener - Adding Shared Deck")
                    deckPicker.openAnkiWebSharedDecks()
                }
            }
        binding.addSharedButton.setOnClickListener(addSharedListener)

        // Enable keyboard activation for Enter/DPAD_CENTER keys
        val addSharedKeyListener =
            createActivationKeyListener("Add Shared Deck button: ENTER key pressed") {
                if (isFABOpen) {
                    closeFloatingActionMenu(style = CloseAnimation.LONG)
                    deckPicker.openAnkiWebSharedDecks()
                }
            }
        binding.addSharedButton.setOnKeyListener(addSharedKeyListener)
        // Mirrors the touch DoubleTapListener above: TalkBack and hardware keyboards activate via
        // ACTION_CLICK -> performClick(), which bypasses the touch GestureDetector. Opening the menu
        // must live here too, otherwise the FAB is inoperable when a screen reader is enabled.
        val fabMainClickListener =
            View.OnClickListener {
                if (!isFABOpen) {
                    showFloatingActionMenu()
                } else {
                    Timber.d("configureFloatingActionsMenu::fabMain::onClickListener - Adding Note")
                    addNote()
                }
            }
        binding.fabMain.setOnClickListener(fabMainClickListener)

        if (initiallyOpen) {
            showFloatingActionMenu()
        }
    }

    /**
     * Opens the [NoteEditorFragment], preserving the menu during the outgoing transition.
     * @see DeckPicker.addNote
     */
    private fun addNote() {
        if (pendingMenuReset) return
        pendingMenuReset = true
        // Save a closed menu state without changing its appearance during the transition.
        isFABOpen = false
        deckPicker.addNote()
    }

    private fun resetMenuAfterAdding() {
        if (!pendingMenuReset) return
        pendingMenuReset = false
        closeFloatingActionMenu(style = CloseAnimation.LONG, animate = false)
    }

    /**
     * Sets the label of the 'Create deck' button from the backend translation.
     */
    private fun setCreateDeckButtonLabel() {
        binding.addDeckButton.apply {
            text = with(context) { TR.sentenceCase.createDeck }
            contentDescription = text
            isExtended = true
        }
    }

    /** Animation styles for closing the FAB menu, with offsets in pixels. */
    enum class CloseAnimation(
        val deckOffset: Float,
        val otherOffset: Float,
    ) {
        /** Back or an outside tap moves the menu buttons a shorter distance. */
        SHORT(deckOffset = 300f, otherOffset = 400f),

        /** Menu actions, the navigation drawer, or hiding the FAB move the buttons farther. */
        LONG(deckOffset = 400f, otherOffset = 600f),
    }

    fun interface FloatingActionBarToggleListener {
        /** Triggered when the drawer is starting to open/close */
        fun onBeginToggle(isOpening: Boolean)
    }
}
