// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.utils

import android.app.Activity
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Parcelable
import androidx.annotation.CheckResult
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.core.content.getSystemService
import com.ichi2.anki.CommonString
import com.ichi2.anki.common.appName
import com.ichi2.anki.common.utils.android.showThemedToast
import com.ichi2.anki.snackbar.canProperlyShowSnackbars
import com.ichi2.anki.snackbar.showSnackbar
import kotlinx.parcelize.Parcelize
import timber.log.Timber

object ClipboardUtil {
    val IMAGE_MIME_TYPES = arrayOf("image/*")
    val AUDIO_MIME_TYPES = arrayOf("audio/*")
    val VIDEO_MIME_TYPES = arrayOf("video/*")
    val IMPORT_MIME_TYPES = arrayOf("application/*", "text/*")
    val MEDIA_MIME_TYPES = arrayOf(*IMAGE_MIME_TYPES, *AUDIO_MIME_TYPES, *VIDEO_MIME_TYPES)

    fun hasImage(clipboard: ClipboardManager?): Boolean =
        clipboard
            ?.primaryClip
            ?.let { hasImage(it.description) }
            ?: false

    fun hasImage(description: ClipDescription?): Boolean =
        description
            ?.run { IMAGE_MIME_TYPES.any { hasMimeType(it) } }
            ?: false

    fun hasVideo(description: ClipDescription?): Boolean =
        description
            ?.run { VIDEO_MIME_TYPES.any { hasMimeType(it) } }
            ?: false

    private fun ClipboardManager.getFirstItem() = primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)

    fun getUri(clipboard: ClipboardManager?): Uri? = clipboard?.getFirstItem()?.uri

    fun hasMedia(clipboard: ClipboardManager?): Boolean =
        clipboard
            ?.primaryClip
            ?.let { hasMedia(it.description) }
            ?: false

    fun hasMedia(description: ClipDescription?): Boolean =
        description
            ?.run { MEDIA_MIME_TYPES.any { hasMimeType(it) } }
            ?: false

    fun ClipData.items() =
        sequence {
            for (j in 0 until itemCount) {
                yield(getItemAt(j))
            }
        }

    fun getDescription(clipboard: ClipboardManager?): ClipDescription? = clipboard?.primaryClip?.description

    @CheckResult
    fun getPlainText(
        clipboard: ClipboardManager?,
        context: Context,
    ): CharSequence? = clipboard?.getFirstItem()?.coerceToText(context)
}

/**
 * Text copied to the clipboard cannot be unbounded. To ensure callers of [copyToClipboard] are aware
 * that provided text may be truncated, we require that they pass an explicit instance of this value class.
 */
@Parcelize
@JvmInline
value class TruncatedString private constructor(
    val value: String,
) : Parcelable {
    companion object {
        /**
         * Text in the clipboard cannot exceed 1 MB because that is the limit of the Binder IPC transaction buffer.
         * We truncate at a much lower limit because the 1 MB limit is shared between all ongoing transactions.
         */
        @VisibleForTesting
        const val MAX_CLIPBOARD_TEXT_LENGTH = 100_000

        /**
         * If [text] exceeds the maximum length ([MAX_CLIPBOARD_TEXT_LENGTH]), it is truncated and a warning is logged. Does not throw.
         *
         * TODO: Low-priority, only affects last character in the rare case of a truncation: emojis, graphemes etc. may be split in half; should split on valid boundary
         */
        fun from(text: String): TruncatedString {
            if (text.length > MAX_CLIPBOARD_TEXT_LENGTH) {
                Timber.w("Text length (${text.length}) exceeds maximum clipboard length ($MAX_CLIPBOARD_TEXT_LENGTH). Truncating.")
            }
            return TruncatedString(text.take(MAX_CLIPBOARD_TEXT_LENGTH))
        }
    }
}

/**
 * Copies the provided [text] to the clipboard (possibly truncated, see [TruncatedString])
 * and shows either a snackbar, if possible, or a toast with a success/failure
 * message if the system does not already show a 'copied to clipboard' message
 *
 * @param text a [TruncatedString] of the text that needs to be copied
 * @param successMessageId message that needs to be shown after successfully copying the text
 * @param failureMessageId message that needs to be shown in case failed to copy the text
 * @return whether the text was copied to the clipboard
 */
fun Context.copyToClipboard(
    text: TruncatedString,
    @StringRes successMessageId: Int = CommonString.about_ankidroid_successfully_copied_debug_info,
    @StringRes failureMessageId: Int = CommonString.failed_to_copy,
): Boolean {
    val copied = copyTextToClipboard(text)
    // in Android S_V2 and above, the system is guaranteed to show a message on a successful copy
    // so we don't need to do anything
    val doesNotNeedToShowMessage = copied && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2

    if (doesNotNeedToShowMessage) {
        Timber.v("successfully copied to clipboard & system informed user of copy")
        return true
    }

    val confirmationMessage = if (copied) successMessageId else failureMessageId

    if (this is Activity && canProperlyShowSnackbars()) {
        showSnackbar(confirmationMessage)
    } else {
        showThemedToast(this, confirmationMessage, shortLength = true)
    }

    return copied
}

/**
 * The method attempts to obtain the system clipboard manager using the current context. If the clipboard
 * manager is not available, it logs a warning and returns `false`.
 *
 * If the clipboard manager is obtained, the method creates a new clip with the provided text along with
 * the application name and version information. It then sets this clip as the primary clip on the clipboard.
 * The text has already been truncated to a safe length, see [TruncatedString].
 *
 * @param text The text to be copied to the clipboard.
 * @return `true` if the text was successfully copied to the clipboard, `false` if clipboard access failed.
 */
private fun Context.copyTextToClipboard(text: TruncatedString): Boolean {
    val clipboardManager = this.getSystemService<ClipboardManager>()
    if (clipboardManager == null) {
        Timber.w("Failed to obtain ClipboardManager")
        return false
    }

    return try {
        clipboardManager.setPrimaryClip(
            ClipData.newPlainText(
                "$appName v${VersionUtils.pkgVersionName}",
                text.value,
            ),
        )
        true
    } catch (e: Exception) {
        Timber.w(e, "Failed to copy text to clipboard")
        false
    }
}
