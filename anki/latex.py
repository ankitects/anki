# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import re, os, shutil, cgi
from anki.utils import checksum, call, namedtmp, tmpdir, isMac, stripHTML
from anki.hooks import addHook
from anki.lang import _

latexCmd = ["latex", "-interaction=nonstopmode"]
latexDviPngCmd = ["dvipng", "-D", "200", "-T", "tight"]
build = True # if off, use existing media but don't create new
regexps = {
    "standard": re.compile(r"\[latex\](.+?)\[/latex\]", re.DOTALL | re.IGNORECASE),
    "expression": re.compile(r"\[\$\](.+?)\[/\$\]", re.DOTALL | re.IGNORECASE),
    "math": re.compile(r"\[\$\$\](.+?)\[/\$\$\]", re.DOTALL | re.IGNORECASE),
    }

# add standard tex install location to osx
if isMac:
    os.environ['PATH'] += ":/usr/texbin"

def stripLatex(text):
    for match in regexps['standard'].finditer(text):
        text = text.replace(match.group(), "")
    for match in regexps['expression'].finditer(text):
        text = text.replace(match.group(), "")
    for match in regexps['math'].finditer(text):
        text = text.replace(match.group(), "")
    return text

def mungeQA(html, type, fields, model, data, col):
    "Convert TEXT with embedded latex tags to image links."
    for match in regexps['standard'].finditer(html):
        html = html.replace(match.group(), _imgLink(col, match.group(1), model))
    for match in regexps['expression'].finditer(html):
        html = html.replace(match.group(), _imgLink(
            col, "$" + match.group(1) + "$", model))
    for match in regexps['math'].finditer(html):
        html = html.replace(match.group(), _imgLink(
            col,
            "\\begin{displaymath}" + match.group(1) + "\\end{displaymath}", model))
    return html

def _imgLink(col, latex, model):
    "Return an img link for LATEX, creating if necesssary."
    txt = _latexFromHtml(col, latex)
    fname = "latex-%s.png" % checksum(txt.encode("utf8"))
    link = '<img src="%s">' % fname
    if os.path.exists(fname):
        return link
    elif not build:
        return u"[latex]%s[/latex]" % latex
    else:
        err = _buildImg(col, txt, fname, model)
        if err:
            return err
        else:
            return link

def _latexFromHtml(col, latex):
    "Convert entities and fix newlines."
    latex = re.sub("<br( /)?>|<div>", "\n", latex)
    latex = stripHTML(latex)
    return latex

def _buildImg(col, latex, fname, model):
    # add header/footer & convert to utf8
    latex = (model["latexPre"] + "\n" +
             latex + "\n" +
             model["latexPost"])
    latex = latex.encode("utf8")
    # it's only really secure if run in a jail, but these are the most common
    tmplatex = latex.replace("\\includegraphics", "")
    for bad in ("write18", "\\readline", "\\input", "\\include", "\\catcode",
                "\\openout", "\\write", "\\loop", "\\def", "\\shipout"):
        if bad in tmplatex:
            return _("""\
For security reasons, '%s' is not allowed on cards. You can still use \
it by placing the command in a different package, and importing that \
package in the LaTeX header instead.""") % bad
    # write into a temp file
    log = open(namedtmp("latex_log.txt"), "w")
    texpath = namedtmp("tmp.tex")
    texfile = file(texpath, "w")
    texfile.write(latex)
    texfile.close()
    mdir = col.media.dir()
    oldcwd = os.getcwd()
    png = namedtmp("tmp.png")
    try:
        # generate dvi
        os.chdir(tmpdir())
        if call(latexCmd + ["tmp.tex"], stdout=log, stderr=log):
            return _errMsg("latex", texpath)
        # and png
        if call(latexDviPngCmd + ["tmp.dvi", "-o", "tmp.png"],
                stdout=log, stderr=log):
            return _errMsg("dvipng", texpath)
        # add to media
        shutil.copyfile(png, os.path.join(mdir, fname))
        return
    finally:
        os.chdir(oldcwd)

def _errMsg(type, texpath):
    msg = (_("Error executing %s.") % type) + "<br>"
    msg += (_("Generated file: %s") % texpath) + "<br>"
    try:
        log = open(namedtmp("latex_log.txt", rm=False)).read()
        if not log:
            raise Exception()
        msg += "<small><pre>" + cgi.escape(log) + "</pre></small>"
    except:
        msg += _("Have you installed latex and dvipng?")
        pass
    return msg

# setup q/a filter
addHook("mungeQA", mungeQA)
