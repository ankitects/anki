// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.compose.theme.AnkiDroidTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreferenceStepperViewportRegressionTest : RobolectricTest() {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `bringing the input into view after viewport shrink preserves focus`() {
        var viewportHeight by mutableStateOf(600.dp)
        composeRule.setContent {
            AnkiDroidTheme {
                Box(Modifier.height(viewportHeight)) {
                    TimeboxSettingsScreen(
                        isLoading = false,
                        isEnabled = true,
                        minutes = 20,
                        onToggleEnabled = {},
                        onIncrement = {},
                        onDecrement = {},
                        onSetMinutes = {},
                        onNavigateUp = {},
                    )
                }
            }
        }
        val input = composeRule.onNode(hasSetTextAction())
        input.performClick()
        input.assertIsFocused()

        // An opening IME reduces the viewport and scrolls the focused input into view.
        composeRule.runOnIdle { viewportHeight = 240.dp }

        input.assertIsFocused()
    }
}
