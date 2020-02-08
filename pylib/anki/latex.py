# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import html
import os
import re
import shutil
from typing import Any, Optional

import anki
from anki import hooks
from anki.lang import _
from anki.models import NoteType
from anki.template import TemplateRenderContext, TemplateRenderOutput
from anki.utils import call, checksum, isMac, namedtmp, stripHTML, tmpdir

pngCommands = [
    ["latex", "-interaction=nonstopmode", "tmp.tex"],
    ["dvipng", "-D", "200", "-T", "tight", "tmp.dvi", "-o", "tmp.png"],
]

svgCommands = [
    ["latex", "-interaction=nonstopmode", "tmp.tex"],
    ["dvisvgm", "--no-fonts", "--exact", "-Z", "2", "tmp.dvi", "-o", "tmp.svg"],
]

build = True  # if off, use existing media but don't create new
regexps = {
    "standard": re.compile(r"\[latex\](.+?)\[/latex\]", re.DOTALL | re.IGNORECASE),
    "expression": re.compile(r"\[\$\](.+?)\[/\$\]", re.DOTALL | re.IGNORECASE),
    "math": re.compile(r"\[\$\$\](.+?)\[/\$\$\]", re.DOTALL | re.IGNORECASE),
}

# add standard tex install location to osx
if isMac:
    os.environ["PATH"] += ":/usr/texbin:/Library/TeX/texbin"


def stripLatex(text) -> Any:
    for match in regexps["standard"].finditer(text):
        text = text.replace(match.group(), "")
    for match in regexps["expression"].finditer(text):
        text = text.replace(match.group(), "")
    for match in regexps["math"].finditer(text):
        text = text.replace(match.group(), "")
    return text


def on_card_did_render(output: TemplateRenderOutput, ctx: TemplateRenderContext):
    output.question_text = render_latex(
        output.question_text, ctx.note_type(), ctx.col()
    )
    output.answer_text = render_latex(output.answer_text, ctx.note_type(), ctx.col())


def render_latex(html: str, model: NoteType, col: anki.storage._Collection,) -> str:
    "Convert TEXT with embedded latex tags to image links."
    for match in regexps["standard"].finditer(html):
        html = html.replace(match.group(), _imgLink(col, match.group(1), model))
    for match in regexps["expression"].finditer(html):
        html = html.replace(
            match.group(), _imgLink(col, "$" + match.group(1) + "$", model)
        )
    for match in regexps["math"].finditer(html):
        html = html.replace(
            match.group(),
            _imgLink(
                col,
                "\\begin{displaymath}" + match.group(1) + "\\end{displaymath}",
                model,
            ),
        )
    return html


def _imgLink(col, latex: str, model: NoteType) -> str:
    "Return an img link for LATEX, creating if necesssary."
    txt = _latexFromHtml(col, latex)

    if model.get("latexsvg", False):
        ext = "svg"
    else:
        ext = "png"

    # is there an existing file?
    fname = "latex-%s.%s" % (checksum(txt.encode("utf8")), ext)
    link = '<img class=latex src="%s">' % fname
    if os.path.exists(fname):
        return link

    # building disabled?
    if not build:
        return "[latex]%s[/latex]" % latex

    err = _buildImg(col, txt, fname, model)
    if err:
        return err
    else:
        return link


def _latexFromHtml(col, latex: str) -> str:
    "Convert entities and fix newlines."
    latex = re.sub("<br( /)?>|<div>", "\n", latex)
    latex = stripHTML(latex)
    return latex


def _buildImg(col, latex: str, fname: str, model: NoteType) -> Optional[str]:
    # add header/footer
    latex = model["latexPre"] + "\n" + latex + "\n" + model["latexPost"]
    # it's only really secure if run in a jail, but these are the most common
    tmplatex = latex.replace("\\includegraphics", "")
    for bad in (
        "\\write18",
        "\\readline",
        "\\input",
        "\\include",
        "\\catcode",
        "\\openout",
        "\\write",
        "\\loop",
        "\\def",
        "\\shipout",
    ):
        # don't mind if the sequence is only part of a command
        bad_re = "\\" + bad + "[^a-zA-Z]"
        if re.search(bad_re, tmplatex):
            return (
                _(
                    """\
For security reasons, '%s' is not allowed on cards. You can still use \
it by placing the command in a different package, and importing that \
package in the LaTeX header instead."""
                )
                % bad
            )

    # commands to use?
    if model.get("latexsvg", False):
        latexCmds = svgCommands
        ext = "svg"
    else:
        latexCmds = pngCommands
        ext = "png"

    # write into a temp file
    log = open(namedtmp("latex_log.txt"), "w")
    texpath = namedtmp("tmp.tex")
    texfile = open(texpath, "w", encoding="utf8")
    texfile.write(latex)
    texfile.close()
    mdir = col.media.dir()
    oldcwd = os.getcwd()
    png = namedtmp("tmp.%s" % ext)
    try:
        # generate png
        os.chdir(tmpdir())
        for latexCmd in latexCmds:
            if call(latexCmd, stdout=log, stderr=log):
                return _errMsg(latexCmd[0], texpath)
        # add to media
        shutil.copyfile(png, os.path.join(mdir, fname))
        return None
    finally:
        os.chdir(oldcwd)
        log.close()


def _errMsg(type: str, texpath: str) -> Any:
    msg = (_("Error executing %s.") % type) + "<br>"
    msg += (_("Generated file: %s") % texpath) + "<br>"
    try:
        with open(namedtmp("latex_log.txt", rm=False)) as f:
            log = f.read()
        if not log:
            raise Exception()
        msg += "<small><pre>" + html.escape(log) + "</pre></small>"
    except:
        msg += _("Have you installed latex and dvipng/dvisvgm?")
    return msg


hooks.card_did_render.append(on_card_did_render)  # type: ignore
# For some reason, Anki's type keep telling "Cannot determine type of 'card_did_render'  [has-type]". So ignoring is necessary here.
