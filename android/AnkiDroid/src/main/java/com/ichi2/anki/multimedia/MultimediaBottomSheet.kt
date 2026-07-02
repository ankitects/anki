/*
 * Copyright (c) 2024 Ashish Yadav <mailtoashish693@gmail.com>
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

package com.ichi2.anki.multimedia

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.ichi2.anki.R
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.databinding.FragmentBottomsheetMultimediaBinding
import com.ichi2.anki.multimedia.MultimediaBottomSheet.MultimediaAction.OPEN_CAMERA
import com.ichi2.anki.multimedia.MultimediaBottomSheet.MultimediaAction.OPEN_DRAWING
import com.ichi2.anki.multimedia.MultimediaBottomSheet.MultimediaAction.SELECT_AUDIO_FILE
import com.ichi2.anki.multimedia.MultimediaBottomSheet.MultimediaAction.SELECT_AUDIO_RECORDING
import com.ichi2.anki.multimedia.MultimediaBottomSheet.MultimediaAction.SELECT_IMAGE_FILE
import com.ichi2.anki.multimedia.MultimediaBottomSheet.MultimediaAction.SELECT_VIDEO_FILE
import dev.androidbroadcast.vbpd.viewBinding

/**
 * A BottomSheetDialogFragment class that provides options for selecting multimedia actions.
 */
@NeedsTest("Test to ensure correct option is selected")
class MultimediaBottomSheet : BottomSheetDialogFragment(R.layout.fragment_bottomsheet_multimedia) {
    private val viewModel: MultimediaViewModel by activityViewModels()

    private val binding by viewBinding(FragmentBottomsheetMultimediaBinding::bind)

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        /** setup a click on the listener to emit [MultimediaViewModel.multimediaAction] */
        fun setupListener(
            layout: LinearLayout,
            action: MultimediaAction,
        ) = layout.setOnClickListener {
            viewModel.setMultimediaAction(action)
            dismiss()
        }

        setupListener(binding.multimediaActionImage, SELECT_IMAGE_FILE)
        setupListener(binding.multimediaActionAudio, SELECT_AUDIO_FILE)
        setupListener(binding.multimediaActionDrawing, OPEN_DRAWING)
        setupListener(binding.multimediaActionRecording, SELECT_AUDIO_RECORDING)
        setupListener(binding.multimediaActionVideo, SELECT_VIDEO_FILE)
        setupListener(binding.multimediaActionCamera, OPEN_CAMERA)
    }

    override fun onStart() {
        super.onStart()
        BottomSheetBehavior.from(binding.root.parent as View).state = BottomSheetBehavior.STATE_EXPANDED
    }

    /**
     * An enum representing the different actions available for multimedia selection within a multimedia note.
     *
     * This enum defines the possible actions a user can take when interacting with multimedia fields in a multimedia note.
     * These actions typically trigger UI updates or functionality related to adding or manipulating multimedia content.
     */
    enum class MultimediaAction {
        SELECT_IMAGE_FILE,
        SELECT_AUDIO_FILE,
        OPEN_DRAWING,
        SELECT_AUDIO_RECORDING,
        SELECT_VIDEO_FILE,
        OPEN_CAMERA,
    }
}
