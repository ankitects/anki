# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

from PyQt4.QtGui import *
from PyQt4.QtCore import *

from anki.sound import playFromText, stripSounds
from anki.latex import renderLatex, stripLatex
from ankiqt import ui

import re, os, sys, urllib
import ankiqt

def openLink(link):
    QDesktopServices.openUrl(QUrl(link))

def openWikiLink(page):
    openLink(ankiqt.appWiki + page)

def showWarning(text, parent=None):
    "Show a small warning with an OK button."
    if not parent:
        parent = ankiqt.mw
    QMessageBox.warning(parent, "Anki", text)

def showInfo(text, parent=None, help=""):
    "Show a small info window with an OK button."
    if not parent:
        parent = ankiqt.mw
    sb = QMessageBox.Ok
    if help:
        sb |= QMessageBox.Help
    while 1:
        ret = QMessageBox.information(parent, "Anki", text, sb)
        if ret == QMessageBox.Help:
            openWikiLink(help)
        else:
            break

def showText(text, parent=None):
    if not parent:
        parent = ankiqt.mw
    d = QDialog(parent)
    d.setWindowTitle("Anki")
    v = QVBoxLayout()
    l = QLabel(text)
    l.setWordWrap(True)
    l.setTextInteractionFlags(Qt.TextSelectableByMouse)
    v.addWidget(l)
    buts = QDialogButtonBox.Ok
    b = QDialogButtonBox(buts)
    v.addWidget(b)
    d.setLayout(v)
    d.connect(b.button(QDialogButtonBox.Ok),
              SIGNAL("clicked()"), d.accept)
    d.exec_()

def askUser(text, parent=None):
    "Show a yes/no question. Return true if yes."
    if not parent:
        parent = ankiqt.mw
    r = QMessageBox.question(parent, "Anki", text,
                             QMessageBox.Yes | QMessageBox.No)
    return r == QMessageBox.Yes

class GetTextDialog(QDialog):

    def __init__(self, parent, question, help=None, edit=None):
        QDialog.__init__(self, parent, Qt.Window)
        self.setWindowTitle("Anki")
        self.question = question
        self.help = help
        v = QVBoxLayout()
        v.addWidget(QLabel(question))
        if not edit:
            edit = QLineEdit()
        self.l = edit
        v.addWidget(self.l)
        buts = QDialogButtonBox.Ok | QDialogButtonBox.Cancel
        if help:
            buts |= QDialogButtonBox.Help
        b = QDialogButtonBox(buts)
        v.addWidget(b)
        self.setLayout(v)
        self.connect(b.button(QDialogButtonBox.Ok),
                     SIGNAL("clicked()"), self.accept)
        self.connect(b.button(QDialogButtonBox.Cancel),
                     SIGNAL("clicked()"), self.reject)
        if help:
            self.connect(b.button(QDialogButtonBox.Help),
                         SIGNAL("clicked()"), self.helpRequested)

    def accept(self):
        return QDialog.accept(self)

    def reject(self):
        return QDialog.reject(self)

    def helpRequested(self):
        QDesktopServices.openUrl(QUrl(ankiqt.appWiki + self.help))

def getText(prompt, parent=None, help=None, edit=None):
    if not parent:
        parent = ankiqt.mw
    d = GetTextDialog(parent, prompt, help=help, edit=edit)
    ret = d.exec_()
    return (unicode(d.l.text()), ret)

def getOnlyText(*args, **kwargs):
    (s, r) = getText(*args, **kwargs)
    if r:
        return s
    else:
        return u""

def getTag(parent, deck, question, tags="user", **kwargs):
    te = ui.tagedit.TagEdit(parent)
    te.setDeck(deck, tags)
    return getText(question, parent, edit=te, **kwargs)

def getFile(parent, title, dir, key):
    "Ask the user for a file. Use DIR as config variable."
    dirkey = dir+"Directory"
    file = unicode(QFileDialog.getOpenFileName(
        parent, title, ankiqt.mw.config.get(dirkey, ""), key))
    if file:
        dir = os.path.dirname(file)
        ankiqt.mw.config[dirkey] = dir
    return file

def getSaveFile(parent, title, dir, key, ext):
    "Ask the user for a file to save. Use DIR as config variable."
    dirkey = dir+"Directory"
    file = unicode(QFileDialog.getSaveFileName(
        parent, title, ankiqt.mw.config.get(dirkey, ""), key,
        None, QFileDialog.DontConfirmOverwrite))
    if file:
        # add extension
        if not file.lower().endswith(ext):
            file += ext
        # save new default
        dir = os.path.dirname(file)
        ankiqt.mw.config[dirkey] = dir
        # check if it exists
        if os.path.exists(file):
            if not askUser(
                _("This file exists. Are you sure you want to overwrite it?"),
                parent):
                return None
    return file

def saveGeom(widget, key):
    key += "Geom"
    ankiqt.mw.config[key] = widget.saveGeometry()

def restoreGeom(widget, key):
    key += "Geom"
    if ankiqt.mw.config.get(key):
        widget.restoreGeometry(ankiqt.mw.config[key])

def saveSplitter(widget, key):
    key += "Splitter"
    ankiqt.mw.config[key] = widget.saveState()

def restoreSplitter(widget, key):
    key += "Splitter"
    if ankiqt.mw.config.get(key):
        widget.restoreState(ankiqt.mw.config[key])

def mungeQA(deck, txt):
    txt = renderLatex(deck, txt)
    txt = stripSounds(txt)
    def quote(match):
        match = unicode(match.group(1))
        if match.lower().startswith("http"):
            src = match
        else:
            if sys.platform.startswith("win32"):
                prefix = u"file:///"
            else:
                prefix = u"file://"
            src = prefix + unicode(
                urllib.quote(os.path.join(deck.mediaDir(
                create=True), match).encode("utf-8")), "utf-8")
        return 'img src="%s"' % src
    txt = re.sub('img src="(.*?)"', quote, txt)
    return txt
