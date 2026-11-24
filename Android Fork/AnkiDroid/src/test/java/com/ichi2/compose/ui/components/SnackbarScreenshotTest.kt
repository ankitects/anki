// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.ichi2.anki.ComposeScreenshotTest
import org.junit.Test
import org.robolectric.annotation.Config

@Config(qualifiers = "w360dp-h320dp-xhdpi")
class SnackbarScreenshotTest : ComposeScreenshotTest() {
    @Test
    fun snackbarAppearance() =
        capture(
            name = "snackbar_appearance",
            node = hasTestTag(SNACKBAR_SAMPLES),
            beforeCapture = {
                composeRule.onNodeWithText("Changes saved").assertIsDisplayed()
                composeRule.onNodeWithText("Undo").assertIsDisplayed()
            },
        ) {
            Column(
                Modifier
                    .testTag(SNACKBAR_SAMPLES)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(vertical = 12.dp),
            ) {
                SnackbarSample("Changes saved")
                SnackbarSample("Tag removed", actionLabel = "Undo")
            }
        }
}

private const val SNACKBAR_SAMPLES = "snackbar_samples"

@Composable
private fun SnackbarSample(
    message: String,
    actionLabel: String? = null,
) {
    val hostState = remember { SnackbarHostState() }
    SnackbarHost(hostState)
    LaunchedEffect(hostState) {
        hostState.showSnackbar(
            message,
            actionLabel = actionLabel,
            withDismissAction = actionLabel != null,
            duration = SnackbarDuration.Indefinite,
        )
    }
}
