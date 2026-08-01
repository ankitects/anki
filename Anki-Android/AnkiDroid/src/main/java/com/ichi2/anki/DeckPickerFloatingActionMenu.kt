// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Akshay Jadhav <jadhavakshay0701@gmail.com>

package com.ichi2.anki

import android.animation.Animator
import android.content.Context
import android.content.res.ColorStateList
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import androidx.annotation.VisibleForTesting
import com.google.android.material.color.MaterialColors
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.common.android.animationEnabled
import com.ichi2.anki.databinding.ActivityHomescreenBinding
import com.ichi2.anki.databinding.IncludeFloatingAddButtonBinding
import com.ichi2.anki.ui.DoubleTapListener
import com.ichi2.anki.ui.internationalization.sentenceCase
import timber.log.Timber

class DeckPickerFloatingActionMenu(
    private val context: Context,
    homescreenBinding: ActivityHomescreenBinding,
    private val deckPicker: DeckPicker,
) {
    // TODO: refactor this to decouple with Homescreen & DeckPicker
    private val binding: IncludeFloatingAddButtonBinding = homescreenBinding.deckPickerPane.floatingActionButton

    /** Layout deck_picker.xml is attached here */
    private val linearLayout: LinearLayout = homescreenBinding.deckPickerPane.deckpickerView
    private val studyOptionsFrame: View? = homescreenBinding.studyoptionsFrame

    // Colors values obtained from attributes
    private val fabNormalColor = MaterialColors.getColor(binding.fabMain, R.attr.fab_normal)
    private val fabPressedColor = MaterialColors.getColor(binding.fabMain, R.attr.fab_pressed)

    // Add Note Drawable Icon
    private val addNoteIcon: Int = R.drawable.ic_add_note

    // Add White Icon
    private val addWhiteIcon: Int = R.drawable.ic_add

    var isFABOpen = false

    var toggleListener: FloatingActionBarToggleListener? = null

    @Suppress("unused")
    val isFragmented: Boolean
        get() = studyOptionsFrame != null

    @VisibleForTesting
    fun showFloatingActionMenu() {
        toggleListener?.onBeginToggle(isOpening = true)
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
            binding.fabMain.extend()

            with(binding) {
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

    /**
     * This function takes a parameter which decides if we want to apply the rise and shrink animation
     * for FAB or not.
     *
     * Case 1: When the FAB is already opened and we close it by pressing the back button then we need to show
     * the rise and shrink animation and get back to the FAB with `+` icon.
     *
     * Case 2: When the user opens the side navigation drawer (without touching the FAB). In that case we don't
     * want to show any type of rise and shrink animation for the FAB so we put the value `false` for the parameter.
     */
    fun closeFloatingActionMenu(applyRiseAndShrinkAnimation: Boolean) {
        toggleListener?.onBeginToggle(isOpening = false)
        if (applyRiseAndShrinkAnimation) {
            linearLayout.alpha = 1f
            studyOptionsFrame?.let { it.alpha = 1f }
            isFABOpen = false
            binding.fabBGLayout.visibility = View.GONE
            if (deckPicker.animationEnabled()) {
                // Changes the background color of FAB to default
                binding.fabMain.backgroundTintList = ColorStateList.valueOf(fabNormalColor)
                // Close with animation
                binding.fabMain.setIconResource(addWhiteIcon)
                binding.fabMain.shrink()

                with(binding) {
                    addSharedButton.animate().alpha(0f).duration = 50
                    addDeckButton.animate().alpha(0f).duration = 100
                    addFilteredDeckButton.animate().alpha(0f).duration = 100
                    addSharedButton.animate().translationY(400f).duration = 100
                    addDeckButton
                        .animate()
                        .translationY(300f)
                        .setDuration(50)
                        .setListener(
                            object : Animator.AnimatorListener {
                                override fun onAnimationStart(animator: Animator) {}

                                override fun onAnimationEnd(animator: Animator) {
                                    if (!isFABOpen) {
                                        addSharedButton.visibility = View.GONE
                                        addDeckButton.visibility = View.GONE
                                        addFilteredDeckButton.visibility = View.GONE
                                    }
                                }

                                override fun onAnimationCancel(animator: Animator) {}

                                override fun onAnimationRepeat(animator: Animator) {}
                            },
                        )
                    addFilteredDeckButton
                        .animate()
                        .translationY(400f)
                        .setDuration(100)
                        .setListener(
                            object : Animator.AnimatorListener {
                                override fun onAnimationStart(animator: Animator) {}

                                override fun onAnimationEnd(animator: Animator) {
                                    if (!isFABOpen) {
                                        addSharedButton.visibility = View.GONE
                                        addDeckButton.visibility = View.GONE
                                        addFilteredDeckButton.visibility = View.GONE
                                    }
                                }

                                override fun onAnimationCancel(animator: Animator) {}

                                override fun onAnimationRepeat(animator: Animator) {}
                            },
                        )
                }
            } else {
                // Close without animation
                binding.addSharedButton.visibility = View.GONE
                binding.addDeckButton.visibility = View.GONE
                binding.addFilteredDeckButton.visibility = View.GONE
                binding.fabMain.isExtended = false

                binding.fabMain.setIconResource(addWhiteIcon)
            }
        } else {
            linearLayout.alpha = 1f
            studyOptionsFrame?.let { it.alpha = 1f }
            isFABOpen = false
            binding.fabBGLayout.visibility = View.GONE
            if (deckPicker.animationEnabled()) {
                // Changes the background color of FAB to default
                binding.fabMain.backgroundTintList = ColorStateList.valueOf(fabNormalColor)
                // Close with animation
                binding.fabMain.setIconResource(addWhiteIcon)
                binding.fabMain.shrink()

                with(binding) {
                    addSharedButton.animate().alpha(0f).duration = 70
                    addDeckButton.animate().alpha(0f).duration = 50
                    addFilteredDeckButton.animate().alpha(0f).duration = 50
                    addSharedButton.animate().translationY(600f).duration = 100
                    addDeckButton
                        .animate()
                        .translationY(400f)
                        .setDuration(50)
                        .setListener(
                            object : Animator.AnimatorListener {
                                override fun onAnimationStart(animator: Animator) {}

                                override fun onAnimationEnd(animator: Animator) {
                                    if (!isFABOpen) {
                                        addSharedButton.visibility = View.GONE
                                        addDeckButton.visibility = View.GONE
                                        addFilteredDeckButton.visibility = View.GONE
                                    }
                                }

                                override fun onAnimationCancel(animator: Animator) {}

                                override fun onAnimationRepeat(animator: Animator) {}
                            },
                        )
                    addFilteredDeckButton
                        .animate()
                        .translationY(600f)
                        .setDuration(100)
                        .setListener(
                            object : Animator.AnimatorListener {
                                override fun onAnimationStart(animator: Animator) {}

                                override fun onAnimationEnd(animator: Animator) {
                                    if (!isFABOpen) {
                                        addSharedButton.visibility = View.GONE
                                        addDeckButton.visibility = View.GONE
                                        addFilteredDeckButton.visibility = View.GONE
                                    }
                                }

                                override fun onAnimationCancel(animator: Animator) {}

                                override fun onAnimationRepeat(animator: Animator) {}
                            },
                        )
                }
            } else {
                // Close without animation
                binding.addSharedButton.visibility = View.GONE
                binding.addDeckButton.visibility = View.GONE
                binding.addFilteredDeckButton.visibility = View.GONE
                binding.fabMain.isExtended = false

                binding.fabMain.setIconResource(addWhiteIcon)
            }
        }
    }

    fun showFloatingActionButton() {
        if (!binding.fabMain.isShown) {
            Timber.i("DeckPicker:: showFloatingActionButton()")
            binding.fabMain.visibility = View.VISIBLE
        }
    }

    fun hideFloatingActionButton() {
        if (binding.fabMain.isShown) {
            // close the menu if it's open so the other menu items are hidden as well
            closeFloatingActionMenu(false)
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
                            closeFloatingActionMenu(applyRiseAndShrinkAnimation = false)
                            addNote()
                        }
                        return@setOnKeyListener true
                    }
                    KeyEvent.KEYCODE_ESCAPE -> {
                        if (isFABOpen) {
                            Timber.d("FAB main button: ESC key pressed - closing menu")
                            closeFloatingActionMenu(applyRiseAndShrinkAnimation = true)
                            return@setOnKeyListener true
                        }
                    }
                }
            }
            false
        }

        binding.fabBGLayout.setOnClickListener { closeFloatingActionMenu(applyRiseAndShrinkAnimation = true) }
        val addDeckListener =
            View.OnClickListener {
                if (isFABOpen) {
                    closeFloatingActionMenu(applyRiseAndShrinkAnimation = false)
                    deckPicker.showCreateDeckDialog()
                }
            }
        setCreateDeckButtonLabel()
        binding.addDeckButton.setOnClickListener(addDeckListener)

        // Enable keyboard activation for Enter/DPAD_CENTER keys
        val addDeckKeyListener =
            createActivationKeyListener("Add Deck button: ENTER key pressed") {
                if (isFABOpen) {
                    closeFloatingActionMenu(applyRiseAndShrinkAnimation = false)
                    deckPicker.showCreateDeckDialog()
                }
            }
        binding.addDeckButton.setOnKeyListener(addDeckKeyListener)
        val addFilteredDeckListener =
            View.OnClickListener {
                if (isFABOpen) {
                    closeFloatingActionMenu(applyRiseAndShrinkAnimation = false)
                    deckPicker.showCreateFilteredDeckDialog()
                }
            }
        binding.addFilteredDeckButton.setOnClickListener(addFilteredDeckListener)

        // Enable keyboard activation for Enter/DPAD_CENTER keys
        val addFilteredDeckKeyListener =
            createActivationKeyListener("Add Filtered Deck button: ENTER key pressed") {
                if (isFABOpen) {
                    closeFloatingActionMenu(applyRiseAndShrinkAnimation = false)
                    deckPicker.showCreateFilteredDeckDialog()
                }
            }
        binding.addFilteredDeckButton.setOnKeyListener(addFilteredDeckKeyListener)
        val addSharedListener =
            View.OnClickListener {
                if (isFABOpen) {
                    closeFloatingActionMenu(applyRiseAndShrinkAnimation = false)
                    Timber.d("configureFloatingActionsMenu::addSharedButton::onClickListener - Adding Shared Deck")
                    deckPicker.openAnkiWebSharedDecks()
                }
            }
        binding.addSharedButton.setOnClickListener(addSharedListener)

        // Enable keyboard activation for Enter/DPAD_CENTER keys
        val addSharedKeyListener =
            createActivationKeyListener("Add Shared Deck button: ENTER key pressed") {
                if (isFABOpen) {
                    closeFloatingActionMenu(applyRiseAndShrinkAnimation = false)
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
                    closeFloatingActionMenu(applyRiseAndShrinkAnimation = false)
                    Timber.d("configureFloatingActionsMenu::fabMain::onClickListener - Adding Note")
                    addNote()
                }
            }
        binding.fabMain.setOnClickListener(fabMainClickListener)
    }

    /**
     * Closes the FAB menu and opens the [NoteEditorFragment]
     * @see DeckPicker.addNote
     */
    private fun addNote() {
        deckPicker.addNote()
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

    fun interface FloatingActionBarToggleListener {
        /** Triggered when the drawer is starting to open/close */
        fun onBeginToggle(isOpening: Boolean)
    }
}
