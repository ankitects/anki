// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.unit.dp
import com.ichi2.anki.ComposeScreenshotTest
import com.ichi2.anki.R
import org.junit.Test
import com.ichi2.anki.common.android.R as CommonR

class GroupedListScreenshotTest : ComposeScreenshotTest() {
    @Test
    fun groupedList() = capture("grouped_list", node = hasTestTag(SAMPLE_TAG)) { GroupedListSample() }

    @Test
    fun largeText() = capture("grouped_list_large_text", node = hasTestTag(SAMPLE_TAG), fontScale = 2f) { GroupedListSample() }
}

private const val SAMPLE_TAG = "grouped_list_sample"

@Composable
private fun GroupedListSample() {
    Column(
        modifier = Modifier.testTag(SAMPLE_TAG).background(MaterialTheme.colorScheme.background).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        GroupedList {
            item(
                leadingContent = { RowIcon(R.drawable.ic_settings_black) },
                trailingContent = { RowIcon(CommonR.drawable.ic_chevron_right) },
            ) { Text("Option 1") }
        }
        GroupedList {
            item(leadingContent = { RowIcon(R.drawable.ic_manual_black_24dp) }) { Text("Option 2") }
            item(
                leadingContent = { RowIcon(R.drawable.ic_sync) },
                trailingContent = { Switch(checked = true, onCheckedChange = null) },
            ) { Text("Switch 1") }
            item(
                leadingContent = { RowIcon(R.drawable.ic_notifications) },
                trailingContent = { Switch(checked = false, onCheckedChange = null) },
            ) { Text("Switch 2") }
        }
        GroupedList {
            item(trailingContent = { Text("Value 1") }) { Text("Option 3") }
            item(trailingContent = { Text("Value 2") }) { Text("Option 4") }
            item { Text("Option 5") }
        }
    }
}

@Composable
private fun RowIcon(
    @DrawableRes icon: Int,
) = Icon(painterResource(icon), contentDescription = null)
