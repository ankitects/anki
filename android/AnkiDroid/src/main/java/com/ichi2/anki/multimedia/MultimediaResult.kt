/*
 * Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.multimedia

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import androidx.fragment.app.Fragment
import com.ichi2.anki.compat.CompatHelper.Companion.getSerializableCompat
import com.ichi2.anki.multimediacard.fields.IField

/**
 * Typed result returned by a [MultimediaActivity] launched from the note editor.
 */
sealed interface MultimediaResult {
    /** Index of the field in the note editor that requested the capture. */
    val fieldIndex: Int

    /** Media was captured/selected and is attached to [field]. */
    data class Success(
        override val fieldIndex: Int,
        val field: IField,
    ) : MultimediaResult

    /** User dismissed the capture/selection without producing media. */
    data class Cancelled(
        override val fieldIndex: Int,
    ) : MultimediaResult
}

/**
 * [ActivityResultContract] for launching a [MultimediaActivity] and parsing the
 * returned [MultimediaResult].
 */
class MultimediaResultContract : ActivityResultContract<Intent, MultimediaResult?>() {
    override fun createIntent(
        context: Context,
        input: Intent,
    ): Intent = input

    override fun parseResult(
        resultCode: Int,
        intent: Intent?,
    ): MultimediaResult? {
        val extras = intent?.extras ?: return null
        val index = extras.getInt(EXTRA_FIELD_INDEX, -1).takeIf { it >= 0 } ?: return null
        return when (resultCode) {
            Activity.RESULT_OK -> {
                val field = extras.getSerializableCompat<IField>(EXTRA_FIELD) ?: return null
                MultimediaResult.Success(index, field)
            }
            Activity.RESULT_CANCELED -> MultimediaResult.Cancelled(index)
            else -> null
        }
    }

    companion object {
        internal const val EXTRA_FIELD = "multimedia_result"
        internal const val EXTRA_FIELD_INDEX = "multimedia_result_index"
    }
}

/**
 * Sets the activity result of the [Fragment]'s host to the given [MultimediaResult]
 * and finishes the activity.
 */
fun Fragment.setMultimediaResultAndFinish(result: MultimediaResult) {
    val data =
        Intent().apply {
            putExtra(MultimediaResultContract.EXTRA_FIELD_INDEX, result.fieldIndex)
            if (result is MultimediaResult.Success) {
                putExtra(MultimediaResultContract.EXTRA_FIELD, result.field)
            }
        }
    val resultCode =
        when (result) {
            is MultimediaResult.Success -> Activity.RESULT_OK
            is MultimediaResult.Cancelled -> Activity.RESULT_CANCELED
        }
    requireActivity().setResult(resultCode, data)
    requireActivity().finish()
}
