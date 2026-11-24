// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.destinations

sealed class WidgetConfigDestination : Destination() {
    abstract val appWidgetId: Int

    data class CardAnalysis(
        override val appWidgetId: Int,
    ) : WidgetConfigDestination()

    data class DeckPicker(
        override val appWidgetId: Int,
    ) : WidgetConfigDestination()
}
