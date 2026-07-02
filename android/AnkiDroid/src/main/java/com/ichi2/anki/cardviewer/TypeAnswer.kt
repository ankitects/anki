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

package com.ichi2.anki.cardviewer

import android.content.SharedPreferences
import android.content.res.Resources
import androidx.annotation.VisibleForTesting
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.R
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.servicelayer.LanguageHint
import com.ichi2.anki.servicelayer.LanguageHintService.languageHint
import org.intellij.lang.annotations.Language
import timber.log.Timber
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * @param useInputTag use an `<input>` tag to allow for HTML styling
 * @param autoFocus Whether the user wants to focus "type in answer"
 */
class TypeAnswer(
    val useInputTag: Boolean,
    val autoFocus: Boolean,
) {
    /** The correct answer in the compare to field if answer should be given by learner. `null` if no answer is expected. */
    var correct: String? = null
        private set

    var combining: Boolean = true
        private set

    /** What the learner actually typed (externally mutable) */
    var input = ""

    /** Font face of the 'compare to' field */
    var font = ""
        private set

    /** The font size of the 'compare to' field */
    var size = 0
        private set

    var languageHint: LanguageHint? = null
        private set

    /**
     * Optional warning for when a typed answer can't be displayed
     *
     * * empty card
     * * unknown field specified [R.string.unknown_type_field_warning]
     * */
    var warning: String? = null
        private set

    /**
     * @return true If entering input via EditText
     * and if the current card has a `{{type:field}}` on the card template
     */
    fun validForEditText(): Boolean = !useInputTag && correct != null

    fun autoFocusEditText(): Boolean = validForEditText() && autoFocus

    /**
     * Extract type answer/cloze text and font/size
     * @param card The next card to display
     */
    fun updateInfo(
        col: Collection,
        card: Card,
        res: Resources,
    ) {
        combining = true
        correct = null
        val q = card.question(col)
        val m = PATTERN.matcher(q)
        var clozeIdx = 0
        if (!m.find()) {
            return
        }
        var fldTag = m.group(1)!!
        // if it's a cloze, extract data
        if (fldTag.startsWith("cloze:")) {
            // get field and cloze position
            clozeIdx = card.ord + 1
            fldTag = fldTag.split(":").toTypedArray()[1]
        }
        if (fldTag.startsWith("nc:")) {
            combining = false
            fldTag = fldTag.split(":").toTypedArray()[1]
        }
        // loop through fields for a match
        for (fld in card.noteType(col).fields) {
            val name = fld.name
            if (name == fldTag) {
                correct = card.note(col).getItem(name)
                if (clozeIdx != 0) {
                    // narrow to cloze
                    correct = contentForCloze(correct!!, clozeIdx)
                }
                font = fld.font
                size = fld.fontSize
                languageHint = fld.languageHint
                break
            }
        }
        when (correct) {
            null -> {
                warning =
                    if (clozeIdx != 0) {
                        CollectionManager.TR.cardTemplateRenderingEmptyFront()
                    } else {
                        res.getString(R.string.unknown_type_field_warning, fldTag)
                    }
            }
            "" -> {
                correct = null
            }
            else -> {
                warning = null
            }
        }
    }

    /**
     * Format question field when it contains typeAnswer or clozes. If there was an error during type text extraction, a
     * warning is displayed
     *
     * @param buf The question text
     * @return The formatted question text
     */
    @Suppress("ktlint:standard:max-line-length")
    fun filterQuestion(buf: String): String {
        val m = PATTERN.matcher(buf)
        if (warning != null) {
            return m.replaceFirst(warning!!)
        }
        val sb = java.lang.StringBuilder()

        fun append(
            @Language("HTML") html: String,
        ) = sb.append(html)
        if (useInputTag) {
            // These functions are defined in the JavaScript file assets/scripts/card.js. We get the text back in
            // shouldOverrideUrlLoading() in createWebView() in this file.

            append(
                """<center>
<input type="text" name="typed" id="typeans" data-focus="$autoFocus" onfocus="taFocus();" oninput='taChange(this);' onKeyPress="return taKey(this, event)" autocomplete="off" """,
            )
            // We have to watch out. For the preview we don’t know the font or font size. Skip those there. (Anki
            // desktop just doesn't show the input tag there. Do it with standard values here instead.)
            if (font.isNotEmpty() && size > 0) {
                append("style=\"font-family: '")
                    .append(font)
                    .append("'; font-size: ")
                    .append(size)
                    .append("px;\" ")
            }
            append(">\n</center>\n")
        } else {
            append("<span id=\"typeans\" class=\"typePrompt")
            append("\">........</span>")
        }
        return m.replaceAll(sb.toString())
    }

    /**
     * Fill the placeholder for the type comparison: `[[type:(.+?)]]`
     *
     * Replaces with the HTML for the correct answer, and the comparison to the correct answer if appropriate.
     *
     * @param answer The card content on the back of the card
     *
     * @return The formatted answer text with `[[type:(.+?)]]` replaced with HTML
     */
    fun filterAnswer(answer: String): String {
        val userAnswer = input
        val correctAnswer = correct
        Timber.d("correct answer = %s", correctAnswer)
        Timber.d("user answer = %s", userAnswer)
        return filterAnswer(answer, userAnswer, correctAnswer ?: "")
    }

    /**
     * Fill the placeholder for the type comparison. Show the correct answer, and the comparison if appropriate.
     *
     * @param answer The answer text
     * @param userAnswer Text typed by the user, or empty.
     * @param correctAnswer The correct answer, taken from the note.
     * @return The formatted answer text
     */
    fun filterAnswer(
        answer: String,
        userAnswer: String,
        correctAnswer: String,
    ): String {
        val m: Matcher = PATTERN.matcher(answer)
        val sb = StringBuilder()

        fun append(
            @Language("HTML") html: String,
        ) = sb.append(html)

        val comparisonText = CollectionManager.compareAnswer(correctAnswer, userAnswer, combining)
        append(Matcher.quoteReplacement(comparisonText))
        return m.replaceAll(sb.toString())
    }

    companion object {
        /** Regular expression in card data for a 'type answer' after processing has occurred */
        val PATTERN: Pattern = Pattern.compile("\\[\\[type:(.+?)]]")

        fun createInstance(preferences: SharedPreferences): TypeAnswer =
            TypeAnswer(
                useInputTag = preferences.getBoolean("useInputTag", false),
                autoFocus = preferences.getBoolean("autoFocusTypeInAnswer", true),
            )

        /**
         * Return the correct answer to use for {{type::cloze::NN}} fields.
         *
         * @param txt The field text with the clozes
         * @param idx The index of the cloze to use
         * @return If the cloze strings are the same, return a single cloze string, otherwise, return
         * a string with a comma-separated list of strings with the correct index.
         */
        @VisibleForTesting
        fun contentForCloze(
            txt: String,
            idx: Int,
        ): String? {
            // In Android, } should be escaped
            val re = Pattern.compile("\\{\\{c$idx::(.+?)\\}\\}")
            val m = re.matcher(txt)
            val matches: MutableList<String?> = ArrayList()
            var groupOne: String
            while (m.find()) {
                groupOne = m.group(1)!!
                val colonColonIndex = groupOne.indexOf("::")
                if (colonColonIndex > -1) {
                    // Cut out the hint.
                    groupOne = groupOne.take(colonColonIndex)
                }
                matches.add(groupOne)
            }
            val uniqMatches: Set<String?> = HashSet(matches) // Allow to check whether there are distinct strings

            // Make it consistent with the Desktop version (see issue #8229)
            return if (uniqMatches.size == 1) {
                matches[0]
            } else {
                matches.joinToString(", ")
            }
        }
    }
}
