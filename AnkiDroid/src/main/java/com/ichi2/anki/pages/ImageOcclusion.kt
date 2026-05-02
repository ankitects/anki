/*
 *  Copyright (c) 2023 Abdo <abdo@abdnh.net>
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
package com.ichi2.anki.pages

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.widget.TextView
import androidx.activity.addCallback
import androidx.core.os.BundleCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import com.google.android.material.appbar.MaterialToolbar
import com.ichi2.anki.R
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.dialogs.DiscardChangesDialog
import com.ichi2.anki.dialogs.registerDeckSelectedHandler
import com.ichi2.anki.model.SelectableDeck
import com.ichi2.anki.pages.viewmodel.ImageOcclusionArgs
import com.ichi2.anki.pages.viewmodel.ImageOcclusionViewModel
import com.ichi2.anki.pages.viewmodel.ImageOcclusionViewModel.Companion.IO_ARGS_KEY
import com.ichi2.anki.startDeckSelection
import com.ichi2.anki.utils.ext.launchCollectionInLifecycleScope
import timber.log.Timber

/**
 * Page provided by the backend, for a user to add or edit an image occlusion (IO) note
 *
 * IO: Like an image-based cloze: hide parts of an image, revealed on the back
 * ([docs](https://docs.ankiweb.net/editing.html#image-occlusion) and
 * [source](https://github.com/ankitects/anki/blob/main/proto/anki/image_occlusion.proto)).
 *
 * When adding, a user may select the deck of the note
 *
 * **Paths**
 * `/image-occlusion/$PATH`
 * `/image-occlusion/$NOTE_ID`
 *
 * @see ImageOcclusionViewModel
 * @see ImageOcclusion.getIntent
 */
class ImageOcclusion : PageFragment(R.layout.page_image_occlusion) {
    private val viewModel: ImageOcclusionViewModel by viewModels()
    private lateinit var deckNameView: TextView

    override val pagePath: String by lazy {
        val args =
            BundleCompat.getParcelable(requireArguments(), IO_ARGS_KEY, ImageOcclusionArgs::class.java)
                ?: throw IllegalArgumentException("IO args were not setup correctly")
        val suffix =
            when (args) {
                is ImageOcclusionArgs.Add -> Uri.encode(args.imagePath)
                is ImageOcclusionArgs.Edit -> args.noteId
            }
        "image-occlusion/$suffix"
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        with(requireActivity()) {
            onBackPressedDispatcher.addCallback(this) {
                DiscardChangesDialog.showDialog(this@with) {
                    finish()
                }
            }
        }

        deckNameView = view.findViewById(R.id.deck_name)
        deckNameView.setOnClickListener { startDeckSelection(all = false, filtered = false, skipEmptyDefault = false) }

        @NeedsTest("#17393 verify that the added image occlusion cards are put in the correct deck")
        view.findViewById<MaterialToolbar>(R.id.toolbar).setOnMenuItemClickListener {
            if (it.itemId == R.id.action_save) {
                Timber.i("save item selected")
                webViewLayout.evaluateJavascript("anki.imageOcclusion.save()")
            }
            return@setOnMenuItemClickListener true
        }
        registerDeckSelectedHandler(action = ::onDeckSelected)
        setupFlows()
    }

    override fun onCreateWebViewClient(savedInstanceState: Bundle?): PageWebViewClient =
        object : PageWebViewClient() {
            override fun onPageFinished(
                view: WebView?,
                url: String?,
            ) {
                super.onPageFinished(view, url)
                viewModel.args.toImageOcclusionMode().let { options ->
                    view?.evaluateJavascript("globalThis.anki.imageOcclusion.mode = $options") {
                        super.onPageFinished(view, url)
                    }
                }
            }
        }

    private fun onDeckSelected(deck: SelectableDeck?) {
        if (deck == null) return
        require(deck is SelectableDeck.Deck)
        viewModel.handleDeckSelection(deck.deckId)
    }

    // HACK: detect a successful save; #19443 will provide a better method
    // backend calls are only made on success; .save() does not notify on failure
    override suspend fun handlePostRequest(
        uri: PostRequestUri,
        bytes: ByteArray,
    ): ByteArray =
        super.handlePostRequest(uri, bytes).also {
            when (uri.backendMethodName) {
                "addImageOcclusionNote", "updateImageOcclusionNote" -> viewModel.onSaveOperationCompleted()
            }
        }

    private fun setupFlows() {
        fun onDeckNameChanged(name: String) {
            deckNameView.text = name
        }

        viewModel.deckNameFlow?.launchCollectionInLifecycleScope(::onDeckNameChanged) ?: run {
            deckNameView.isVisible = false
        }
    }

    companion object {
        /**
         * @param args arguments for either adding or editing a note
         */
        fun getIntent(
            context: Context,
            args: ImageOcclusionArgs,
        ): Intent {
            val arguments = bundleOf(IO_ARGS_KEY to args)
            return SingleFragmentActivity.getIntent(context, ImageOcclusion::class, arguments)
        }
    }
}
