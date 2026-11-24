// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.ui.preview

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

/** Light and dark dialog previews with a transparent canvas around the dialog's outline. */
@Preview(
    name = "Light",
    group = "dialog",
    showBackground = false,
    uiMode = Configuration.UI_MODE_NIGHT_NO,
)
@Preview(
    name = "Dark",
    group = "dialog",
    showBackground = false,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
annotation class DialogPreviews
