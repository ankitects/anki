/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.common.android

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import timber.log.Timber
import java.util.Locale

/**
 * Returns a [Context] wrapped with the in-app locale.
 *
 * Needed for resources accessed outside an Activity (e.g. from a [android.content.BroadcastReceiver]
 * or [android.app.Service]):
 *
 * On API < 33, [AppCompatDelegate.setApplicationLocales] only applies to Activity contexts, so
 * resources resolve in the system locale.
 *
 * Returns [this] unchanged (System language) when no in-app language is set.
 *
 * This method will not throw.
 */
fun Context.withAppLocale(): Context =
    try {
        val tag = getCurrentLocaleTag()
        if (tag.isEmpty()) return this
        val configuration = Configuration(resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(tag))
        return createConfigurationContext(configuration)
    } catch (e: Exception) {
        Timber.w(e, "withAppLocale")
        return this
    }

/**
 * This should always be called after Activity.onCreate()
 * @return locale language tag of the app configured language
 */
fun getCurrentLocaleTag(): String = AppCompatDelegate.getApplicationLocales().toLanguageTags()
