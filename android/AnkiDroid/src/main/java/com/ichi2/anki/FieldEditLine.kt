/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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
package com.ichi2.anki

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.ClassLoaderCreator
import android.util.AttributeSet
import android.util.SparseArray
import android.view.ActionMode
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.DrawableRes
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.os.ParcelCompat
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import com.ichi2.anki.common.utils.android.getDensityAdjustedValue
import com.ichi2.anki.common.utils.annotation.KotlinCleanup
import com.ichi2.anki.compat.setTooltipTextCompat
import com.ichi2.anki.databinding.ViewCardMultimediaEditlineBinding
import com.ichi2.ui.AnimationUtil.collapseView
import com.ichi2.ui.AnimationUtil.expandView
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
    public override fun onSaveInstanceState(): Parcelable? {
        val state = super.onSaveInstanceState()
        val savedState = SavedState(state)
        savedState.childrenStates = SparseArray()
        savedState.editTextId = binding.editText.id
        savedState.toggleStickyId = binding.toggleSticky.id
        savedState.mediaButtonId = binding.mediaButton.id
        savedState.expandButtonId = binding.expandButton.id
        for (i in 0 until childCount) {
            getChildAt(i).saveHierarchyState(savedState.childrenStates)
        }
        savedState.expansionState = expansionState
        return savedState
    }

    public override fun onRestoreInstanceState(state: Parcelable) {
        if (state !is SavedState) {
            super.onRestoreInstanceState(state)
            return
        }
        val editTextId = binding.editText.id
        val toggleStickyId = binding.toggleSticky.id
        val mediaButtonId = binding.mediaButton.id
        val expandButtonId = binding.expandButton.id
        binding.editText.id = state.editTextId
        binding.toggleSticky.id = state.toggleStickyId
        binding.mediaButton.id = state.mediaButtonId
        binding.expandButton.id = state.expandButtonId
        super.onRestoreInstanceState(state.superState)
        for (i in 0 until childCount) {
            getChildAt(i).restoreHierarchyState(state.childrenStates)
        }
        binding.editText.id = editTextId
        binding.toggleSticky.id = toggleStickyId
        binding.mediaButton.id = mediaButtonId
        binding.expandButton.id = expandButtonId
        if (expansionState != state.expansionState) {
            toggleExpansionState()
        }
        expansionState = state.expansionState ?: ExpansionState.EXPANDED
    }

    @KotlinCleanup("convert to parcelable")
    internal class SavedState : BaseSavedState {
        var childrenStates: SparseArray<Parcelable>? = null
        var editTextId = 0
        var toggleStickyId = 0
        var mediaButtonId = 0
        var expandButtonId = 0
        var expansionState: ExpansionState? = null

        constructor(superState: Parcelable?) : super(superState)

        override fun writeToParcel(
            out: Parcel,
            flags: Int,
        ) {
            super.writeToParcel(out, flags)
            out.writeSparseArray(childrenStates)
            out.writeInt(editTextId)
            out.writeInt(toggleStickyId)
            out.writeInt(mediaButtonId)
            out.writeInt(expandButtonId)
            out.writeSerializable(expansionState)
        }

        private constructor(source: Parcel, loader: ClassLoader) : super(source) {
            childrenStates = ParcelCompat.readSparseArray(source, loader, Parcelable::class.java)
            editTextId = source.readInt()
            toggleStickyId = source.readInt()
            mediaButtonId = source.readInt()
            expandButtonId = source.readInt()
            expansionState =
                ParcelCompat.readSerializable(
                    source,
                    ExpansionState::class.java.classLoader,
                    ExpansionState::class.java,
                )
        }

        companion object {
            @JvmField // required field that makes Parcelables from a Parcel
            @Suppress("unused")
            val CREATOR: Parcelable.Creator<SavedState> =
                object : ClassLoaderCreator<SavedState> {
                    override fun createFromParcel(
                        source: Parcel,
                        loader: ClassLoader,
                    ): SavedState = SavedState(source, loader)

                    override fun createFromParcel(source: Parcel): SavedState = throw IllegalStateException()

                    override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
                }
        }
    }

    enum class ExpansionState {
        EXPANDED,
        COLLAPSED,
    }
}
