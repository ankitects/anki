/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
 *  Copyright (c) 2025 Brayan Oliveira <brayandso.dev@gmail.com>
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
package com.ichi2.preferences

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.preference.DialogPreference
import androidx.preference.PreferenceFragmentCompat
import com.ichi2.anki.R
import com.ichi2.anki.databinding.DialogControlPreferenceBinding
import com.ichi2.anki.dialogs.GestureSelectionDialogUtils
import com.ichi2.anki.dialogs.GestureSelectionDialogUtils.onGestureChanged
import com.ichi2.anki.dialogs.KeySelectionDialogUtils
import com.ichi2.anki.dialogs.WarningDisplay
import com.ichi2.anki.preferences.SettingsFragment
import com.ichi2.anki.preferences.allPreferences
import com.ichi2.anki.preferences.requirePreference
import com.ichi2.anki.reviewer.Binding
import com.ichi2.anki.reviewer.MappableBinding
import com.ichi2.anki.reviewer.MappableBinding.Companion.toPreferenceString
import com.ichi2.anki.utils.ext.requireString
import com.ichi2.ui.AxisPicker
import com.ichi2.ui.GesturePicker
import com.ichi2.ui.KeyPicker
import com.ichi2.utils.create
import com.ichi2.utils.customView
import com.ichi2.utils.dp
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show

/**
 * A preference which allows mapping of inputs to actions (example: keys -> commands)
 *
 * The user is allowed to either add or remove previously mapped keys
 */
open class ControlPreference :
    DialogPreference,
    DialogFragmentProvider {
    @Suppress("unused")
    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int,
    ) : super(context, attrs, defStyleAttr, defStyleRes)

    @Suppress("unused")
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    @Suppress("unused")
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    @Suppress("unused")
    constructor(context: Context) : super(context)

    open fun getMappableBindings(): List<MappableBinding> = MappableBinding.fromPreferenceString(value)

    protected open fun onKeySelected(binding: Binding): Unit = addBinding(binding)

    protected open fun onAxisSelected(binding: Binding): Unit = addBinding(binding)

    open val areGesturesEnabled: Boolean = false

    protected open fun onGestureSelected(binding: Binding) = Unit

    /** @return whether the binding is used in another action */
    open fun warnIfUsed(
        binding: Binding,
        warningDisplay: WarningDisplay,
    ): Boolean {
        val bindingPreference = getPreferenceAssignedTo(binding) ?: return false
        if (bindingPreference == this) return false
        val actionTitle = bindingPreference.title ?: ""
        val warning = context.getString(R.string.bindings_already_bound, actionTitle)
        warningDisplay.setWarning(warning)
        return true
    }

    var value: String?
        get() = getPersistedString(null)
        set(value) {
            if (value != getPersistedString(null)) {
                persistString(value)
                notifyChanged()
            }
        }

    override fun getSummary(): CharSequence = getMappableBindings().joinToString(", ") { it.toDisplayString(context) }

    override fun makeDialogFragment(): DialogFragment = ControlPreferenceDialogFragment()

    protected open fun createGesturePicker(): GesturePicker = GestureSelectionDialogUtils.getGesturePicker(context)

    fun showGesturePickerDialog() {
        AlertDialog.Builder(context).show {
            setTitle(title)
            setIcon(icon)
            val gesturePicker = createGesturePicker()
            positiveButton(R.string.dialog_ok) {
                val gesture = gesturePicker.getGesture() ?: return@positiveButton
                val binding = Binding.GestureInput(gesture)
                onGestureSelected(binding)
                it.dismiss()
            }
            negativeButton(R.string.dialog_cancel) { it.dismiss() }
            customView(view = gesturePicker)
            gesturePicker.onGestureChanged { gesture ->
                warnIfUsedOrClearWarning(Binding.GestureInput(gesture), gesturePicker)
            }
        }
    }

    fun showKeyPickerDialog() {
        AlertDialog.Builder(context).show {
            val keyPicker: KeyPicker = KeyPicker.inflate(context)
            customView(view = keyPicker.rootLayout)
            setTitle(title)
            setIcon(icon)

            // When the user presses a key
            keyPicker.setBindingChangedListener { binding ->
                warnIfUsedOrClearWarning(binding, keyPicker)
            }
            positiveButton(R.string.dialog_ok) {
                val binding = keyPicker.getBinding() ?: return@positiveButton
                onKeySelected(binding)
                it.dismiss()
            }
            negativeButton(R.string.dialog_cancel) { it.dismiss() }
            keyPicker.setKeycodeValidation(KeySelectionDialogUtils.disallowModifierKeyCodes())
        }
    }

    fun showAddAxisDialog() {
        val axisPicker = AxisPicker(context)
        val dialog =
            AlertDialog.Builder(context).create {
                customView(view = axisPicker.binding.root)
                setTitle(title)
                setIcon(icon)
                negativeButton(R.string.dialog_cancel) { it.dismiss() }
            }
        axisPicker.setBindingChangedListener { binding ->
            dialog.dismiss()
            val bindingPref = getPreferenceAssignedTo(binding)
            if (bindingPref != null && bindingPref != this) {
                AlertDialog.Builder(context).show {
                    val warning = context.getString(R.string.bindings_already_bound, bindingPref.title)
                    setTitle(binding.toDisplayString(context))
                    setMessage(warning)
                    setPositiveButton(R.string.dialog_positive_replace) { _, _ ->
                        onAxisSelected(binding)
                    }
                    setNegativeButton(R.string.dialog_cancel) { _, _ -> }
                }
            } else {
                onAxisSelected(binding)
            }
        }
        dialog.show()
    }

    protected fun warnIfUsedOrClearWarning(
        binding: Binding,
        warningDisplay: WarningDisplay,
    ) {
        if (!warnIfUsed(binding, warningDisplay)) {
            warningDisplay.clearWarning()
        }
    }

    fun removeMappableBinding(binding: MappableBinding) {
        val bindings = getMappableBindings().toMutableList()
        bindings.remove(binding)
        value = bindings.toPreferenceString()
    }

    private fun addBinding(binding: Binding) {
        val newBinding = MappableBinding(binding)
        getPreferenceAssignedTo(binding)?.removeMappableBinding(newBinding)
        val bindings = getMappableBindings().toMutableList()
        bindings.add(newBinding)
        value = bindings.toPreferenceString()
    }

    /**
     * @return a list of preferences related to the same context or screen.
     */
    protected open fun getRelatedPreferences(): List<ControlPreference> =
        preferenceManager.preferenceScreen.allPreferences().filterIsInstance<ControlPreference>()

    /**
     * Checks if any other related preference
     * has the given [binding] assigned to.
     *
     * @see getRelatedPreferences
     */
    protected open fun getPreferenceAssignedTo(binding: Binding): ControlPreference? {
        for (pref in getRelatedPreferences()) {
            val bindings = pref.getMappableBindings().map { it.binding }
            if (binding in bindings) {
                return pref
            }
        }
        return null
    }
}

open class ControlPreferenceDialogFragment : DialogFragment() {
    protected lateinit var preference: ControlPreference

    @Suppress("DEPRECATION") // targetFragment
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val key = requireArguments().requireString(SettingsFragment.PREF_DIALOG_KEY)
        preference = (targetFragment as PreferenceFragmentCompat).requirePreference(key)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogControlPreferenceBinding.inflate(requireActivity().layoutInflater)

        setupAddBindingDialogs(binding)
        setupRemoveControlEntries(binding)

        return AlertDialog.Builder(requireContext()).create {
            setTitle(preference.title)
            setIcon(preference.icon)
            customView(binding.root, paddingTop = 16.dp.toPx(context))
            negativeButton(R.string.dialog_cancel)
        }
    }

    private fun setupAddBindingDialogs(binding: DialogControlPreferenceBinding) {
        binding.addGesture.apply {
            setOnClickListener {
                preference.showGesturePickerDialog()
                dismiss()
            }
            isVisible = preference.areGesturesEnabled
        }

        binding.addKey.setOnClickListener {
            preference.showKeyPickerDialog()
            dismiss()
        }

        binding.addAxis.setOnClickListener {
            preference.showAddAxisDialog()
            dismiss()
        }
    }

    private fun setupRemoveControlEntries(binding: DialogControlPreferenceBinding) {
        val bindings = preference.getMappableBindings().toMutableList()
        if (bindings.isEmpty()) {
            binding.listView.isVisible = false
            return
        }
        val titles =
            bindings.map {
                getString(R.string.binding_remove_binding, getDisplayString(it))
            }
        binding.listView.apply {
            adapter = ArrayAdapter(requireContext(), R.layout.item_control_preference, titles)
            setOnItemClickListener { _, _, index, _ ->
                bindings.removeAt(index)
                preference.value = bindings.toPreferenceString()
                dismiss()
            }
        }
    }

    /** @return how a binding should be displayed to the user */
    protected open fun getDisplayString(mappableBinding: MappableBinding): String = mappableBinding.toDisplayString(requireContext())
}
