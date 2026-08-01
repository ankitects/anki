/*
 * Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>
 * Copyright (c) 2012 Kostas Spyropoulos <inigo.aldana@gmail.com>
 * Copyright (c) 2015 Houssam Salem <houssam.salem.au@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.libanki

import anki.card_rendering.ExtractLatexResponse
import anki.config.ConfigKey
import com.ichi2.anki.libanki.utils.LibAnkiAlias
import com.ichi2.anki.libanki.utils.append

data class ExtractedLatex(
    val fileName: String,
    val latexBody: String,
)

data class ExtractedLatexOutput(
    val html: String,
    val latex: List<ExtractedLatex>,
) {
    companion object {
        fun fromProto(proto: ExtractLatexResponse): ExtractedLatexOutput =
            ExtractedLatexOutput(
                html = proto.text,
                latex = proto.latexList.map { l -> ExtractedLatex(fileName = l.filename, latexBody = l.latexBody) },
            )
    }
}

@LibAnkiAlias("on_card_did_render")
fun onCardDidRender(
    col: Collection,
    output: TemplateManager.TemplateRenderContext.TemplateRenderOutput,
    ctx: TemplateManager.TemplateRenderContext,
) {
    output.questionText =
        renderLatex(
            output.questionText,
            ctx.noteType(),
            col,
        )
    output.answerText = renderLatex(output.answerText, ctx.noteType(), col)
}

/**
 * Convert embedded latex tags in text to image links.
 */
@LibAnkiAlias("render_latex")
fun renderLatex(
    html: String,
    model: NotetypeJson,
    col: Collection,
): String {
    val (html, err) = renderLatexRetuningErrors(html, model, col)
    val result = StringBuilder(html)
    if (err.isNotEmpty()) {
        result.append(err.joinToString("\n"))
    }
    return result.toString()
}

/**
 * Returns (text, errors).
 * errors will be non-empty if LaTeX failed to render.
 */
@LibAnkiAlias("render_latex_returning_errors")
fun renderLatexRetuningErrors(
    html: String,
    model: NotetypeJson,
    col: Collection,
    expandClozes: Boolean = false,
): Pair<String, List<String>> {
    val svg = model.latexsvg
    val header = model.latexPre
    val footer = model.latexPost

    val proto = col.backend.extractLatex(text = html, svg = svg, expandClozes = expandClozes)
    val out = ExtractedLatexOutput.fromProto(proto)
    val errors = mutableListOf<String>()
    val html = out.html
    val renderLatex = col.config.getBool(ConfigKey.Bool.RENDER_LATEX)

    for (latex in out.latex) {
        // don't need to render?
        if (col.media.have(latex.fileName)) {
            continue
        }
        if (!renderLatex) {
            errors.append(col.tr.preferencesLatexGenerationDisabled())
            return html to errors
        }

        val err = saveLatexImage(col, latex, header, footer, svg)
        if (err != null) {
            errors.append(err)
        }
    }
    return html to errors
}

// deliberately set the method to always return null as
// AnkiDroid does not support the generation of LaTeX images.
@Suppress("unused")
@LibAnkiAlias("_save_latex_image")
private fun saveLatexImage(
    col: Collection,
    extractedLatex: ExtractedLatex,
    header: String,
    footer: String,
    svg: Boolean,
): String? = null
