// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>

package com.ichi2.anki.dialogs

import android.content.Context
import android.content.res.Resources
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.analytics.AnalyticsDialogFragment
import com.ichi2.anki.common.android.appContext
import timber.log.Timber

abstract class AsyncDialogFragment : AnalyticsDialogFragment() {
    /* provide methods for text to show in notification bar when the DialogFragment
       can't be shown due to the host activity being in stopped state.
       This can happen when the DialogFragment is shown from
       the onPostExecute() method of an AsyncTask */
    abstract val notificationMessage: String?
    abstract val notificationTitle: String
    open val dialogHandlerMessage: DialogHandlerMessage? get() = null

    protected fun res(): Resources =
        try {
            resources
        } catch (e: IllegalStateException) {
            // Fragment SyncErrorDialog not attached to a context.
            // this will occur when obtaining `notificationTitle`/`notificationMessage` and
            // the app is in the background
            Timber.i("resources failure. Likely due to app backgrounded")
            AnkiDroidApp.appResources
        } catch (e: Exception) {
            Timber.w(e, "resources failure. Returning AnkiDroidApp resources as fallback.")
            AnkiDroidApp.appResources
        }

    /**
     * Return the {@link Context} this fragment is currently associated with.
     * Uses [appContext] if [requireContext] fails
     */
    protected fun getSafeContext(): Context =
        try {
            requireContext()
        } catch (e: Exception) {
            Timber.w(e, "Error getting context; using AnkiDroidApp")
            appContext
        }
}
