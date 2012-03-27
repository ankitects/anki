# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from aqt.qt import *
import re, os, sys, urllib2, ctypes, simplejson, traceback
from anki.utils import stripHTML, isWin, isMac, namedtmp
from anki.sound import play
from anki.hooks import runHook
from aqt.sound import getAudio
from aqt.webview import AnkiWebView
from aqt.utils import shortcut, showInfo, showWarning, getBase, getFile, \
    openHelp
import aqt
import anki.js
from BeautifulSoup import BeautifulSoup

# fixme: when tab order returns to the webview, the previously focused field
# is focused, which is not good when the user is tabbing through the dialog
# fixme: set rtl in div css

# fixme: commit from tag area causes error

pics = ("jpg", "jpeg", "png", "tif", "tiff", "gif")
audio =  ("wav", "mp3", "ogg", "flac")

_html = """
<html><head>%s<style>
.field {
  border: 1px solid #aaa; background:#fff; color:#000; padding: 5px;
}
.field:after {
    content: ".";
    display: block;
    height: 0;
    clear: both;
    visibility: hidden;
}
.fname { font-size: 12px; vertical-align: middle; padding: 0; }
#dupes { font-size: 12px; }
img { max-width: 150; max-height: 150; }
body { margin: 5px; }
</style><script>
%s

insertHTMLOK = %s;

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
    function pos(obj) {
    	var cur = 0;
        do {
          cur += obj.offsetTop;
         } while (obj = obj.offsetParent);
    	return cur;
    }
    var y = pos(elem);
    if ((window.pageYOffset+window.innerHeight) < (y+elem.offsetHeight))
        window.scroll(0,y+elem.offsetHeight-window.innerHeight);
    else if (window.pageYOffset > y)
        window.scroll(0, y-15);
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

function wrappedExceptForWhitespace(text, front, back) {
    var match = text.match(/^(\s*)([^]*?)(\s*)$/);
    return match[1] + front + match[2] + back + match[3];
};

function wrap(front, back) {
    var s = window.getSelection();
    var r = s.getRangeAt(0);
    var content = r.cloneContents();
    var span = document.createElement("span")
    span.appendChild(content);
    var new_ = wrappedExceptForWhitespace(span.innerHTML, front, back);
    if (insertHTMLOK) {
        setFormat("inserthtml", new_);
    } else {
        r.deleteContents();
        r.collapse(true);
        r.insertNode(document.createTextNode(new_));
        saveField('key');
    }
};

function setFields(fields, focusTo) {
    var txt = "";
    for (var i=0; i<fields.length; i++) {
        var n = fields[i][0];
        var f = fields[i][1];
        if (!f) {
            f = "<br>";
        }
        txt += "<tr><td class=fname>{0}</td></tr><tr><td width=100%%>".format(n);
        txt += "<div id=f{0} onkeydown='onKey();' onmouseup='onKey();'".format(i);
        txt += " onfocus='onFocus(this);' onblur='onBlur();' class=field ";
        txt += "contentEditable=true>{0}</div>".format(f);
        txt += "</td></tr>";
    }
    $("#fields").html("<table cellpadding=0 width=100%%>"+txt+"</table>");
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
        $("#f"+i)[0].dir = fonts[i][2] ? "rtl" : "ltr";
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

# caller is responsible for resetting note on reset
class Editor(object):
    def __init__(self, mw, widget, parentWindow, addMode=False):
        self.mw = mw
        self.widget = widget
        self.parentWindow = parentWindow
        self.note = None
        self.stealFocus = True
        self.addMode = addMode
        self._loaded = False
        self._keepButtons = False
        self.currentField = 0
        # current card, for card layout
        self.card = None
        self.setupOuter()
        self.setupButtons()
        self.setupWeb()
        self.setupTagsAndDeck()
        self.setupKeyboard()

    # Initial setup
    ############################################################

    def setupOuter(self):
        l = QVBoxLayout()
        l.setMargin(0)
        l.setSpacing(0)
        self.widget.setLayout(l)
        self.outerLayout = l

    def setupWeb(self):
        self.web = EditorWebView(self.widget, self)
        self.web.allowDrops = True
        self.web.setBridge(self.bridge)
        self.outerLayout.addWidget(self.web, 1)
        # pick up the window colour
        p = self.web.palette()
        p.setBrush(QPalette.Base, Qt.transparent)
        self.web.page().setPalette(p)
        self.web.setAttribute(Qt.WA_OpaquePaintEvent, False)

    # Top buttons
    ######################################################################

    def _addButton(self, name, func, key=None, tip=None, size=True, text="",
                   check=False, native=False, canDisable=True):
        b = QPushButton(text)
        if check:
            b.connect(b, SIGNAL("clicked(bool)"), func)
        else:
            b.connect(b, SIGNAL("clicked()"), func)
        if size:
            b.setFixedHeight(20)
            b.setFixedWidth(20)
        if not native:
            b.setStyle(self.plastiqueStyle)
            b.setFocusPolicy(Qt.NoFocus)
        else:
            b.setAutoDefault(False)
        if not text:
            b.setIcon(QIcon(":/icons/%s.png" % name))
        if key:
            b.setShortcut(QKeySequence(key))
        if tip:
            b.setToolTip(shortcut(tip))
        if check:
            b.setCheckable(True)
        self.iconsBox.addWidget(b)
        if canDisable:
            self._buttons[name] = b
        return b

    def setupButtons(self):
        self._buttons = {}
        # button styles for mac
        self.plastiqueStyle = QStyleFactory.create("plastique")
        self.widget.setStyle(self.plastiqueStyle)
        # icons
        self.iconsBox = QHBoxLayout()
        if not isMac:
            self.iconsBox.setMargin(6)
        else:
            self.iconsBox.setMargin(0)
        self.iconsBox.setSpacing(0)
        self.outerLayout.addLayout(self.iconsBox)
        b = self._addButton
        b("fields", self.onFields, "",
          shortcut(_("Customize Fields")), size=False, text=_("Fields..."),
          native=True, canDisable=False)
        b("layout", self.onCardLayout, "Ctrl+l",
          shortcut(_("Customize Card Layout (Ctrl+l)")),
          size=False, text=_("Cards..."), native=True, canDisable=False)
        # align to right
        self.iconsBox.addItem(QSpacerItem(20,1, QSizePolicy.Expanding))
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
          _("Remove Formatting (Ctrl+r)"))
        but = b("foreground", self.onForeground, "F7", text=" ")
        self.setupForegroundButton(but)
        but = b("cloze", self.onCloze, "Ctrl+Shift+c",
                _("Cloze (Ctrl+Shift+c)"), text="[...]")
        but.setFixedWidth(24)
        # fixme: better image names
        b("mail-attachment", self.onAddMedia, "F3",
          _("Attach pictures/audio/video (F3)"))
        b("media-record", self.onRecSound, "F5", _("Record audio (F5)"))
        b("adv", self.onAdvanced, text=u"▾")
        s = QShortcut(QKeySequence("Ctrl+t, t"), self.widget)
        s.connect(s, SIGNAL("activated()"), self.insertLatex)
        s = QShortcut(QKeySequence("Ctrl+t, e"), self.widget)
        s.connect(s, SIGNAL("activated()"), self.insertLatexEqn)
        s = QShortcut(QKeySequence("Ctrl+t, m"), self.widget)
        s.connect(s, SIGNAL("activated()"), self.insertLatexMathEnv)
        s = QShortcut(QKeySequence("Ctrl+shift+x"), self.widget)
        s.connect(s, SIGNAL("activated()"), self.onHtmlEdit)

    def enableButtons(self, val=True):
        for b in self._buttons.values():
            b.setEnabled(val)

    def disableButtons(self):
        self.enableButtons(False)

    def onFields(self):
        from aqt.fields import FieldDialog
        self.saveNow()
        FieldDialog(self.mw, self.note, parent=self.parentWindow)

    def onCardLayout(self):
        from aqt.clayout import CardLayout
        self.saveNow()
        if self.card:
            ord = self.card.ord
        else:
            ord = 0
        CardLayout(self.mw, self.note, ord=ord, parent=self.parentWindow,
               addMode=self.addMode)
        self.loadNote()

    # JS->Python bridge
    ######################################################################

    def bridge(self, str):
        if not self.note or not runHook:
            # shutdown
            return
        # focus lost or key/button pressed?
        if str.startswith("blur") or str.startswith("key"):
            (type, txt) = str.split(":", 1)
            txt = self.mungeHTML(txt)
            # misbehaving apps may include a null byte in the text
            txt = txt.replace("\x00", "")
            self.note.fields[self.currentField] = txt
            self.mw.requireReset()
            if not self.addMode:
                self.note.flush()
            if type == "blur":
                if not self._keepButtons:
                    self.disableButtons()
                runHook("editFocusLost", self.note)
            else:
                runHook("editTimer", self.note)
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

    # Setting/unsetting the current note
    ######################################################################

    def _loadFinished(self, w):
        self._loaded = True
        if self.note:
            self.loadNote()

    def setNote(self, note, hide=True):
        "Make NOTE the current note."
        self.note = note
        # change timer
        if self.note:
            self.web.setHtml(_html % (getBase(self.mw.col), anki.js.jquery,
                                      (isMac or isWin) and 1 or 0,
                                  _("Show Duplicates")),
                             loadCB=self._loadFinished)
            self.updateTagsAndDeck()
            self.updateKeyboard()
        else:
            self.hideCompleters()
            if hide:
                self.widget.hide()

    def loadNote(self):
        field = self.currentField
        if not self._loaded:
            # will be loaded when page is ready
            return
        self.web.eval("setFields(%s, %d);" % (
            simplejson.dumps(self.note.items()), field))
        self.web.eval("setFonts(%s);" % (
            simplejson.dumps(self.fonts())))
        self.checkValid()
        self.widget.show()
        if self.stealFocus:
            self.web.setFocus()

    def focus(self):
        self.web.setFocus()

    def fonts(self):
        return [(f['font'], f['size'], f['rtl'])
                for f in self.note.model()['flds']]

    def saveNow(self):
        "Must call this before adding cards, closing dialog, etc."
        if not self.note:
            return
        self._keepButtons = True
        self.web.eval("saveField('blur');")
        self._keepButtons = False
        self.saveTagsAndDeck()

    def checkValid(self):
        cols = []
        err = None
        for f in self.note.fields:
            cols.append("#fff")
        err = self.note.dupeOrEmpty()
        if err == 2:
            cols[0] = "#fcc"
            self.web.eval("showDupes();")
        else:
            self.web.eval("hideDupes();")
        self.web.eval("setBackgrounds(%s);" % simplejson.dumps(cols))

    def showDupes(self):
        contents = self.note.fields[0]
        browser = aqt.dialogs.open("Browser", self.mw)
        browser.form.searchEdit.setText(
            "'model:%s' '%s:%s'" % (
                self.note.model()['name'],
                self.note.model()['flds'][0]['name'],
                contents))
        browser.onSearch()

    def fieldsAreBlank(self):
        if not self.note:
            return True
        for f in self.note.fields:
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
                 lambda: openHelp("editor"))
        form.textEdit.setPlainText(self.note.fields[self.currentField])
        form.textEdit.moveCursor(QTextCursor.End)
        d.exec_()
        html = form.textEdit.toPlainText()
        # filter html through beautifulsoup so we can strip out things like a
        # leading </div>
        html = unicode(BeautifulSoup(html))
        self.note.fields[self.currentField] = html
        self.loadNote()

    # Tag & deck handling
    ######################################################################

    def setupTagsAndDeck(self):
        import aqt.tagedit
        g = QGroupBox(self.widget)
        g.setFlat(True)
        tb = QGridLayout()
        tb.setSpacing(12)
        tb.setMargin(6)
        # deck
        if self.addMode:
            l = QLabel(_("Deck"))
            tb.addWidget(l, 0, 0)
            self.deck = aqt.tagedit.TagEdit(self.widget, type=1)
            self.deck.connect(self.deck, SIGNAL("lostFocus"),
                              self.saveTagsAndDeck)
            tb.addWidget(self.deck, 0, 1)
        else:
            self.deck = None
        # tags
        l = QLabel(_("Tags"))
        tb.addWidget(l, 1, 0)
        self.tags = aqt.tagedit.TagEdit(self.widget)
        self.tags.connect(self.tags, SIGNAL("lostFocus"),
                          self.saveTagsAndDeck)
        tb.addWidget(self.tags, 1, 1)
        g.setLayout(tb)
        self.outerLayout.addWidget(g)

    def updateTagsAndDeck(self):
        if self.tags.col != self.mw.col:
            if self.deck:
                self.deck.setCol(self.mw.col)
            self.tags.setCol(self.mw.col)
        if self.addMode:
            self.deck.setText(self.mw.col.decks.name(self.note.model()['did']))
        self.tags.setText(self.note.stringTags().strip())

    def saveTagsAndDeck(self):
        if not self.note:
            return
        self.note.tags = self.mw.col.tags.split(self.tags.text())
        if self.addMode:
            name = self.deck.text()
            if not name.strip():
                self.note.model()['did'] = 1
            else:
                self.note.model()['did'] = self.mw.col.decks.id(name)
            # save tags to model
            m = self.note.model()
            m['tags'] = self.note.tags
            self.mw.col.models.save(m)
        if not self.addMode:
            self.note.flush()
        runHook("tagsUpdated", self.note)

    def hideCompleters(self):
        self.tags.hideCompleter()
        if self.addMode:
            self.deck.hideCompleter()

    # Format buttons
    ######################################################################

    def toggleBold(self, bool):
        self._eval("setFormat('bold');")

    def toggleItalic(self, bool):
        self._eval("setFormat('italic');")

    def toggleUnderline(self, bool):
        self._eval("setFormat('underline');")

    def toggleSuper(self, bool):
        self._eval("setFormat('superscript');")

    def toggleSub(self, bool):
        self._eval("setFormat('subscript');")

    def removeFormat(self):
        self._eval("setFormat('removeFormat');")

    def onCloze(self):
        # check that the model is set up for cloze deletion
        if 'cloze' not in self.note.model()['tmpls'][0]['qfmt']:
            showInfo(_("Cloze deletion requires a Cloze note type."),
                     help="ClozeDeletion")
            return
        f = self.note.fields[self.currentField]
        # find the highest existing cloze
        m = re.findall("\{\{c(\d+)::", f)
        if m:
            next = sorted([int(x) for x in m])[-1] + 1
            if self.mw.app.keyboardModifiers() & Qt.AltModifier:
                next -= 1
        else:
            next = 1
        self._eval("wrap('{{c%d::', '}}');" % next)

    def _eval(self, str):
        # some versions of webkit crash if we try a dom-modifying operation
        # before outstanding UI events have been processed
        self.mw.app.processEvents()
        self.mw.progress.timer(100, lambda: self.web.eval(str), False)

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
        recent = self.mw.pm.profile['recentColours']
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
        for n, c in enumerate(reversed(self.mw.pm.profile['recentColours'])):
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
        cb.setShortcut(QKeySequence("F5"))
        cb.connect(cb, SIGNAL("clicked()"), self.onNewColour)
        cb.setFixedWidth(80)
        cb.setFixedHeight(16)
        cb.setAutoDefault(False)
        g.addWidget(cb, n+2, 0, 1, 2)
        self.colourDiag = p
        p.show()

    def onRemoveColour(self, colour):
        recent = self.mw.pm.profile['recentColours']
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
        recent = self.mw.pm.profile['recentColours']
        recent.remove(colour)
        recent.append(colour)
        self._eval("setFormat('forecolor', '%s')" % colour)
        self.colourDiag.close()
        self.colourChanged()

    def onNewColour(self):
        new = QColorDialog.getColor(Qt.white, self.widget)
        self.widget.raise_()
        recent = self.mw.pm.profile['recentColours']
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
        self._eval("setFormat('inserthtml', %s);" % simplejson.dumps(html))

    def _addMedia(self, path, canDelete=False):
        "Add to media folder and return basename."
        # copy to media folder
        name = self.mw.col.media.addFile(path)
        # remove original?
        if canDelete and self.mw.pm.profile['deleteMedia']:
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

    # Advanced menu
    ######################################################################

    def onAdvanced(self):
        m = QMenu(self.mw)
        a = m.addAction(_("LaTeX"))
        a.setShortcut(QKeySequence("Ctrl+t, t"))
        a.connect(a, SIGNAL("triggered()"), self.insertLatex)
        a = m.addAction(_("LaTeX Equation"))
        a.setShortcut(QKeySequence("Ctrl+t, e"))
        a.connect(a, SIGNAL("triggered()"), self.insertLatexEqn)
        a = m.addAction(_("LaTeX Math Env."))
        a.setShortcut(QKeySequence("Ctrl+t, m"))
        a.connect(a, SIGNAL("triggered()"), self.insertLatexMathEnv)
        a = m.addAction(_("Edit HTML"))
        a.setShortcut(QKeySequence("Ctrl+shift+x"))
        a.connect(a, SIGNAL("triggered()"), self.onHtmlEdit)
        m.exec_(QCursor.pos())

    # LaTeX
    ######################################################################

    def insertLatex(self):
        self._eval("wrap('[latex]', '[/latex]');")

    def insertLatexEqn(self):
        self._eval("wrap('[$]', '[/$]');")

    def insertLatexMathEnv(self):
        self._eval("wrap('[$$]', '[/$$]');")

    # Keyboard layout
    ######################################################################

    def setupKeyboard(self):
        if isWin and self.mw.pm.profile['preserveKeyboard']:
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
        AnkiWebView.__init__(self)
        self.editor = editor
        self.errtxt = _("An error occured while opening %s")
        self.strip = self.editor.mw.pm.profile['stripHTML']

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
        self.restoreClip()

    def mouseReleaseEvent(self, evt):
        if not isMac and not isWin and evt.button() == Qt.MidButton:
            # middle click on x11; munge the clipboard before standard
            # handling
            self.prepareClip(mode=QClipboard.Selection)
            AnkiWebView.mouseReleaseEvent(self, evt)
            self.restoreClip(mode=QClipboard.Selection)
        else:
            AnkiWebView.mouseReleaseEvent(self, evt)

    # Buggy; disable for now.
    # def contextMenuEvent(self, evt):
    #     # adjust in case the user is going to paste
    #     self.prepareClip()
    #     QWebView.contextMenuEvent(self, evt)
    #     self.restoreClip()

    def dropEvent(self, evt):
        oldmime = evt.mimeData()
        # coming from this program?
        if evt.source():
            assert oldmime.hasHtml()
            mime = QMimeData()
            mime.setHtml(self._filteredHTML(oldmime.html()))
        else:
            mime = self._processMime(oldmime)
        # create a new event with the new mime data
        new = QDropEvent(evt.pos(), evt.possibleActions(), mime,
                         evt.mouseButtons(), evt.keyboardModifiers())
        evt.accept()
        QWebView.dropEvent(self, new)
        self.editor.saveNow()

    def prepareClip(self, keep=False, mode=QClipboard.Clipboard):
        clip = self.editor.mw.app.clipboard()
        mime = clip.mimeData(mode=mode)
        self.saveClip(mode=mode)
        if keep:
            new = QMimeData()
            if mime.hasHtml():
                new.setHtml(self._filteredHTML(mime.html()))
            else:
                new.setText(mime.text())
            mime = new
        else:
            mime = self._processMime(mime)
        clip.setMimeData(mime, mode=mode)

    def restoreClip(self, mode=QClipboard.Clipboard):
        if not self.origClip:
            return
        clip = self.editor.mw.app.clipboard()
        clip.setMimeData(self.origClip, mode=mode)

    def saveClip(self, mode):
        # we don't own the clipboard object, so we need to copy it
        mime = self.editor.mw.app.clipboard().mimeData(mode=mode)
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
        url = mime.urls()[0].toString()
        link = None
        for suffix in pics+audio:
            if url.lower().endswith(suffix):
                link = self._retrieveURL(url)
                break
        if not link:
            # not a supported media type; include link verbatim
            link = url
        mime = QMimeData()
        mime.setHtml(link)
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
        else:
            html = self._filteredHTML(html)
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

    def _filteredHTML(self, html):
        doc = BeautifulSoup(html)
        # filter out implicit formatting from webkit
        for tag in doc("span", "Apple-style-span"):
            tag.replaceWithChildren()
        # turn file:/// links into relative ones
        for tag in doc("img"):
            if tag['src'].lower().startswith("file://"):
                tag['src'] = os.path.basename(tag['src'])
        # strip superfluous elements
        for elem in "html", "head", "body", "meta":
            for tag in doc(elem):
                tag.replaceWithChildren()
        html = unicode(doc)
        return html
