/*
 * Copyright (c) 2025 Ashish Yadav <mailtoashish693@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.preferences.profiles

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.MaterialToolbar
import com.ichi2.anki.R
import com.ichi2.anki.databinding.FragmentSwitchProfilesBinding
import com.ichi2.utils.ValidationResult
import com.ichi2.utils.input
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import com.ichi2.utils.title
import dev.androidbroadcast.vbpd.viewBinding

/**
 * A [Fragment] that allows the user to switch between different profiles.
 */
class SwitchProfilesFragment : Fragment(R.layout.fragment_switch_profiles) {
    private val binding by viewBinding(FragmentSwitchProfilesBinding::bind)

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<MaterialToolbar>(R.id.toolbar).apply {
            setTitle("Switch profile")
            setNavigationOnClickListener {
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }

        binding.addProfileFab.setOnClickListener {
            showAddProfileDialog()
        }
    }

    fun showAddProfileDialog() {
        AlertDialog
            .Builder(requireContext())
            .show {
                title(text = "Add profile")

                positiveButton(R.string.dialog_add)
                negativeButton(R.string.dialog_cancel)
                setView(R.layout.dialog_generic_text_input)
            }.input(
                hint = "Profile name",
                displayKeyboard = true,
                validator = { text ->
                    when {
                        text.isNotBlank() -> ValidationResult.VALID
                        else -> ValidationResult.REJECTED
                    }
                },
            ) { _, _ ->
                // TODO: handle profile creation
            }
    }
}
