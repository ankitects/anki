// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.ui

import android.content.Context
import android.view.KeyEvent
import android.view.LayoutInflater
import com.ichi2.anki.databinding.DialogKeyPickerBinding
import com.ichi2.anki.dialogs.WarningDisplay
import com.ichi2.anki.reviewer.Binding
import timber.log.Timber

typealias KeyCode = Int

/**
 * Square dialog which allows a user to select a [Binding] for a key press
 * This does not yet support bluetooth headsets.
 */
class KeyPicker(
    private val viewBinding: DialogKeyPickerBinding,
) : WarningDisplay {
    val rootLayout = viewBinding.root

    override val warningTextView: FixedTextView = viewBinding.warning

    private val context: Context get() = rootLayout.context

    private var text: String
        set(value) {
            viewBinding.selectedKey.text = value
        }
        get() = viewBinding.selectedKey.text.toString()

    private var onBindingChangedListener: ((Binding) -> Unit)? = null
    private var isValidKeyCode: ((KeyCode) -> Boolean)? = null

    /** Currently bound key */
    private var binding: Binding? = null

    fun getBinding(): Binding? = binding

    fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return true

        // When accepting a keypress, we only want to find the keycode, not the unicode character.
        val newBinding =
            Binding
                .possibleKeyBindings(event)
                .filterIsInstance<Binding.KeyCode>()
                .firstOrNull { binding -> isValidKeyCode?.invoke(binding.keycode) != false } ?: return true
        Timber.d("Changed key to '%s'", newBinding)
        binding = newBinding
        text = newBinding.toDisplayString(context)
        onBindingChangedListener?.invoke(newBinding)
        return true
    }

    fun setBindingChangedListener(listener: (Binding) -> Unit) {
        onBindingChangedListener = listener
    }

    fun setKeycodeValidation(validation: (KeyCode) -> Boolean) {
        isValidKeyCode = validation
    }

    init {
        viewBinding.selectedKey.requestFocus()
        viewBinding.selectedKey.setOnKeyListener { _, _, event -> dispatchKeyEvent(event) }
    }

    companion object {
        fun inflate(context: Context): KeyPicker {
            val layoutInflater = LayoutInflater.from(context)
            val binding = DialogKeyPickerBinding.inflate(layoutInflater)
            return KeyPicker(binding)
        }
    }
}
