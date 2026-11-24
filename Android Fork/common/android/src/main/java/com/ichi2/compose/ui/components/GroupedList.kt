// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.ichi2.anki.common.android.R
import com.ichi2.anki.settings.enums.Theme
import com.ichi2.compose.theme.widgetBorder
import com.ichi2.compose.ui.preview.AnkiDroidPreview
import com.ichi2.compose.ui.preview.AnkiDroidThemeProvider

/**
 * Shows the rows added in [content] as one group of rounded tiles, like Android's Settings app.
 *
 * In the E-ink theme the group is outlined instead, with thin lines between its rows.
 */
@Composable
fun GroupedList(
    modifier: Modifier = Modifier,
    content: GroupedListScope.() -> Unit,
) {
    val items = GroupedListItems().apply(content).items
    val border = MaterialTheme.widgetBorder
    if (border == null) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items.forEachIndexed { index, item ->
                GroupedListRow(item, shape = rowShape(index, items.lastIndex))
            }
        }
    } else {
        val shape = RoundedCornerShape(20.dp)
        Column(modifier.clip(shape).border(border, shape)) {
            items.forEachIndexed { index, item ->
                if (index > 0) {
                    val start = if (items[index - 1].leadingContent != null) 72.dp else 16.dp
                    Spacer(
                        Modifier
                            .padding(start = start, end = 16.dp)
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(border.brush),
                    )
                }
                GroupedListRow(item, shape = RectangleShape)
            }
        }
    }
}

/** Adds rows to a [GroupedList]. */
interface GroupedListScope {
    /**
     * Adds a row.
     *
     * @param modifier applied to the whole row, for example `Modifier.clickable` or `Modifier.toggleable`
     * @param leadingContent usually an icon, centred in a 40dp box
     * @param trailingContent usually a chevron, a switch or a value
     */
    fun item(
        modifier: Modifier = Modifier,
        leadingContent: (@Composable () -> Unit)? = null,
        trailingContent: (@Composable () -> Unit)? = null,
        headlineContent: @Composable () -> Unit,
    )
}

private class GroupedListItem(
    val modifier: Modifier,
    val leadingContent: (@Composable () -> Unit)?,
    val trailingContent: (@Composable () -> Unit)?,
    val headlineContent: @Composable () -> Unit,
)

private class GroupedListItems : GroupedListScope {
    val items = mutableListOf<GroupedListItem>()

    override fun item(
        modifier: Modifier,
        leadingContent: (@Composable () -> Unit)?,
        trailingContent: (@Composable () -> Unit)?,
        headlineContent: @Composable () -> Unit,
    ) {
        items += GroupedListItem(modifier, leadingContent, trailingContent, headlineContent)
    }
}

private fun rowShape(
    index: Int,
    lastIndex: Int,
): Shape {
    val top = if (index == 0) 20.dp else 4.dp
    val bottom = if (index == lastIndex) 20.dp else 4.dp
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

/** One row of a [GroupedList]: leading content, headline, then trailing content. */
@Composable
private fun GroupedListRow(
    item: GroupedListItem,
    shape: Shape,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .then(item.modifier)
                .heightIn(min = 72.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item.leadingContent?.let { leading ->
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { leading() }
            }
            Spacer(Modifier.width(16.dp))
        }
        Box(Modifier.weight(1f)) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                ProvideTextStyle(MaterialTheme.typography.bodyLarge, item.headlineContent)
            }
        }
        item.trailingContent?.let { trailing ->
            Spacer(Modifier.width(16.dp))
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                ProvideTextStyle(MaterialTheme.typography.bodyMedium, trailing)
            }
        }
    }
}

@Preview(widthDp = 360)
@Composable
private fun GroupedListPreview(
    @PreviewParameter(AnkiDroidThemeProvider::class) theme: Theme,
) {
    AnkiDroidPreview(theme) {
        Column(
            modifier = Modifier.background(MaterialTheme.colorScheme.background).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            GroupedList {
                item(
                    leadingContent = { Icon(painterResource(R.drawable.ic_help_black_24dp), contentDescription = null) },
                    trailingContent = { Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null) },
                ) { Text("Option 1") }
                item(
                    leadingContent = { Icon(painterResource(R.drawable.ic_help_black_24dp), contentDescription = null) },
                    trailingContent = { Switch(checked = true, onCheckedChange = null) },
                ) { Text("Switch 1") }
            }
            GroupedList {
                item(trailingContent = { Text("Value 1") }) { Text("Option 2") }
                item { Text("Option 3") }
            }
        }
    }
}
