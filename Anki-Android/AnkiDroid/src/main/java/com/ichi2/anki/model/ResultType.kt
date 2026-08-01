/*
 *  Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>
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
