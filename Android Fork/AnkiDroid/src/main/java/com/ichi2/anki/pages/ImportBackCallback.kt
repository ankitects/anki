// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import androidx.activity.OnBackPressedCallback
import com.ichi2.anki.CollectionManager

/**
 * Cancels the import of an import page on Back, while the page shows its progress.
 *
 * Only enabled while the page polls `latestProgress`. The page calls `importDone` once the
 * import is done, but some polls follow it, so they're ignored.
 */
class ImportBackCallback : OnBackPressedCallback(false) {
    private var isDone = false

    /** Updates whether the callback is enabled for a POST request of the page */
    fun onPostRequest(uri: PostRequestUri) {
        isEnabled =
            when (uri.backendMethodName) {
                "latestProgress" -> !isDone
                "importDone" -> {
                    isDone = true
                    false
                }
                else -> false
            }
    }

    override fun handleOnBackPressed() {
        CollectionManager.getBackend().setWantsAbort()
        // once triggered the callback is not needed as the import process can't be resumed
        remove()
    }
}
