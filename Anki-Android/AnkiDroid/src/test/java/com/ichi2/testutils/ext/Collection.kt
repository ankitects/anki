/*
 *  Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.testutils.ext

import androidx.appcompat.app.AppCompatDelegate
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.libanki.Collection
import com.ichi2.utils.LanguageUtil

/**
 * Closes and reopens the backend using the provided [language], typically for
 * [CollectionManager.TR] calls
 *
 * This does not set the [application locales][AppCompatDelegate.setApplicationLocales]
 *
 * @param language tag in the form: `de` or `zh-CN`
 */
@Suppress("UnusedReceiverParameter")
suspend fun Collection.reopenWithLanguage(language: String) {
    LanguageUtil.setDefaultBackendLanguages(language)
    CollectionManager.discardBackend()
    CollectionManager.getColUnsafe()
}
