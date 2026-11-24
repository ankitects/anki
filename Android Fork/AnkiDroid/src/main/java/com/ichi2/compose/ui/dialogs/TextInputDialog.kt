// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.compose.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.modifiers.TextAutoSizeLayoutScope
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.res.use
import com.ichi2.anki.CommonString
import com.ichi2.compose.ui.preview.AnkiDroidPreview
import com.ichi2.compose.ui.preview.DialogPreviews
import com.ichi2.compose.utils.toAnnotatedString

/**
 * Dialog asking the user for text. Compose counterpart of
 * the View based `AlertDialog.input(...)` helper.
 *
 * @param onConfirm called with the text, but only while it is valid
 * @param initialText initial value shown in the field
 * @param initialSelection initial cursor position or selected range; defaults to the end of [initialText]
 * @param validation validation rules and when to run them; defaults to accepting any input
 * @param helperText shown when no error is displayed; an empty string reserves feedback space
 * @param description optional context below the title
 * @param transformInput applied to text edits, not cursor-only changes
 * @param enabled whether confirmation is allowed from the button or keyboard
 * @param singleLine whether the field is restricted to one line; multiline input uses the Enter key for newlines
 */
@Composable
fun TextInputDialog(
    title: String,
    label: String,
    confirmText: String,
    onConfirm: (String) -> Unit,
    onDismissRequest: () -> Unit,
    dismissText: String = stringResource(CommonString.dialog_cancel),
    initialText: String = "",
    initialSelection: TextRange = TextRange(initialText.length),
    maxLengthCounter: Int? = null,
    lengthOf: (String) -> Int = { it.length },
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    validation: InputValidation = InputValidation.None,
    helperText: String? = null,
    description: String? = null,
    transformInput: (TextFieldValue) -> TextFieldValue = { it },
    enabled: Boolean = true,
    singleLine: Boolean = true,
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
) {
    var input by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialText, selection = initialSelection))
    }
    val validationState =
        rememberTextInputValidation(
            text = { input.text },
            enabled = enabled,
            validation = validation,
            onConfirm = onConfirm,
        )
    val validateOnInput = validation.validateOnInput
    val displayedError = validationState.errorMessage
    val supportingMessage = (displayedError ?: helperText)?.toAnnotatedString()
    val hasSupportingText = maxLengthCounter != null || supportingMessage != null || !validateOnInput
    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current
    val density = LocalDensity.current
    val titleFontSize = MaterialTheme.typography.headlineSmall.fontSize
    val titleAutoSize =
        remember(context, density, titleFontSize) {
            val multilineFontSize =
                context
                    .obtainStyledAttributes(
                        null,
                        intArrayOf(android.R.attr.textSize),
                        android.R.attr.textAppearanceMedium,
                        android.R.style.TextAppearance_Medium,
                    ).use { attributes ->
                        with(density) { attributes.getDimensionPixelSize(0, titleFontSize.roundToPx()).toSp() }
                    }
            DialogTitleAutoSize(titleFontSize, multilineFontSize)
        }

    val dismiss = {
        validationState.onDismiss()
        onDismissRequest()
    }

    AnkiDroidDialog(
        onDismissRequest = dismiss,
        modifier = Modifier.width(alertDialogWidth()),
        properties = properties,
    ) {
        Surface(
            modifier = Modifier.widthIn(min = 280.dp, max = 560.dp),
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
            shadowElevation = ankiDroidDialogElevation(),
        ) {
            Layout(modifier = Modifier.padding(ankiDroidDialogContentPadding()), content = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        title,
                        style =
                            MaterialTheme.typography.headlineSmall.copy(
                                // Match the XML TextView's font metrics, including after shrinking.
                                lineHeight = TextUnit.Unspecified,
                                platformStyle = PlatformTextStyle(includeFontPadding = true),
                            ),
                        color = AlertDialogDefaults.titleContentColor,
                        modifier = Modifier.padding(bottom = if (description == null) 16.dp else 8.dp),
                        autoSize = titleAutoSize,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (description != null) {
                        Text(
                            description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = AlertDialogDefaults.textContentColor,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(bottom = 16.dp),
                        )
                    }
                    OutlinedTextField(
                        value = input,
                        onValueChange = { proposed ->
                            if (input.text != proposed.text) {
                                input = transformInput(proposed)
                                validationState.onTextChanged()
                            } else {
                                input = proposed
                            }
                        },
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        label = { Text(label, Modifier.testTag("text_input_label")) },
                        isError = displayedError != null,
                        supportingText =
                            if (!hasSupportingText) {
                                null
                            } else {
                                {
                                    Row(Modifier.fillMaxWidth()) {
                                        if (supportingMessage != null || !validateOnInput) {
                                            Text(supportingMessage ?: AnnotatedString(""), Modifier.weight(1f))
                                        } else {
                                            Spacer(Modifier.weight(1f))
                                        }
                                        if (maxLengthCounter != null) Text("${lengthOf(input.text)}/$maxLengthCounter")
                                    }
                                }
                            },
                        singleLine = singleLine,
                        minLines = if (singleLine) 1 else 4,
                        maxLines = if (singleLine) 1 else 8,
                        keyboardOptions =
                            KeyboardOptions(
                                capitalization = capitalization,
                                imeAction = if (singleLine) ImeAction.Done else ImeAction.Default,
                            ),
                        keyboardActions = KeyboardActions(onDone = { validationState.onConfirm() }),
                    )
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = if (hasSupportingText) 8.dp else 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TextButton(onClick = dismiss) { Text(dismissText) }
                    TextButton(
                        onClick = validationState.onConfirm,
                        enabled = validationState.canConfirm,
                    ) { Text(confirmText) }
                }
            }) { children, constraints ->
                val actions = children[1].measure(constraints.copy(minHeight = 0))
                val body =
                    children[0].measure(
                        constraints.copy(minHeight = 0, maxHeight = (constraints.maxHeight - actions.height).coerceAtLeast(0)),
                    )
                layout(constraints.maxWidth, body.height + actions.height) {
                    body.placeRelative(0, 0)
                    actions.placeRelative(0, body.height)
                }
            }
            LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
        }
    }
}

/** Matches AppCompat's DialogTitle: use medium text when the headline does not fit on one line. */
private data class DialogTitleAutoSize(
    val singleLineFontSize: TextUnit,
    val multilineFontSize: TextUnit,
) : TextAutoSize {
    override fun TextAutoSizeLayoutScope.getFontSize(
        constraints: Constraints,
        text: AnnotatedString,
    ): TextUnit {
        val layout = performLayout(constraints, text, singleLineFontSize)
        return if (layout.lineCount > 1 || layout.isLineEllipsized(0)) multilineFontSize else singleLineFontSize
    }
}

/** @see TextInputDialog */
@DialogPreviews
@Composable
private fun TextInputDialogPreview() {
    AnkiDroidPreview {
        TextInputDialog("Rename deck", "Deck name", "Rename", {}, {}, maxLengthCounter = 50)
    }
}
