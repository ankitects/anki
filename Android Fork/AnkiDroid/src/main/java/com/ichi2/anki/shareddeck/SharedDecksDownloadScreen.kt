// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.shareddeck

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalGridApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Grid
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.compose.theme.AnkiDroidTheme
import com.ichi2.compose.theme.dimensions
import com.ichi2.compose.theme.widgetBorder
import com.ichi2.compose.ui.preview.ThemePreviews

private val MaxContentWidth = 600.dp
private val ProgressRingSize = 240.dp
private val ProgressRingStroke = 10.dp
private val InfoBadgeSize = 28.dp
private val ProgressAnimationSpec =
    SpringSpec(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow,
        visibilityThreshold = 1 / 1000f,
    )
private val PercentTextAnimationSpec =
    SpringSpec(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
        visibilityThreshold = 1 / 1000f,
    )
private const val RING_ENTER_MILLIS = 300
private const val RING_ENTER_DELAY_MILLIS = 100
private const val RING_EXIT_MILLIS = 150
private const val RING_ENTER_INITIAL_SCALE = 0.92f
private const val RING_EXIT_TARGET_SCALE = 0.8f

/** Stateless download screen. State lives in [SharedDecksDownloadViewModel]. */
@Composable
fun SharedDecksDownloadScreen(
    state: SharedDecksDownloadUiState,
    onCancelClick: () -> Unit,
    onImportClick: () -> Unit,
    onTryAgainClick: () -> Unit,
    onOpenInBrowserClick: () -> Unit,
    animationsEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val insets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxSize()
                .pointerInput(Unit) {}
                .windowInsetsPadding(insets.only(WindowInsetsSides.Top))
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(insets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = MaxContentWidth)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight),
        ) {
            Header(fileName = state.fileName)
            Spacer(Modifier.weight(1f))
            ProgressRing(
                state = state,
                animationsEnabled = animationsEnabled,
                modifier = Modifier.padding(vertical = MaterialTheme.dimensions.screenEdge),
            )
            Spacer(Modifier.weight(1f))
            StatCards(state)
            InfoCard()
            if (state.isWaitingForNetwork) NetworkWarning()
            Actions(
                phase = state.phase,
                onCancelClick = onCancelClick,
                onImportClick = onImportClick,
                onTryAgainClick = onTryAgainClick,
                onOpenInBrowserClick = onOpenInBrowserClick,
            )
        }
    }
}

/** Title naming the file being downloaded. */
@Composable
private fun Header(fileName: String?) {
    val edge = MaterialTheme.dimensions.screenEdge
    Text(
        text = fileName?.let { stringResource(CommonString.downloading_file, it) }.orEmpty(),
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth().padding(top = edge, start = edge, end = edge),
    )
}

/** A spinning ring until the first bytes arrive, then the ring with the progress in it. */
@Composable
private fun ProgressRing(
    state: SharedDecksDownloadUiState,
    animationsEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val targetProgress = ((state.percent ?: 0f) / 100f).coerceIn(0f, 1f)
    val progressSpec: AnimationSpec<Float> = if (animationsEnabled) ProgressAnimationSpec else snap()
    val percentSpec: AnimationSpec<Float> = if (animationsEnabled) PercentTextAnimationSpec else snap()
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = progressSpec,
        label = "DownloadProgress",
    )
    val animatedPercent by animateFloatAsState(
        targetValue = targetProgress * 100f,
        animationSpec = percentSpec,
        label = "DownloadPercentText",
    )

    AnimatedContent(
        targetState = state,
        modifier = modifier.fillMaxWidth(),
        transitionSpec = {
            if (animationsEnabled) {
                val enter =
                    fadeIn(tween(RING_ENTER_MILLIS, RING_ENTER_DELAY_MILLIS)) +
                        scaleIn(tween(RING_ENTER_MILLIS, RING_ENTER_DELAY_MILLIS), initialScale = RING_ENTER_INITIAL_SCALE)
                val exit =
                    fadeOut(tween(RING_EXIT_MILLIS)) +
                        scaleOut(tween(RING_EXIT_MILLIS), targetScale = RING_EXIT_TARGET_SCALE)
                enter togetherWith exit
            } else {
                EnterTransition.None togetherWith ExitTransition.None
            }
        },
        contentAlignment = Alignment.Center,
        label = "DownloadRingContent",
        contentKey = { it.isWaitingToStart },
    ) { shownState ->
        RingWithProgress(
            state = shownState,
            spinWhileWaiting = animationsEnabled && !shownState.isWaitingForNetwork,
            progress = { animatedProgress },
            percent = { animatedPercent },
        )
    }
}

/**
 * Progress ring with the percentage and size in the middle.
 *
 * @param spinWhileWaiting whether the ring spins before the first bytes arrive, rather than sitting empty
 */
@Composable
private fun RingWithProgress(
    state: SharedDecksDownloadUiState,
    spinWhileWaiting: Boolean,
    progress: () -> Float,
    percent: () -> Float,
) {
    val border = MaterialTheme.widgetBorder
    val colors = MaterialTheme.colorScheme
    val trackColor = if (border != null) colors.surface else colors.surfaceVariant
    val sizeTextColor = if (border != null) colors.onSurface else colors.onSurfaceVariant
    val indicatorModifier =
        Modifier.size(ProgressRingSize).drawWithContent {
            drawContent()
            if (border != null) {
                // The thin outline distinguishes the track from the thicker progress arc.
                drawCircle(
                    brush = border.brush,
                    radius = (size.minDimension - ProgressRingStroke.toPx()) / 2,
                    style = Stroke(width = border.width.toPx()),
                )
            }
        }

    Box(contentAlignment = Alignment.Center) {
        if (state.percent == null || (state.isWaitingToStart && spinWhileWaiting)) {
            CircularProgressIndicator(
                modifier = indicatorModifier,
                strokeWidth = ProgressRingStroke,
                trackColor = trackColor,
            )
        } else {
            CircularProgressIndicator(
                progress = progress,
                modifier = indicatorModifier,
                strokeWidth = ProgressRingStroke,
                trackColor = trackColor,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            when {
                state.phase == DownloadPhase.Failed -> RingLabel(stringResource(CommonString.download_failed))
                state.percent == null -> RingLabel(TR.syncDownloadingFromAnkiweb())
                state.isWaitingToStart -> Unit
                else -> {
                    PercentageText(percent())
                    downloadSizeText(state.downloadedBytes, state.totalBytes)?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = sizeTextColor,
                            modifier = Modifier.padding(top = MaterialTheme.dimensions.space50),
                        )
                    }
                }
            }
        }
    }
}

/** Text in the ring when there's no percentage to show. */
@Composable
private fun RingLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = Modifier.widthIn(max = ProgressRingSize).padding(horizontal = MaterialTheme.dimensions.space400),
    )
}

/** The percentage in the ring, with a smaller % sign. */
@Composable
private fun PercentageText(percent: Float) {
    val style = MaterialTheme.typography.displayLarge
    val text = stringResource(CommonString.percentage, formatDownloadPercent(percent))
    val sign = text.indexOf('%')
    Text(
        text =
            buildAnnotatedString {
                append(text)
                if (sign >= 0) addStyle(SpanStyle(fontSize = style.fontSize * 0.6f), sign, sign + 1)
            },
        style = style,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
    )
}

/** Size downloaded out of the total, or null while neither is known. */
@Composable
private fun downloadSizeText(
    downloadedBytes: Long,
    totalBytes: Long,
): String? {
    val context = LocalContext.current
    if (totalBytes <= 0 && downloadedBytes <= 0) return null
    val downloaded = Formatter.formatFileSize(context, downloadedBytes)
    if (totalBytes <= 0) return downloaded
    return stringResource(CommonString.progress_amount_bytes, downloaded, Formatter.formatFileSize(context, totalBytes))
}

/** Speed and time left, side by side with equal widths and matching heights. */
@OptIn(ExperimentalGridApi::class)
@Composable
private fun StatCards(state: SharedDecksDownloadUiState) {
    val dimensions = MaterialTheme.dimensions
    Grid(
        config = {
            column(minmax(0.dp, 1.fr))
            column(minmax(0.dp, 1.fr))
            columnGap(dimensions.space150)
        },
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = dimensions.screenEdge, end = dimensions.screenEdge, bottom = dimensions.space150),
    ) {
        StatCard(
            label = stringResource(CommonString.download_speed_label),
            value = speedText(state.speedBytesPerSecond, state.downloadedBytes),
            modifier = Modifier.fillMaxSize(),
        )
        StatCard(
            label = stringResource(CommonString.download_time_left_label),
            value = timeLeftText(state.secondsRemaining),
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Card with a small label above its value. */
@Composable
private fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    TonalCard(modifier = modifier) {
        Column(modifier = Modifier.padding(MaterialTheme.dimensions.sectionGap)) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(hyphens = Hyphens.Auto),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = MaterialTheme.dimensions.space50),
            )
        }
    }
}

/** Download speed, or a placeholder until the first bytes arrive. */
@Composable
private fun speedText(
    bytesPerSecond: Long,
    downloadedBytes: Long,
): String {
    if (bytesPerSecond <= 0 && downloadedBytes <= 0) return stringResource(CommonString.download_speed_unknown)
    val speed = Formatter.formatFileSize(LocalContext.current, bytesPerSecond.coerceAtLeast(0))
    return stringResource(CommonString.download_speed_unit, speed)
}

/** Time left, or a placeholder until it can be estimated. */
@Composable
private fun timeLeftText(secondsRemaining: Long?): String =
    secondsRemaining?.let(DateUtils::formatElapsedTime) ?: stringResource(CommonString.download_time_unknown)

/** Card saying it's fine to use other apps while the download runs. */
@Composable
private fun InfoCard() {
    val dimensions = MaterialTheme.dimensions
    TonalCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = dimensions.screenEdge, end = dimensions.screenEdge, bottom = dimensions.screenEdge),
    ) {
        Row(
            modifier = Modifier.padding(dimensions.sectionGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(InfoBadgeSize)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiary)
                        .padding(dimensions.space50),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_done),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiary,
                )
            }
            Text(
                text = stringResource(CommonString.deck_download_progress_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = dimensions.sectionGap),
            )
        }
    }
}

/** Flat card shared by the stat cards and the info card. */
@Composable
private fun TonalCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val border = MaterialTheme.widgetBorder
    val colors = MaterialTheme.colorScheme
    // Outlined e-ink cards use white surfaces and black content without changing the app palette.
    val cardColors =
        if (border != null) {
            colors.copy(
                surfaceVariant = colors.surface,
                onSurfaceVariant = colors.onSurface,
                tertiary = colors.onSurface,
                onTertiary = colors.surface,
            )
        } else {
            colors
        }
    MaterialTheme(colorScheme = cardColors) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = border,
            modifier = modifier,
            content = content,
        )
    }
}

/** Warning shown while the download waits for a network connection. */
@Composable
private fun NetworkWarning() {
    val dimensions = MaterialTheme.dimensions
    Text(
        text = stringResource(CommonString.check_network),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.error,
        textAlign = TextAlign.Center,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = dimensions.sectionGap, vertical = dimensions.space100)
                .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** Buttons for the current [DownloadPhase]. */
@Composable
private fun Actions(
    phase: DownloadPhase,
    onCancelClick: () -> Unit,
    onImportClick: () -> Unit,
    onTryAgainClick: () -> Unit,
    onOpenInBrowserClick: () -> Unit,
) {
    val dimensions = MaterialTheme.dimensions
    val border = MaterialTheme.widgetBorder
    val colors = MaterialTheme.colorScheme
    FlowRow(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = dimensions.screenEdge, end = dimensions.screenEdge, bottom = dimensions.screenEdge),
        horizontalArrangement = Arrangement.spacedBy(dimensions.sectionGap, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(dimensions.space100),
    ) {
        when (phase) {
            // transient: the fragment shows the login prompt and leaves this screen
            DownloadPhase.Downloading, DownloadPhase.LoginRequired ->
                FilledTonalButton(
                    onClick = onCancelClick,
                    border = border,
                    colors =
                        ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (border != null) colors.surface else colors.errorContainer,
                            contentColor = if (border != null) colors.onSurface else colors.onErrorContainer,
                        ),
                ) {
                    Text(stringResource(CommonString.cancel_download))
                }
            DownloadPhase.Complete ->
                Button(onClick = onImportClick) {
                    Text(stringResource(CommonString.import_deck))
                }
            DownloadPhase.Failed -> {
                FilledTonalButton(
                    onClick = onTryAgainClick,
                    border = border,
                ) {
                    Text(stringResource(CommonString.try_again))
                }
                TextButton(onClick = onOpenInBrowserClick) {
                    Text(stringResource(CommonString.open_in_browser))
                }
            }
        }
    }
}

private class DownloadStateProvider : PreviewParameterProvider<SharedDecksDownloadUiState> {
    private val downloading =
        SharedDecksDownloadUiState(
            fileName = "Japanese Core 2000.apkg",
            percent = 42.7f,
            downloadedBytes = 19_200_000,
            totalBytes = 44_900_000,
            speedBytesPerSecond = 1_200_000,
            secondsRemaining = 21,
        )

    override val values =
        sequenceOf(
            SharedDecksDownloadUiState(fileName = downloading.fileName),
            downloading,
            downloading.copy(isWaitingForNetwork = true),
            downloading.copy(phase = DownloadPhase.Complete, percent = 100f, downloadedBytes = 44_900_000),
            downloading.copy(phase = DownloadPhase.Failed, percent = 0f),
        )
}

@ThemePreviews
@Composable
private fun SharedDecksDownloadScreenPreview(
    @PreviewParameter(DownloadStateProvider::class) state: SharedDecksDownloadUiState,
) {
    AnkiDroidTheme {
        SharedDecksDownloadScreen(
            state = state,
            onCancelClick = {},
            onImportClick = {},
            onTryAgainClick = {},
            onOpenInBrowserClick = {},
            animationsEnabled = true,
        )
    }
}
