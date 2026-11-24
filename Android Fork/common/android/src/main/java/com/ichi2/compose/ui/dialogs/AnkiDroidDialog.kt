// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.ui.dialogs

import android.os.Build
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.ichi2.anki.common.android.R

/**
 * A [BasicAlertDialog] whose content renders inline in Compose previews.
 *
 * Callers supply the card's styling and any action buttons, which appear in both modes.
 * [modifier] applies to the content container; [properties] and [onDismissRequest] configure
 * the dialog window at runtime.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnkiDroidDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit,
) {
    val typography = dialogTypography()
    // Preserve TextView's legacy line-height path instead of inheriting Compose's default.
    if (LocalInspectionMode.current) {
        // TODO: Preview the surrounding dialog window, including its scrim, positioning and system UI.
        // Match BasicAlertDialog's content constraints, including caller overrides.
        Box(
            modifier = modifier.padding(horizontal = 24.dp, vertical = 16.dp).sizeIn(minWidth = 280.dp, maxWidth = 560.dp),
            propagateMinConstraints = true,
        ) {
            MaterialTheme(typography = typography) {
                CompositionLocalProvider(LocalTextStyle provides typography.bodyLarge.copy(lineHeightStyle = null), content = content)
            }
        }
    } else {
        BasicAlertDialog(
            onDismissRequest = onDismissRequest,
            modifier = modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            properties = properties,
        ) {
            val window = (LocalView.current.parent as DialogWindowProvider).window
            // A slide here competes with initial field focus and keyboard positioning.
            SideEffect {
                if (window.attributes.windowAnimations != R.style.Animation_AnkiDroidDialog) {
                    window.setWindowAnimations(R.style.Animation_AnkiDroidDialog)
                }
                if (
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.R &&
                    !properties.usePlatformDefaultWidth &&
                    properties.decorFitsSystemWindows &&
                    window.attributes.height != MATCH_PARENT
                ) {
                    // Compose's WRAP_CONTENT height calculation mixes screen and window coordinates
                    // in split screen before API 30. Let the window constrain the height instead.
                    window.setLayout(window.attributes.width, MATCH_PARENT)
                }
            }
            MaterialTheme(typography = typography) {
                CompositionLocalProvider(LocalTextStyle provides typography.bodyLarge.copy(lineHeightStyle = null), content = content)
            }
        }
    }
}

/** Insets inside the card. */
@Composable
fun ankiDroidDialogContentPadding(): PaddingValues = PaddingValues(24.dp)

/** View dialog actions: end aligned, with confirmation above dismissal when stacked. */
@Composable
fun AnkiDroidDialogButtons(
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    confirmEnabled: Boolean = true,
) {
    val topPadding = 24.dp
    val spacing = 8.dp
    Layout(
        modifier = Modifier.fillMaxWidth().padding(top = topPadding),
        content = {
            TextButton(
                onClick = onDismissRequest,
            ) { Text(dismissText) }
            TextButton(
                onClick = onConfirm,
                enabled = confirmEnabled,
            ) { Text(confirmText) }
        },
    ) { children, constraints ->
        val buttonConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val dismiss = children[0].measure(buttonConstraints)
        val confirm = children[1].measure(buttonConstraints)
        val gap = spacing.roundToPx()
        val stacked = dismiss.width + gap + confirm.width > constraints.maxWidth
        val height = if (stacked) confirm.height + dismiss.height else maxOf(confirm.height, dismiss.height)
        layout(constraints.maxWidth, height) {
            if (stacked) {
                confirm.placeRelative(constraints.maxWidth - confirm.width, 0)
                dismiss.placeRelative(constraints.maxWidth - dismiss.width, confirm.height)
            } else {
                dismiss.placeRelative(constraints.maxWidth - confirm.width - gap - dismiss.width, (height - dismiss.height) / 2)
                confirm.placeRelative(constraints.maxWidth - confirm.width, (height - confirm.height) / 2)
            }
        }
    }
}
