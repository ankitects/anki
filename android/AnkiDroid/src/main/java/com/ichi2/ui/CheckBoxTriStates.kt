/*
 Copyright (c) 2021 Tarek Mohamed Abdalla <tarekkma@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.ui

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatCheckBox
import com.ichi2.anki.R
import com.ichi2.anki.common.utils.annotation.KotlinCleanup

/**
 * Based on https://gist.github.com/kevin-barrientos/d75a5baa13a686367d45d17aaec7f030.
 */
@KotlinCleanup("_state -> field = value in setter")
class CheckBoxTriStates : AppCompatCheckBox {
    enum class State {
        INDETERMINATE,
        UNCHECKED,
        CHECKED,
    }

    private var _state: State = State.UNCHECKED

    override fun setChecked(checked: Boolean) {
        _state =
            if (checked) {
                State.CHECKED
            } else {
                State.UNCHECKED
            }
    }

    override fun setOnCheckedChangeListener(listener: OnCheckedChangeListener?) {
        // we never truly set the listener to the client implementation, instead we only hold
        // a reference to it and invoke it when needed.
        if (privateListener !== listener) {
            clientListener = listener
        }

        // always use our implementation
        super.setOnCheckedChangeListener(privateListener)
    }

    var cycleCheckedToIndeterminate = false
    var cycleIndeterminateToChecked = false

    /**
     * This is the listener set to the super class which is going to be invoked each
     * time the check state has changed.
     */
    private val privateListener =
        OnCheckedChangeListener { _, _ ->
            // checkbox status is changed from unchecked to checked.
            toggle()
        }

    /**
     * Holds a reference to the listener set by a client, if any.
     */
    private var clientListener: OnCheckedChangeListener? = null

    /**
     * This flag is needed to avoid accidentally changing the current [_state] when
     * [onRestoreInstanceState] calls [setChecked]
     * invoking our [privateListener] and therefore changing the real state.
     */
    private var restoring = false

    constructor(context: Context) : super(context) {
        init(context, null)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(context, attrs)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init(context, attrs)
    }

    var state: State
        get() = _state
        set(state) {
            if (!restoring && _state != state) {
                _state = state
                clientListener?.onCheckedChanged(this, this.isChecked)
                updateBtn()
            }
        }

    override fun toggle() {
        state =
            when (_state) {
                State.INDETERMINATE ->
                    if (cycleIndeterminateToChecked) {
                        State.CHECKED
                    } else {
                        State.UNCHECKED
                    }
                State.UNCHECKED -> State.CHECKED
                State.CHECKED ->
                    if (cycleCheckedToIndeterminate) {
                        State.INDETERMINATE
                    } else {
                        State.UNCHECKED
                    }
            }
    }

    override fun isChecked(): Boolean = _state != State.UNCHECKED

    override fun onSaveInstanceState(): Parcelable {
        val superState = super.onSaveInstanceState()
        val savedState = SavedState(superState)
        savedState.state = _state
        savedState.cycleCheckedToIndeterminate = cycleCheckedToIndeterminate
        savedState.cycleIndeterminateToChecked = cycleIndeterminateToChecked
        return savedState
    }

    override fun onRestoreInstanceState(state: Parcelable) {
        restoring = true // indicates that the ui is restoring its state
        val savedState = state as SavedState
        super.onRestoreInstanceState(savedState.superState)
        this.state = savedState.state
        cycleCheckedToIndeterminate = savedState.cycleCheckedToIndeterminate
        cycleIndeterminateToChecked = savedState.cycleIndeterminateToChecked
        requestLayout()
        restoring = false
    }

    private fun init(
        context: Context,
        attrs: AttributeSet?,
    ) {
        cycleCheckedToIndeterminate = true
        cycleIndeterminateToChecked = false
        if (attrs != null) {
            val a =
                context.theme.obtainStyledAttributes(
                    attrs,
                    R.styleable.CheckBoxTriStates,
                    0,
                    0,
                )
            cycleCheckedToIndeterminate =
                a.getBoolean(
                    R.styleable.CheckBoxTriStates_cycle_checked_to_indeterminate,
                    cycleCheckedToIndeterminate,
                )
            cycleIndeterminateToChecked =
                a.getBoolean(
                    R.styleable.CheckBoxTriStates_cycle_indeterminate_to_checked,
                    cycleIndeterminateToChecked,
                )
        }
        updateBtn()
        setOnCheckedChangeListener(privateListener)
    }

    private fun updateBtn() {
        val btnDrawable: Int =
            when (_state) {
                State.UNCHECKED -> R.drawable.ic_baseline_check_box_outline_blank_24_inset
                State.CHECKED -> R.drawable.ic_baseline_check_box_24_inset
                State.INDETERMINATE -> R.drawable.ic_baseline_indeterminate_check_box_24_inset
            }
        setButtonDrawable(btnDrawable)
    }

    @KotlinCleanup("https://stackoverflow.com/a/69476454")
    private class SavedState : BaseSavedState {
        lateinit var state: State
        var cycleCheckedToIndeterminate = false
        var cycleIndeterminateToChecked = false

        constructor(superState: Parcelable?) : super(superState)
        private constructor(source: Parcel) : super(source) {
            state = State.entries[source.readInt()]
            cycleCheckedToIndeterminate = source.readInt() != 0
            cycleIndeterminateToChecked = source.readInt() != 0
        }

        override fun writeToParcel(
            out: Parcel,
            flags: Int,
        ) {
            super.writeToParcel(out, flags)
            out.writeValue(state)
            out.writeInt(if (cycleCheckedToIndeterminate) 1 else 0)
            out.writeInt(if (cycleIndeterminateToChecked) 1 else 0)
        }

        override fun toString(): String =
            (
                "CheckboxTriState.SavedState{" +
                    Integer.toHexString(System.identityHashCode(this)) +
                    " state=" + state +
                    " cycleCheckedToIndeterminate=" + cycleCheckedToIndeterminate +
                    " cycleIndeterminateToChecked=" + cycleIndeterminateToChecked + "}"
            )

        companion object {
            @JvmField // required field that makes Parcelables from a Parcel
            @Suppress("unused")
            val CREATOR: Parcelable.Creator<SavedState> =
                object : Parcelable.Creator<SavedState> {
                    override fun createFromParcel(source: Parcel): SavedState = SavedState(source)

                    override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
                }
        }
    }
}
