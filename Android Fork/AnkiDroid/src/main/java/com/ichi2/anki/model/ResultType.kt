// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.model

import android.os.Parcelable
import androidx.fragment.app.FragmentManager
import kotlinx.parcelize.Parcelize

/**
 * Identifies the source of a bundle returned from the Fragment Result API
 *
 * Used to avoid registering multiple listeners with [FragmentManager.setFragmentResultListener]
 *
 * Pass in a [ResultType] when creating a fragment, and match on the ResultType when
 *  receiving the result.
 *
 * **Example**
 * ```kotlin
 * registerFieldSelectionHandler { resultType, fieldName ->
 *     when (resultType.value) {
 *         "bare_field" -> insertField("{{$fieldName}}")
 *         "type" -> insertField("{{type:$fieldName}}")
 *     }
 * }
 *
 * FieldSelectionDialog.createInstance(ResultType("bare_field"))
 * ```
 */
@Parcelize
@JvmInline
value class ResultType(
    val value: String,
) : Parcelable {
    override fun toString() = value
}
