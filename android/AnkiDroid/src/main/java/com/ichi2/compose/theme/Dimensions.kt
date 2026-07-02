// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.compose.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacing scale for Compose screens, read the same way as the Material 3 slots
 * (`MaterialTheme.dimensions.space200`) Material 3 ships no spacing tokens of
 * its own, so this fills that gap.
 *
 * [screenEdge] and [sectionGap] are semantic aliases for the two steps used most
 * often at screen level; reach for the `spaceNNN` tokens elsewhere.
 */
@Immutable
data class Dimensions(
    val space25: Dp = 2.dp,
    val space50: Dp = 4.dp,
    val space100: Dp = 8.dp,
    val space150: Dp = 12.dp,
    val space200: Dp = 16.dp,
    val space300: Dp = 24.dp,
    val space400: Dp = 32.dp,
    val space800: Dp = 64.dp,
    val screenEdge: Dp = 24.dp,
    val sectionGap: Dp = 16.dp,
)

/**
 * Provides the active [Dimensions] down the Compose tree. Prefer reading via
 * [MaterialTheme.dimensions]; use this directly only to override tokens in a
 * `CompositionLocalProvider` (previews, tests).
 */
val LocalDimensions = staticCompositionLocalOf { Dimensions() }

/**
 * Access the spacing scale the same way as [MaterialTheme.colorScheme] or
 * [MaterialTheme.typography].
 */
val MaterialTheme.dimensions: Dimensions
    @Composable
    @ReadOnlyComposable
    get() = LocalDimensions.current
