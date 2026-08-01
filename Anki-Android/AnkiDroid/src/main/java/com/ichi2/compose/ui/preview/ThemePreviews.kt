// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.compose.ui.preview

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

// TODO: see if we can do better, all 4 themes should be made available for preview

/**
 * Multi-preview annotation that renders the annotated composable in both
 * light and dark system UI modes.
 *
 * Apply to any private `*Preview` function in a composable file so the IDE
 * shows both variants side-by-side without duplicating @Preview blocks.
 */
@Preview(
    name = "Light",
    group = "theme",
    uiMode = Configuration.UI_MODE_NIGHT_NO,
    showBackground = true,
)
annotation class ThemePreviews
