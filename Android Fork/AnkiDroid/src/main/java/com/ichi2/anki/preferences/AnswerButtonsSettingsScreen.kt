// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.ichi2.anki.R
import com.ichi2.anki.common.android.Animations
import com.ichi2.anki.ui.internationalization.tr
import com.ichi2.anki.ui.windows.reviewer.AnswerArea
import com.ichi2.anki.ui.windows.reviewer.AnswerButtonsNextTime
import com.ichi2.compose.theme.AnkiDroidTheme
import com.ichi2.compose.ui.components.GroupedList
import com.ichi2.compose.ui.components.GroupedListScope
import com.ichi2.compose.ui.preview.ThemePreviews
import kotlin.math.roundToInt
import androidx.appcompat.R as AppCompatR
import com.ichi2.anki.common.android.R as CommonR

private const val MIN_BUTTON_SIZE = 100
private const val MAX_BUTTON_SIZE = 200
private const val BUTTON_SIZE_STEP = 10

/** Displays the study screen's answer button settings alongside an interactive preview. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnswerButtonsSettingsScreen(
    state: AnswerButtonsSettingsState,
    showNextTimesTitle: String,
    onShowAnswerButtonsChange: (Boolean) -> Unit,
    onShowNextTimesChange: (Boolean) -> Unit,
    onHideHardAndEasyButtonsChange: (Boolean) -> Unit,
    onUseThemeColorsChange: (Boolean) -> Unit,
    onButtonSizeChange: (Int) -> Unit,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(CommonR.string.answer_buttons)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            painter = painterResource(R.drawable.ic_baseline_arrow_back_24),
                            contentDescription = stringResource(AppCompatR.string.abc_action_bar_up_description),
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .consumeWindowInsets(contentPadding),
        ) {
            AnswerAreaPreview(state)
            val showAnswerButtonsTitle = stringResource(CommonR.string.show_answer_buttons)
            val hideHardAndEasyTitle = stringResource(CommonR.string.hide_hard_and_easy)
            val useThemeColorsTitle = stringResource(CommonR.string.answer_buttons_theme_colors)
            val buttonSizeTitle = stringResource(CommonR.string.button_size)
            val buttonSizeValue = stringResource(CommonR.string.percentage, state.buttonSize.toString())
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .navigationBarsPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                GroupedList {
                    switchItem(
                        icon = R.drawable.ic_remove_red_eye_white,
                        title = showAnswerButtonsTitle,
                        checked = state.showAnswerButtons,
                        onCheckedChange = onShowAnswerButtonsChange,
                    )
                    switchItem(
                        icon = R.drawable.ic_running_clock,
                        title = showNextTimesTitle,
                        checked = state.showNextTimes == true,
                        onCheckedChange = onShowNextTimesChange,
                        enabled = state.showAnswerButtons && state.showNextTimes != null,
                    )
                    switchItem(
                        icon = R.drawable.ic_remove_done,
                        title = hideHardAndEasyTitle,
                        checked = state.hideHardAndEasyButtons,
                        onCheckedChange = onHideHardAndEasyButtonsChange,
                        enabled = state.showAnswerButtons,
                    )
                    switchItem(
                        icon = R.drawable.ic_palette,
                        title = useThemeColorsTitle,
                        checked = state.useThemeColors,
                        onCheckedChange = onUseThemeColorsChange,
                        enabled = state.showAnswerButtons,
                    )
                }
                GroupedList {
                    item {
                        EnabledContentColor(state.showAnswerButtons) {
                            Column {
                                Row {
                                    Text(buttonSizeTitle, modifier = Modifier.weight(1f))
                                    Text(buttonSizeValue)
                                }
                                Slider(
                                    value = state.buttonSize.toFloat(),
                                    onValueChange = { onButtonSizeChange((it / BUTTON_SIZE_STEP).roundToInt() * BUTTON_SIZE_STEP) },
                                    modifier =
                                        Modifier.semantics {
                                            contentDescription = buttonSizeTitle
                                            stateDescription = buttonSizeValue
                                        },
                                    valueRange = MIN_BUTTON_SIZE.toFloat()..MAX_BUTTON_SIZE.toFloat(),
                                    steps = (MAX_BUTTON_SIZE - MIN_BUTTON_SIZE) / BUTTON_SIZE_STEP - 1,
                                    enabled = state.showAnswerButtons,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Lets users try the answer area with the current settings.
 * Reserves space for the largest answer area so the settings below stay in place as the size changes.
 */
@Composable
private fun AnswerAreaPreview(state: AnswerButtonsSettingsState) {
    var isAnswerShown by rememberSaveable { mutableStateOf(true) }
    val animationsEnabled = LocalInspectionMode.current || Animations.areAnimationsEnabled(LocalContext.current)
    val enterTransition = if (animationsEnabled) fadeIn() else EnterTransition.None
    val exitTransition = if (animationsEnabled) fadeOut() else ExitTransition.None
    val translations = tr()
    val previewNextTimes =
        AnswerButtonsNextTime(
            again = "<${translations.schedulingAnswerButtonTimeMinutes(1)}",
            hard = "<${translations.schedulingAnswerButtonTimeMinutes(6)}",
            good = "<${translations.schedulingAnswerButtonTimeMinutes(10)}",
            easy = translations.schedulingAnswerButtonTimeDays(4),
        )
    Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        AnswerArea(
            isAnswerShown = true,
            nextTimes = previewNextTimes,
            onRatingClicked = {},
            onShowAnswerClicked = {},
            buttonsHeightPercentage = MAX_BUTTON_SIZE,
            modifier = Modifier.clearAndSetSemantics {}.measureOnly(),
        )
        AnimatedVisibility(
            visible = state.showAnswerButtons,
            enter = enterTransition,
            exit = exitTransition,
        ) {
            AnswerArea(
                isAnswerShown = isAnswerShown,
                nextTimes = if (state.showNextTimes == true) previewNextTimes else null,
                onRatingClicked = { isAnswerShown = false },
                onShowAnswerClicked = { isAnswerShown = true },
                showHardAndEasyButtons = !state.hideHardAndEasyButtons,
                buttonsHeightPercentage = state.buttonSize,
                useThemeColors = state.useThemeColors,
            )
        }
        AnimatedVisibility(
            visible = !state.showAnswerButtons,
            enter = enterTransition,
            exit = exitTransition,
        ) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_hide), contentDescription = null)
                    Text(stringResource(CommonR.string.answer_buttons_hidden))
                }
            }
        }
    }
}

private fun Modifier.measureOnly(): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {}
    }

/** An [item] with a switch as widget */
private fun GroupedListScope.switchItem(
    @DrawableRes icon: Int,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) = item(
    modifier = Modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
    leadingContent = { EnabledContentColor(enabled) { Icon(painterResource(icon), contentDescription = null) } },
    trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
) {
    EnabledContentColor(enabled) { Text(title) }
}

/** Dims the current content color when a setting is disabled. */
@Composable
private fun EnabledContentColor(
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    val color = LocalContentColor.current
    CompositionLocalProvider(
        LocalContentColor provides if (enabled) color else color.copy(alpha = 0.38f),
        content = content,
    )
}

/** Previews the default answer button settings across app themes in Android Studio. */
@ThemePreviews
@Composable
private fun AnswerButtonsSettingsScreenPreview() {
    AnkiDroidTheme {
        AnswerButtonsSettingsScreen(
            state =
                AnswerButtonsSettingsState(
                    showAnswerButtons = true,
                    showNextTimes = true,
                    hideHardAndEasyButtons = false,
                    useThemeColors = false,
                    buttonSize = 100,
                ),
            showNextTimesTitle = "Show next review time above answer buttons",
            onShowAnswerButtonsChange = {},
            onShowNextTimesChange = {},
            onHideHardAndEasyButtonsChange = {},
            onUseThemeColorsChange = {},
            onButtonSizeChange = {},
            onNavigateUp = {},
        )
    }
}
