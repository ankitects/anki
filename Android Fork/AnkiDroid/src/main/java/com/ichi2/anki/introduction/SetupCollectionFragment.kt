// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Shridhar Goel <shridhar.goel@gmail.com>                   *

package com.ichi2.anki.introduction

import android.os.Bundle
import android.os.Parcelable
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import com.ichi2.anki.R
import com.ichi2.anki.databinding.FragmentIntroductionBinding
import com.ichi2.anki.introduction.SetupCollectionFragment.CollectionSetupOption.DeckPickerWithNewCollection
import com.ichi2.anki.introduction.SetupCollectionFragment.CollectionSetupOption.SyncFromExistingAccount
import com.ichi2.anki.utils.bottomCornerClearance
import dev.androidbroadcast.vbpd.viewBinding
import kotlinx.parcelize.Parcelize

/**
 * Allows a user multiple choices for setting up the collection:
 *
 * * Starting normally
 * * Syncing from AnkiWeb - this allows the user to log in and performs a sync when the DeckPicker is loaded
 *
 * This exists for two reasons:
 * 1) Ensuring that a user does not create two profiles: one for Anki Desktop and one for AnkiDroid
 * 2) Adds a screen that allows for 'advanced' setup.
 * for example: selecting a 'safe' folder using scoped storage, which would not have been deleted
 * if the app is uninstalled.
 */
class SetupCollectionFragment : Fragment(R.layout.fragment_introduction) {
    val binding by viewBinding(FragmentIntroductionBinding::bind)

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        setupEdgeToEdge(view)

        binding.getStarted.setOnClickListener { setResult(DeckPickerWithNewCollection) }
        binding.syncProfile.setOnClickListener { setResult(SyncFromExistingAccount) }
    }

    /** Applies edge-to-edge insets for the screen */
    private fun setupEdgeToEdge(view: View) {
        // systemBars (not just statusBars) so a landscape 3-button navigation bar,
        // which is a side inset, is also cleared
        ViewCompat.setOnApplyWindowInsetsListener(view) { root, insets ->
            val bars =
                insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
                )
            // the root stays full-bleed apart from the bottom, so the decorative top gradient
            // reaches the top and side edges. the buttons are the bottom-most touch targets,
            // so they clear the rounded display corners as well as the navigation bar
            root.updatePadding(bottom = maxOf(bars.bottom, insets.bottomCornerClearance(root)))
            binding.introContent.updatePadding(left = bars.left, right = bars.right)
            insets
        }
    }

    private fun setResult(option: CollectionSetupOption) {
        setFragmentResult(REQUEST_COLLECTION_SETUP, Bundle().apply { putParcelable(RESULT_KEY, option) })
    }

    @Parcelize
    enum class CollectionSetupOption : Parcelable {
        /** Continues to the DeckPicker with a new collection */
        DeckPickerWithNewCollection,

        /** Syncs an existing profile from AnkiWeb */
        SyncFromExistingAccount,
    }

    companion object {
        const val REQUEST_COLLECTION_SETUP = "collectionSetup"
        const val RESULT_KEY = "result"
    }
}
