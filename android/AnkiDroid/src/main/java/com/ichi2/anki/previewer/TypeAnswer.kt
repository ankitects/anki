/*
 *  Copyright (c) 2024 Brayan Oliveira <brayandso.dev@gmail.com>
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
package com.ichi2.anki.previewer

import android.os.LocaleList
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.libanki.Field
import com.ichi2.anki.servicelayer.LanguageHintService
import com.ichi2.anki.servicelayer.LanguageHintService.languageHint
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.VisibleForTesting

/**
 * Handles `type in the answer card` properties
 *
 * @see [combining]
 * @see [imeHintLocales]
 * */
@NeedsTest("combining and non combining answers are properly parsed")
@NeedsTest("cloze and non cloze 'type in the answer' cards are properly parsed")
class TypeAnswer private constructor(
    private val text: String,
    /** whether combining characters should be compared. Defined by the presence of the
     *   `nc:` specifier in the type answer tag */
    private val combining: Boolean,
    private val field: Field,
    var expectedAnswer: String,
) {
    val font = field.font
    val fontSize = field.fontSize

    /** a field property specific to AnkiDroid that allows to automatically select
     *   a language for the keyboard. @see [LanguageHintService] */
    val imeHintLocales: LocaleList? by lazy {
        field.languageHint?.let { LocaleList(it) }
    }

    suspend fun answerFilter(typedAnswer: String = ""): String {
        val answerComparison = withCol { compareAnswer(expectedAnswer, provided = typedAnswer, combining = combining) }

        @Language("HTML")
        val repl = """<div style="font-family: '$font'; font-size: ${fontSize}px">$answerComparison</div>"""
        return typeAnsRe.replace(text, Regex.escapeReplacement(repl))
    }

    companion object {
        /** removes `[[type:]]` tags from the given [text] */
        fun removeTags(text: String): String = typeAnsRe.replace(text, "")

        /**
         * @return a [TypeAnswer] instance if [text] contains a `[[type:Field]]` tag
         * with a valid field name, or null if not.
         *
         * ([Source](https://github.com/ankitects/anki/blob/8af63f81eb235b8d21df4e8eeaa6e02f46b3fbf6/qt/aqt/reviewer.py#L702))
         */
        suspend fun getInstance(
            card: Card,
            text: String,
        ): TypeAnswer? {
            val match = typeAnsRe.find(text) ?: return null
            val rawField = match.groups[1]?.value ?: return null

            var combining = true
            val typeAnsFieldName =
                if (rawField.startsWith("cloze:")) {
                    rawField.split(":")[1]
                } else if (rawField.startsWith("nc:")) {
                    combining = false
                    rawField.split(":")[1]
                } else {
                    rawField
                }
            val fields = withCol { card.noteType(this).fields }
            val typeAnswerField = fields.firstOrNull { it.name == typeAnsFieldName } ?: return null
            val expectedAnswer = getExpectedTypeInAnswer(card, rawField = rawField, fieldName = typeAnsFieldName)

            return TypeAnswer(
                text = text,
                combining = combining,
                field = typeAnswerField,
                expectedAnswer = expectedAnswer,
            )
        }

        /**
         * @param rawField the content (x) of a `{{type:x}}` placeholder
         * @param fieldName the name of the field in the card template
         */
        @NeedsTest("cloze type-in-answer are properly parsed")
        private suspend fun getExpectedTypeInAnswer(
            card: Card,
            rawField: String,
            fieldName: String,
        ): String {
            val expected = withCol { card.note(this@withCol).getItem(fieldName) }
            return if (rawField.startsWith("cloze:")) {
                val clozeIdx = card.ord + 1
                withCol {
                    extractClozeForTyping(expected, clozeIdx)
                }
            } else {
                expected
            }
        }
    }
}

@VisibleForTesting
val typeAnsRe = Regex("\\[\\[type:(.+?)]]")
