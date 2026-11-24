// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

/** Transient Android UI work requested by [CardBrowserViewModel]. */
sealed interface BrowserUiEffect {
    data object RefreshControls : BrowserUiEffect
}
