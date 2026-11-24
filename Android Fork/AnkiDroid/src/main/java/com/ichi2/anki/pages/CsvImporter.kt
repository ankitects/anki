// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import com.google.android.material.appbar.MaterialToolbar
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.common.destinations.CsvImporterDestination
import com.ichi2.anki.hideShowButtonCss

/**
 * Anki page used to import text/csv files
 */
class CsvImporter : PageFragment() {
    override val pagePath: String by lazy {
        val filePath = requireArguments().getString(ARG_FILE_PATH)
        "import-csv$filePath"
    }

    /** Replaced on each page load: a recreated WebView reloads the page, which may start another import */
    private var backCallback: ImportBackCallback? = null

    override fun onCreateWebViewClient(savedInstanceState: Bundle?): PageWebViewClient {
        backCallback?.remove()
        backCallback = ImportBackCallback().also { requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, it) }
        super.onCreate(savedInstanceState)
        return CsvImporterWebViewClient()
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<MaterialToolbar>(R.id.toolbar)?.title = TR.actionsImport()
    }

    override suspend fun handlePostRequest(
        uri: PostRequestUri,
        bytes: ByteArray,
    ): ByteArray {
        backCallback?.onPostRequest(uri)
        return super.handlePostRequest(uri, bytes)
    }

    inner class CsvImporterWebViewClient : PageWebViewClient() {
        override fun onPageFinished(
            view: WebView?,
            url: String?,
        ) {
            view!!.evaluateJavascript(hideShowButtonCss) {
                super.onPageFinished(view, url)
            }
        }
    }

    companion object {
        private const val ARG_FILE_PATH = "filePath"

        /**
         * @param filePath path of the csv file that will be imported, which should be accessible by AnkiDroid
         * @return an intent to open the [CsvImporter] page on [SingleFragmentActivity]
         */
        fun getIntent(
            context: Context,
            filePath: String,
        ): Intent {
            val arguments = Bundle().apply { putString(ARG_FILE_PATH, filePath) }
            return SingleFragmentActivity.getIntent(context, fragmentClass = CsvImporter::class, arguments)
        }
    }
}

/** Builds the [Intent] that opens the CSV importer for this destination. */
fun CsvImporterDestination.toIntent(context: Context): Intent = CsvImporter.getIntent(context, filePath)
