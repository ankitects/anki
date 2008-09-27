# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

"""\
Latex support
==============================
"""
__docformat__ = 'restructuredtext'

import re, tempfile, os, sys, subprocess
from htmlentitydefs import entitydefs
try:
    import hashlib
    md5 = hashlib.md5
except ImportError:
    import md5
    md5 = md5.new

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

tmpdir = tempfile.mkdtemp(prefix="anki-latex")

# add standard tex install location to osx
if sys.platform == "darwin":
    os.environ['PATH'] += ":/usr/texbin"

def renderLatex(deck, text):
    "Convert TEXT with embedded latex tags to image links."
    for match in regexps['standard'].finditer(text):
        text = text.replace(match.group(), imgLink(deck, match.group(1)))
    for match in regexps['expression'].finditer(text):
        text = text.replace(match.group(), imgLink(
            deck, "$" + match.group(1) + "$"))
    for match in regexps['math'].finditer(text):
        text = text.replace(match.group(), imgLink(
            deck,
            "\\begin{displaymath}" + match.group(1) + "\\end{displaymath}"))
    return text

def stripLatex(text):
    for match in regexps['standard'].finditer(text):
        text = text.replace(match.group(), "")
    for match in regexps['expression'].finditer(text):
        text = text.replace(match.group(), "")
    for match in regexps['math'].finditer(text):
        text = text.replace(match.group(), "")
    return text

def call(*args, **kwargs):
    try:
        o = subprocess.Popen(*args, **kwargs)
    except OSError:
        # command not found
        return -1
    while 1:
        try:
            ret = o.wait()
        except OSError:
            # interrupted system call
            continue
        break
    return ret

def imgLink(deck, latex):
    "Parse LATEX and return a HTML image representing the output."
    for match in re.compile("&([a-z]+);", re.IGNORECASE).finditer(latex):
        if match.group(1) in entitydefs:
            latex = latex.replace(match.group(), entitydefs[match.group(1)])
    latex = re.sub("<br( /)?>", "\n", latex)
    latex = latex.encode("utf-8")
    imageFile = "latex-%s.png" % md5(latex).hexdigest()
    imagePath = os.path.join(deck.mediaDir(create=True), imageFile)
    imagePath = imagePath.encode(sys.getfilesystemencoding())
    if not os.path.exists(imagePath):
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
            errmsg = _("Error executing 'latex' or 'dvipng' - are they installed?")
            if call(["latex", "-interaction=nonstopmode",
                     texpath], stdout=log, stderr=log, startupinfo=si):
                return errmsg
            if call(latexDviPngCmd + ["tmp.dvi", "-o", imagePath],
                    stdout=log, stderr=log, startupinfo=si):
                return errmsg
        finally:
            os.chdir(oldcwd)
    return '<img src="%s">' % imageFile
