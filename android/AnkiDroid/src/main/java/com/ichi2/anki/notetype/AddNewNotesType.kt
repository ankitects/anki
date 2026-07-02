/*
 * Copyright (c) 2024 Neel Doshi <neeldoshi147@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.notetype

import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.addTextChangedListener
import anki.notetypes.StockNotetype
import anki.notetypes.copy
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.databinding.DialogNewNoteTypeBinding
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.libanki.Utils
import com.ichi2.anki.libanki.addNotetype
import com.ichi2.anki.libanki.addNotetypeLegacy
import com.ichi2.anki.libanki.backend.BackendUtils
import com.ichi2.anki.libanki.getNotetype
import com.ichi2.anki.libanki.getNotetypeNames
import com.ichi2.anki.libanki.getStockNotetype
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.anki.withProgress
import com.ichi2.utils.customView
import com.ichi2.utils.dp
import com.ichi2.utils.moveCursorToEnd
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton

class AddNewNotesType(
    private val activity: ManageNotetypes,
) {
    private lateinit var binding: DialogNewNoteTypeBinding

    suspend fun showAddNewNotetypeDialog() {
        binding = DialogNewNoteTypeBinding.inflate(LayoutInflater.from(activity))
        val (allOptions, currentNames) =
            activity.withProgress {
                withCol {
                    val standardNotetypesModels =
                        StockNotetype.Kind.entries
                            .filter { it != StockNotetype.Kind.UNRECOGNIZED }
                            .map {
                                AddNotetypeUiModel(
                                    id = it.number.toLong(),
                                    name = getStockNotetype(it).name,
                                    isStandard = true,
                                )
                            }
                    val foundNotetypes = getNotetypeNames()
                    Pair(
                        mutableListOf<AddNotetypeUiModel>().apply {
                            addAll(standardNotetypesModels)
                            addAll(foundNotetypes.map { it.toUiModel() })
                        },
                        foundNotetypes.map { it.name },
                    )
                }
            }
        val dialog =
            AlertDialog
                .Builder(activity)
                .apply {
                    setTitle(with(activity) { TR.sentenceCase.addNoteType })
                    customView(
                        binding.root,
                        paddingStart = 24.dp.toPx(activity),
                        paddingEnd = 24.dp.toPx(activity),
                        paddingTop = 24.dp.toPx(activity),
                        paddingBottom = 0,
                    )
                    positiveButton(R.string.dialog_add) { _ ->
                        val newName =
                            binding.notetypeNewName.text
                                .toString()
                                .trim()
                        if (newName.isEmpty()) return@positiveButton
                        val selectedPosition = binding.notetypeNewType.selectedItemPosition
                        if (selectedPosition == AdapterView.INVALID_POSITION) return@positiveButton
                        val selectedOption = allOptions[selectedPosition]
                        if (selectedOption.isStandard) {
                            addStandardNotetype(newName, selectedOption)
                        } else {
                            cloneStandardNotetype(newName, selectedOption)
                        }
                    }
                    negativeButton(R.string.dialog_cancel)
                }.show()
        dialog.initializeViewsWith(allOptions, currentNames)
    }

    private fun AlertDialog.initializeViewsWith(
        optionsToDisplay: List<AddNotetypeUiModel>,
        currentNames: List<String>,
    ) {
        val addPrefixStr = context.resources.getString(R.string.model_browser_add_add)
        val clonePrefixStr = context.resources.getString(R.string.model_browser_add_clone)

        binding.notetypeTypeLabel.text = TR.notetypesType()
        binding.notetypeNewNameLayout.hint = TR.deckConfigNamePrompt()

        binding.notetypeNewName.addTextChangedListener { editableText ->
            val currentName = editableText?.toString()?.trim() ?: ""
            val alreadyExists = currentNames.any { it.equals(currentName, true) }

            binding.notetypeNewNameLayout.error =
                if (alreadyExists) {
                    context.getString(R.string.error_name_exists)
                } else {
                    null
                }

            positiveButton.isEnabled = currentName.isNotEmpty() && !alreadyExists
        }
        binding.notetypeNewType.apply {
            onItemSelectedListener =
                object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(
                        av: AdapterView<*>?,
                        rv: View?,
                        index: Int,
                        id: Long,
                    ) {
                        val selectedNotetype = optionsToDisplay[index]
                        binding.notetypeNewName.setText(randomizeName(selectedNotetype.name))
                        binding.notetypeNewName.moveCursorToEnd()
                    }

                    override fun onNothingSelected(widget: AdapterView<*>?) {
                        binding.notetypeNewName.setText("")
                    }
                }
            adapter =
                ArrayAdapter(
                    context,
                    android.R.layout.simple_list_item_1,
                    android.R.id.text1,
                    optionsToDisplay.map {
                        String.format(
                            if (it.isStandard) addPrefixStr else clonePrefixStr,
                            it.name,
                        )
                    },
                ).apply {
                    setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }
            binding.notetypeNewName.requestFocus()
            window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        }
    }

    private fun addStandardNotetype(
        newName: String,
        selectedOption: AddNotetypeUiModel,
    ) {
        activity.launchCatchingTask {
            withCol {
                val kind = StockNotetype.Kind.forNumber(selectedOption.id.toInt())
                val updatedStandardNotetype =
                    getStockNotetype(kind).apply {
                        name = newName
                    }
                addNotetypeLegacy(BackendUtils.toJsonBytes(updatedStandardNotetype))
            }
            activity.viewModel.refreshNoteTypes()
        }
    }

    private fun cloneStandardNotetype(
        newName: String,
        model: AddNotetypeUiModel,
    ) {
        activity.launchCatchingTask {
            withCol {
                val targetNotetype = getNotetype(model.id)
                val newNotetype =
                    targetNotetype.copy {
                        id = 0
                        name = newName
                    }
                addNotetype(newNotetype)
            }
            activity.viewModel.refreshNoteTypes()
        }
    }

    /**
     * Takes the current timestamp from [Collection] and appends it to the end of the new note
     * type to dissuade the user from reusing names(which are technically not unique however).
     */
    private fun randomizeName(currentName: String): String =
        "$currentName-${Utils.checksum(TimeManager.time.intTimeMS().toString()).substring(0, 5)}"
}
