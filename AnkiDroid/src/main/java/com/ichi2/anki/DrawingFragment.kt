/*
 * Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.core.graphics.createBitmap
import androidx.fragment.app.Fragment
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.common.time.getTimestamp
import com.ichi2.anki.compat.CompatHelper
import com.ichi2.anki.databinding.FragmentDrawingBinding
import com.ichi2.anki.dialogs.DiscardChangesDialog
import com.ichi2.anki.ui.windows.reviewer.whiteboard.WhiteboardFragment
import com.ichi2.anki.ui.windows.reviewer.whiteboard.WhiteboardView
import com.ichi2.themes.Themes
import dev.androidbroadcast.vbpd.viewBinding

class DrawingFragment : Fragment(R.layout.fragment_drawing) {
    private val binding by viewBinding(FragmentDrawingBinding::bind)
    private val whiteboardFragment
        get() = childFragmentManager.findFragmentById(R.id.fragment_container) as? WhiteboardFragment

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.apply {
            setNavigationOnClickListener {
                // avoid showing the discard changes dialog only if the user hasn't drawn anything,
                // even if is is erased or undone, since they may want to undo/redo something.
                if (whiteboardFragment?.isEmpty() == true) {
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                } else {
                    DiscardChangesDialog.showDialog(requireContext()) {
                        requireActivity().onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_save -> onSaveDrawing()
                    else -> {}
                }
                true
            }
        }
    }

    private fun onSaveDrawing() {
        val whiteboardView = whiteboardFragment?.binding?.whiteboardView ?: return
        val imagePath = saveWhiteboard(whiteboardView)
        val result =
            Intent().apply {
                putExtra(IMAGE_PATH_KEY, imagePath)
            }
        // TODO don't depend on an Activity
        requireActivity().setResult(Activity.RESULT_OK, result)
        requireActivity().finish()
    }

    private fun saveWhiteboard(view: WhiteboardView): Uri {
        val bitmap = createBitmap(view.width, view.height)
        val canvas = Canvas(bitmap)

        // TODO: drop the baked-in background and save with a transparent canvas, so the drawing
        //  adapts to whichever theme the card is reviewed under and empty space costs nothing
        //  in the file.
        val backgroundColor =
            if (Themes.isNightTheme) {
                Color.BLACK
            } else {
                Color.WHITE
            }
        canvas.drawColor(backgroundColor)

        view.draw(canvas)

        val baseFileName = "Whiteboard" + getTimestamp(TimeManager.time)
        return CompatHelper.compat.saveImage(
            requireContext(),
            bitmap,
            baseFileName,
            "webp",
            CompatHelper.compat.webpLossyFormat,
            90,
        )
    }

    companion object {
        const val IMAGE_PATH_KEY = "path"

        fun getIntent(context: Context): Intent = SingleFragmentActivity.getIntent(context, DrawingFragment::class)
    }
}
