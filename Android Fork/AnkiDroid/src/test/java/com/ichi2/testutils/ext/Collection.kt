// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils.ext

import androidx.appcompat.app.AppCompatDelegate
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.Note
import com.ichi2.anki.libanki.testutils.ext.addNote
import com.ichi2.anki.libanki.testutils.ext.createBasicTypingNoteType
import com.ichi2.utils.LanguageUtil
import net.ankiweb.rsdroid.BackendFactory

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
    val previousLanguages = BackendFactory.defaultLanguages
    try {
        LanguageUtil.setDefaultBackendLanguages(language)
        CollectionManager.discardBackend()
        CollectionManager.getColUnsafe()
    } finally {
        BackendFactory.defaultLanguages = previousLanguages
    }
}

/**
 * Add a typing note with the `nosuggest` filter.
 *
 * @see com.ichi2.anki.model.FieldFilters.NoSuggestFilter
 */
fun Collection.addNoSuggestNote(): Note {
    val noteType = createBasicTypingNoteType("No suggestions")
    noteType.templates[0].qfmt = "{{Front}}{{nosuggest:type:Back}}"
    notetypes.save(noteType)
    return newNote(noteType).also {
        it.setField(0, "Question without suggestions")
        it.setField(1, "été")
        addNote(it)
    }
}
