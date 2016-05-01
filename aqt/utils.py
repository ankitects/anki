# Copyright: Damien Elmes <anki@ichi2.net>
# -*- coding: utf-8 -*-
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from aqt.qt import *
import re, os, sys, urllib, subprocess
import aqt
from anki.sound import stripSounds
from anki.utils import isWin, isMac, invalidFilename

def openHelp(section):
    link = aqt.appHelpSite
    if section:
        link += "#%s" % section
    openLink(link)

def openLink(link):
    tooltip(_("Loading..."), period=1000)
    QDesktopServices.openUrl(QUrl(link))

def showWarning(text, parent=None, help="", title="Anki"):
    "Show a small warning with an OK button."
    return showInfo(text, parent, help, "warning", title=title)

def showCritical(text, parent=None, help="", title="Anki"):
    "Show a small critical error with an OK button."
    return showInfo(text, parent, help, "critical", title=title)

def showInfo(text, parent=False, help="", type="info", title="Anki"):
    "Show a small info window with an OK button."
    if parent is False:
        parent = aqt.mw.app.activeWindow() or aqt.mw
    if type == "warning":
        icon = QMessageBox.Warning
    elif type == "critical":
        icon = QMessageBox.Critical
    else:
        icon = QMessageBox.Information
    mb = QMessageBox(parent)
    mb.setText(text)
    mb.setIcon(icon)
    mb.setWindowModality(Qt.WindowModal)
    mb.setWindowTitle(title)
    b = mb.addButton(QMessageBox.Ok)
    b.setDefault(True)
    if help:
        b = mb.addButton(QMessageBox.Help)
        b.connect(b, SIGNAL("clicked()"), lambda: openHelp(help))
        b.setAutoDefault(False)
    return mb.exec_()

def showText(txt, parent=None, type="text", run=True, geomKey=None, \
        minWidth=500, minHeight=400, title="Anki"):
    if not parent:
        parent = aqt.mw.app.activeWindow() or aqt.mw
    diag = QDialog(parent)
    diag.setWindowTitle(title)
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
    def onReject():
        if geomKey:
            saveGeom(diag, geomKey)
        QDialog.reject(diag)
    diag.connect(box, SIGNAL("rejected()"), onReject)
    diag.setMinimumHeight(minHeight)
    diag.setMinimumWidth(minWidth)
    if geomKey:
        restoreGeom(diag, geomKey)
    if run:
        diag.exec_()
    else:
        return diag, box

def askUser(text, parent=None, help="", defaultno=False, msgfunc=None, \
        title="Anki"):
    "Show a yes/no question. Return true if yes."
    if not parent:
        parent = aqt.mw.app.activeWindow()
    if not msgfunc:
        msgfunc = QMessageBox.question
    sb = QMessageBox.Yes | QMessageBox.No
    if help:
        sb |= QMessageBox.Help
    while 1:
        if defaultno:
            default = QMessageBox.No
        else:
            default = QMessageBox.Yes
        r = msgfunc(parent, title, text, sb, default)
        if r == QMessageBox.Help:

            openHelp(help)
        else:
            break
    return r == QMessageBox.Yes

class ButtonedDialog(QMessageBox):

    def __init__(self, text, buttons, parent=None, help="", title="Anki"):
        QDialog.__init__(self, parent)
        self.buttons = []
        self.setWindowTitle(title)
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
            openHelp(self.help)
        return self.clickedButton().text()

    def setDefault(self, idx):
        self.setDefaultButton(self.buttons[idx])

def askUserDialog(text, buttons, parent=None, help="", title="Anki"):
    if not parent:
        parent = aqt.mw
    diag = ButtonedDialog(text, buttons, parent, help, title=title)
    return diag

class GetTextDialog(QDialog):

    def __init__(self, parent, question, help=None, edit=None, default=u"", \
                 title="Anki", minWidth=400):
        QDialog.__init__(self, parent)
        self.setWindowTitle(title)
        self.question = question
        self.help = help
        self.qlabel = QLabel(question)
        self.setMinimumWidth(minWidth)
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
        openHelp(self.help)

def getText(prompt, parent=None, help=None, edit=None, default=u"", title="Anki"):
    if not parent:
        parent = aqt.mw.app.activeWindow() or aqt.mw
    d = GetTextDialog(parent, prompt, help=help, edit=edit,
                      default=default, title=title)
    d.setWindowModality(Qt.WindowModal)
    ret = d.exec_()
    return (unicode(d.l.text()), ret)

def getOnlyText(*args, **kwargs):
    (s, r) = getText(*args, **kwargs)
    if r:
        return s
    else:
        return u""

# fixme: these utilities could be combined into a single base class
def chooseList(prompt, choices, startrow=0, parent=None):
    if not parent:
        parent = aqt.mw.app.activeWindow()
    d = QDialog(parent)
    d.setWindowModality(Qt.WindowModal)
    l = QVBoxLayout()
    d.setLayout(l)
    t = QLabel(prompt)
    l.addWidget(t)
    c = QListWidget()
    c.addItems(choices)
    c.setCurrentRow(startrow)
    l.addWidget(c)
    bb = QDialogButtonBox(QDialogButtonBox.Ok)
    bb.connect(bb, SIGNAL("accepted()"), d, SLOT("accept()"))
    l.addWidget(bb)
    d.exec_()
    return c.currentRow()

def getTag(parent, deck, question, tags="user", **kwargs):
    from aqt.tagedit import TagEdit
    te = TagEdit(parent)
    te.setCol(deck)
    ret = getText(question, parent, edit=te, **kwargs)
    te.hideCompleter()
    return ret

# File handling
######################################################################

def getFile(parent, title, cb, filter="*.*", dir=None, key=None):
    "Ask the user for a file."
    assert not dir or not key
    if not dir:
        dirkey = key+"Directory"
        dir = aqt.mw.pm.profile.get(dirkey, "")
    else:
        dirkey = None
    d = QFileDialog(parent)
    # fix #233 crash
    if isMac:
        d.setOptions(QFileDialog.DontUseNativeDialog)
    d.setFileMode(QFileDialog.ExistingFile)
    if os.path.exists(dir):
        d.setDirectory(dir)
    d.setWindowTitle(title)
    d.setNameFilter(filter)
    ret = []
    def accept():
        # work around an osx crash
        #aqt.mw.app.processEvents()
        file = unicode(list(d.selectedFiles())[0])
        if dirkey:
            dir = os.path.dirname(file)
            aqt.mw.pm.profile[dirkey] = dir
        if cb:
            cb(file)
        ret.append(file)
    d.connect(d, SIGNAL("accepted()"), accept)
    d.exec_()
    return ret and ret[0]

def getSaveFile(parent, title, dir_description, key, ext, fname=None):
    """Ask the user for a file to save. Use DIR_DESCRIPTION as config
    variable. The file dialog will default to open with FNAME."""
    config_key = dir_description + 'Directory'
    base = aqt.mw.pm.profile.get(config_key, aqt.mw.pm.base)
    path = os.path.join(base, fname)
    file = unicode(QFileDialog.getSaveFileName(
        parent, title, path, u"{0} (*{1})".format(key, ext),
        options=QFileDialog.DontConfirmOverwrite))
    if file:
        # add extension
        if not file.lower().endswith(ext):
            file += ext
        # save new default
        dir = os.path.dirname(file)
        aqt.mw.pm.profile[config_key] = dir
        # check if it exists
        if os.path.exists(file):
            if not askUser(
                _("This file exists. Are you sure you want to overwrite it?"),
                parent):
                return None
    return file

def saveGeom(widget, key):
    key += "Geom"
    aqt.mw.pm.profile[key] = widget.saveGeometry()

def restoreGeom(widget, key, offset=None, adjustSize=False):
    key += "Geom"
    if aqt.mw.pm.profile.get(key):
        widget.restoreGeometry(aqt.mw.pm.profile[key])
        if isMac and offset:
            if qtminor > 6:
                # bug in osx toolkit
                s = widget.size()
                widget.resize(s.width(), s.height()+offset*2)
    else:
        if adjustSize:
            widget.adjustSize()

def saveState(widget, key):
    key += "State"
    aqt.mw.pm.profile[key] = widget.saveState()

def restoreState(widget, key):
    key += "State"
    if aqt.mw.pm.profile.get(key):
        widget.restoreState(aqt.mw.pm.profile[key])

def saveSplitter(widget, key):
    key += "Splitter"
    aqt.mw.pm.profile[key] = widget.saveState()

def restoreSplitter(widget, key):
    key += "Splitter"
    if aqt.mw.pm.profile.get(key):
        widget.restoreState(aqt.mw.pm.profile[key])

def saveHeader(widget, key):
    key += "Header"
    aqt.mw.pm.profile[key] = widget.saveState()

def restoreHeader(widget, key):
    key += "Header"
    if aqt.mw.pm.profile.get(key):
        widget.restoreState(aqt.mw.pm.profile[key])

def mungeQA(col, txt):
    txt = col.media.escapeImages(txt)
    txt = stripSounds(txt)
    # osx webkit doesn't understand font weight 600
    txt = re.sub("font-weight: *600", "font-weight:bold", txt)
    if isMac:
        # custom fonts cause crashes on osx at the moment
        txt = txt.replace("font-face", "invalid")

    return txt

def applyStyles(widget):
    p = os.path.join(aqt.mw.pm.base, "style.css")
    if os.path.exists(p):
        widget.setStyleSheet(open(p).read())

def getBase(col):
    base = None
    mdir = col.media.dir()
    if isWin and not mdir.startswith("\\\\"):
        prefix = u"file:///"
    else:
        prefix = u"file://"
    mdir = mdir.replace("\\", "/")
    base = prefix + unicode(
        urllib.quote(mdir.encode("utf-8")),
        "utf-8") + "/"
    return '<base href="%s">' % base

def openFolder(path):
    if isWin:
        if isinstance(path, unicode):
            path = path.encode(sys.getfilesystemencoding())
        subprocess.Popen(["explorer", path])
    else:
        QDesktopServices.openUrl(QUrl("file://" + path))

def shortcut(key):
    if isMac:
        return re.sub("(?i)ctrl", "Command", key)
    return key

def maybeHideClose(bbox):
    if isMac:
        b = bbox.button(QDialogButtonBox.Close)
        if b:
            bbox.removeButton(b)

def addCloseShortcut(widg):
    if not isMac:
        return
    widg._closeShortcut = QShortcut(QKeySequence("Ctrl+W"), widg)
    widg.connect(widg._closeShortcut, SIGNAL("activated()"),
                 widg, SLOT("reject()"))

def downArrow():
    if isWin:
        return u"▼"
    # windows 10 is lacking the smaller arrow on English installs
    return u"▾"

# Tooltips
######################################################################

_tooltipTimer = None
_tooltipLabel = None

def tooltip(msg, period=3000, parent=None):
    global _tooltipTimer, _tooltipLabel
    class CustomLabel(QLabel):
        def mousePressEvent(self, evt):
            evt.accept()
            self.hide()
    closeTooltip()
    aw = parent or aqt.mw.app.activeWindow() or aqt.mw
    lab = CustomLabel("""\
<table cellpadding=10>
<tr>
<td><img src=":/icons/help-hint.png"></td>
<td>%s</td>
</tr>
</table>""" % msg, aw)
    lab.setFrameStyle(QFrame.Panel)
    lab.setLineWidth(2)
    lab.setWindowFlags(Qt.ToolTip)
    p = QPalette()
    p.setColor(QPalette.Window, QColor("#feffc4"))
    p.setColor(QPalette.WindowText, QColor("#000000"))
    lab.setPalette(p)
    lab.move(
        aw.mapToGlobal(QPoint(0, -100 + aw.height())))
    lab.show()
    _tooltipTimer = aqt.mw.progress.timer(
        period, closeTooltip, False)
    _tooltipLabel = lab

def closeTooltip():
    global _tooltipLabel, _tooltipTimer
    if _tooltipLabel:
        try:
            _tooltipLabel.deleteLater()
        except:
            # already deleted as parent window closed
            pass
        _tooltipLabel = None
    if _tooltipTimer:
        _tooltipTimer.stop()
        _tooltipTimer = None

# true if invalid; print warning
def checkInvalidFilename(str, dirsep=True):
    bad = invalidFilename(str, dirsep)
    if bad:
        showWarning(_("The following character can not be used: %s") %
                    bad)
        return True
    return False
