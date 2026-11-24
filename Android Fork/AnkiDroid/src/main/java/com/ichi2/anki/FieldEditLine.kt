// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Parcelable
import android.util.AttributeSet
import android.util.SparseArray
import android.view.ActionMode
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.DrawableRes
import androidx.constraintlayout.widget.ConstraintSet
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import com.ichi2.anki.common.utils.android.getDensityAdjustedValue
import com.ichi2.anki.common.utils.annotation.KotlinCleanup
import com.ichi2.anki.compat.setTooltipTextCompat
import com.ichi2.anki.databinding.ViewCardMultimediaEditlineBinding
import com.ichi2.ui.AnimationUtil.collapseView
import com.ichi2.ui.AnimationUtil.expandView
import kotlinx.parcelize.Parcelize
import java.util.Locale

@KotlinCleanup("replace _name with `field`")
class FieldEditLine : FrameLayout {
    val binding = ViewCardMultimediaEditlineBinding.inflate(LayoutInflater.from(context), this, true)
    private var _name: String? = null
    private var expansionState = ExpansionState.EXPANDED

    var enableAnimation = true

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    init {
        binding.toggleSticky.setTooltipTextCompat(CollectionManager.TR.editingToggleSticky())
        // 7433 -
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            binding.editText.id = generateViewId()
            binding.toggleSticky.id = generateViewId()
            binding.mediaButton.id = generateViewId()
            binding.expandButton.id = generateViewId()
            binding.editText.nextFocusForwardId = binding.toggleSticky.id
            binding.toggleSticky.nextFocusForwardId = binding.mediaButton.id
            binding.mediaButton.nextFocusForwardId = binding.expandButton.id
            ConstraintSet().apply {
                clone(binding.constraintLayout)
                connect(binding.toggleSticky.id, ConstraintSet.END, binding.mediaButton.id, ConstraintSet.START)
                connect(binding.mediaButton.id, ConstraintSet.END, binding.expandButton.id, ConstraintSet.START)
                applyTo(binding.constraintLayout)
            }
        }
        setExpanderBackgroundImage()
        binding.expandButton.setOnClickListener { toggleExpansionState() }
        binding.editText.init()
        binding.label.setPaddingRelative(getDensityAdjustedValue(context, 3.4f).toInt(), 0, 0, 0)
    }

    private fun toggleExpansionState() {
        expansionState =
            when (expansionState) {
                ExpansionState.EXPANDED -> {
                    collapseView(binding.editText, enableAnimation)
                    ExpansionState.COLLAPSED
                }
                ExpansionState.COLLAPSED -> {
                    expandView(binding.editText, enableAnimation)
                    ExpansionState.EXPANDED
                }
            }
        setExpanderBackgroundImage()
    }

    private fun setExpanderBackgroundImage() {
        when (expansionState) {
            ExpansionState.COLLAPSED -> binding.expandButton.background = getBackgroundImage(R.drawable.ic_expand_more_black_24dp_xml)
            ExpansionState.EXPANDED -> binding.expandButton.background = getBackgroundImage(R.drawable.ic_expand_less_black_24dp)
        }
    }

    private fun getBackgroundImage(
        @DrawableRes idRes: Int,
    ): Drawable? = VectorDrawableCompat.create(this.resources, idRes, context.theme)

    fun setActionModeCallbacks(callback: ActionMode.Callback?) {
        binding.editText.customSelectionActionModeCallback = callback
        binding.editText.customInsertionActionModeCallback = callback
    }

    fun setHintLocale(hintLocale: Locale?) {
        if (hintLocale != null) {
            binding.editText.setHintLocale(hintLocale)
        }
    }

    fun setContent(
        content: String?,
        replaceNewline: Boolean,
    ) {
        binding.editText.setContent(content, replaceNewline)
    }

    fun setOrd(i: Int) {
        binding.editText.ord = i
    }

    var name: String?
        get() = _name
        set(name) {
            _name = name
            binding.editText.contentDescription = name
            binding.label.text = name
        }

    val lastViewInTabOrder: View
        get() = binding.expandButton

    override fun dispatchSaveInstanceState(container: SparseArray<Parcelable>) {
        dispatchFreezeSelfOnly(container)
    }

    override fun dispatchRestoreInstanceState(container: SparseArray<Parcelable>) {
        dispatchThawSelfOnly(container)
    }

    // keeps platform defined nullability
    @Suppress("RedundantNullableReturnType")
    public override fun onSaveInstanceState(): Parcelable? =
        SavedState(
            state = super.onSaveInstanceState(),
            name = _name,
            content = binding.editText.text?.toString(),
            ord = binding.editText.ord,
            expansionState = expansionState,
            selectionStart = binding.editText.selectionStart,
            selectionEnd = binding.editText.selectionEnd,
        )

    public override fun onRestoreInstanceState(state: Parcelable) {
        if (state !is SavedState) {
            super.onRestoreInstanceState(state)
            return
        }
        super.onRestoreInstanceState(state.superState)
        name = state.name
        setContent(state.content, replaceNewline = false)
        // put the cursor/selection back where it was, ignoring stale out of range values
        val textLength = binding.editText.text?.length ?: 0
        if (state.selectionStart in 0..textLength && state.selectionEnd in 0..textLength) {
            binding.editText.setSelection(state.selectionStart, state.selectionEnd)
        }
        binding.editText.ord = state.ord
        if (expansionState != state.expansionState) {
            toggleExpansionState()
        }
        expansionState = state.expansionState
    }

    @Parcelize
    internal class SavedState(
        val state: Parcelable?,
        val name: String?,
        val content: String?,
        val ord: Int,
        val expansionState: ExpansionState,
        val selectionStart: Int,
        val selectionEnd: Int,
    ) : BaseSavedState(state)

    enum class ExpansionState {
        EXPANDED,
        COLLAPSED,
    }
}
