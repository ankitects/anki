/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.servicelayer

import android.os.LocaleList
import android.widget.EditText
import com.ichi2.anki.common.utils.ext.getStringOrNull
import com.ichi2.anki.libanki.Field
import com.ichi2.anki.libanki.NotetypeJson
import com.ichi2.anki.libanki.Notetypes
import timber.log.Timber
import java.util.Locale

/**
 * The language that a keyboard should open with when an [EditText] is selected
 *
 * Used so a user doesn't need to change keyboard languages when adding a note, or typing answers
 *
 * [2021] GBoard is the only known keyboard which supports this API
 */
typealias LanguageHint = Locale

object LanguageHintService {
    fun setLanguageHintForField(
        notetypes: Notetypes,
        notetype: NotetypeJson,
        fieldPos: Int,
        selectedLocale: Locale,
    ) {
        val field = notetype.getField(fieldPos)
        field.languageHint = selectedLocale
        notetypes.save(notetype)

        Timber.i("Set field locale to %s", selectedLocale)
    }

    fun EditText.applyLanguageHint(languageHint: LanguageHint?) {
        this.imeHintLocales = if (languageHint != null) LocaleList(languageHint) else null
    }

    var Field.languageHint: LanguageHint?
        get() = jsonObject.getStringOrNull("ad-hint-locale") ?.let { Locale.forLanguageTag(it) }
        set(value) {
            jsonObject.put("ad-hint-locale", value?.toLanguageTag())
        }
}
