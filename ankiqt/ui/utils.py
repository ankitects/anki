# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

from PyQt4.QtGui import *
from PyQt4.QtCore import *

from anki.sound import playFromText, stripSounds
from anki.latex import renderLatex, stripLatex
from ankiqt import ui

import re, os, sys, urllib, time
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

def askUser(text, parent=None, help=""):
    "Show a yes/no question. Return true if yes."
    if not parent:
        parent = ankiqt.mw
    sb = QMessageBox.Yes | QMessageBox.No
    if help:
        sb |= QMessageBox.Help
    while 1:
        r = QMessageBox.question(parent, "Anki", text, sb,
                                 QMessageBox.Yes)
        if r == QMessageBox.Help:
            openWikiLink(help)
        else:
            break
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

def saveState(widget, key):
    key += "State"
    ankiqt.mw.config[key] = widget.saveState()

def restoreState(widget, key):
    key += "State"
    if ankiqt.mw.config.get(key):
        widget.restoreState(ankiqt.mw.config[key])

def saveSplitter(widget, key):
    key += "Splitter"
    ankiqt.mw.config[key] = widget.saveState()

def restoreSplitter(widget, key):
    key += "Splitter"
    if ankiqt.mw.config.get(key):
        widget.restoreState(ankiqt.mw.config[key])

def saveHeader(widget, key):
    key += "Header"
    ankiqt.mw.config[key] = widget.saveState()

def restoreHeader(widget, key):
    key += "Header"
    if ankiqt.mw.config.get(key):
        widget.restoreState(ankiqt.mw.config[key])

def mungeQA(deck, txt):
    txt = renderLatex(deck, txt)
    txt = stripSounds(txt)
    # webkit currently doesn't handle underline
    txt = txt.replace("text-decoration: underline;",
                      "border-bottom: 1px solid #000;")
    return txt

def getBase(deck):
    if deck and deck.mediaDir():
        if sys.platform.startswith("win32"):
            prefix = u"file:///"
        else:
            prefix = u"file://"
        base = prefix + unicode(
            urllib.quote(deck.mediaDir().encode("utf-8")),
            "utf-8")
        return '<base href="%s/">' % base
    else:
        return ""

class ProgressWin(object):

    def __init__(self, parent, max=0, min=0, title=None):
        if not title:
            title = "Anki"
        self.diag = QProgressDialog("", "", min, max, parent)
        self.diag.setWindowTitle(title)
        self.diag.setCancelButton(None)
        self.diag.setAutoClose(False)
        self.diag.setAutoReset(False)
        self.diag.setMinimumDuration(0)
        self.diag.show()
        self.counter = min
        self.min = min
        self.max = max
        self.lastTime = time.time()
        self.app = QApplication.instance()
        if max == 0:
            self.diag.setLabelText(_("Processing..."))

    def update(self, label=None, value=None, process=True):
        #print self.min, self.counter, self.max, label, time.time() - self.lastTime
        self.lastTime = time.time()
        if label:
            self.diag.setLabelText(label)
        if value is None:
            value = self.counter
            self.counter += 1
        else:
            self.counter = value + 1
        if self.max:
            self.diag.setValue(value)
        if process:
            self.app.processEvents()

    def finish(self):
        if self.max:
            self.diag.setValue(self.max)
            self.app.processEvents()
            time.sleep(0.1)
        self.diag.cancel()
