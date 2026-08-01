// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.compat

import android.speech.tts.UtteranceProgressListener
import timber.log.Timber

abstract class UtteranceProgressListenerCompat : UtteranceProgressListener() {
    abstract override fun onError(
        utteranceId: String?,
        errorCode: Int,
    )

    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated("")
    override fun onError(utteranceId: String?) {
        // required for UtteranceProgressListener, but also deprecated
        Timber.e("onError(string) should not have been called")
    }
}
