// SPDX-License-Identifier: GPL-3.0-or-later

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
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.common.utils.dp
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
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show

/**
 * Maps keyboard, gesture, and joystick bindings to the action identified by [key].
 *
 * Users can add or remove bindings. Adding a binding can replace its assignment to another related action.
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

    /**
     * Called after the user confirms a key binding. The base implementation adds it to this action.
     */
    protected open fun onKeySelected(binding: Binding): Unit = addBinding(binding)

    /**
     * Called after an axis selection and any confirmation to replace another action's binding.
     * The base implementation adds the binding to this action.
     */
    protected open fun onAxisSelected(binding: Binding): Unit = addBinding(binding)

    open val areGesturesEnabled: Boolean = false

    /**
     * Called after the user confirms a gesture. Subclasses must override this to store the binding.
     */
    protected open fun onGestureSelected(binding: Binding) = Unit

    /** @return the title of the other action using [binding], or null if there is no conflict */
    open fun otherActionTitleForBinding(binding: Binding): CharSequence? {
        val bindingPreference = getPreferenceAssignedTo(binding) ?: return null
        if (bindingPreference == this) return null
        return bindingPreference.title ?: ""
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
            positiveButton(CommonString.dialog_ok) {
                val gesture = gesturePicker.getGesture() ?: return@positiveButton
                val binding = Binding.GestureInput(gesture)
                onGestureSelected(binding)
                it.dismiss()
            }
            negativeButton(CommonString.dialog_cancel) { it.dismiss() }
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
            positiveButton(CommonString.dialog_ok) {
                val binding = keyPicker.getBinding() ?: return@positiveButton
                onKeySelected(binding)
                it.dismiss()
            }
            negativeButton(CommonString.dialog_cancel) { it.dismiss() }
            keyPicker.setKeycodeValidation(KeySelectionDialogUtils.disallowModifierKeyCodes())
        }
    }

    /**
     * Shows a joystick axis picker and asks for confirmation before replacing another action's binding.
     */
    fun showAddAxisDialog() {
        val axisPicker = AxisPicker(context)
        val dialog =
            AlertDialog.Builder(context).create {
                customView(view = axisPicker.binding.root)
                setTitle(title)
                setIcon(icon)
                negativeButton(CommonString.dialog_cancel) { it.dismiss() }
            }
        axisPicker.setBindingChangedListener { binding ->
            dialog.dismiss()
            val bindingPref = getPreferenceAssignedTo(binding)
            if (bindingPref != null && bindingPref != this) {
                AlertDialog.Builder(context).show {
                    val warning = context.getString(CommonString.bindings_already_bound, bindingPref.title)
                    setTitle(binding.toDisplayString(context))
                    setMessage(warning)
                    setPositiveButton(CommonString.dialog_positive_replace) { _, _ ->
                        onAxisSelected(binding)
                    }
                    setNegativeButton(CommonString.dialog_cancel) { _, _ -> }
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
        val otherActionTitle = otherActionTitleForBinding(binding)
        if (otherActionTitle != null) {
            val warning = context.getString(CommonString.bindings_already_bound, otherActionTitle)
            warningDisplay.setWarning(warning)
        } else {
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

        val key = requireArguments().requireString(SettingsFragment.ARG_PREFERENCE_KEY)
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
            negativeButton(CommonString.dialog_cancel)
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
                getString(CommonString.binding_remove_binding, getDisplayString(it))
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
