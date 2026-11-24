// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Sumit Singh <sumitsinghkoranga7@gmail.com>
package com.ichi2.anki

import android.app.Application
import android.content.Context
import com.ichi2.widget.SmallWidgetStatus
import com.ichi2.widget.WidgetRepository

/**
 * Backs [WidgetRepository] with [MetaDB]. Swap this out to move widget
 * storage to MMKV/DataStore without touching the widget callers.
 *
 * TODO: replace the MetaDB backing with MMKV or DataStore (#20737)
 */
internal class MetaDbWidgetRepository(
    private val context: Context,
) : WidgetRepository {
    override fun storeSmallWidgetStatus(status: SmallWidgetStatus) = MetaDB.storeSmallWidgetStatus(context, status)

    override fun getWidgetSmallStatus(): SmallWidgetStatus = MetaDB.getWidgetSmallStatus(context)

    override fun dueCardsCount(): Int = MetaDB.getNotificationStatus(context)
}

/** Wires up [MetaDbWidgetRepository] as the global [WidgetRepository]. */
context(application: Application)
fun initializeWidgetRepository() {
    WidgetRepository.register(MetaDbWidgetRepository(application))
}
