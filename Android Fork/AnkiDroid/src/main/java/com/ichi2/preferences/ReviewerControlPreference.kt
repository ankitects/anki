// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.preferences

import android.content.Context
import android.util.AttributeSet
import androidx.fragment.app.DialogFragment
import com.ichi2.anki.R
import com.ichi2.anki.cardviewer.GestureProcessor
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.dialogs.CardSideSelectionDialog
import com.ichi2.anki.preferences.allPreferences
import com.ichi2.anki.reviewer.Binding
import com.ichi2.anki.reviewer.CardSide
import com.ichi2.anki.reviewer.MappableBinding
import com.ichi2.anki.reviewer.MappableBinding.Companion.toPreferenceString
import com.ichi2.anki.reviewer.ReviewerBinding
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.utils.ext.usingStyledAttributes

open class ReviewerControlPreference : ControlPreference {
    open var side: CardSide? = null
        protected set

    @Suppress("unused")
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, androidx.preference.R.attr.dialogPreferenceStyle)
    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
    ) : this(context, attrs, defStyleAttr, android.R.attr.dialogPreferenceStyle)
    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int,
    ) : super(context, attrs, defStyleAttr, defStyleRes) {
        side =
            context.usingStyledAttributes(attrs, R.styleable.ReviewerControlPreference) {
                val value = getInt(R.styleable.ReviewerControlPreference_cardSide, -1)
                when (value) {
                    0 -> CardSide.QUESTION
                    1 -> CardSide.ANSWER
                    2 -> CardSide.BOTH
                    else -> null
                }
            }
    }

    override fun getSummary(): CharSequence =
        // Don't show the `Q:` and `A:` prefixes if the side is set
        if (side != null) {
            getMappableBindings().joinToString(", ") { it.binding.toDisplayString(context) }
        } else {
            super.getSummary()
        }

    override val areGesturesEnabled: Boolean
        get() = Prefs.isNewStudyScreenEnabled || sharedPreferences?.getBoolean(GestureProcessor.PREF_KEY, false) ?: false

    override fun makeDialogFragment(): DialogFragment = ReviewerControlPreferenceDialogFragment()

    override fun getMappableBindings(): List<ReviewerBinding> = ReviewerBinding.fromPreferenceString(value).toList()

    @Suppress("UNCHECKED_CAST")
    override fun getRelatedPreferences(): List<ReviewerControlPreference> =
        preferenceManager.preferenceScreen
            .allPreferences()
            .filter {
                it::class == ReviewerControlPreference::class
            } as List<ReviewerControlPreference>

    override fun getPreferenceAssignedTo(binding: Binding): ControlPreference? {
        val cardSide = side ?: return super.getPreferenceAssignedTo(binding)
        val reviewerBinding = ReviewerBinding(binding, cardSide)
        // Bindings only conflict when the card sides overlap
        return getPreferencesAssignedTo(reviewerBinding).firstOrNull()
    }

    @NeedsTest("Ensure correct preferences are returned for side-specific binding")
    private fun getPreferencesAssignedTo(binding: ReviewerBinding): List<ReviewerControlPreference> =
        getRelatedPreferences().filter { preference -> binding in preference.getMappableBindings() }

    fun interface OnBindingSelectedListener {
        /**
         * Called when a binding is selected, before the side is set. This allows listeners
         * to respond before the value is set, and potentially override it.
         *
         * @param binding The selected binding.
         *
         * @return True if the listener has consumed the event, false otherwise.
         */
        fun onBindingSelected(binding: Binding): Boolean
    }

    private var onBindingSelectedListener: OnBindingSelectedListener? = null

    fun setOnBindingSelectedListener(listener: OnBindingSelectedListener) {
        onBindingSelectedListener = listener
    }

    override fun onKeySelected(binding: Binding) = setBinding(binding)

    override fun onGestureSelected(binding: Binding) = setBinding(binding)

    override fun onAxisSelected(binding: Binding) = setBinding(binding)

    fun setBinding(binding: Binding) {
        if (onBindingSelectedListener?.onBindingSelected(binding) == true) return
        selectSide { side ->
            addBinding(binding, side)
        }
    }

    fun addBinding(
        binding: Binding,
        side: CardSide,
    ) {
        val newBinding = ReviewerBinding(binding, side)
        // Before adding new binding, remove all conflicting bindings
        getPreferencesAssignedTo(newBinding).forEach { preference ->
            preference.removeDuplicateBindings(newBinding)
        }
        val bindings = ReviewerBinding.fromPreferenceString(value).toMutableList()
        bindings.add(newBinding)
        value = bindings.toPreferenceString()
    }

    @NeedsTest("Check dup removal, including partial side overlap: e.g. QUESTION & BOTH")
    private fun removeDuplicateBindings(binding: ReviewerBinding) {
        val bindings = ReviewerBinding.fromPreferenceString(value).toMutableList()
        bindings.removeAll { it == binding } // Uses overridden .equals() to detect overlaps
        value = bindings.toPreferenceString()
    }

    /**
     * If this command can be executed on a single side, execute the callback on this side.
     * Otherwise, ask the user to select one or two side(s) and execute the callback on them.
     */
    private fun selectSide(callback: (c: CardSide) -> Unit) {
        val cardSide = side
        if (cardSide != null) {
            callback(cardSide)
        } else {
            CardSideSelectionDialog.displayInstance(context, callback)
        }
    }
}

class ReviewerControlPreferenceDialogFragment : ControlPreferenceDialogFragment() {
    override fun getDisplayString(mappableBinding: MappableBinding): String {
        val side = (preference as? ReviewerControlPreference)?.side
        return if (side != null) {
            mappableBinding.binding.toDisplayString(requireContext())
        } else {
            super.getDisplayString(mappableBinding)
        }
    }
}
