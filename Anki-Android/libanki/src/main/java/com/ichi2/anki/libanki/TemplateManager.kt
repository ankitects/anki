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
 *
 *   This file incorporates code under the following license
 *   https://github.com/ankitects/anki/blob/2.1.34/pylib/anki/template.py
 *
 *     Copyright: Ankitects Pty Ltd and contributors
 *     License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
 */

package com.ichi2.anki.libanki

import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.libanki.TemplateManager.PartiallyRenderedCard.Companion.avTagsToNative
import com.ichi2.anki.libanki.backend.BackendUtils
import com.ichi2.anki.libanki.backend.model.toBackendNote
import com.ichi2.anki.libanki.utils.append
import com.ichi2.anki.libanki.utils.len
import net.ankiweb.rsdroid.exceptions.BackendTemplateException

private typealias Union<A, B> = Pair<A, B>
private typealias TemplateReplacementList = MutableList<Union<String?, TemplateManager.TemplateReplacement?>>

/**
 * Template.py in python. Called TemplateManager for technical reasons (conflict with Kotlin typealias)
 *
 * This file contains the Kotlin portion of the template rendering code.
 * Templates can have filters applied to field replacements.
 *
 * The Rust template rendering code will apply any built in filters, and stop at the first
 * unrecognized filter. The remaining filters are returned to Kotlin, and applied using the hook system.
 *
 * For example, {{myfilter:hint:text:Field}} will apply the built in text and hint filters,
 * and then attempt to apply myfilter. If no add-ons have provided the filter,
 * the filter is skipped.
 */
class TemplateManager {
    data class TemplateReplacement(
        val fieldName: String,
        var currentText: String,
        val filters: List<String>,
    )

    data class PartiallyRenderedCard(
        val qnodes: TemplateReplacementList,
        val anodes: TemplateReplacementList,
        val css: String,
        val latexSvg: Boolean,
        val isEmpty: Boolean,
    ) {
        companion object {
            fun fromProto(out: anki.card_rendering.RenderCardResponse): PartiallyRenderedCard {
                val qnodes = nodesFromProto(out.questionNodesList)
                val anodes = nodesFromProto(out.answerNodesList)

                return PartiallyRenderedCard(qnodes, anodes, out.css, out.latexSvg, out.isEmpty)
            }

            fun nodesFromProto(nodes: List<anki.card_rendering.RenderedTemplateNode>): TemplateReplacementList {
                val results: TemplateReplacementList = mutableListOf()
                for (node in nodes) {
                    if (node.valueCase == anki.card_rendering.RenderedTemplateNode.ValueCase.TEXT) {
                        results.append(Pair(node.text, null))
                    } else {
                        results.append(
                            Pair(
                                null,
                                TemplateReplacement(
                                    fieldName = node.replacement.fieldName,
                                    currentText = node.replacement.currentText,
                                    filters = node.replacement.filtersList,
                                ),
                            ),
                        )
                    }
                }

                return results
            }

            fun avTagToNative(tag: anki.card_rendering.AVTag): AvTag {
                val value = tag.valueCase
                return if (value == anki.card_rendering.AVTag.ValueCase.SOUND_OR_VIDEO) {
                    SoundOrVideoTag(filename = tag.soundOrVideo)
                } else {
                    TTSTag(
                        fieldText = tag.tts.fieldText,
                        lang = tag.tts.lang,
                        voices = tag.tts.voicesList,
                        otherArgs = tag.tts.otherArgsList,
                        // The backend currently sends speed = 1, even when undefined.
                        // We agreed that '1' should be classed as 'use system' and ignored
                        // https://github.com/ankidroid/Anki-Android/issues/15598#issuecomment-1953653639
                        speed = tag.tts.speed.let { if (it == 1f) null else it },
                    )
                }
            }

            fun avTagsToNative(tags: List<anki.card_rendering.AVTag>): List<AvTag> = tags.map { avTagToNative(it) }.toList()
        }
    }

    /**
     * Holds information for the duration of one card render.
     * This may fetch information lazily in the future, so please avoid
     * using the _private fields directly.
     */
    @Suppress("ktlint:standard:property-naming")
    class TemplateRenderContext(
        card: Card,
        note: Note,
        browser: Boolean = false,
        notetype: NotetypeJson? = null,
        template: CardTemplate? = null,
        private var fillEmpty: Boolean = false,
    ) {
        @Suppress("ktlint:standard:backing-property-naming")
        private var _card: Card = card

        @Suppress("ktlint:standard:backing-property-naming")
        private var _note: Note = note

        @Suppress("ktlint:standard:backing-property-naming")
        private var _browser: Boolean = browser

        @Suppress("ktlint:standard:backing-property-naming")
        private var _template: CardTemplate? = template

        private var noteType: NotetypeJson = notetype ?: note.notetype
        private var latexSvg = false

        companion object {
            fun fromExistingCard(
                col: Collection,
                card: Card,
                browser: Boolean,
            ): TemplateRenderContext = TemplateRenderContext(card, card.note(col), browser)

            fun fromCardLayout(
                note: Note,
                card: Card,
                notetype: NotetypeJson,
                template: CardTemplate,
                fillEmpty: Boolean,
            ): TemplateRenderContext =
                TemplateRenderContext(
                    card,
                    note,
                    notetype = notetype,
                    template = template,
                    fillEmpty = fillEmpty,
                )
        }

        /**
         * Returns the card being rendered.
         * Be careful not to call .q() or .a() on the card, or you'll create an
         * infinite loop.
         */
        fun card() = _card

        fun note() = _note

        fun noteType() = noteType

        fun latexSvg(): Boolean = latexSvg

        @NeedsTest(
            "TTS tags `fieldText` is correctly extracted when sources are parsed to file scheme",
        )
        fun render(col: Collection): TemplateRenderOutput {
            val partial: PartiallyRenderedCard
            try {
                partial = partiallyRender(col)
            } catch (e: BackendTemplateException) {
                return TemplateRenderOutput(
                    questionText = e.localizedMessage ?: e.toString(),
                    answerText = e.localizedMessage ?: e.toString(),
                    questionAvTags = emptyList(),
                    answerAvTags = emptyList(),
                )
            }

            val qtext = applyCustomFilters(partial.qnodes, this, frontSide = null)
            val qout = col.backend.extractAvTags(text = qtext, questionSide = true)

            val atext = applyCustomFilters(partial.anodes, this, frontSide = qout.text)
            val aout = col.backend.extractAvTags(text = atext, questionSide = false)

            val output =
                TemplateRenderOutput(
                    questionText = qout.text,
                    answerText = aout.text,
                    questionAvTags = avTagsToNative(qout.avTagsList),
                    answerAvTags = avTagsToNative(aout.avTagsList),
                    css = noteType().css,
                )

            latexSvg = partial.latexSvg

            if (!_browser) {
                onCardDidRender(col, output, this)
            }

            return output
        }

        fun partiallyRender(col: Collection): PartiallyRenderedCard {
            val proto =
                col.run {
                    if (_template != null) {
                        // card layout screen
                        backend.renderUncommittedCardLegacy(
                            _note.toBackendNote(),
                            _card.ord,
                            BackendUtils.toJsonBytes(_template!!),
                            fillEmpty,
                            true,
                        )
                    } else {
                        // existing card (eg study mode)
                        backend.renderExistingCard(_card.id, _browser, true)
                    }
                }
            return PartiallyRenderedCard.fromProto(proto)
        }

        /** Stores the rendered templates and extracted AV tags. */
        data class TemplateRenderOutput(
            var questionText: String,
            var answerText: String,
            val questionAvTags: List<AvTag>,
            val answerAvTags: List<AvTag>,
            val css: String = "",
        ) {
            fun questionAndStyle() = "<style>$css</style>$questionText"

            fun answerAndStyle() = "<style>$css</style>$answerText"
        }

        /** Complete rendering by applying any pending custom filters. */
        fun applyCustomFilters(
            rendered: TemplateReplacementList,
            ctx: TemplateRenderContext,
            frontSide: String?,
        ): String {
            // template already fully rendered?
            if (len(rendered) == 1 && rendered[0].first != null) {
                return rendered[0].first!!
            }

            var res = ""
            for (union in rendered) {
                if (union.first != null) {
                    res += union.first!!
                } else {
                    val node = union.second!!
                    // do we need to inject in FrontSide?
                    if (node.fieldName == "FrontSide" && frontSide != null) {
                        node.currentText = frontSide
                    }

                    var fieldText = node.currentText
                    for (filterName in node.filters) {
                        fieldFilters[filterName]?.let {
                            fieldText = it.apply(fieldText, node.fieldName, filterName, ctx)
                        }
                    }

                    res += fieldText
                }
            }
            return res
        }
    }

    /**
     * Defines custom `{{filters:..}}`
     *
     * Custom filters can check `filterName` to decide whether it should modify
     * `fieldText` or not before returning it
     */
    abstract class FieldFilter {
        abstract fun apply(
            fieldText: String,
            fieldName: String,
            filterName: String,
            ctx: TemplateRenderContext,
        ): String
    }

    companion object {
        val fieldFilters: MutableMap<String, FieldFilter> = mutableMapOf()
    }
}
