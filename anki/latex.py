# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

"""\
Latex support
==============================
"""
__docformat__ = 'restructuredtext'

import re, tempfile, os, sys, shutil, cgi, subprocess
from anki.utils import genID, checksum, call
from anki.hooks import addHook
from htmlentitydefs import entitydefs
from anki.lang import _

latexDviPngCmd = ["dvipng", "-D", "200", "-T", "tight"]

regexps = {
    "standard": re.compile(r"\[latex\](.+?)\[/latex\]", re.DOTALL | re.IGNORECASE),
    "expression": re.compile(r"\[\$\](.+?)\[/\$\]", re.DOTALL | re.IGNORECASE),
    "math": re.compile(r"\[\$\$\](.+?)\[/\$\$\]", re.DOTALL | re.IGNORECASE),
    }

tmpdir = tempfile.mkdtemp(prefix="anki")

# add standard tex install location to osx
if sys.platform == "darwin":
    os.environ['PATH'] += ":/usr/texbin"

def renderLatex(deck, text, build=True):
    "Convert TEXT with embedded latex tags to image links."
    for match in regexps['standard'].finditer(text):
        text = text.replace(match.group(), imgLink(deck, match.group(1),
                                                   build))
    for match in regexps['expression'].finditer(text):
        text = text.replace(match.group(), imgLink(
            deck, "$" + match.group(1) + "$", build))
    for match in regexps['math'].finditer(text):
        text = text.replace(match.group(), imgLink(
            deck,
            "\\begin{displaymath}" + match.group(1) + "\\end{displaymath}",
            build))
    return text

def stripLatex(text):
    for match in regexps['standard'].finditer(text):
        text = text.replace(match.group(), "")
    for match in regexps['expression'].finditer(text):
        text = text.replace(match.group(), "")
    for match in regexps['math'].finditer(text):
        text = text.replace(match.group(), "")
    return text

def latexImgFile(deck, latexCode):
    key = checksum(latexCode)
    return "latex-%s.png" % key

def mungeLatex(deck, latex):
    "Convert entities, fix newlines, convert to utf8, and wrap pre/postamble."
    for match in re.compile("&([a-z]+);", re.IGNORECASE).finditer(latex):
        if match.group(1) in entitydefs:
            latex = latex.replace(match.group(), entitydefs[match.group(1)])
    latex = re.sub("<br( /)?>", "\n", latex)
    latex = (deck.getVar("latexPre") + "\n" +
             latex + "\n" +
             deck.getVar("latexPost"))
    latex = latex.encode("utf-8")
    return latex

def buildImg(deck, latex):
    log = open(os.path.join(tmpdir, "latex_log.txt"), "w+")
    texpath = os.path.join(tmpdir, "tmp.tex")
    texfile = file(texpath, "w")
    texfile.write(latex)
    texfile.close()
    # make sure we have a valid mediaDir
    mdir = deck.mediaDir(create=True)
    oldcwd = os.getcwd()
    if sys.platform == "win32":
        si = subprocess.STARTUPINFO()
        try:
            si.dwFlags |= subprocess.STARTF_USESHOWWINDOW
        except:
            si.dwFlags |= subprocess._subprocess.STARTF_USESHOWWINDOW
    else:
        si = None
    try:
        os.chdir(tmpdir)
        def errmsg(type):
            msg = _("Error executing %s.\n") % type
            try:
                log = open(os.path.join(tmpdir, "latex_log.txt")).read()
                msg += "<small><pre>" + cgi.escape(log) + "</pre></small>"
            except:
                msg += _("Have you installed latex and dvipng?")
                pass
            return msg
        if call(["latex", "-interaction=nonstopmode",
                 "tmp.tex"], stdout=log, stderr=log, startupinfo=si):
            return (False, errmsg("latex"))
        if call(latexDviPngCmd + ["tmp.dvi", "-o", "tmp.png"],
                stdout=log, stderr=log, startupinfo=si):
            return (False, errmsg("dvipng"))
        # add to media
        target = latexImgFile(deck, latex)
        shutil.copy2(os.path.join(tmpdir, "tmp.png"),
                     os.path.join(mdir, target))
        return (True, target)
    finally:
        os.chdir(oldcwd)

def imageForLatex(deck, latex, build=True):
    "Return an image that represents 'latex', building if necessary."
    imageFile = latexImgFile(deck, latex)
    ok = True
    if build and (not imageFile or not os.path.exists(imageFile)):
        (ok, imageFile) = buildImg(deck, latex)
    if not ok:
        return (False, imageFile)
    return (True, imageFile)

def imgLink(deck, latex, build=True):
    "Parse LATEX and return a HTML image representing the output."
    munged = mungeLatex(deck, latex)
    (ok, img) = imageForLatex(deck, munged, build)
    if ok:
        return '<img src="%s" alt="%s">' % (img, latex)
    else:
        return img

def formatQA(html, type, cid, mid, fact, tags, cm, deck):
    return renderLatex(deck, html)

# setup q/a filter
addHook("formatQA", formatQA)
