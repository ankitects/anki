// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences.components

import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.settings.enums.Theme
import com.ichi2.compose.theme.widgetBorder
import com.ichi2.compose.ui.preview.AnkiDroidPreview
import com.ichi2.compose.ui.preview.AnkiDroidThemeProvider
import com.ichi2.anki.common.android.R as CommonR

/**
 * Full-width card with a title and toggle switch for enabling or disabling a preference.
 */
@Composable
fun PreferenceSwitchCard(
    title: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val containerColor = MaterialTheme.colorScheme.surfaceVariant
    val contentColor = preferenceCardContentColor()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .toggleable(
                    value = isChecked,
                    role = Role.Switch,
                    onValueChange = { checked ->
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        onCheckedChange(checked)
                    },
                ),
        shape = RoundedCornerShape(28.dp),
        color = containerColor,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = if (isLandscape) 12.dp else 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = isChecked,
                onCheckedChange = null,
                modifier =
                    Modifier.semantics {
                        contentDescription = title
                    },
            )
        }
    }
}

/**
 * Numeric preference card combining +/- stepper buttons with an inline editable text field.
 */
@Composable
fun PreferenceStepperCard(
    title: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    validRange: IntRange,
    unitSuffix: @Composable (count: Int) -> String,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val windowWidthDp = with(density) { windowInfo.containerSize.width.toDp() }
    val iconSize =
        when {
            windowWidthDp >= 600.dp -> 32.dp
            windowWidthDp >= 360.dp -> 28.dp
            else -> 24.dp
        }
    var textValue by rememberSaveable { mutableStateOf(value.toString()) }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(value) {
        if (textValue.toIntOrNull() != value) {
            textValue = value.toString()
        }
    }

    val count = textValue.toIntOrNull() ?: value
    val safeCount = if (count in validRange) count else value
    val suffixText = unitSuffix(safeCount)

    val controlBorder = MaterialTheme.widgetBorder
    val buttonColors =
        if (controlBorder != null) {
            IconButtonDefaults.outlinedIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                disabledContainerColor = MaterialTheme.colorScheme.surface,
            )
        } else {
            IconButtonDefaults.filledTonalIconButtonColors()
        }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = if (isLandscape) 10.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = preferenceCardContentColor(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StepperButton(
                    iconRes = CommonR.drawable.ic_remove,
                    contentDescription = stringResource(CommonString.minus_sign),
                    enabled = (textValue.toIntOrNull() ?: value) > validRange.first,
                    colors = buttonColors,
                    border = controlBorder,
                    iconSize = iconSize,
                    onClick = {
                        val currentVal = textValue.toIntOrNull() ?: value
                        val prevVal = (currentVal - 1).coerceIn(validRange)
                        textValue = prevVal.toString()
                        onValueChange(prevVal)
                        focusManager.clearFocus()
                    },
                )

                StepperInputField(
                    textValue = textValue,
                    onTextChange = { textValue = it },
                    value = value,
                    onValueChange = onValueChange,
                    validRange = validRange,
                    suffixText = suffixText,
                    controlBorder = controlBorder,
                )

                StepperButton(
                    iconRes = CommonR.drawable.ic_add,
                    contentDescription = stringResource(CommonString.plus_sign),
                    enabled = (textValue.toIntOrNull() ?: value) < validRange.last,
                    colors = buttonColors,
                    border = controlBorder,
                    iconSize = iconSize,
                    onClick = {
                        val currentVal = textValue.toIntOrNull() ?: value
                        val nextVal = (currentVal + 1).coerceIn(validRange)
                        textValue = nextVal.toString()
                        onValueChange(nextVal)
                        focusManager.clearFocus()
                    },
                )
            }
        }
    }
}

@Composable
private fun StepperButton(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    enabled: Boolean,
    colors: IconButtonColors,
    border: BorderStroke?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
) {
    OutlinedIconButton(
        onClick = onClick,
        modifier = modifier.size(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = colors,
        border = border,
        enabled = enabled,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun RowScope.StepperInputField(
    textValue: String,
    onTextChange: (String) -> Unit,
    value: Int,
    onValueChange: (Int) -> Unit,
    validRange: IntRange,
    suffixText: String,
    controlBorder: BorderStroke?,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    fun commitOrRevert() {
        val p = textValue.toIntOrNull()
        if (p != null && p in validRange) {
            onValueChange(p)
        } else {
            onTextChange(value.toString())
        }
    }

    val parsed = textValue.toIntOrNull()
    val isValid = parsed != null && parsed in validRange
    val isError = textValue.isNotEmpty() && !isValid
    val inputBorder =
        controlBorder?.let {
            if (isError) BorderStroke(it.width, MaterialTheme.colorScheme.error) else it
        }
    val inputContainerColor = if (controlBorder != null) MaterialTheme.colorScheme.surface else Color.Transparent

    OutlinedTextField(
        value = textValue,
        onValueChange = { input ->
            val digits = input.filter { it.isDigit() }
            val noLeadingZeros =
                if (digits.length > 1 && digits.startsWith("0")) {
                    digits.trimStart('0').ifEmpty { "0" }
                } else {
                    digits
                }
            val maxDigits = validRange.last.toString().length
            val filtered = noLeadingZeros.take(maxDigits)
            onTextChange(filtered)
            val p = filtered.toIntOrNull()
            if (p != null && p in validRange) {
                onValueChange(p)
            }
        },
        modifier =
            modifier
                .weight(1f, fill = false)
                .widthIn(max = 130.dp)
                .padding(horizontal = 3.dp)
                .then(
                    if (inputBorder != null) {
                        Modifier.border(inputBorder, RoundedCornerShape(12.dp))
                    } else {
                        Modifier
                    },
                ).onFocusChanged { focusState ->
                    val lostFocus = isFocused && !focusState.isFocused
                    isFocused = focusState.isFocused
                    if (lostFocus) {
                        commitOrRevert()
                    }
                },
        suffix = {
            Text(
                text = suffixText,
                style = MaterialTheme.typography.bodySmall,
                color = preferenceCardContentColor(),
            )
        },
        shape = RoundedCornerShape(12.dp),
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedContainerColor = inputContainerColor,
                unfocusedContainerColor = inputContainerColor,
                errorContainerColor = inputContainerColor,
            ),
        textStyle =
            LocalTextStyle.current.copy(
                textAlign = TextAlign.Start,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            ),
        singleLine = true,
        isError = isError,
        keyboardOptions =
            KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
            ),
        keyboardActions =
            KeyboardActions(
                onDone = {
                    commitOrRevert()
                    focusManager.clearFocus()
                },
            ),
    )
}

/**
 * Explanatory help section displaying an info icon and descriptive text.
 *
 * Modeled directly after Android Settings (Material 3) footer style:
 * - 24dp info icon placed directly above the explanatory text
 * - 12dp vertical spacing between the icon and the text
 * - 8dp horizontal padding relative to the card container
 */
@Composable
fun PreferenceHelpSection(
    text: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_outline_info_24),
            contentDescription = null,
            tint = preferenceCardContentColor(),
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = preferenceCardContentColor(),
            lineHeight = 20.sp,
        )
    }
}

/**
 * Resolves content text/icon color respecting High Contrast / E-ink widget borders.
 */
@Composable
private fun preferenceCardContentColor(): Color =
    if (MaterialTheme.widgetBorder != null) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

@Preview
@Composable
private fun PreferenceSwitchCardPreview(
    @PreviewParameter(AnkiDroidThemeProvider::class) theme: Theme,
) {
    AnkiDroidPreview(theme = theme) {
        Surface {
            PreferenceSwitchCard(
                title = "Use timebox",
                isChecked = true,
                onCheckedChange = {},
            )
        }
    }
}

@Preview
@Composable
private fun PreferenceStepperCardPreview(
    @PreviewParameter(AnkiDroidThemeProvider::class) theme: Theme,
) {
    AnkiDroidPreview(theme = theme) {
        Surface {
            PreferenceStepperCard(
                title = "Time limit",
                value = 20,
                onValueChange = {},
                validRange = 1..999,
                unitSuffix = { "mins" },
            )
        }
    }
}

@Preview
@Composable
private fun PreferenceHelpSectionPreview(
    @PreviewParameter(AnkiDroidThemeProvider::class) theme: Theme,
) {
    AnkiDroidPreview(theme = theme) {
        Surface {
            PreferenceHelpSection(
                text = "When no other cards are due, study learning cards early.",
            )
        }
    }
}
