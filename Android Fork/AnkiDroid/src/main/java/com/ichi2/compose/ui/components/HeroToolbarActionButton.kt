// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ichi2.anki.CommonString
import com.ichi2.compose.ui.preview.AnkiDroidPreview
import com.ichi2.compose.ui.preview.ThemePreviews

/** A prominent primary action for a toolbar. */
@Composable
fun HeroToolbarActionButton(
    modifier: Modifier = Modifier,
    label: String = stringResource(CommonString.multimedia_editor_field_editing_done),
    enabled: Boolean = true,
    onClick: () -> Unit = {},
) {
    Box(modifier.padding(horizontal = 8.dp)) {
        Button(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp), enabled = enabled) {
            Text(text = label)
        }
    }
}

@ThemePreviews
@Composable
private fun HeroToolbarActionButtonPreview() {
    AnkiDroidPreview {
        HeroToolbarActionButton()
    }
}
