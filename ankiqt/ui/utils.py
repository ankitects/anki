# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

from PyQt4.QtGui import *
from PyQt4.QtCore import *

from anki.sound import playFromText, stripSounds
from ankiqt import ui

import re, os, sys, urllib, time
import ankiqt

def openLink(link):
    QDesktopServices.openUrl(QUrl(link))

def openWikiLink(page):
    openLink(ankiqt.appWiki + page)

def showWarning(text, parent=None, help=""):
    "Show a small warning with an OK button."
    return showInfo(text, parent, help, QMessageBox.warning)

def showCritical(text, parent=None, help=""):
    "Show a small critical error with an OK button."
    return showInfo(text, parent, help, QMessageBox.critical)

def showInfo(text, parent=None, help="", func=None):
    "Show a small info window with an OK button."
    if not parent:
        parent = ankiqt.mw
    if not func:
        func = QMessageBox.information
    sb = QMessageBox.Ok
    if help:
        sb |= QMessageBox.Help
    while 1:
        ret = func(parent, "Anki", text, sb)
        if ret == QMessageBox.Help:
            openWikiLink(help)
        else:
            break

def showText(txt, parent=None, type="text"):
    if not parent:
        parent = ankiqt.mw
    diag = QDialog(parent)
    diag.setWindowTitle("Anki")
    layout = QVBoxLayout(diag)
    diag.setLayout(layout)
    text = QTextEdit()
    text.setReadOnly(True)
    if type == "text":
        text.setPlainText(txt)
    else:
        text.setHtml(txt)
    layout.addWidget(text)
    box = QDialogButtonBox(QDialogButtonBox.Close)
    layout.addWidget(box)
    diag.connect(box, SIGNAL("rejected()"), diag, SLOT("reject()"))
    diag.setMinimumHeight(400)
    diag.setMinimumWidth(500)
    diag.exec_()

def askUser(text, parent=None, help="", defaultno=False):
    "Show a yes/no question. Return true if yes."
    if not parent:
        parent = ankiqt.mw
    sb = QMessageBox.Yes | QMessageBox.No
    if help:
        sb |= QMessageBox.Help
    while 1:
        if defaultno:
            default = QMessageBox.No
        else:
            default = QMessageBox.Yes
        r = QMessageBox.question(parent, "Anki", text, sb,
                                 default)
        if r == QMessageBox.Help:
            openWikiLink(help)
        else:
            break
    return r == QMessageBox.Yes

class ButtonedDialog(QMessageBox):

    def __init__(self, text, buttons, parent=None, help=""):
        QDialog.__init__(self, parent)
        self.buttons = []
        self.setWindowTitle("Anki")
        self.help = help
        self.setIcon(QMessageBox.Warning)
        self.setText(text)
        # v = QVBoxLayout()
        # v.addWidget(QLabel(text))
        # box = QDialogButtonBox()
        # v.addWidget(box)
        for b in buttons:
            self.buttons.append(
                self.addButton(b, QMessageBox.AcceptRole))
        if help:
            self.addButton(_("Help"), QMessageBox.HelpRole)
            buttons.append(_("Help"))
        #self.setLayout(v)

    def run(self):
        self.exec_()
        but = self.clickedButton().text()
        if but == "Help":
            # FIXME stop dialog closing?
            openWikiLink(self.help)
        return self.clickedButton().text()

    def setDefault(self, idx):
        self.setDefaultButton(self.buttons[idx])

def askUserDialog(text, buttons, parent=None, help=""):
    if not parent:
        parent = ankiqt.mw
    diag = ButtonedDialog(text, buttons, parent, help)
    return diag

class GetTextDialog(QDialog):

    def __init__(self, parent, question, help=None, edit=None, default=u"",
                 title="Anki"):
        QDialog.__init__(self, parent, Qt.Window)
        self.setWindowTitle(title)
        self.question = question
        self.help = help
        self.qlabel = QLabel(question)
        v = QVBoxLayout()
        v.addWidget(self.qlabel)
        if not edit:
            edit = QLineEdit()
        self.l = edit
        if default:
            self.l.setText(default)
            self.l.selectAll()
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

def getText(prompt, parent=None, help=None, edit=None, default=u"", title="Anki"):
    if not parent:
        parent = ankiqt.mw
    d = GetTextDialog(parent, prompt, help=help, edit=edit,
                      default=default, title=title)
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

def restoreGeom(widget, key, offset=None):
    key += "Geom"
    if ankiqt.mw.config.get(key):
        widget.restoreGeometry(ankiqt.mw.config[key])
        if sys.platform.startswith("darwin") and offset:
            from ankiqt.ui.main import QtConfig as q
            minor = (q.qt_version & 0x00ff00) >> 8
            if minor > 6:
                # bug in osx toolkit
                s = widget.size()
                widget.resize(s.width(), s.height()+offset*2)

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
    txt = stripSounds(txt)
    # osx webkit doesn't understand font weight 600
    txt = re.sub("font-weight:.+?;", "font-weight: bold;", txt)
    return txt

def applyStyles(widget):
    try:
        styleFile = open(os.path.join(ankiqt.mw.config.configPath,
                                      "style.css"))
        widget.setStyleSheet(styleFile.read())
    except (IOError, OSError):
        pass

def getBase(deck, card):
    base = None
    if deck and card:
        if deck.getBool("remoteImages") and card.fact.model.features:
            base = card.fact.model.features
        elif deck.mediaDir():
            if sys.platform.startswith("win32"):
                prefix = u"file:///"
            else:
                prefix = u"file://"
            base = prefix + unicode(
                urllib.quote(deck.mediaDir().encode("utf-8")),
                "utf-8") + "/"
    if base:
        return '<base href="%s">' % base
    else:
        return ""

class ProgressWin(object):

    def __init__(self, parent, max=0, min=0, title=None, immediate=False):
        if not title:
            title = "Anki"
        self.diag = QProgressDialog("", "", min, max, parent)
        self.diag.setWindowTitle(title)
        self.diag.setCancelButton(None)
        self.diag.setAutoClose(False)
        self.diag.setAutoReset(False)
        # qt doesn't seem to honour this consistently, and it's not triggered
        # by the db progress handler, so we set it high and use maybeShow() below
        if immediate:
            self.diag.show()
        else:
            self.diag.setMinimumDuration(100000)
        self.counter = min
        self.min = min
        self.max = max
        self.firstTime = time.time()
        self.lastTime = time.time()
        self.app = QApplication.instance()
        self.shown = False
        if max == 0:
            self.diag.setLabelText(_("Processing..."))

    def maybeShow(self):
        if time.time() - self.firstTime > 2 and not self.shown:
            self.shown = True
            self.diag.show()

    def update(self, label=None, value=None, process=True):
        #print self.min, self.counter, self.max, label, time.time() - self.lastTime
        self.maybeShow()
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
