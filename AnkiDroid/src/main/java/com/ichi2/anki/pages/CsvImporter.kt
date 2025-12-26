/*
 *  Copyright (c) 2022 Brayan Oliveira <brayandso.dev@gmail.com>
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
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import androidx.activity.OnBackPressedCallback
import androidx.core.os.bundleOf
import com.google.android.material.appbar.MaterialToolbar
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.R
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.hideShowButtonCss
import com.ichi2.utils.OLDEST_WORKING_WEBVIEW_VERSION
import com.ichi2.utils.WebViewVersion

/**
 * Anki page used to import text/csv files
 */
class CsvImporter : PageFragment() {
    override val pagePath: String by lazy {
        val filePath = requireArguments().getString(KEY_FILE_PATH)
        "import-csv$filePath"
    }

    override val minimumWebViewVersion: WebViewVersion = OLDEST_WORKING_WEBVIEW_VERSION

    override fun onCreateWebViewClient(savedInstanceState: Bundle?): PageWebViewClient {
        // the back callback is only enabled when import is running and showing progress
        val backCallback =
            object : OnBackPressedCallback(false) {
                override fun handleOnBackPressed() {
                    CollectionManager.getBackend().setWantsAbort()
                    // once triggered the callback is not needed as the import process can't be resumed
                    remove()
                }
            }
        super.onCreate(savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(this, backCallback)
        return CsvImporterWebViewClient(backCallback)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<MaterialToolbar>(R.id.toolbar)?.setTitle(R.string.menu_import)
    }

    inner class CsvImporterWebViewClient(
        private val backCallback: OnBackPressedCallback,
    ) : PageWebViewClient() {
        /**
         * Ideally, to handle the state of the back callback, we would just need to check for
         * `/latestProgress` calls followed by one `/importDone` call. However there are some extra
         * calls to `/latestProgress` AFTER `/importDone` and this property keeps track of this.
         */
        private var isDone = false

        override fun onPageFinished(
            view: WebView?,
            url: String?,
        ) {
            view!!.evaluateJavascript(hideShowButtonCss) {
                super.onPageFinished(view, url)
            }
        }

        override fun onLoadResource(
            view: WebView?,
            url: String?,
        ) {
            super.onLoadResource(view, url)
            backCallback.isEnabled =
                when {
                    url == null -> false
                    url.endsWith("latestProgress") && !isDone -> true
                    url.endsWith("importDone") -> {
                        isDone = true // import was done so disable any back callback changes after this call
                        false
                    }
                    else -> false
                }
        }
    }

    companion object {
        private const val KEY_FILE_PATH = "filePath"

        /**
         * @param filePath path of the csv file that will be imported, which should be accessible by AnkiDroid
         * @return an intent to open the [CsvImporter] page on [SingleFragmentActivity]
         */
        fun getIntent(
            context: Context,
            filePath: String,
        ): Intent {
            val arguments = bundleOf(KEY_FILE_PATH to filePath)
            return SingleFragmentActivity.getIntent(context, fragmentClass = CsvImporter::class, arguments)
        }
    }
}
