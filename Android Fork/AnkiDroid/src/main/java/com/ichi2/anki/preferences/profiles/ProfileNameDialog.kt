// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.preferences.profiles

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.ichi2.anki.CommonString
import com.ichi2.anki.multiprofile.ProfileName
import com.ichi2.anki.multiprofile.ProfileName.ValidationResult
import com.ichi2.compose.theme.AnkiDroidTheme
import com.ichi2.compose.ui.dialogs.InputValidation
import com.ichi2.compose.ui.dialogs.TextInputDialog
import com.ichi2.compose.ui.preview.DialogPreviews

/**
 * Dialog asking for the display name of a new profile.
 *
 * Validation goes through [ProfileName.validate], so the UI enforces the same
 * rules as the domain layer. [onConfirm] receives the validated name.
 */
@Composable
fun AddProfileDialog(
    existingNames: List<String>,
    onDismissRequest: () -> Unit,
    onConfirm: (ProfileName) -> Unit,
) {
    ProfileNameDialog(
        title = stringResource(CommonString.add_profile),
        confirmText = stringResource(CommonString.dialog_add),
        initialText = "",
        existingNames = existingNames,
        onDismissRequest = onDismissRequest,
        onConfirm = onConfirm,
    )
}

@Composable
fun RenameProfileDialog(
    currentName: String,
    existingNames: List<String>,
    onDismissRequest: () -> Unit,
    onConfirm: (ProfileName) -> Unit,
) {
    ProfileNameDialog(
        title = stringResource(CommonString.rename_profile),
        confirmText = stringResource(CommonString.rename),
        initialText = currentName,
        existingNames = existingNames,
        onDismissRequest = onDismissRequest,
        onConfirm = onConfirm,
    )
}

@Composable
private fun ProfileNameDialog(
    title: String,
    confirmText: String,
    initialText: String,
    existingNames: List<String>,
    onDismissRequest: () -> Unit,
    onConfirm: (ProfileName) -> Unit,
) {
    val emptyError = stringResource(CommonString.profile_name_empty_error)
    val tooLongError = stringResource(CommonString.profile_name_too_long_error, ProfileName.MAX_LENGTH)
    val nameExistsError = stringResource(CommonString.error_name_exists)

    TextInputDialog(
        title = title,
        label = stringResource(CommonString.profile_name),
        confirmText = confirmText,
        onConfirm = { text ->
            (ProfileName.validate(text) as? ValidationResult.Valid)?.let { onConfirm(it.name) }
        },
        onDismissRequest = onDismissRequest,
        initialText = initialText,
        maxLengthCounter = ProfileName.MAX_LENGTH,
        lengthOf = { it.trim().length },
        capitalization = KeyboardCapitalization.Words,
        validation =
            InputValidation.Immediate { text ->
                when (val result = ProfileName.validate(text)) {
                    is ValidationResult.Valid -> nameExistsError.takeIf { result.name.existsIn(existingNames) }
                    is ValidationResult.Empty -> emptyError
                    is ValidationResult.TooLong -> tooLongError
                }
            },
    )
}

@DialogPreviews
@Composable
private fun AddProfileDialogPreview() {
    AnkiDroidTheme {
        AddProfileDialog(existingNames = emptyList(), onDismissRequest = {}, onConfirm = {})
    }
}

@DialogPreviews
@Composable
private fun RenameProfileDialogPreview() {
    AnkiDroidTheme {
        RenameProfileDialog(currentName = "Work", existingNames = emptyList(), onDismissRequest = {}, onConfirm = {})
    }
}
