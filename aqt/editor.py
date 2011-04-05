# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

from PyQt4.QtGui import *
from PyQt4.QtCore import *
from PyQt4.QtSvg import * # fixme: obsolete?
from PyQt4.QtWebKit import QWebView
import re, os, sys, tempfile, urllib2, ctypes, simplejson
from anki.utils import stripHTML
from anki.sound import play
from anki.hooks import addHook, removeHook, runHook, runFilter
from aqt.sound import getAudio
from aqt.webview import AnkiWebView
from aqt.utils import shortcut, showInfo, showWarning, getBase
import anki.js

_html = """
<html><head>%s<style>
.field {
  border: 1px solid #aaa; background:#fff; color:#000; padding: 5px;
}
.fname { font-size: 14px; vertical-align: middle; padding-right: 5px; }
img { max-width: 150; max-height: 150; }
</style><script>
%s

String.prototype.format = function() {
    var args = arguments;
    return this.replace(/\{\d+\}/g, function(m){
            return args[m.match(/\d+/)]; });
};

var currentField = null;
var changeTimer = null;

function onKey() {
    // esc clears focus, allowing dialog to close
    if (window.event.which == 27) {
        currentField.blur();
        return;
    }
    clearChangeTimer();
    changeTimer = setTimeout(function () {
        sendState();
        saveField("key"); }, 200);
};

function sendState() {
    var r = {
        'bold': document.queryCommandState("bold"),
        'italic': document.queryCommandState("italic"),
        'under': document.queryCommandState("underline"),
        'super': document.queryCommandState("superscript"),
        'sub': document.queryCommandState("subscript"),
        'col': document.queryCommandValue("forecolor")
    };
    py.run("state:" + JSON.stringify(r));
};

function setFormat(cmd, arg) {
    document.execCommand(cmd, false, arg);
};

function clearChangeTimer() {
    if (changeTimer) {
        clearTimeout(changeTimer);
        changeTimer = null;
    }
};

function onFocus(elem) {
    currentField = elem;
    setTimeout(foo, 1);
}

function foo() {
    var s = document.getSelection();
    if (s.rangeCount) {
        var r = s.getRangeAt(0);
        r.collapse();
        s.removeAllRanges();
        s.addRange(r);
    }
};

function onBlur() {
    if (currentField) {
        saveField("focus");
    }
    clearChangeTimer();
    currentField = null;
};

function saveField(type) {
    // type is either 'focus' or 'key'
    py.run(type + ":" + currentField.id.substring(1) + ":" + currentField.innerHTML);
};

function cloze() {
    var s = window.getSelection()
    var r = s.getRangeAt(0).cloneContents();
    var c = document.createElement('div');
    c.appendChild(r);
    var txt = c.innerHTML;
    py.run("cloze:" + currentField.id.substring(1) + ":" + txt);
};

function setFields(fields) {
    var txt = "";
    for (var i=0; i<fields.length; i++) {
        var n = fields[i][0];
        var f = fields[i][1];
        txt += "<tr><td class=fname>{0}</td><td width=100%%>".format(n);
        txt += "<div id=f{0} onkeydown='onKey();' onmouseup='onKey();'".format(i);
        txt += " onfocus='onFocus(this);' onblur='onBlur();' class=field ";
        txt += "contentEditable=true>{0}</div>".format(f);
        txt += "</td></tr>";
    }
    $("#fields").html("<table cellpadding=3>"+txt+"</table>");
    $("#f0").focus();
};

$(function () {
    // ignore drops outside the editable area
    document.body.ondragover = function () {
        e = window.event.srcElement;
        do {
            if (e.contentEditable == "true") {
                return;
            }
            e = window.parentNode;
        } while (e);
        window.event.preventDefault();
    }
});
</script></head><body>
<div id="fields"></div>
</body></html>
"""

# fixme: use shortcut() for mac shortcuts

if sys.platform.startswith("win32"):
    ActivateKeyboardLayout = ctypes.windll.user32.ActivateKeyboardLayout
    ActivateKeyboardLayout.restype = ctypes.c_void_p
    ActivateKeyboardLayout.argtypes = [ctypes.c_void_p, ctypes.c_uint]
    GetKeyboardLayout = ctypes.windll.user32.GetKeyboardLayout
    GetKeyboardLayout.restype = ctypes.c_void_p
    GetKeyboardLayout.argtypes = [ctypes.c_uint]

class Editor(object):
    def __init__(self, mw, widget):
        self.widget = widget
        self.mw = mw
        self.fact = None
        self.onChange = None
        self._loaded = False
        # to be handled js side
        #self.lastFocusedEdit = None
        self.changeTimer = None
        # current card, for card layout
        self.card = None
        addHook("deckClosed", self.deckClosedHook)
        addHook("guiReset", self.refresh)
        addHook("colourChanged", self.colourChanged)
        self.setupOuter()
        self.setupButtons()
        self.setupWeb()
        self.setupTags()

    def close(self):
        removeHook("deckClosed", self.deckClosedHook)
        removeHook("guiReset", self.refresh)
        removeHook("colourChanged", self.colourChanged)

    # Initial setup
    ############################################################

    def setupOuter(self):
        l = QVBoxLayout()#self.widget)
        l.setMargin(0)
        l.setSpacing(3)
        self.widget.setLayout(l)
        self.outerLayout = l

    def setupWeb(self):
        self.web = EditorWebView(self.widget, self)
        self.web.allowDrops = True
        self.web.setBridge(self.bridge)
        self.outerLayout.addWidget(self.web)
        # pick up the window colour
        p = self.web.palette()
        p.setBrush(QPalette.Base, Qt.transparent)
        self.web.page().setPalette(p)
        self.web.setAttribute(Qt.WA_OpaquePaintEvent, False)

    # Top buttons
    ######################################################################

    def _addButton(self, name, func, key=None, tip=None, size=True, text="",
                   check=False):
        b = QPushButton(text)
        if check:
            b.connect(b, SIGNAL("clicked(bool)"), func)
        else:
            b.connect(b, SIGNAL("clicked()"), func)
        if size:
            b.setFixedHeight(20)
            b.setFixedWidth(20)
        b.setStyle(self.plastiqueStyle)
        b.setFocusPolicy(Qt.NoFocus)
        if not text:
            b.setIcon(QIcon(":/icons/%s.png" % name))
        if key:
            b.setShortcut(key)
        if tip:
            b.setToolTip(tip)
        if check:
            b.setCheckable(True)
        self.iconsBox.addWidget(b)
        self._buttons[name] = b
        return b

    def setupButtons(self):
        self._buttons = {}
        # button styles for mac
        self.plastiqueStyle = QStyleFactory.create("plastique")
        self.widget.setStyle(self.plastiqueStyle)
        # icons
        self.iconsBox = QHBoxLayout()
        self.iconsBox.setMargin(0)
        self.iconsBox.setSpacing(0)
        self.outerLayout.addLayout(self.iconsBox)
        # align to right
        self.iconsBox.addItem(QSpacerItem(20,1, QSizePolicy.Expanding))
        b = self._addButton
        b("layout", self.onCardLayout, "Ctrl+l",
          shortcut(_("Layout (Ctrl+l)")), size=False, text=_("Layout..."))
        b("text_bold", self.toggleBold, "Ctrl+b", _("Bold text (Ctrl+b)"),
          check=True)
        b("text_italic", self.toggleItalic, "Ctrl+i", _("Italic text (Ctrl+i)"),
          check=True)
        b("text_under", self.toggleUnderline, "Ctrl+u",
          _("Underline text (Ctrl+u)"), check=True)
        b("text_super", self.toggleSuper, "Ctrl+=",
          _("Superscript (Ctrl+=)"), check=True)
        b("text_sub", self.toggleSub, "Ctrl+Shift+=",
          _("Subscript (Ctrl+Shift+=)"), check=True)
        b("text_remove", self.removeFormat, "Ctrl+r",
          _("Subscript (Ctrl+r)"))
        but = b("foreground", self.onForeground, "F7", text=" ")
        self.setupForegroundButton(but)
        but = b("cloze", self.onCloze, "F9", _("Cloze (F9)"), text="[...]")
        but.setFixedWidth(24)
        # fixme: better image names
        but = b("colors", self.onAddPicture, "F3", _("Add picture (F3)"))
        but = b("text-speak", self.onAddSound, "F3", _("Add audio/video (F4)"))
        but = b("media-record", self.onRecSound, "F5", _("Record audio (F5)"))
        but = b("tex", self.latexMenu, "Ctrl+t", _("LaTeX (Ctrl+t)"))
        # insertLatex, insertLatexEqn, insertLatexMathEnv
        but = b("text-xml", self.onHtmlEdit, "Ctrl+x", _("Source (Ctrl+x)"))

    def setupForegroundButton(self, but):
        self.foregroundFrame = QFrame()
        self.foregroundFrame.setAutoFillBackground(True)
        self.colourChanged()
        hbox = QHBoxLayout()
        hbox.addWidget(self.foregroundFrame)
        hbox.setMargin(5)
        but.setLayout(hbox)

    def enableButtons(self, val=True):
        self.bold.setEnabled(val)
        self.italic.setEnabled(val)
        self.underline.setEnabled(val)
        self.foreground.setEnabled(val)
        self.addPicture.setEnabled(val)
        self.addSound.setEnabled(val)
        self.latex.setEnabled(val)
        self.latexEqn.setEnabled(val)
        self.latexMathEnv.setEnabled(val)
        self.cloze.setEnabled(val)
        self.htmlEdit.setEnabled(val)
        self.recSound.setEnabled(val)

    def disableButtons(self):
        self.enableButtons(False)

    def onCardLayout(self):
        from aqt.clayout import CardLayout
        if self.card:
            type = 1; ord = self.card.ord
        else:
            type = 0; ord = 0
        CardLayout(self.mw, self.fact, type=type, ord=ord, parent=self.widget)

    # JS->Python bridge
    ######################################################################

    def bridge(self, str):
        if str.startswith("focus") or str.startswith("key"):
            print str
            (type, num, txt) = str.split(":", 2)
            self.fact._fields[int(num)] = txt
            if type == "focus":
                runHook("editor.focusLost", self.fact)
            else:
                runHook("editor.keyPressed", self.fact)
            self.fact.flush()
        elif str.startswith("state"):
            (cmd, txt) = str.split(":", 1)
            r = simplejson.loads(txt)
            self._buttons['text_bold'].setChecked(r['bold'])
            self._buttons['text_italic'].setChecked(r['italic'])
            self._buttons['text_under'].setChecked(r['under'])
            self._buttons['text_super'].setChecked(r['super'])
            self._buttons['text_sub'].setChecked(r['sub'])
        elif str.startswith("cloze"):
            (cmd, num, txt) = str.split(":", 2)
            if not txt:
                showInfo(_("Please select some text first."),
                     help="ClozeDeletion")
                return
            # check that the model is set up for cloze deletion
            ok = False
            for t in self.fact.model().templates:
                if "cloze" in t['qfmt'] or "cloze" in t['afmt']:
                    ok = True
                    break
            if not ok:
                showInfo(_("Please add a cloze deletion model."),
                     help="ClozeDeletion")
                return
            num = int(num)
            f = self.fact._fields[num]
            # find the highest existing cloze
            m = re.findall("\{\{c(\d+)::", f)
            if m:
                next = sorted([int(x) for x in m])[-1] + 1
            else:
                next = 1
            self.fact._fields[num] = f.replace(
                txt, "{{c%d::%s}}" % (next, txt))
            self.loadFact()
        else:
            print str

    # Setting/unsetting the current fact
    ######################################################################

    def _loadFinished(self, w):
        self._loaded = True
        if self.fact:
            self.loadFact()

    def setFact(self, fact):
        "Make FACT the current fact."
        self.fact = fact
        if self.changeTimer:
            self.changeTimer.stop()
            self.changeTimer = None
        if self.fact:
            self.web.setHtml(_html % (getBase(self.mw.deck), anki.js.all),
                             loadCB=self._loadFinished)
            self.updateTags()
        else:
            self.widget.hide()

    def loadFact(self):
        if not self._loaded:
            # will be loaded when page is ready
            return
        # fixme: focus on first widget
        self.web.eval("setFields(%s);" % simplejson.dumps(self.fact.items()))
        self.widget.show()

    def refresh(self):
        if self.fact:
            self.fact.load()
            # fixme: what if fact is deleted?
            self.setFact(self.fact)

    def deckClosedHook(self):
        self.setFact(None)

        # if field.fieldModel.features:
        #     w.setLayoutDirection(Qt.RightToLeft)
        # else:
        #     w.setLayoutDirection(Qt.LeftToRight)

        # catch changes
        w.connect(w, SIGNAL("lostFocus"),
                    lambda w=w: self.onFocusLost(w))
        w.connect(w, SIGNAL("textChanged()"),
                    self.onTextChanged)
        w.connect(w, SIGNAL("currentCharFormatChanged(QTextCharFormat)"),
                    lambda w=w: self.formatChanged(w))
        return w

        if check:
            self.checkValid()

    def saveFieldsNow(self):
        "Must call this before adding cards, closing dialog, etc."
        if not self.fact:
            return
        # disable timer
        if self.changeTimer:
            self.changeTimer.stop()
            self.changeTimer = None
            if self.onChange:
                self.onChange('field')
        # save fields and run features
        w = self.focusedEdit()
        if w:
            self.onFocusLost(w)
        self.onTagChange()
        # ensure valid
        self.checkValid()

    def checkValid(self):
        empty = []
        dupe = []
        for field in self.fact.fields:
            p = QPalette()
            p.setColor(QPalette.Text, QColor("#000000"))
            if not self.fieldValid(field):
                empty.append(field)
                p.setColor(QPalette.Base, QColor("#ffffcc"))
                self.fields[field.name][1].setPalette(p)
            elif not self.fieldUnique(field):
                dupe.append(field)
                p.setColor(QPalette.Base, QColor("#ffcccc"))
                self.fields[field.name][1].setPalette(p)
            else:
                p.setColor(QPalette.Base, QColor("#ffffff"))
                self.fields[field.name][1].setPalette(p)

    def onHtmlEdit(self):
        def helpRequested():
            aqt.openHelp("HtmlEditor")
        w = self.focusedEdit()
        if w:
            self.saveFields()
            d = QDialog(self.widget)
            form = aqt.forms.edithtml.Ui_Dialog()
            form.setupUi(d)
            d.connect(form.buttonBox, SIGNAL("helpRequested()"),
                     helpRequested)
            form.textEdit.setPlainText(self.widgets[w].value)
            form.textEdit.moveCursor(QTextCursor.End)
            d.exec_()
            w.setHtml(unicode(form.textEdit.toPlainText()).\
                      replace("\n", ""))
            self.saveFields()

    # Tag and group handling
    ######################################################################

    def setupTags(self):
        import aqt.tagedit
        g = QGroupBox(self.widget)
        tb = QGridLayout()
        tb.setSpacing(12)
        tb.setMargin(6)
        # group
        l = QLabel(_("Group"))
        tb.addWidget(l, 0, 0)
        self.group = aqt.tagedit.TagEdit(self.widget, type=1)
        self.group.connect(self.group, SIGNAL("lostFocus"),
                          self.onGroupChange)
        tb.addWidget(self.group, 0, 1)
        # tags
        l = QLabel(_("Tags"))
        tb.addWidget(l, 1, 0)
        self.tags = aqt.tagedit.TagEdit(self.widget)
        self.tags.connect(self.tags, SIGNAL("lostFocus"),
                          self.onTagChange)
        tb.addWidget(self.tags, 1, 1)
        g.setLayout(tb)
        self.outerLayout.addWidget(g)

    def updateTags(self):
        if self.tags.deck != self.mw.deck:
            self.tags.setDeck(self.mw.deck)
            self.group.setDeck(self.mw.deck)
            self.group.setText(self.mw.deck.groupName(
                self.fact.model().conf['gid']))

    def onGroupChange(self):
        pass

    def onTagChange(self):
        if not self.fact:
            return
        old = self.fact.tags
        self.fact.tags = canonifyTags(unicode(self.tags.text()))
        if old != self.fact.tags:
            self.deck.db.flush()
            self.deck.updateFactTags([self.fact.id])
            self.fact.setModified(textChanged=True, deck=self.deck)
            self.deck.flushMod()
            self.mw.reset(runHooks=False)
        if self.onChange:
            self.onChange('tag')

    # Format buttons
    ######################################################################

    def toggleBold(self, bool):
        self.web.eval("setFormat('bold');")

    def toggleItalic(self, bool):
        self.web.eval("setFormat('italic');")

    def toggleUnderline(self, bool):
        self.web.eval("setFormat('underline');")

    def toggleSuper(self, bool):
        self.web.eval("setFormat('superscript');")

    def toggleSub(self, bool):
        self.web.eval("setFormat('subscript');")

    def removeFormat(self):
        self.web.eval("setFormat('removeFormat');")

    def onCloze(self):
        self.removeFormat()
        self.web.eval("cloze();")

    # Foreground colour
    ######################################################################

    def _updateForegroundButton(self, txtcol):
        self.foregroundFrame.setPalette(QPalette(QColor(txtcol)))
        self.foregroundFrame.setStyleSheet("* {background-color: %s}" %
                                           txtcol)

    def colourChanged(self):
        recent = self.mw.config['recentColours']
        self._updateForegroundButton(recent[-1])

    def onForeground(self):
        class ColourPopup(QDialog):
            def __init__(self, parent):
                QDialog.__init__(self, parent, Qt.FramelessWindowHint)
            def event(self, evt):
                if evt.type() == QEvent.WindowDeactivate:
                    self.close()
                return QDialog.event(self, evt)
        p = ColourPopup(self.widget)
        p.move(self.foregroundFrame.mapToGlobal(QPoint(0,0)))
        g = QGridLayout(p)
        g.setMargin(4)
        g.setSpacing(0)
        p.setLayout(g)
        lastWidget = None
        self.colourNext = QShortcut(QKeySequence("F7"), p)
        p.connect(self.colourNext, SIGNAL("activated()"),
                  self.onNextColour)
        self.colourChoose = QShortcut(QKeySequence("F6"), p)
        p.connect(self.colourChoose, SIGNAL("activated()"),
                  self.onChooseColourKey)
        for n, c in enumerate(reversed(self.mw.config['recentColours'])):
            col = QToolButton()
            col.setAutoRaise(True)
            col.setFixedWidth(64)
            col.setFixedHeight(16)
            col.setAutoFillBackground(True)
            col.setPalette(QPalette(QColor(c)))
            col.setStyleSheet("* {background-color: %s}" %
                              c)
            col.connect(col, SIGNAL("clicked()"),
                        lambda c=c: self.onChooseColour(c))
            g.addWidget(col, n, 0)
            if lastWidget:
                p.setTabOrder(lastWidget, col)
            lastWidget = col
            but = QPushButton("X")
            but.setFixedWidth(16)
            but.setFixedHeight(16)
            but.setAutoDefault(False)
            but.connect(but, SIGNAL("clicked()"),
                        lambda c=c: self.onRemoveColour(c))
            g.addWidget(but, n, 1)
        spc = QSpacerItem(5,10, QSizePolicy.Fixed)
        g.addItem(spc, n+1, 0)
        cb = QPushButton(_("+"))
        cb.connect(cb, SIGNAL("clicked()"), self.onNewColour)
        cb.setFixedWidth(80)
        cb.setFixedHeight(16)
        cb.setAutoDefault(False)
        g.addWidget(cb, n+2, 0, 1, 2)
        self.colourDiag = p
        p.show()

    def onRemoveColour(self, colour):
        recent = self.mw.config['recentColours']
        recent.remove(colour)
        if not recent:
            recent.append("#000000")
        self.colourDiag.close()
        self.onForeground()
        runHook("colourChanged")

    def onNextColour(self):
        try:
            self.colourDiag.focusWidget().nextInFocusChain().setFocus()
        except:
            ui.utils.showInfo("Your Qt version is too old to support this.")

    def onChooseColourKey(self):
        try:
            self.colourDiag.focusWidget().click()
        except:
            # dialog focused
            pass

    def onChooseColour(self, colour):
        recent = self.mw.config['recentColours']
        recent.remove(colour)
        recent.append(colour)
        self.web.eval("setFormat('forecolor', '%s')" % colour)
        self.colourDiag.close()
        runHook("colourChanged")

    def onNewColour(self):
        new = QColorDialog.getColor(Qt.white, self.widget)
        self.widget.raise_()
        recent = self.mw.config['recentColours']
        if new.isValid():
            txtcol = unicode(new.name())
            if txtcol not in recent:
                recent.append(txtcol)
            runHook("colourChanged")
            self.onChooseColour(txtcol)

    # Audio/video/images
    ######################################################################

    def initMedia(self):
        os.chdir(self.deck.mediaDir(create=True))

    def onAddPicture(self):
        # get this before we open the dialog
        w = self.focusedEdit()
        key = (_("Images") +
               " (*.jpg *.png *.gif *.tiff *.svg *.tif *.jpeg)")
        file = ui.utils.getFile(self.widget, _("Add an image"), "picture", key)
        if not file:
            return
        if file.lower().endswith(".svg"):
            # convert to a png
            s = QSvgRenderer(file)
            i = QImage(s.defaultSize(), QImage.Format_ARGB32_Premultiplied)
            p = QPainter()
            p.begin(i)
            s.render(p)
            p.end()
            (fd, name) = tempfile.mkstemp(prefix="anki", suffix=".png")
            file = unicode(name, sys.getfilesystemencoding())
            i.save(file)
        self._addPicture(file, widget=w)

    def _addPicture(self, file, widget=None):
        self.initMedia()
        if widget:
            w = widget
        else:
            w = self.focusedEdit()
        path = self._addMedia(file)
        self.maybeDelete(path, file)
        w.insertHtml('<img src="%s">' % path)

    def _addMedia(self, file):
        try:
            return self.deck.addMedia(file)
        except (IOError, OSError), e:
            ui.utils.showWarning(_("Unable to add media: %s") % unicode(e),
                                 parent=self.widget)

    def onAddSound(self):
        # get this before we open the dialog
        w = self.focusedEdit()
        key = (_("Sounds/Videos") +
               " (*.mp3 *.ogg *.wav *.avi *.ogv *.mpg *.mpeg *.mov *.mp4 " +
               "*.mkv *.ogx *.ogv *.oga *.flv *.swf *.flac)")
        file = ui.utils.getFile(self.widget, _("Add audio"), "audio", key)
        if not file:
            return
        self._addSound(file, widget=w)

    def _addSound(self, file, widget=None, copy=True):
        self.initMedia()
        if widget:
            w = widget
        else:
            w = self.focusedEdit()
        if copy:
            path = self._addMedia(file)
            self.maybeDelete(path, file)
        else:
            path = file
        anki.sound.play(path)
        w.insertHtml('[sound:%s]' % path)

    def maybeDelete(self, new, old):
        if not self.mw.config['deleteMedia']:
            return
        if new == os.path.basename(old):
            return
        try:
            os.unlink(old)
        except:
            pass

    def onRecSound(self):
        self.initMedia()
        w = self.focusedEdit()
        try:
            file = getAudio(self.widget)
        except:
            if sys.platform.startswith("darwin"):
                ui.utils.showInfo(_('''\
Please install <a href="http://www.thalictrum.com/software/lame-3.97.dmg.gz">lame</a>
to enable recording.'''), parent=self.widget)
                return
            raise
        if file:
            self._addSound(file, w, copy=False)

    # LaTeX
    ######################################################################

    def latexMenu(self):
        pass

    def insertLatex(self):
        w = self.focusedEdit()
        if w:
            selected = w.textCursor().selectedText()
            self.deck.mediaDir(create=True)
            cur = w.textCursor()
            pos = cur.position()
            w.insertHtml("[latex]%s[/latex]" % selected)
            cur.setPosition(pos+7)
            w.setTextCursor(cur)

    def insertLatexEqn(self):
        w = self.focusedEdit()
        if w:
            selected = w.textCursor().selectedText()
            self.deck.mediaDir(create=True)
            cur = w.textCursor()
            pos = cur.position()
            w.insertHtml("[$]%s[/$]" % selected)
            cur.setPosition(pos+3)
            w.setTextCursor(cur)

    def insertLatexMathEnv(self):
        w = self.focusedEdit()
        if w:
            selected = w.textCursor().selectedText()
            self.deck.mediaDir(create=True)
            cur = w.textCursor()
            pos = cur.position()
            w.insertHtml("[$$]%s[/$$]" % selected)
            cur.setPosition(pos+4)
            w.setTextCursor(cur)

# Pasting, drag & drop, and keyboard layouts
######################################################################

class EditorWebView(AnkiWebView):

    pics = ("jpg", "jpeg", "png", "tif", "tiff", "gif")
    audio =  ("wav", "mp3", "ogg", "flac")

    def __init__(self, parent, editor):
        AnkiWebView.__init__(self, parent)
        self.editor = editor
        self.__tmpDir = None
        self.errtxt = _("An error occured while opening %s")
        self.strip = self.editor.mw.config['stripHTML']
        # if sys.platform.startswith("win32"):
        #     self._ownLayout = None

    # after the drop/copy, make sure data updated?

    def keyPressEvent(self, evt):
        self._curKey = True
        if evt.matches(QKeySequence.Paste):
            self.onPaste()
        return QWebView.keyPressEvent(self, evt)

    def contextMenuEvent(self, evt):
        # adjust in case the user is going to paste
        self.onPaste()
        QWebView.contextMenuEvent(self, evt)

    def dropEvent(self, evt):
        oldmime = evt.mimeData()
        # coming from us?
        if evt.source() == self:
            # if they're copying just an image, we need to turn it into html
            # again
            txt = ""
            mime = QMimeData()
            if not oldmime.hasHtml() and oldmime.hasUrls():
                # qt gives it to us twice
                txt += '<img src="%s">' % os.path.basename(
                    unicode(oldmime.urls()[0].toString()))
                mime.setHtml(txt)
            else:
                mime.setText(oldmime.text())
        else:
            mime = self._processMime(oldmime)
        # create a new event with the new mime data
        new = QDropEvent(evt.pos(), evt.possibleActions(), mime,
                         evt.mouseButtons(), evt.keyboardModifiers())
        evt.accept()
        QWebView.dropEvent(self, new)

    def onPaste(self):
        clip = self.editor.mw.app.clipboard()
        mime = clip.mimeData()
        mime = self._processMime(mime)
        clip.setMimeData(mime)

    def _processMime(self, mime):
        print "html=%s image=%s urls=%s txt=%s" % (
            mime.hasHtml(), mime.hasImage(), mime.hasUrls(), mime.hasText())
        print "html", mime.html()
        print "urls", mime.urls()
        print "text", mime.text()
        if mime.hasUrls():
            return self._processUrls(mime)
        elif mime.hasImage():
            return self._processImage(mime)
        elif mime.hasText() and (self.strip or not mime.hasHtml()):
            return self._processText(mime)
        elif mime.hasHtml():
            return self._processHtml(mime)
        else:
            # nothing
            return QMimeData()

    def _processUrls(self, mime):
        links = []
        for url in mime.urls():
            url = unicode(url.toString())
            link = self._retrieveURL(url)
            if link:
                links.append(link)
        mime = QMimeData()
        mime.setHtml("".join(links))
        return mime

    def _processText(self, mime):
        txt = unicode(mime.text())
        l = txt.lower()
        html = None
        # firefox on linux just gives us a url for an image
        if "\n" in l and (l.startswith("http://") or l.startswith("file://")):
            txt = txt.split("\r\n")[0]
            html = self._retrieveURL(txt)
        new = QMimeData()
        if html:
            new.setHtml(html)
        else:
            new.setText(mime.text())
        return new

    def _processHtml(self, mime):
        html = mime.html()
        if self.strip:
            html = stripHTML(html)
        mime = QMimeData()
        mime.setHtml(html)
        return mime

    def _processImage(self, mime):
        im = QImage(mime.imageData())
        name = os.path.join(self._tmpDir(), "paste-%d.png" % im.cacheKey())
        uname = unicode(name, sys.getfilesystemencoding())
        if im.hasAlphaChannel():
            im.save(uname)
        else:
            im.save(uname, None, 95)
        mime = QMimeData()
        mime.setHtml(self._addMedia(uname))
        return mime

    def _retrieveURL(self, url):
        # is it media?
        ext = name.split(".")[-1].lower()
        if ext not in self.pics and ext not in self.audio:
            return
        # fetch it into a temporary folder
        try:
            req = urllib2.Request(url, None, {
                'User-Agent': 'Mozilla/5.0 (compatible; Anki)'})
            filecontents = urllib2.urlopen(req).read()
        except urllib2.URLError, e:
            showWarning(self.errtxt % e)
            return
        path = os.path.join(self._tmpDir(), os.path.basename(url))
        file = open(path, "wb")
        file.write(filecontents)
        file.close()
        return self._addMedia(path)

    def _addMedia(self, path):
        # copy to media folder
        name = self.editor.mw.deck.media.addFile(path)
        print "name was", name
        # return a local html link
        ext = name.split(".")[-1].lower()
        if ext in self.pics:
            return '<img src="%s">' % name
        else:
            # FIXME: should also autoplay audio
            return '[sound:%s]' % name

    def _tmpDir(self):
        if not self.__tmpDir:
            self.__tmpDir = tempfile.mkdtemp(prefix="anki")
        return self.__tmpDir

    # def focusOutEvent(self, evt):
    #     if self.mw.config['preserveKeyboard'] and sys.platform.startswith("win32"):
    #         self._ownLayout = GetKeyboardLayout(0)
    #         ActivateKeyboardLayout(self._programLayout, 0)
    #     self.emit(SIGNAL("lostFocus"))

    # def focusInEvent(self, evt):
    #     if self.mw.config['preserveKeyboard'] and sys.platform.startswith("win32"):
    #         self._programLayout = GetKeyboardLayout(0)
    #         if self._ownLayout == None:
    #             self._ownLayout = self._programLayout
    #         ActivateKeyboardLayout(self._ownLayout, 0)
