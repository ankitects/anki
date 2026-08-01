/*
 *  Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.model

import androidx.annotation.CheckResult
import com.ichi2.anki.libanki.NotetypeJson

// Classes relating to a chain of field filters: {{type:cloze:Front}}

/**
 * An accumulated list of properties which a filter chain would apply to the field value.
 *
 * Filters define the operations they perform, and their requirements, allowing
 * a check to see if a filter can be applied to a chain of filters.
 *
 * For example: if a HTML hint is produced with `hint`, `text` which strips HTML
 * would not be a suggested filter in AnkiDroid.
 *
 * NOTE: Anki Desktop allows addons to define custom filters, these constraints should
 * only be used to validate inserting new filters in the AnkiDroid editor.
 *
 * @see FieldFilter
 * @see com.ichi2.anki.libanki.TemplateManager.FieldFilter
 */
data class FilterContext(
    /**
     * Whether the filter is being applied to a [cloze note type][NotetypeJson.isCloze].
     *
     * Cloze note types support additional field filters
     */
    val isClozeNoteType: Boolean,
    /**
     * Whether HTML was produced by the filter(s)
     *
     * The `text` filter strips HTML, and should not be recommended after
     */
    val producesHtml: Boolean = false,
    /**
     * Whether text in the form: ```A[B]``` will be processed.
     *
     * This relates to producing Ruby annotations, typically outputting the 'reading' of Japanese text:
     * Furigana, Kanji and Kana.
     *
     * A number of filters expect input text in the format: `日本語[にほんご]`.
     * If this filter is set, then the square brackets have been removed, and further processing
     * expecting the pronunciation syntax will fail.
     */
    val stripsAnkiPronunciationSyntax: Boolean = false,
    /**
     * Whether no further processing of the field is required.
     *
     * Text to speech and type filters produce `[anki:]` or `[[type:]]` annotations, designed to be
     * processed by individual Anki clients.
     *
     * Applying more filters to these outputs would corrupt them.
     */
    val isTerminal: Boolean = false,
)

/**
 * Controls how a field is rendered on a card (type the answer, TTS, Strip HTML, etc.)
 *
 * In `{{hint:Front}}`, `hint` is the filter.
 *
 * Filters are executed from right to left: `{{hint:text:Front}}` is equivalent to:
 * `hint(text(Front))`
 *
 * See [Anki Manual: Field Replacements](https://docs.ankiweb.net/templates/fields.html)
 */
sealed interface FieldFilter {
    /**
     * The name of the filter.
     *
     * Use [render] when building the output string, as the TTS filter contains options.
     */
    val name: String

    /**
     * Whether this filter can be applied given the current [context]
     *
     * Override to impose additional constraints (e.g. requiring a cloze note type).
     */
    fun canApplyTo(context: FilterContext): Boolean = !context.isTerminal

    /**
     * Returns the updated [FilterContext] which results from applying this filter.
     *
     * Override this method if applying this filter may mean other filters can't be applied.
     */
    fun updateContext(input: FilterContext): FilterContext = input

    /**
     * Outputs the test of the filter, without the `:` character. e.g. `cloze-only`
     *
     * Override if filter options are necessary: `tts lang=en_GB`
     */
    fun render(): String = name

    /**
     * Whether the filter can be applied
     *
     * Override if options are necessary (e.g. `tts` is invalid without a `lang` parameter)
     */
    val isValid: Boolean get() = true
}

/**
 * [FieldFilters] defines all default [field filters][FieldFilter] in AnkiDroid.
 *
 * NOTE: Anki Desktop addons can support custom filters other than these.
 *  These filters are skipped in AnkiDroid.
 */
object FieldFilters {
    val ALL =
        listOf(
            TextFilter,
            ClozeFilter,
            ClozeOnlyFilter,
            HintFilter,
            TypeTheAnswerFilter,
            TypeTheAnswerNonCombiningFilter,
            TextToSpeechFilter(),
            FuriganaFilter,
            KanaFilter,
            KanjiFilter,
        )

    /**
     * A filter which strips HTML from a field, often used in custom HTML or JavaScript
     * to avoid user error.
     *
     * ```
     * // keep the link intact if '<b>word</b>' is entered:
     * <a href="http://example.com/search?q={{text:Expression}}">lookup</a>
     * ```
     *
     * See [Anki Manual: Field Replacements - HTML Stripping](https://docs.ankiweb.net/templates/fields.html#html-stripping)
     */
    object TextFilter : FieldFilter {
        override val name: String = "text"

        override fun canApplyTo(context: FilterContext) = super.canApplyTo(context) && !context.producesHtml
    }

    /**
     * Specifies that the field should be processed as containing cloze deletions.
     *
     * `{{cloze:Front}}` enables the `{{c1::` syntax in fields.
     *
     * `{{c2::foo}}` means that any field substitution on Card 2 wrapped in `{{c2::...}}` is
     * transformed to `[...]` on the front of the card.
     *
     * A hint parameter may be supplied: `{{c1::answer::hint}}`, replacing `[...]` with
     * ```[hint]```
     *
     * When the card is flipped, `[...]` is and displayed as the following (in blue):
     * `<span class="cloze" data-ordinal="2">answer</span>` to reveal the answer.
     *
     * See [Anki Manual: Card Generation - Cloze Templates](https://docs.ankiweb.net/templates/generation.html#cloze-templates)
     */
    object ClozeFilter : FieldFilter {
        override val name: String = "cloze"

        override fun canApplyTo(context: FilterContext): Boolean = super.canApplyTo(context) && context.isClozeNoteType
    }

    /**
     * `{{cloze-only:Front}}` extracts cloze deletion(s) in the Front field, allowing them to
     * be spoken, or processed with JavaScript.
     *
     * If multiple deletions occur, they are separated by `, `
     *
     * ```
     * // {{cloze-only:Front}}, where Front = "{{c1::a}} {{c2::b}} {{c1::c}}" on Card 1 produces:
     * a, c
     * ```
     */
    object ClozeOnlyFilter : FieldFilter {
        override val name: String = "cloze-only"

        override fun canApplyTo(context: FilterContext): Boolean = super.canApplyTo(context) && context.isClozeNoteType
    }

    /**
     * A hint field obscures an element until it's tapped, allowing a hint without needing to flip
     * a card.
     *
     * Hints do not work on audio, as the audio will play automatically.
     *
     * ```
     * <!-- {{hint:Front}} with Front = "abc" produces; id: 123; -->
     * <a class=hint href="#" onclick="this.style.display='none';
     * document.getElementById('hint123').style.display='block';
     * return false;" draggable=false>
     *     Front
     * </a>
     * <div id="hint123" class=hint style="display: none">abc</div>
     * ```
     *
     * See [Anki Manual: Field Replacements - Hint Fields](https://docs.ankiweb.net/templates/fields.html#hint-fields)
     */
    object HintFilter : FieldFilter {
        override val name: String = "hint"

        override fun updateContext(input: FilterContext) = input.copy(producesHtml = true)
    }

    /**
     * `{{type:}}` produces a type the answer card: On the front of the card, a text input is
     * displayed on the front of the card. This input is persisted, and compared to the value of the
     * field on the back of the card, and the user's results are compared.
     *
     * Anki template rendering does not produce HTML here. Instead, the following markers are
     * produced:
     *
     * ```
     * // assume Front = abc and the syntax is {{type:[modifier]:Front}}
     * [[type:abc]]
     * [[type:cloze:abc]]
     * [[type:nc:abc]]
     * ```
     *
     * Note: `type` enables a preceding filter: `{{nc:Front}}` is a noop, but `{{type:nc:Front}}`
     * processed.
     */
    object TypeTheAnswerFilter : FieldFilter {
        override val name: String = "type"

        override fun updateContext(input: FilterContext) = input.copy(isTerminal = true)
    }

    /**
     * A `type` filter which avoids comparing accepts when evaluating a typed answer.
     *
     * If used:
     *
     * - "elite" matches "élite"
     * - Arabic diacritics are removed
     *
     * ```
     * // Template output of {{type:nc:Front}} when Front = abc
     * [[type:nc:abc]]
     * ```
     *
     * @see TypeTheAnswerFilter
     *
     * See [Anki Manual: Field Replacements - Ignoring Diacritics](https://docs.ankiweb.net/templates/fields.html#ignoring-diacritics)
     */
    object TypeTheAnswerNonCombiningFilter : FieldFilter {
        override val name: String = "type:nc"

        override fun updateContext(input: FilterContext) = input.copy(isTerminal = true)
    }

    /**
     * A terminal operator which outputs a special marker to the HTML. This marker is processed by
     * each Anki client, speaking it using the system text-to-speech engine and replacing it with
     * an HTML 'play' button, allowing the user to replay the spoken text.
     *
     * This filter requires a 'language' option, and supports an unbounded list of options
     * which individual TTS implementations may process.
     *
     * ```
     * // {{tts en_US speed=0.8:Front}}. Anything after 'en_US' is copied into the tag below:
     * [anki:tts lang=en_US speed=0.8]This text should is read.[/anki:tts]
     * ```
     *
     * [See: Anki Manual: Field Replacements - Text to Speech for individual fields](https://docs.ankiweb.net/templates/fields.html#text-to-speech-for-individual-fields)
     */
    data class TextToSpeechFilter(
        val options: TextToSpeechOptions = TextToSpeechOptions(),
    ) : FieldFilter {
        override val name: String = "tts"

        override fun updateContext(input: FilterContext) = input.copy(isTerminal = true)

        override val isValid: Boolean
            get() = super.isValid && options.isValid

        /** Outputs the filter and all options. e.g. tts lang=en_GB */
        override fun render(): String = "$name${options.render()}"

        // TODO: What should happen if rendering and .isValid = false
        data class TextToSpeechOptions(
            val language: String? = null,
            val voices: List<String>? = null,
            val speed: Float? = null,
        ) {
            fun render(): String {
                val output =
                    buildList {
                        if (language != null) add("lang=$language")
                        if (voices != null) add("voices=${voices.joinToString(separator = ",")}")
                        if (speed != null) add("speed=$speed")
                    }.joinToString(separator = " ")
                if (output.isEmpty()) return ""
                return " $output"
            }

            val isValid: Boolean
                get() = !language.isNullOrEmpty()
        }
    }

    /**
     * Outputs text in square brackets as
     *  [ruby characters](https://en.wikipedia.org/wiki/Ruby_character)
     *
     * ```
     * 日本語[にほんご]
     * ```
     *
     * outputs:
     *
     * ```html
     * <ruby><rb>日本語</rb><rt>にほんご</rt></ruby>
     * ```
     *
     * which looks like:
     *
     * ```
     * にほんご // the reading appears above the word, in smaller ruby characters
     * 日本語
     * ```
     *
     * See [Anki Manual: Field Replacements - Ruby Characters](https://docs.ankiweb.net/templates/fields.html#ruby-characters)
     *
     * @see KanaFilter
     * @see KanjiFilter
     */
    object FuriganaFilter : FieldFilter {
        override val name: String = "furigana"

        override fun canApplyTo(context: FilterContext) = super.canApplyTo(context) && !context.stripsAnkiPronunciationSyntax

        override fun updateContext(input: FilterContext) = input.copy(stripsAnkiPronunciationSyntax = true, producesHtml = true)
    }

    /**
     * Outputs only the text in square brackets of a field
     *
     * Example:
     * ```
     * 日本語[にほんご] => にほんご
     * ```
     *
     * See [Anki Manual: Field Replacements - Ruby Characters](https://docs.ankiweb.net/templates/fields.html#ruby-characters)
     *
     * @see FuriganaFilter
     */
    object KanaFilter : FieldFilter {
        override val name: String = "kana"

        override fun canApplyTo(context: FilterContext) = super.canApplyTo(context) && !context.stripsAnkiPronunciationSyntax

        override fun updateContext(input: FilterContext) = input.copy(stripsAnkiPronunciationSyntax = true)
    }

    /**
     * Outputs only the field text which is NOT in square brackets
     *
     * Example:
     * ```
     * 日本語[にほんご] => 日本語
     * ```
     *
     * See [Anki Manual: Field Replacements - Ruby Characters](https://docs.ankiweb.net/templates/fields.html#ruby-characters)
     *
     * @see FuriganaFilter
     */
    object KanjiFilter : FieldFilter {
        override val name: String = "kanji"

        override fun canApplyTo(context: FilterContext) = super.canApplyTo(context) && !context.stripsAnkiPronunciationSyntax

        override fun updateContext(input: FilterContext) = input.copy(stripsAnkiPronunciationSyntax = true)
    }
}

/**
 * A valid list of field filters
 *
 * `{{text:hint:Front}}` would be invalid, as 'text' strips the HTML from 'hint'
 */
class FieldFilterChain private constructor(
    val fieldName: FieldName,
    // NOTE: filters are stored and applied right to left {{hint:text:Front}} = hint(text(Front))
    // filters[0] is the rightmost filter visually in the chain
    val filters: List<FieldFilter>,
    val context: FilterContext,
    val isValid: Boolean,
) {
    /**
     * Returns a new chain if a filter can be added, `null` otherwise
     *
     * See: [FieldFilter.canApplyTo]. Duplicate filters are also invalid in a chain
     */
    @CheckResult
    fun tryAdd(
        filter: FieldFilter,
        allowInvalid: Boolean = false,
    ): FieldFilterChain? {
        if (!allowInvalid && (!filter.isValid || !this.isValid)) return null
        if (!filter.canApplyTo(context)) return null

        // filters cannot be applied twice
        if (filters.map { it.name }.contains(filter.name)) return null

        return FieldFilterChain(
            fieldName = fieldName,
            filters = filters + filter,
            context = filter.updateContext(context),
            isValid = this.isValid && filter.isValid,
        )
    }

    @CheckResult
    fun render() = toString()

    override fun toString(): String {
        if (filters.isEmpty()) return """{{$fieldName}}"""
        val renderedFilters = filters.reversed().joinToString(separator = ":") { it.render() }
        return "{{$renderedFilters:$fieldName}}"
    }

    companion object {
        @CheckResult
        fun build(
            field: FieldName,
            isClozeNoteType: Boolean,
        ): FieldFilterChain =
            FieldFilterChain(
                fieldName = field,
                filters = emptyList(),
                context = FilterContext(isClozeNoteType = isClozeNoteType),
                isValid = true,
            )
    }
}
