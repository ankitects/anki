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

package com.ichi2.anki.dialogs

import android.app.Dialog
import android.os.Bundle
import androidx.core.os.BundleCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.dialogs.FieldSelectionDialog.Companion.RESULT_TYPE
import com.ichi2.anki.dialogs.FieldSelectionDialog.Companion.registerFieldSelectionHandler
import com.ichi2.anki.libanki.Field
import com.ichi2.anki.model.ResultType
import com.ichi2.anki.utils.ext.requireParcelable
import com.ichi2.anki.utils.ext.requireString
import com.ichi2.utils.create
import timber.log.Timber

/**
 * A dialog to display all [fields][Field] in the collection for selection
 *
 * Use [registerFieldSelectionHandler] to handle results from this class
 *
 * @see InsertFieldDialog for selecting fields for use in the card template editor
 */
// TODO: Support searching
// TODO: Support looking up via note type.
class FieldSelectionDialog : DialogFragment() {
    val fieldNames
        get() =
            requireNotNull(requireArguments().getStringArrayList(ARG_FIELD_NAMES)) {
                ARG_FIELD_NAMES
            }

    val resultType get() =
        requireNotNull(BundleCompat.getParcelable(requireArguments(), RESULT_TYPE, ResultType::class.java)) {
            RESULT_TYPE
        }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        MaterialAlertDialogBuilder(requireContext()).create {
            setTitle(R.string.card_template_editor_select_field)
            setItems(fieldNames.toTypedArray()) { _, which ->
                onFieldSelected(fieldNames[which])
            }
        }

    private fun onFieldSelected(fieldName: String) {
        parentFragmentManager.setFragmentResult(
            REQUEST_KEY,
            Bundle().apply {
                putParcelable(RESULT_TYPE, resultType)
                putString(RESULT_SELECTED_FIELD_NAME, fieldName)
            },
        )
    }

    companion object {
        const val TAG = "FieldSelectionDialog"
        private const val ARG_FIELD_NAMES = "fieldNames"
        private const val REQUEST_KEY = "requestKey"

        /** Result key for the Fragment Result API. Name of the selected field */
        private const val RESULT_SELECTED_FIELD_NAME = "selectedFieldName"

        /**
         * Returned in the Fragment Result API bundle.
         *
         * User-supplied value to differentiate between different instances of [FieldSelectionDialog]
         *
         * Default value: `""`
         */
        private const val RESULT_TYPE = "resultType"

        /**
         * @param resultType optional result string returned in the Fragment Result API bundle under
         *  the [RESULT_TYPE] key
         */
        suspend fun createInstance(resultType: ResultType = ResultType("")): FieldSelectionDialog {
            val fieldNames =
                withCol {
                    notetypes
                        .all()
                        .flatMap { nt -> nt.fields.map { fld -> fld.name } }
                }.distinct()

            val bundle =
                Bundle().apply {
                    putParcelable(RESULT_TYPE, resultType)
                    putStringArrayList(ARG_FIELD_NAMES, ArrayList(fieldNames))
                }

            Timber.i("Creating $TAG for resultType: %s", resultType)
            return FieldSelectionDialog().apply { arguments = bundle }
        }

        /**
         * Registers a fragment result listener to handle a field selection
         *
         * @param action a lambda with the type of action and the name of the field
         *
         * @see FieldSelectionDialog
         */
        context(lifecycleOwner: LifecycleOwner)
        fun FragmentManager.registerFieldSelectionHandler(action: (ResultType, String) -> Unit) {
            setFragmentResultListener(
                REQUEST_KEY,
                lifecycleOwner,
            ) { _, bundle ->
                val resultType = bundle.requireParcelable<ResultType>(RESULT_TYPE)
                val fieldName = bundle.requireString(RESULT_SELECTED_FIELD_NAME)
                Timber.i("$TAG: selected %s", resultType)
                Timber.d("Selected field: %s", fieldName)
                action(resultType, fieldName)
            }
        }
    }
}
