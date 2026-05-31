/*
 * Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>
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
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.ui.windows.reviewer.whiteboard

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.PopupWindow
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.DispatchKeyEventListener
import com.ichi2.anki.R
import com.ichi2.anki.android.back.doubleBackPressCallback
import com.ichi2.anki.cardviewer.Gesture
import com.ichi2.anki.common.utils.android.systemIsInNightMode
import com.ichi2.anki.compat.CompatHelper.Companion.compat
import com.ichi2.anki.databinding.FragmentWhiteboardBinding
import com.ichi2.anki.databinding.PopupBrushOptionsBinding
import com.ichi2.anki.databinding.PopupEraserOptionsBinding
import com.ichi2.anki.preferences.reviewer.WhiteboardAction
import com.ichi2.anki.reviewer.BindingMap
import com.ichi2.anki.reviewer.ReviewerBinding
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.utils.ext.sharedPrefs
import com.ichi2.utils.dp
import com.ichi2.utils.increaseHorizontalPaddingOfMenuIcons
import com.ichi2.utils.toRGBAHex
import dev.androidbroadcast.vbpd.viewBinding
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import timber.log.Timber
import kotlin.math.roundToInt

/**
 * Fragment that displays a whiteboard and its controls.
 */
class WhiteboardFragment :
    Fragment(R.layout.fragment_whiteboard),
    PopupMenu.OnMenuItemClickListener,
    DispatchKeyEventListener {
    private val viewModel: WhiteboardViewModel by viewModels {
        WhiteboardViewModel.factory(AnkiDroidApp.sharedPrefs())
    }

    val binding by viewBinding(FragmentWhiteboardBinding::bind)
    private lateinit var bindingMap: BindingMap<ReviewerBinding, WhiteboardAction>
    private var doubleBackCallback: OnBackPressedCallback? = null

    private var eraserPopup: PopupWindow? = null
    private var brushConfigPopup: PopupWindow? = null

    /** Called when no bindings used the triggered gestures */
    var gestureFallbackListener: ((Gesture) -> Unit)? = null

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        val isNightMode = systemIsInNightMode(requireContext())
        viewModel.loadState(isNightMode)

        setupUI()
        observeViewModel(binding.whiteboardView)
        setupDoubleBackPress()

        binding.whiteboardView.onNewPath = viewModel::addPath
        binding.whiteboardView.onEraseGestureStart = viewModel::startPathEraseGesture
        binding.whiteboardView.onEraseGestureMove = viewModel::erasePathsToPoint
        binding.whiteboardView.onEraseGestureEnd = viewModel::endPathEraseGesture
    }

    private fun setupDoubleBackPress() {
        val isUsingGesturesNavigation = context?.let { compat.isUsingSystemGestureNavigation(it) } == true
        doubleBackCallback =
            doubleBackPressCallback(
                enabled = !isHidden && isUsingGesturesNavigation,
                onFirstBack = { showSnackbar(R.string.back_pressed_once, Snackbar.LENGTH_SHORT) },
                shouldReEnable = {
                    !isHidden && isUsingGesturesNavigation
                },
            ).also {
                requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, it)
            }
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        val isUsingGesturesNavigation = context?.let { compat.isUsingSystemGestureNavigation(it) } == true
        doubleBackCallback?.isEnabled = !hidden && isUsingGesturesNavigation
    }

    private fun setupUI() {
        val toolbar = binding.whiteboardToolbar

        toolbar.overflowButton.setOnClickListener {
            val popupMenu = PopupMenu(requireContext(), toolbar.overflowButton)
            requireActivity().menuInflater.inflate(R.menu.whiteboard, popupMenu.menu)
            with(popupMenu.menu) {
                findItem(R.id.action_toggle_stylus).isChecked = viewModel.isStylusOnlyMode.value
                (this as? MenuBuilder)?.setOptionalIconsVisible(true)
                context?.increaseHorizontalPaddingOfMenuIcons(this)

                val alignmentMenuItemId =
                    when (viewModel.toolbarAlignment.value) {
                        ToolbarAlignment.LEFT -> R.id.action_align_left
                        ToolbarAlignment.RIGHT -> R.id.action_align_right
                        ToolbarAlignment.BOTTOM -> R.id.action_align_bottom
                    }
                findItem(alignmentMenuItemId).isEnabled = false
            }
            popupMenu.setOnMenuItemClickListener(this)
            popupMenu.show()
        }

        toolbar.undoButton.setOnClickListener { viewModel.undo() }
        toolbar.redoButton.setOnClickListener { viewModel.redo() }
        toolbar.eraserButton.setOnClickListener {
            if (viewModel.isEraserActive.value) {
                toolbar.eraserButton.isChecked = true
                if (eraserPopup?.isShowing == true) {
                    eraserPopup?.dismiss()
                } else {
                    showEraserOptionsPopup(it)
                }
            } else {
                viewModel.enableEraser()
            }
        }

        toolbar.onBrushClick = { view, index ->
            if (viewModel.activeBrushIndex.value == index && !viewModel.isEraserActive.value) {
                showBrushConfigurationPopup(view, index)
            } else {
                viewModel.setActiveBrush(index)
            }
        }

        toolbar.onBrushLongClick = { index ->
            if (viewModel.brushes.value.size > 1) {
                showRemoveColorDialog(index)
            } else {
                Timber.i("Tried to remove the last brush of the whiteboard")
                showSnackbar(R.string.cannot_remove_last_brush_message)
            }
        }

        viewModel.canUndo.onEach { toolbar.undoButton.isEnabled = it }.launchIn(lifecycleScope)
        viewModel.canRedo.onEach { toolbar.redoButton.isEnabled = it }.launchIn(lifecycleScope)

        binding.whiteboardToolbar.onToolbarVisibilityChanged = { isShown ->
            viewModel.setIsToolbarShown(isShown)
        }

        bindingMap = BindingMap(sharedPrefs(), WhiteboardAction.entries, viewModel)
        binding.root.setOnGenericMotionListener { _, event ->
            bindingMap.onGenericMotionEvent(event)
        }
        binding.whiteboardView.setOnMultiTouchListener { touchNumber ->
            val gesture =
                when (touchNumber) {
                    2 -> Gesture.TWO_FINGER_TAP
                    3 -> Gesture.THREE_FINGER_TAP
                    4 -> Gesture.FOUR_FINGER_TAP
                    else -> return@setOnMultiTouchListener
                }
            val result = bindingMap.onGesture(gesture)
            if (!result) {
                gestureFallbackListener?.invoke(gesture)
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        return bindingMap.onKeyDown(event)
    }

    fun onScreenShake(): Boolean = bindingMap.onGesture(Gesture.SHAKE)

    /**
     * Sets up observers for the ViewModel's flows.
     */
    private fun observeViewModel(whiteboardView: WhiteboardView) {
        val toolbar = binding.whiteboardToolbar

        viewModel.paths.onEach(whiteboardView::setHistory).launchIn(lifecycleScope)

        combine(
            viewModel.brushColor,
            viewModel.activeStrokeWidth,
        ) { color, width ->
            whiteboardView.setCurrentBrush(color, width)
        }.launchIn(lifecycleScope)

        combine(
            viewModel.isEraserActive,
            viewModel.eraserMode,
            viewModel.eraserDisplayWidth,
        ) { isActive, mode, width ->
            whiteboardView.isEraserActive = isActive
            toolbar.eraserButton.updateState(isActive, mode, width)
            whiteboardView.eraserMode = mode
            if (!isActive) {
                eraserPopup?.dismiss()
            }
        }.launchIn(lifecycleScope)

        viewModel.brushes
            .onEach { brushesInfo ->
                toolbar.setBrushes(brushesInfo, viewModel.activeBrushIndex.value, viewModel.isEraserActive.value)
            }.launchIn(lifecycleScope)

        viewModel.activeBrushIndex
            .onEach {
                toolbar.updateSelection(it, viewModel.isEraserActive.value)
            }.launchIn(lifecycleScope)

        viewModel.isEraserActive
            .onEach {
                toolbar.updateSelection(viewModel.activeBrushIndex.value, it)
            }.launchIn(lifecycleScope)

        viewModel.isStylusOnlyMode
            .onEach { isEnabled ->
                whiteboardView.isStylusOnlyMode = isEnabled
            }.launchIn(lifecycleScope)

        viewModel.toolbarAlignment
            .onEach { alignment ->
                toolbar.setAlignment(alignment)
                updateToolbarPosition(alignment)
            }.launchIn(lifecycleScope)

        viewModel.isToolbarShown
            .onEach { isShown ->
                if (isShown) {
                    showToolbar()
                } else {
                    hideToolbar()
                }
            }.launchIn(lifecycleScope)
    }

    /**
     * Shows a dialog for adding a new brush color.
     */
    private fun showAddColorDialog() {
        requireContext()
            .showColorPickerDialog(viewModel.brushColor.value) { color ->
                Timber.i("Added brush with color ${color.toRGBAHex()}")
                viewModel.addBrush(color)
            }
    }

    /**
     * Shows a confirmation dialog for removing a brush.
     */
    private fun showRemoveColorDialog(index: Int) {
        AlertDialog
            .Builder(requireContext())
            .setMessage(R.string.whiteboard_remove_brush_message)
            .setPositiveButton(R.string.dialog_remove) { dialog, _ ->
                Timber.i("Removed brush of index %d", index)
                viewModel.removeBrush(index)
            }.setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    /**
     * Shows a popup for adjusting the stroke width or color of a specific brush.
     */
    private fun showBrushConfigurationPopup(
        anchorView: View,
        brushIndex: Int,
    ) {
        Timber.i("Showing brush %d popup", brushIndex)

        val inflater = LayoutInflater.from(requireContext())
        val popupBrushBinding = PopupBrushOptionsBinding.inflate(inflater)

        val currentBrush = viewModel.brushes.value.getOrNull(brushIndex) ?: return

        val previewDrawable = (popupBrushBinding.colorPickerButton.icon as? LayerDrawable)
        val fillDrawable = previewDrawable?.findDrawableByLayerId(R.id.brush_preview_fill) as? GradientDrawable
        fillDrawable?.setColor(currentBrush.color)
        popupBrushBinding.colorPickerButton.icon = previewDrawable

        popupBrushBinding.colorPickerButton.setOnClickListener {
            showChangeColorDialog()
        }

        popupBrushBinding.strokeWidthSlider.value = currentBrush.width
        popupBrushBinding.strokeWidthValueIndicator.text = currentBrush.width.roundToInt().toString()
        popupBrushBinding.colorPickerButton.iconSize = currentBrush.width.roundToInt()

        // Set slider colors
        val color = currentBrush.color
        val colorStateList = ColorStateList.valueOf(color)
        popupBrushBinding.strokeWidthSlider.trackActiveTintList = colorStateList
        popupBrushBinding.strokeWidthSlider.thumbTintList = colorStateList
        popupBrushBinding.strokeWidthSlider.haloTintList = colorStateList

        popupBrushBinding.strokeWidthSlider.addOnChangeListener { _, value, fromUser ->
            // Dynamically change the size of the brush preview icon
            popupBrushBinding.colorPickerButton.iconSize = value.roundToInt()
            popupBrushBinding.strokeWidthValueIndicator.text = value.roundToInt().toString()

            if (fromUser) viewModel.setActiveStrokeWidth(value)
        }
        popupBrushBinding.strokeWidthSlider.setLabelFormatter { value: Float ->
            value.roundToInt().toString()
        }

        brushConfigPopup =
            PopupWindow(popupBrushBinding.root, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true)
        brushConfigPopup?.elevation = resources.getDimension(R.dimen.study_screen_elevation)
        brushConfigPopup?.setOnDismissListener {
            brushConfigPopup = null
        }

        popupBrushBinding.root.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val yOffset = -(anchorView.height + popupBrushBinding.root.measuredHeight)
        val xOffset = (anchorView.width - popupBrushBinding.root.measuredWidth) / 2
        brushConfigPopup?.showAsDropDown(anchorView, xOffset, yOffset)
    }

    /**
     * Shows a color picker popup to change the active brush's color.
     */
    private fun showChangeColorDialog() {
        requireContext()
            .showColorPickerDialog(viewModel.brushColor.value) { color ->
                viewModel.updateBrushColor(color)
                brushConfigPopup?.dismiss()
            }
    }

    /**
     * Shows a popup with eraser options (mode, width, clear).
     */
    private fun showEraserOptionsPopup(anchorView: View) {
        val inflater = LayoutInflater.from(requireContext())
        val eraserWidthBinding = PopupEraserOptionsBinding.inflate(inflater)

        eraserWidthBinding.eraserWidthSlider.value = viewModel.eraserDisplayWidth.value
        eraserWidthBinding.eraserWidthSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) viewModel.setActiveStrokeWidth(value)
        }
        eraserWidthBinding.eraserWidthSlider.setLabelFormatter { value: Float ->
            value.roundToInt().toString()
        }

        eraserWidthBinding.eraserModeToggleGroup.clearOnButtonCheckedListeners()
        when (viewModel.eraserMode.value) {
            EraserMode.STROKE -> eraserWidthBinding.eraserModeToggleGroup.check(R.id.eraser_mode_stroke)
            EraserMode.INK -> eraserWidthBinding.eraserModeToggleGroup.check(R.id.eraser_mode_ink)
        }
        eraserWidthBinding.eraserModeToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.eraser_mode_stroke -> {
                        viewModel.setEraserMode(EraserMode.STROKE)
                        eraserWidthBinding.eraserWidthSlider.value =
                            viewModel.strokeEraserStrokeWidth.value
                    }

                    R.id.eraser_mode_ink -> {
                        viewModel.setEraserMode(EraserMode.INK)
                        eraserWidthBinding.eraserWidthSlider.value =
                            viewModel.inkEraserStrokeWidth.value
                    }
                }
            }
        }

        eraserPopup = PopupWindow(eraserWidthBinding.root, 280.dp.toPx(requireContext()), ViewGroup.LayoutParams.WRAP_CONTENT, true)
        eraserPopup?.elevation = 8f
        eraserPopup?.setOnDismissListener {
            binding.whiteboardToolbar.updateSelection(viewModel.activeBrushIndex.value, viewModel.isEraserActive.value)
            eraserPopup = null
        }

        eraserWidthBinding.root.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val yOffset = -(anchorView.height + eraserWidthBinding.root.measuredHeight)
        val xOffset = (anchorView.width - eraserWidthBinding.root.measuredWidth) / 2
        eraserPopup?.showAsDropDown(anchorView, xOffset, yOffset)
    }

    @SuppressLint("RtlHardcoded")
    private fun updateToolbarPosition(alignment: ToolbarAlignment) {
        binding.whiteboardToolbar.updateLayoutParams<FrameLayout.LayoutParams> {
            gravity =
                when (alignment) {
                    ToolbarAlignment.BOTTOM -> Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    ToolbarAlignment.LEFT -> Gravity.LEFT or Gravity.CENTER_VERTICAL
                    ToolbarAlignment.RIGHT -> Gravity.RIGHT or Gravity.CENTER_VERTICAL
                }
        }
    }

    private fun showToolbar() {
        binding.whiteboardToolbar.post {
            binding.whiteboardToolbar.show()
        }
    }

    private fun hideToolbar() {
        binding.whiteboardToolbar.post {
            binding.whiteboardToolbar.hide()
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        Timber.i("WhiteboardFragment::onMenuItemClick %s", item.title)
        when (item.itemId) {
            R.id.action_add_brush -> showAddColorDialog()
            R.id.action_toggle_stylus -> {
                item.isChecked = !item.isChecked
                viewModel.toggleStylusOnlyMode()
            }
            R.id.action_hide_toolbar -> viewModel.setIsToolbarShown(false)
            R.id.action_align_left -> viewModel.setToolbarAlignment(ToolbarAlignment.LEFT)
            R.id.action_align_bottom -> viewModel.setToolbarAlignment(ToolbarAlignment.BOTTOM)
            R.id.action_align_right -> viewModel.setToolbarAlignment(ToolbarAlignment.RIGHT)
            R.id.action_clear -> viewModel.clearCanvas()
            else -> return false
        }
        return true
    }

    /**
     * Sets a listener to when the whiteboard is scrolled vertically,
     * which can happen by scrolling with two fingers, or with just one
     * if the `Stylus mode` is enabled.
     */
    fun setOnScrollByListener(listener: OnScrollByListener) {
        binding.whiteboardView.setOnScrollByListener(listener)
    }

    fun resetCanvas() = viewModel.reset()

    /**
     * @return whether the whiteboard is completely empty, including the undo and redo stacks.
     */
    fun isEmpty(): Boolean = !viewModel.canUndo.value && !viewModel.canRedo.value
}
