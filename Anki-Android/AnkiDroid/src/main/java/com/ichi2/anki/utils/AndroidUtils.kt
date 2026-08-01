// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.utils

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.ichi2.anki.R
import com.ichi2.anki.common.android.AdaptionUtil
import com.ichi2.anki.common.android.appContext
import com.ichi2.anki.common.utils.android.showThemedToast
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.utils.copyToClipboard
import timber.log.Timber

/**
 * Acquire a wake lock and release it after running [block].
 *
 * @param levelAndFlags Combination of wake lock level and flag values defining
 *   the requested behavior of the WakeLock
 * @param tag Your class name (or other tag) for debugging purposes
 * @return The return value of `block`
 *
 * @see android.os.PowerManager.newWakeLock
 */
inline fun <T> withWakeLock(
    levelAndFlags: Int,
    tag: String,
    block: () -> T,
): T {
    val context = appContext
    val wakeLock =
        ContextCompat
            .getSystemService(context, PowerManager::class.java)!!
            .newWakeLock(levelAndFlags, context.packageName + ":" + tag)

    wakeLock.acquire()

    return try {
        block()
    } finally {
        wakeLock.release()
    }
}

fun Context.openUrl(
    @StringRes url: Int,
) {
    openUrl(getString(url))
}

fun Context.openUrl(url: String) {
    openUrl(url.toUri())
}

fun Context.openUrl(uri: Uri) {
    if (!AdaptionUtil.hasWebBrowser(this)) {
        val noBrowserMessage = getString(R.string.no_browser_msg, uri.toString())
        if (this is FragmentActivity) {
            showSnackbar(noBrowserMessage) {
                setAction(android.R.string.copyUrl) {
                    copyToClipboard(uri.toString())
                }
            }
        } else {
            showThemedToast(this, noBrowserMessage, shortLength = false)
        }
        return
    }
    try {
        startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (ex: Exception) {
        Timber.w("No app found to handle opening an external url from ${this::class.java.name}")
        if (this is FragmentActivity) {
            showSnackbar(R.string.activity_start_failed)
        } else {
            showThemedToast(this, R.string.activity_start_failed, false)
        }
    }
}

// necessary for Fragments that are BaseSnackbarBuilderProvider to work correctly
fun Fragment.openUrl(uri: Uri) {
    if (!AdaptionUtil.hasWebBrowser(requireContext())) {
        showSnackbar(getString(R.string.no_browser_msg, uri.toString()))
        return
    }
    try {
        startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (ex: ActivityNotFoundException) {
        Timber.w("No app found to handle opening an external url from ${Fragment::class.java.name}")
        showSnackbar(R.string.activity_start_failed)
    }
}

fun Fragment.openUrl(
    @StringRes stringRes: Int,
) = openUrl(requireContext().getString(stringRes).toUri())

/** Hides the soft keyboard from an Activity */
fun Activity.hideKeyboard() {
    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    val view = currentFocus ?: View(this)
    imm.hideSoftInputFromWindow(view.windowToken, 0)
}

/** Hides the soft keyboard from a Fragment */
fun Fragment.hideKeyboard() {
    activity?.hideKeyboard()
}

/** Hides the soft keyboard from a View */
fun View.hideKeyboard() {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.hideSoftInputFromWindow(windowToken, 0)
}

/**
 * Hides the IME, and runs [block] after the operation completes
 *
 * [block] may be executed immediately if the IME is already hidden
 *
 * @param block the code to run after the IME is hidden. Run at most once
 */
fun FragmentActivity.doOnImeHidden(block: () -> Unit) {
    val view = window.decorView
    WindowInsetsControllerCompat(window, view).hide(WindowInsetsCompat.Type.ime())

    var blockHasRun = false

    fun runBlockOnce() {
        if (blockHasRun) return
        blockHasRun = true
        Timber.v("IME hidden. executing...")
        block()
    }

    ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
        val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())

        if (!imeVisible) {
            ViewCompat.setOnApplyWindowInsetsListener(view, null)
            runBlockOnce()
        }

        insets
    }

    // ensure the block is run immediately if the IME is already hidden
    ViewCompat.requestApplyInsets(view)
}
