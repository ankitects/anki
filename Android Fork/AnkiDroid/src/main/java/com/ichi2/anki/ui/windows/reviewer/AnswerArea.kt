// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.reviewer

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import anki.scheduler.CardAnswer.Rating
import com.ichi2.anki.R
import com.ichi2.compose.theme.ankiColors
import com.ichi2.compose.theme.ankiDimensions
import com.ichi2.compose.theme.widgetBorder
import com.ichi2.anki.common.android.R as CommonR

@Composable
fun AnswerArea(
    isAnswerShown: Boolean,
    nextTimes: AnswerButtonsNextTime?,
    onRatingClicked: (Rating) -> Unit,
    onShowAnswerClicked: () -> Unit,
    modifier: Modifier = Modifier,
    showHardAndEasyButtons: Boolean = true,
    buttonsHeightPercentage: Int = 100,
    useThemeColors: Boolean = false,
) {
    val colors = MaterialTheme.ankiColors
    val colorScheme = MaterialTheme.colorScheme

    fun background(default: Color) = if (useThemeColors) colorScheme.primaryContainer else default

    fun content(default: Color) = if (useThemeColors) colorScheme.onPrimaryContainer else default
    Box(
        modifier = modifier.fillMaxWidth().relativeHeight(buttonsHeightPercentage),
        propagateMinConstraints = true,
    ) {
        Row(
            modifier =
                Modifier
                    .height(IntrinsicSize.Min)
                    .invisibleIf(!isAnswerShown),
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.answer_button_margin_horizontal) * 2),
        ) {
            RatingButton(
                easeName = CommonR.string.ease_button_again,
                nextTime = nextTimes?.again,
                textColor = content(colors.onAgainButton),
                backgroundColor = background(colors.againButton),
                onClick = { onRatingClicked(Rating.AGAIN) },
            )
            if (showHardAndEasyButtons) {
                RatingButton(
                    easeName = CommonR.string.ease_button_hard,
                    nextTime = nextTimes?.hard,
                    textColor = content(colors.onHardButton),
                    backgroundColor = background(colors.hardButton),
                    onClick = { onRatingClicked(Rating.HARD) },
                )
            }
            RatingButton(
                easeName = CommonR.string.ease_button_good,
                nextTime = nextTimes?.good,
                textColor = content(colors.onGoodButton),
                backgroundColor = background(colors.goodButton),
                onClick = { onRatingClicked(Rating.GOOD) },
            )
            if (showHardAndEasyButtons) {
                RatingButton(
                    easeName = CommonR.string.ease_button_easy,
                    nextTime = nextTimes?.easy,
                    textColor = content(colors.onEasyButton),
                    backgroundColor = background(colors.easyButton),
                    onClick = { onRatingClicked(Rating.EASY) },
                )
            }
        }
        AnswerButton(
            text = AnnotatedString(stringResource(CommonR.string.show_answer)),
            textColor = content(Color.White),
            backgroundColor = background(colors.showAnswerButton),
            onClick = onShowAnswerClicked,
            modifier = Modifier.matchParentSize().invisibleIf(isAnswerShown),
        )
    }
}

@Composable
private fun RowScope.RatingButton(
    @StringRes easeName: Int,
    nextTime: String?,
    textColor: Color,
    backgroundColor: Color,
    onClick: () -> Unit,
) {
    val name = stringResource(easeName)
    val text =
        if (nextTime == null) {
            AnnotatedString(name)
        } else {
            buildAnnotatedString {
                withStyle(SpanStyle(fontSize = 0.8.em)) { append(nextTime) }
                append("\n")
                append(name)
            }
        }
    AnswerButton(
        text = text,
        textColor = textColor,
        backgroundColor = backgroundColor,
        onClick = onClick,
        modifier = Modifier.weight(1f).fillMaxHeight(),
    )
}

@Composable
private fun AnswerButton(
    text: AnnotatedString,
    textColor: Color,
    backgroundColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val elevation = MaterialTheme.ankiDimensions.studyScreenElevation
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        ElevatedButton(
            onClick = onClick,
            modifier = modifier.padding(vertical = 4.dp),
            shape = RoundedCornerShape(dimensionResource(R.dimen.answer_button_corner_radius)),
            colors = ButtonDefaults.buttonColors(containerColor = backgroundColor, contentColor = textColor),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = elevation, pressedElevation = elevation),
            border = MaterialTheme.widgetBorder,
            contentPadding = PaddingValues(0.dp),
        ) {
            Text(text, textAlign = TextAlign.Center)
        }
    }
}

private fun Modifier.relativeHeight(percentage: Int): Modifier {
    if (percentage <= 100) return this
    return layout { measurable, constraints ->
        val height =
            (measurable.minIntrinsicHeight(constraints.maxWidth) * percentage / 100)
                .coerceIn(constraints.minHeight, constraints.maxHeight)
        val placeable = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
}

private fun Modifier.invisibleIf(invisible: Boolean): Modifier {
    if (!invisible) return this
    return layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {}
    }
}
