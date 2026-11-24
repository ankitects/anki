// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.reviewer.whiteboard

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.PopupWindow
import androidx.activity.OnBackPressedCallback
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CommonString
import com.ichi2.anki.DispatchKeyEventListener
import com.ichi2.anki.R
import com.ichi2.anki.android.back.doubleBackPressCallback
import com.ichi2.anki.cardviewer.Gesture
import com.ichi2.anki.common.utils.android.systemIsInNightMode
import com.ichi2.anki.common.utils.dp
import com.ichi2.anki.compat.CompatHelper.Companion.compat
import com.ichi2.anki.databinding.FragmentWhiteboardBinding
import com.ichi2.anki.databinding.PopupBrushOptionsBinding
import com.ichi2.anki.databinding.PopupEraserOptionsBinding
import com.ichi2.anki.preferences.reviewer.WhiteboardAction
import com.ichi2.anki.reviewer.BindingMap
import com.ichi2.anki.reviewer.ReviewerBinding
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.utils.ext.sharedPrefs
import com.ichi2.utils.increaseHorizontalPaddingOfMenuIcons
import com.ichi2.utils.toRGBAHex
import dev.androidbroadcast.vbpd.viewBinding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import timber.log.Timber
import kotlin.math.roundToInt
import com.ichi2.anki.common.android.R as CommonR

/**
 * Fragment that displays a whiteboard and its controls.
 */
class WhiteboardFragment :
    Fragment(R.layout.fragment_whiteboard),
    PopupMenu.OnMenuItemClickListener,
    DispatchKeyEventListener {
    @VisibleForTesting
    internal val viewModel: WhiteboardViewModel by viewModels {
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

        binding.whiteboardView.isStylusButtonOverriding = viewModel.isStylusOverriding
        binding.whiteboardView.onNewPath = viewModel::addPath
        binding.whiteboardView.onEraseGestureStart = viewModel::startPathEraseGesture
        binding.whiteboardView.onEraseGestureMove = viewModel::erasePathsToPoint
        binding.whiteboardView.onEraseGestureCancel = viewModel::cancelPathEraseGesture
        binding.whiteboardView.onEraseGestureEnd = viewModel::endPathEraseGesture
        binding.whiteboardView.onStylusButtonStateChanged = { isPressed ->
            viewModel.setStylusButtonPressed(isPressed)
            binding.whiteboardView.activeTool = viewModel.activeTool.value
            binding.whiteboardToolbar.updateSelection(
                viewModel.activeBrushIndex.value,
                viewModel.activeTool.value is WhiteboardTool.Eraser,
            )
        }
    }

    private fun setupDoubleBackPress() {
        doubleBackCallback =
            doubleBackPressCallback(
                enabled = computeDoubleBackEnabled(),
                onFirstBack = { showSnackbar(CommonString.back_pressed_once, Snackbar.LENGTH_SHORT) },
                shouldReEnable = { computeDoubleBackEnabled() },
            ).also {
                requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, it)
            }
        // Keep the callback in sync as the inputs change (host toggling drawing mode,
        // hidden state changes from the reviewer's show/hide transactions).
        viewModel.isDrawing
            .onEach { doubleBackCallback?.isEnabled = computeDoubleBackEnabled() }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun computeDoubleBackEnabled(): Boolean {
        val isUsingGesturesNavigation = context?.let { compat.isUsingSystemGestureNavigation(it) } == true
        return !viewModel.isDrawing.value && !isHidden && isUsingGesturesNavigation
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        doubleBackCallback?.isEnabled = computeDoubleBackEnabled()
    }

    /**
     * Switches the whiteboard into "drawing" mode, where the host owns back
     * navigation (e.g. a discard-changes dialog) and the reviewer's "go back again
     * to exit" snackbar is suppressed.
     */
    fun setDrawingMode(drawing: Boolean) {
        viewModel.isDrawing.value = drawing
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
            if (viewModel.activeTool.value is WhiteboardTool.Eraser) {
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
            if (viewModel.activeBrushIndex.value == index && viewModel.activeTool.value !is WhiteboardTool.Eraser) {
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
                showSnackbar(CommonString.cannot_remove_last_brush_message)
            }
        }

        viewModel.canUndo.onEach { toolbar.undoButton.isEnabled = it }.launchIn(viewLifecycleOwner.lifecycleScope)
        viewModel.canRedo.onEach { toolbar.redoButton.isEnabled = it }.launchIn(viewLifecycleOwner.lifecycleScope)

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
            val result = onGesture(gesture)
            if (!result) {
                gestureFallbackListener?.invoke(gesture)
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (isHidden || event.action != KeyEvent.ACTION_DOWN) return false
        return bindingMap.onKeyDown(event)
    }

    fun onScreenShake(): Boolean = !isHidden && onGesture(Gesture.SHAKE)

    /** Handles gestures detected on the whiteboard or on the card beneath it in stylus mode. */
    fun onGesture(gesture: Gesture): Boolean = bindingMap.onGesture(gesture)

    /**
     * Sets up observers for the ViewModel's flows.
     */
    private fun observeViewModel(whiteboardView: WhiteboardView) {
        val toolbar = binding.whiteboardToolbar

        viewModel.paths.onEach(whiteboardView::setHistory).launchIn(viewLifecycleOwner.lifecycleScope)

        viewModel.activeTool
            .onEach { tool ->
                whiteboardView.activeTool = tool
                val isEraser = tool is WhiteboardTool.Eraser
                toolbar.updateSelection(viewModel.activeBrushIndex.value, isEraser)
                toolbar.eraserButton.updateState(isEraser, viewModel.eraser.mode, viewModel.eraser.width)
                if (!isEraser) {
                    eraserPopup?.dismiss()
                }
            }.launchIn(viewLifecycleOwner.lifecycleScope)

        viewModel.activeBrushIndex
            .onEach { index ->
                toolbar.updateSelection(index, viewModel.activeTool.value is WhiteboardTool.Eraser)
            }.launchIn(viewLifecycleOwner.lifecycleScope)

        viewModel.brushes
            .onEach { brushesInfo ->
                toolbar.setBrushes(brushesInfo, viewModel.activeBrushIndex.value, viewModel.activeTool.value is WhiteboardTool.Eraser)
            }.launchIn(viewLifecycleOwner.lifecycleScope)

        viewModel.isStylusOnlyMode
            .onEach { isEnabled ->
                whiteboardView.isStylusOnlyMode = isEnabled
            }.launchIn(viewLifecycleOwner.lifecycleScope)

        viewModel.toolbarAlignment
            .onEach { alignment ->
                toolbar.setAlignment(alignment)
                updateToolbarPosition(alignment)
            }.launchIn(viewLifecycleOwner.lifecycleScope)

        viewModel.isToolbarShown
            .onEach { isShown ->
                if (isShown) {
                    toolbar.show()
                } else {
                    toolbar.hide()
                }
            }.launchIn(viewLifecycleOwner.lifecycleScope)
    }

    /**
     * Shows a dialog for adding a new brush color.
     */
    private fun showAddColorDialog() {
        requireContext()
            .showColorPickerDialog(viewModel.currentBrushColor) { color ->
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
            .setMessage(CommonString.whiteboard_remove_brush_message)
            .setPositiveButton(CommonString.dialog_remove) { dialog, _ ->
                Timber.i("Removed brush of index %d", index)
                viewModel.removeBrush(index)
            }.setNegativeButton(CommonString.dialog_cancel, null)
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

        val typedValue = TypedValue()
        val hasAttr = requireContext().theme.resolveAttribute(CommonR.attr.studyScreenElevation, typedValue, true)
        brushConfigPopup?.elevation =
            if (hasAttr && typedValue.type == TypedValue.TYPE_DIMENSION) {
                typedValue.getDimension(resources.displayMetrics)
            } else {
                0f
            }
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
            .showColorPickerDialog(viewModel.currentBrushColor) { color ->
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

        eraserWidthBinding.eraserWidthSlider.value = viewModel.eraser.width
        eraserWidthBinding.eraserWidthSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) viewModel.setEraserStrokeWidth(value)
        }
        eraserWidthBinding.eraserWidthSlider.setLabelFormatter { value: Float ->
            value.roundToInt().toString()
        }

        eraserWidthBinding.eraserModeToggleGroup.clearOnButtonCheckedListeners()
        when (viewModel.eraser.mode) {
            EraserMode.STROKE -> eraserWidthBinding.eraserModeToggleGroup.check(R.id.eraser_mode_stroke)
            EraserMode.INK -> eraserWidthBinding.eraserModeToggleGroup.check(R.id.eraser_mode_ink)
        }
        eraserWidthBinding.eraserModeToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.eraser_mode_stroke -> {
                        viewModel.setEraserMode(EraserMode.STROKE)
                        eraserWidthBinding.eraserWidthSlider.value = viewModel.eraser.strokeEraserWidth
                    }
                    R.id.eraser_mode_ink -> {
                        viewModel.setEraserMode(EraserMode.INK)
                        eraserWidthBinding.eraserWidthSlider.value = viewModel.eraser.inkWidth
                    }
                }
            }
        }

        eraserPopup = PopupWindow(eraserWidthBinding.root, 280.dp.toPx(requireContext()), ViewGroup.LayoutParams.WRAP_CONTENT, true)
        eraserPopup?.elevation = 8f
        eraserPopup?.setOnDismissListener {
            binding.whiteboardToolbar.updateSelection(viewModel.activeBrushIndex.value, viewModel.activeTool.value is WhiteboardTool.Eraser)
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

    /**
     * Emits `true` when the whiteboard is empty (cannot undo or redo) and `false` otherwise.
     * Useful for hosts that need to react to content changes, e.g. to toggle a back-press
     * callback's `isEnabled`.
     */
    val isEmptyFlow: Flow<Boolean>
        get() =
            combine(viewModel.canUndo, viewModel.canRedo) { canUndo, canRedo ->
                !canUndo && !canRedo
            }
}
