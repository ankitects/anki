// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.widget

/**
 * @param dueCardsCount The number of due cards (new + lrn + rev)
 * @param eta The estimated time to review
 */
data class SmallWidgetStatus(
    val dueCardsCount: Int,
    val eta: Int,
)
