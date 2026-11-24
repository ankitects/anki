// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Sumit Singh <sumitsinghkoranga7@gmail.com>
package com.ichi2.widget

/** The active [WidgetRepository], set during app startup via [WidgetRepository.register]. */
lateinit var widgetRepository: WidgetRepository
    private set

/**
 * Storage for widget status, keeping widget code decoupled from [com.ichi2.anki.MetaDB].
 * This moves with the widgets once they become a separate module.
 */
interface WidgetRepository {
    fun storeSmallWidgetStatus(status: SmallWidgetStatus)

    fun getWidgetSmallStatus(): SmallWidgetStatus

    /** @return the number of due cards; always >= 0 */
    fun dueCardsCount(): Int

    companion object {
        /** Use during app startup to set the global [WidgetRepository] instance. */
        fun register(repository: WidgetRepository) {
            widgetRepository = repository
        }
    }
}
