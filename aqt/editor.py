# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from aqt.qt import *
import re, os, sys, urllib2, ctypes, simplejson, traceback
from anki.utils import stripHTML, isWin, namedtmp
from anki.sound import play
from anki.hooks import runHook
from aqt.sound import getAudio
from aqt.webview import AnkiWebView
from aqt.utils import shortcut, showInfo, showWarning, getBase, getFile
import aqt
import anki.js

# fixme: when tab order returns to the webview, the previously focused field
# is focused, which is not good when the user is tabbing through the dialog
# fixme: set rtl in div css

# fixme: commit from tag/group area causes error

pics = ("jpg", "jpeg", "png", "tif", "tiff", "gif")
audio =  ("wav", "mp3", "ogg", "flac")

_html = """
<html><head>%s<style>
.field {
  border: 1px solid #aaa; background:#fff; color:#000; padding: 5px;
}
.fname { font-size: 14px; vertical-align: middle; padding-right: 5px; }
img { max-width: 150; max-height: 150; }
body { margin: 5px; }
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
        saveField("key"); }, 600);
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

function setFormat(cmd, arg, nosave) {
    document.execCommand(cmd, false, arg);
    if (!nosave) {
        saveField('key');
    }
};

function clearChangeTimer() {
    if (changeTimer) {
        clearTimeout(changeTimer);
        changeTimer = null;
    }
};

function onFocus(elem) {
    currentField = elem;
    // prevent webkit from highlighting the whole field
    $(elem).css("-webkit-user-select", "none");
    setTimeout(function () { unfocusHack() }, 1);
    py.run("focus:" + currentField.id.substring(1));
}

// restore cursor
function unfocusHack() {
    $(currentField).css("-webkit-user-select", "text");
    var r = document.createRange()
    r.selectNodeContents(currentField);
    r.collapse();
    var s = document.getSelection();
    s.addRange(r);
};

function onBlur() {
    if (currentField) {
        saveField("blur");
    }
    clearChangeTimer();
    // if we lose focus, assume the last field is still targeted
    //currentField = null;
};

function saveField(type) {
    // type is either 'blur' or 'key'
    py.run(type + ":" + currentField.innerHTML);
    clearChangeTimer();
};

function wrap(front, back) {
    setFormat('removeFormat', null, true);
    var s = window.getSelection();
    var r = s.getRangeAt(0);
    var content = r.extractContents();
    var span = document.createElement("span")
    span.appendChild(content);
    s.removeAllRanges();
    s.addRange(r);
    var new_ = front + span.innerHTML + back;
    var f = currentField.innerHTML;
    if (f.length && f[f.length-1] === " ") {
        new_ = " " + new_;
    }
    setFormat('inserthtml', new_);
};

function setFields(fields, focusTo) {
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
    if (!focusTo) {
        focusTo = 0;
    }
    $("#f"+focusTo).focus();
};

function setBackgrounds(cols) {
    for (var i=0; i<cols.length; i++) {
        $("#f"+i).css("background", cols[i]);
    }
}

function setFonts(fonts) {
    for (var i=0; i<fonts.length; i++) {
        $("#f"+i).css("font-family", fonts[i][0]);
        $("#f"+i).css("font-size", fonts[i][1]);
    }
}

function showDupes() {
    $("#dupes").show();
}

function hideDupes() {
    $("#dupes").hide();
}

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
<div id="dupes"><a href="#" onclick="py.run('dupes');return false;">%s</a></div>
</body></html>
"""

# caller is responsible for resetting fact on reset
class Editor(object):
    def __init__(self, mw, widget, addMode=False):
        self.widget = widget
        self.mw = mw
        self.fact = None
        self.stealFocus = True
        self.addMode = addMode
        self._loaded = False
        self._keepButtons = False
        # current card, for card layout
        self.card = None
        self.setupOuter()
        self.setupButtons()
        self.setupWeb()
        self.setupTagsAndGroup()
        self.setupKeyboard()

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
            b.setShortcut(QKeySequence(key))
        if tip:
            b.setToolTip(shortcut(tip))
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
        but = b("cloze", self.onCloze, "Ctrl+Shift+c",
                _("Cloze (Ctrl+Shift+c)"), text="[...]")
        but.setFixedWidth(24)
        # fixme: better image names
        b("text-speak", self.onAddMedia, "F3", _("Add pictures/audio/video (F3)"))
        b("media-record", self.onRecSound, "F5", _("Record audio (F5)"))
        b("tex", self.insertLatex, "Ctrl+t, t", _("LaTeX (Ctrl+t then t)"))
        b("math_sqrt", self.insertLatexEqn, "Ctrl+t, e",
                _("LaTeX equation (Ctrl+t then e)"))
        b("math_matrix", self.insertLatexMathEnv, "Ctrl+t, m",
                _("LaTeX math environment (Ctrl+t then m)"))
        but = b("text-xml", self.onHtmlEdit, "Ctrl+Shift+x",
                _("Source (Ctrl+Shift+x)"))

    def enableButtons(self, val=True):
        for b in self._buttons.values():
            b.setEnabled(val)

    def disableButtons(self):
        self.enableButtons(False)

    def onCardLayout(self):
        from aqt.clayout import CardLayout
        self.saveNow()
        if self.card:
            type = 1; ord = self.card.ord
        else:
            type = 0; ord = 0
        CardLayout(self.mw, self.fact, type=type, ord=ord, parent=self.widget)
        self.loadFact()

    # JS->Python bridge
    ######################################################################

    def bridge(self, str):
        if not self.fact or not runHook:
            # shutdown
            return
        # focus lost or key/button pressed?
        if str.startswith("blur") or str.startswith("key"):
            (type, txt) = str.split(":", 1)
            self.fact.fields[self.currentField] = self.mungeHTML(txt)
            self.mw.requireReset()
            self.fact.flush()
            if type == "blur":
                if not self._keepButtons:
                    self.disableButtons()
                runHook("editFocusLost", self.fact)
            else:
                runHook("editTimer", self.fact)
            self.checkValid()
        # focused into field?
        elif str.startswith("focus"):
            (type, num) = str.split(":", 1)
            self.enableButtons()
            self.currentField = int(num)
        # state buttons changed?
        elif str.startswith("state"):
            (cmd, txt) = str.split(":", 1)
            r = simplejson.loads(txt)
            self._buttons['text_bold'].setChecked(r['bold'])
            self._buttons['text_italic'].setChecked(r['italic'])
            self._buttons['text_under'].setChecked(r['under'])
            self._buttons['text_super'].setChecked(r['super'])
            self._buttons['text_sub'].setChecked(r['sub'])
        elif str.startswith("dupes"):
            self.showDupes()
        else:
            print str

    def mungeHTML(self, txt):
        if txt == "<br>":
            txt = ""
        return txt

    # Setting/unsetting the current fact
    ######################################################################

    def _loadFinished(self, w):
        self._loaded = True
        if self.fact:
            self.loadFact()

    def setFact(self, fact, hide=True):
        "Make FACT the current fact."
        self.fact = fact
        # change timer
        if self.fact:
            self.web.setHtml(_html % (getBase(self.mw.deck), anki.js.all,
                                  _("Show Duplicates")),
                             loadCB=self._loadFinished)
            self.updateTagsAndGroup()
            self.updateKeyboard()
        elif hide:
            self.widget.hide()

    def loadFact(self, field=0):
        if not self._loaded:
            # will be loaded when page is ready
            return
        self.web.eval("setFields(%s, %d);" % (
            simplejson.dumps(self.fact.items()), field))
        self.web.eval("setFonts(%s);" % (
            simplejson.dumps(self.fonts())))
        self.checkValid()
        self.widget.show()
        if self.stealFocus:
            self.web.setFocus()

    def focus(self):
        self.web.setFocus()

    def fonts(self):
        return [(f['font'], f['esize'])
                for f in self.fact.model()['flds']]

    def saveNow(self):
        "Must call this before adding cards, closing dialog, etc."
        if not self.fact:
            return
        self._keepButtons = True
        self.web.eval("saveField('blur');")
        self._keepButtons = False
        self.saveTagsAndGroup()

    def checkValid(self):
        cols = []
        self.dupe = None
        for c, p in enumerate(self.fact.problems()):
            if not p:
                cols.append("#fff")
            elif p == "unique":
                cols.append("#fcc")
                self.dupe = c
            else:
                cols.append("#ffc")
        self.web.eval("setBackgrounds(%s);" % simplejson.dumps(cols))
        if self.dupe is not None:
            self.web.eval("showDupes();")
        else:
            self.web.eval("hideDupes();")

    def showDupes(self):
        contents = self.fact.fields[self.dupe]
        browser = aqt.dialogs.open("Browser", self.mw)
        browser.form.searchEdit.setText(
            "'model:%s' '%s'" % (self.fact.model().name, contents))
        browser.onSearch()

    def fieldsAreBlank(self):
        if not self.fact:
            return True
        for f in self.fact.fields:
            if f:
                return False
        return True

    # HTML editing
    ######################################################################

    def onHtmlEdit(self):
        self.saveNow()
        d = QDialog(self.widget)
        form = aqt.forms.edithtml.Ui_Dialog()
        form.setupUi(d)
        d.connect(form.buttonBox, SIGNAL("helpRequested()"),
                 lambda: aqt.openHelp("HtmlEditor"))
        form.textEdit.setPlainText(self.fact.fields[self.currentField])
        form.textEdit.moveCursor(QTextCursor.End)
        d.exec_()
        self.fact.fields[self.currentField] = unicode(
            form.textEdit.toPlainText())
        self.loadFact(self.currentField)

    # Tag and group handling
    ######################################################################

    def setupTagsAndGroup(self):
        import aqt.tagedit
        g = QGroupBox(self.widget)
        g.setFlat(True)
        tb = QGridLayout()
        tb.setSpacing(12)
        tb.setMargin(6)
        # group
        l = QLabel(_("Initial Group"))
        tb.addWidget(l, 0, 0)
        if not self.addMode:
            self.group = QPushButton()
            self.group.connect(self.group, SIGNAL("clicked()"),
                               self.changeGroup)
        else:
            self.group = aqt.tagedit.TagEdit(self.widget, type=1)
            self.group.connect(self.group, SIGNAL("lostFocus"),
                               self.saveTagsAndGroup)
        tb.addWidget(self.group, 0, 1)
        # tags
        l = QLabel(_("Tags"))
        tb.addWidget(l, 0, 2)
        self.tags = aqt.tagedit.TagEdit(self.widget)
        self.tags.connect(self.tags, SIGNAL("lostFocus"),
                          self.saveTagsAndGroup)
        tb.addWidget(self.tags, 0, 3)
        g.setLayout(tb)
        self.outerLayout.addWidget(g)

    def updateTagsAndGroup(self):
        if self.tags.deck != self.mw.deck:
            self.tags.setDeck(self.mw.deck)
            if self.addMode:
                self.group.setDeck(self.mw.deck)
        self.tags.setText(self.fact.stringTags().strip())
        if getattr(self.fact, 'gid', None):
            gid = self.fact.gid
        else:
            gid = self.fact.model().conf['gid']
        self.group.setText(self.mw.deck.groups.name(gid))

    def saveTagsAndGroup(self):
        if not self.fact:
            return
        self.fact.tags = self.mw.deck.tags.split(unicode(self.tags.text()))
        if self.addMode:
            # save group and tags to model
            self.fact.gid = self.mw.deck.groups.id(unicode(self.group.text()))
            m = self.fact.model()
            m['gid'] = self.fact.gid
            m['tags'] = self.fact.tags
            self.mw.deck.models.save(m)
        self.fact.flush()
        runHook("tagsAndGroupUpdated", self.fact)

    def changeGroup(self):
        id = self.fact.id
        runHook("closeEditCurrent")
        browser = aqt.dialogs.open("Browser", self.mw)
        browser.form.searchEdit.setText("fid:%d" % id)
        browser.onSearch()
        browser.setGroup(True)

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
        # check that the model is set up for cloze deletion
        ok = False
        for t in self.fact.model().templates:
            if "cloze" in t['qfmt'] or "cloze" in t['afmt']:
                ok = True
                break
        if not ok:
            showInfo(_("Please use a cloze deletion model."),
                 help="ClozeDeletion")
            return
        f = self.fact.fields[self.currentField]
        # find the highest existing cloze
        m = re.findall("\{\{c(\d+)::", f)
        if m:
            next = sorted([int(x) for x in m])[-1] + 1
        else:
            next = 1
        self.web.eval("wrap('{{c%d::', '}}');" % next)

    # Foreground colour
    ######################################################################

    def setupForegroundButton(self, but):
        self.foregroundFrame = QFrame()
        self.foregroundFrame.setAutoFillBackground(True)
        self.colourChanged()
        hbox = QHBoxLayout()
        hbox.addWidget(self.foregroundFrame)
        hbox.setMargin(5)
        but.setLayout(hbox)

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
        self.colourChanged()

    def onNextColour(self):
        self.colourDiag.focusWidget().nextInFocusChain().setFocus()

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
        self.colourChanged()

    def onNewColour(self):
        new = QColorDialog.getColor(Qt.white, self.widget)
        self.widget.raise_()
        recent = self.mw.config['recentColours']
        if new.isValid():
            txtcol = unicode(new.name())
            if txtcol not in recent:
                recent.append(txtcol)
            self.colourChanged()
            self.onChooseColour(txtcol)

    # Audio/video/images
    ######################################################################

    def onAddMedia(self):
        key = (_("Media") +
               " (*.jpg *.png *.gif *.tiff *.svg *.tif *.jpeg "+
               "*.mp3 *.ogg *.wav *.avi *.ogv *.mpg *.mpeg *.mov *.mp4 " +
               "*.mkv *.ogx *.ogv *.oga *.flv *.swf *.flac)")
        def accept(file):
            self.addMedia(file, canDelete=True)
        file = getFile(self.widget, _("Add Media"), accept, key, key="media")

    def addMedia(self, path, canDelete=False):
        html = self._addMedia(path, canDelete)
        self.web.eval("setFormat('inserthtml', %s);" % simplejson.dumps(html))

    def _addMedia(self, path, canDelete=False):
        "Add to media folder and return basename."
        # copy to media folder
        name = self.mw.deck.media.addFile(path)
        # remove original?
        if canDelete and self.mw.config['deleteMedia']:
            if os.path.abspath(name) != os.path.abspath(path):
                try:
                    os.unlink(old)
                except:
                    pass
        # return a local html link
        ext = name.split(".")[-1].lower()
        if ext in pics:
            return '<img src="%s">' % name
        else:
            anki.sound.play(name)
            return '[sound:%s]' % name

    def onRecSound(self):
        try:
            file = getAudio(self.widget)
        except Exception, e:
            showWarning(_(
                "Couldn't record audio. Have you installed lame and sox?") +
                        "\n\n" + unicode(e))
            return
        self.addMedia(file)

    # LaTeX
    ######################################################################

    def insertLatex(self):
        self.mw.deck.media.dir(create=True)
        self.web.eval("wrap('[latex]', '[/latex]');")

    def insertLatexEqn(self):
        self.mw.deck.media.dir(create=True)
        self.web.eval("wrap('[$]', '[/$]');")

    def insertLatexMathEnv(self):
        self.mw.deck.media.dir(create=True)
        self.web.eval("wrap('[$$]', '[/$$]');")

    # Keyboard layout
    ######################################################################

    def setupKeyboard(self):
        if isWin and self.mw.config['preserveKeyboard']:
            a = ctypes.windll.user32.ActivateKeyboardLayout
            a.restype = ctypes.c_void_p
            a.argtypes = [ctypes.c_void_p, ctypes.c_uint]
            g = ctypes.windll.user32.GetKeyboardLayout
            g.restype = ctypes.c_void_p
            g.argtypes = [ctypes.c_uint]
        else:
            a = g = None
        self.activateKeyboard = a
        self.getKeyboard = g

    def updateKeyboard(self):
        self.keyboardLayouts = {}

    def saveKeyboard(self):
        if not self.getKeyboard:
            return
        self.keyboardLayouts[self.currentField] = self.getKeyboard(0)

    def restoreKeyboard(self):
        if not self.getKeyboard:
            return
        if self.currentField in self.keyboardLayouts:
            self.activateKeyboard(self.keyboardLayouts[self.currentField], 0)

# Pasting, drag & drop, and keyboard layouts
######################################################################

class EditorWebView(AnkiWebView):

    def __init__(self, parent, editor):
        AnkiWebView.__init__(self, parent)
        self.editor = editor
        self.errtxt = _("An error occured while opening %s")
        self.strip = self.editor.mw.config['stripHTML']

    def keyPressEvent(self, evt):
        self._curKey = True
        self.origClip = None
        shiftPaste = (evt.modifiers() == (Qt.ShiftModifier | Qt.ControlModifier)
                      and evt.key() == Qt.Key_V)
        if evt.matches(QKeySequence.Paste) or shiftPaste:
            self.prepareClip(shiftPaste)
        if shiftPaste:
            self.triggerPageAction(QWebPage.Paste)
        QWebView.keyPressEvent(self, evt)
        if self.origClip:
            self.restoreClip()

    def contextMenuEvent(self, evt):
        # adjust in case the user is going to paste
        self.prepareClip()
        QWebView.contextMenuEvent(self, evt)
        self.restoreClip()

    def dropEvent(self, evt):
        oldmime = evt.mimeData()
        # coming from this program?
        if evt.source():
            # if they're copying just an image, we need to turn it into html
            # again
            txt = ""
            mime = QMimeData()
            if not oldmime.hasHtml() and oldmime.hasUrls():
                # qt gives it to us twice
                txt += '<img src="%s">' % os.path.basename(
                    oldmime.urls()[0].toString())
                mime.setHtml(txt)
            else:
                mime.setHtml(oldmime.html())
        else:
            mime = self._processMime(oldmime)
        # create a new event with the new mime data
        new = QDropEvent(evt.pos(), evt.possibleActions(), mime,
                         evt.mouseButtons(), evt.keyboardModifiers())
        evt.accept()
        QWebView.dropEvent(self, new)

    def prepareClip(self, keep=False):
        clip = self.editor.mw.app.clipboard()
        mime = clip.mimeData()
        self.saveClip()
        if keep:
            new = QMimeData()
            if mime.hasHtml():
                new.setHtml(mime.html())
            else:
                new.setText(mime.text())
            mime = new
        else:
            mime = self._processMime(mime)
        clip.setMimeData(mime)

    def restoreClip(self):
        clip = self.editor.mw.app.clipboard()
        clip.setMimeData(self.origClip)

    def saveClip(self):
        # we don't own the clipboard object, so we need to copy it
        mime = self.editor.mw.app.clipboard().mimeData()
        n = QMimeData()
        if mime.hasText():
            n.setText(mime.text())
        if mime.hasHtml():
            n.setHtml(mime.html())
        if mime.hasUrls():
            n.setUrls(mime.urls())
        if mime.hasImage():
            n.setImageData(mime.imageData())
        self.origClip = n

    def _processMime(self, mime):
        # print "html=%s image=%s urls=%s txt=%s" % (
        #     mime.hasHtml(), mime.hasImage(), mime.hasUrls(), mime.hasText())
        # print "html", mime.html()
        # print "urls", mime.urls()
        # print "text", mime.text()
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
            url = url.toString()
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
        name = namedtmp("paste-%d.png" % im.cacheKey())
        uname = unicode(name, sys.getfilesystemencoding())
        if im.hasAlphaChannel():
            im.save(uname)
        else:
            im.save(uname, None, 95)
        mime = QMimeData()
        mime.setHtml(self.editor._addMedia(uname))
        return mime

    def _retrieveURL(self, url):
        # is it media?
        ext = url.split(".")[-1].lower()
        if ext not in pics and ext not in audio:
            return
        # fetch it into a temporary folder
        try:
            req = urllib2.Request(url, None, {
                'User-Agent': 'Mozilla/5.0 (compatible; Anki)'})
            filecontents = urllib2.urlopen(req).read()
        except urllib2.URLError, e:
            showWarning(self.errtxt % e)
            return
        path = namedtmp(os.path.basename(url))
        file = open(path, "wb")
        file.write(filecontents)
        file.close()
        return self.editor._addMedia(path)
