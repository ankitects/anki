# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

"""\
Latex support
==============================
"""
__docformat__ = 'restructuredtext'

import re, tempfile, os, sys, subprocess, stat, time, shutil
from anki.utils import genID, checksum
from htmlentitydefs import entitydefs

latexPreamble = ("\\documentclass[12pt]{article}\n"
                 "\\special{papersize=3in,5in}"
                 "\\usepackage[utf8]{inputenc}"
                 "\\pagestyle{empty}\n"
                 "\\begin{document}")
latexPostamble = "\\end{document}"
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

def call(argv, wait=True, **kwargs):
    try:
        o = subprocess.Popen(argv, **kwargs)
    except OSError:
        # command not found
        return -1
    if wait:
        while 1:
            try:
                ret = o.wait()
            except OSError:
                # interrupted system call
                continue
            break
    else:
        ret = 0
    return ret

def latexImgFile(deck, latexCode):
    key = checksum(latexCode)
    return "latex-%s.png" % key

def latexImgPath(deck, file):
    "Return the path to the cache file in system encoding format."
    path = os.path.join(deck.mediaDir(create=True), file)
    return path.encode(sys.getfilesystemencoding())

def mungeLatex(latex):
    "Convert entities, fix newlines, and convert to utf8."
    for match in re.compile("&([a-z]+);", re.IGNORECASE).finditer(latex):
        if match.group(1) in entitydefs:
            latex = latex.replace(match.group(), entitydefs[match.group(1)])
    latex = re.sub("<br( /)?>", "\n", latex)
    latex = latex.encode("utf-8")
    return latex

def deleteAllLatexImages(deck):
    mdir = deck.mediaDir()
    if not mdir:
        return
    deck.startProgress()
    for c, f in enumerate(os.listdir(mdir)):
        if f.startswith("latex-"):
            os.unlink(os.path.join(mdir, f))
        if c % 100 == 0:
            deck.updateProgress()
    deck.finishProgress()

def cacheAllLatexImages(deck):
    deck.startProgress()
    fields = deck.s.column0("select value from fields")
    for c, field in enumerate(fields):
        if c % 10 == 0:
            deck.updateProgress()
        renderLatex(deck, field)
    deck.finishProgress()

def buildImg(deck, latex):
    log = open(os.path.join(tmpdir, "latex_log.txt"), "w+")
    texpath = os.path.join(tmpdir, "tmp.tex")
    texfile = file(texpath, "w")
    texfile.write(latexPreamble + "\n")
    texfile.write(latex + "\n")
    texfile.write(latexPostamble + "\n")
    texfile.close()
    texpath = texpath.encode(sys.getfilesystemencoding())
    oldcwd = os.getcwd()
    if sys.platform == "win32":
        si = subprocess.STARTUPINFO()
        si.dwFlags |= subprocess.STARTF_USESHOWWINDOW
    else:
        si = None
    try:
        os.chdir(tmpdir)
        errmsg = _(
            "Error executing 'latex' or 'dvipng'.\n"
            "A log file is available here:\n%s") % tmpdir
        if call(["latex", "-interaction=nonstopmode",
                 texpath], stdout=log, stderr=log, startupinfo=si):
            return (False, errmsg)
        if call(latexDviPngCmd + ["tmp.dvi", "-o", "tmp.png"],
                stdout=log, stderr=log, startupinfo=si):
            return (False, errmsg)
        # add to media
        target = latexImgPath(deck, latexImgFile(deck, latex))
        shutil.copy2("tmp.png", target)
        return (True, target)
    finally:
        os.chdir(oldcwd)

def imageForLatex(deck, latex, build=True):
    "Return an image that represents 'latex', building if necessary."
    imageFile = latexImgFile(deck, latex)
    if imageFile:
        path = latexImgPath(deck, imageFile)
    ok = True
    if build and (not imageFile or not os.path.exists(path)):
        (ok, imageFile) = buildImg(deck, latex)
    if not ok:
        return (False, imageFile)
    return (True, imageFile)

def imgLink(deck, latex, build=True):
    "Parse LATEX and return a HTML image representing the output."
    latex = mungeLatex(latex)
    (ok, img) = imageForLatex(deck, latex, build)
    if ok:
        return '<img src="%s">' % img
    else:
        return img
