// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>

package com.ichi2.anki

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.core.graphics.createBitmap
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.common.time.getTimestamp
import com.ichi2.anki.compat.CompatHelper
import com.ichi2.anki.databinding.FragmentDrawingBinding
import com.ichi2.anki.dialogs.DiscardChangesDialog
import com.ichi2.anki.ui.windows.reviewer.whiteboard.WhiteboardFragment
import com.ichi2.anki.ui.windows.reviewer.whiteboard.WhiteboardView
import com.ichi2.themes.Themes
import dev.androidbroadcast.vbpd.viewBinding
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

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
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_save -> onSaveDrawing()
                    else -> {}
                }
                true
            }
        }
        setupBackPressHandling()
    }

    /**
     * Wires the discard-dialog back-press flow and tells the child WhiteboardFragment
     * to suppress its "go back again to exit" snackbar.
     *
     * Deferred via `view.post` so it runs after the child's `onViewCreated`, ensuring
     * our [OnBackPressedCallback] is added to the dispatcher *after* the child's and
     * wins LIFO. The relative order of parent vs child `onViewCreated` differs
     * across platforms (Robolectric runs the child first; real devices have shown
     * the opposite), so the post normalizes it.
     */
    private fun setupBackPressHandling() {
        requireView().post {
            if (!isAdded) return@post
            val whiteboard = whiteboardFragment ?: return@post

            whiteboard.setDrawingMode(true)

            // Standard isEnabled pattern: only intercept the back press when there's
            // content to discard. When empty, the callback stays disabled so the
            // press falls through to the activity finishing and predictive back
            // shows its system exit animation.
            val backCallback =
                object : OnBackPressedCallback(enabled = false) {
                    override fun handleOnBackPressed() {
                        DiscardChangesDialog.showDialog(requireContext()) {
                            isEnabled = false
                            requireActivity().onBackPressedDispatcher.onBackPressed()
                        }
                    }
                }
            requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)

            whiteboard.isEmptyFlow
                .onEach { isEmpty -> backCallback.isEnabled = !isEmpty }
                .launchIn(viewLifecycleOwner.lifecycleScope)
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
