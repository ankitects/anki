// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import com.ichi2.anki.common.android.appContext
import timber.log.Timber
import java.io.File

/**
 * Temporary file containing cards or note IDs to be passed in a Bundle.
 *
 * It avoids [android.os.TransactionTooLargeException] when passing a big amount of data.
 *
 * For activity saved-state files, use [com.ichi2.anki.common.utils.ext.onPermanentDismissal] to
 * clear state.
 *
 * Do not use `ViewModel.onCleared()`; see `onPermanentDismissal`.
 */
class IdsFile(
    path: String,
) : IdSnapshotFile(path),
    Parcelable {
    /**
     * @param directory parent directory of the file. Generally it should be [getDirectory]
     * @param ids ids to store
     */
    constructor(directory: File, ids: List<Long>, purpose: Purpose) : this(
        path = IdSnapshotFile.create(directory, ids, purpose.prefix).path,
    )

    override fun describeContents(): Int = 0

    override fun writeToParcel(
        dest: Parcel,
        flags: Int,
    ) {
        dest.writeString(path)
    }

    enum class Purpose(
        /** Uniquely identifies snapshots by filename: `multiselect-values123456789.tmp` */
        val prefix: String,
    ) {
        SELECTION(prefix = "multiselect-values"),
        FIND_AND_REPLACE(prefix = "find-replace"),
        TAGS(prefix = "ids"),
        PREVIEW(prefix = "ids"),
        EXPORT(prefix = "export"),
        SET_DUE_DATE(prefix = "set-due-date"),
    }

    companion object {
        /** Deletes expired snapshots from this profile's cache without creating the directory. */
        fun deleteStaleFiles(context: Context = appContext) = IdSnapshotFile.deleteStaleFiles(getDirectory(context))

        /** Returns the profile's snapshot directory; it is created only when writing. */
        fun getDirectory(context: Context = appContext): File = IdSnapshotFile.getDirectory(context.cacheDir)

        @JvmField
        @Suppress("unused")
        val CREATOR =
            object : Parcelable.Creator<IdsFile> {
                override fun createFromParcel(source: Parcel?): IdsFile = IdsFile(source!!.readString()!!)

                override fun newArray(size: Int): Array<IdsFile> = arrayOf()
            }
    }
}

/** Attempt to delete the associated [IdsFile] and logs the result */
fun IdsFile.removeSafely(owner: String) {
    runCatching { delete() }
        .onFailure { throwable ->
            Timber.w(
                throwable,
                "Exception when removing IdsFile of $owner",
            )
        }.onSuccess { status ->
            Timber.i(
                "$owner associated IdsFile was deleted: $status",
            )
        }
}
