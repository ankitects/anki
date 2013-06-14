# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from aqt.qt import *
import re, os, urllib2, ctypes
from anki.utils import stripHTML, isWin, isMac, namedtmp, json, stripHTMLMedia
from anki.sound import play
from anki.hooks import runHook, runFilter
from aqt.sound import getAudio
from aqt.webview import AnkiWebView
from aqt.utils import shortcut, showInfo, showWarning, getBase, getFile, \
    openHelp, tooltip
import aqt
import anki.js
from BeautifulSoup import BeautifulSoup
import urllib

# fixme: when tab order returns to the webview, the previously focused field
# is focused, which is not good when the user is tabbing through the dialog
# fixme: set rtl in div css

# fixme: commit from tag area causes error

pics = ("jpg", "jpeg", "png", "tif", "tiff", "gif", "svg")
audio =  ("wav", "mp3", "ogg", "flac", "mp4", "swf", "mov", "mpeg", "mkv")

_html = """
<html><head>%s<style>
.field {
  border: 1px solid #aaa; background:#fff; color:#000; padding: 5px;
}
/* prevent floated images from being displayed outside field */
.field:after {
    content: "";
    display: block;
    height: 0;
    clear: both;
    visibility: hidden;
}
.fname { vertical-align: middle; padding: 0; }
img { max-width: 90%%; }
body { margin: 5px; }
</style><script>
%s

var currentField = null;
var changeTimer = null;
var dropTarget = null;

String.prototype.format = function() {
    var args = arguments;
    return this.replace(/\{\d+\}/g, function(m){
            return args[m.match(/\d+/)]; });
};

function onKey() {
    // esc clears focus, allowing dialog to close
    if (window.event.which == 27) {
        currentField.blur();
        return;
    }
    clearChangeTimer();
    if (currentField.innerHTML == "<div><br></div>") {
        // fix empty div bug. slight flicker, but must be done in a timer
        changeTimer = setTimeout(function () {
            currentField.innerHTML = "<br>";
            sendState();
            saveField("key"); }, 1);
    } else {
        changeTimer = setTimeout(function () {
            sendState();
            saveField("key"); }, 600);
    }
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
    py.run("focus:" + currentField.id.substring(1));
    // don't adjust cursor on mouse clicks
    if (mouseDown) { return; }
    // do this twice so that there's no flicker on newer versions
    caretToEnd();
    // need to do this in a timeout for older qt versions
    setTimeout(function () { caretToEnd() }, 1);
    // scroll if bottom of element off the screen
    function pos(obj) {
    	var cur = 0;
        do {
          cur += obj.offsetTop;
         } while (obj = obj.offsetParent);
    	return cur;
    }
    var y = pos(elem);
    if ((window.pageYOffset+window.innerHeight) < (y+elem.offsetHeight) ||
        window.pageYOffset > y) {
        window.scroll(0,y+elem.offsetHeight-window.innerHeight);
    }
}

function focusField(n) {
    $("#f"+n).focus();
}

function onDragOver(elem) {
    // if we focus the target element immediately, the drag&drop turns into a
    // copy, so note it down for later instead
    dropTarget = elem;
}

function caretToEnd() {
    var r = document.createRange()
    r.selectNodeContents(currentField);
    r.collapse(false);
    var s = document.getSelection();
    s.removeAllRanges();
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
    if (!currentField) {
        // no field has been focused yet
        return;
    }
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
    setFormat("inserthtml", new_);
    if (!span.innerHTML) {
        // run with an empty selection; move cursor back past postfix
        r = s.getRangeAt(0);
        r.setStart(r.startContainer, r.startOffset - back.length);
        r.collapse(true);
        s.removeAllRanges();
        s.addRange(r);
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
        txt += "ondragover='onDragOver(this);' ";
        txt += "contentEditable=true class=field>{0}</div>".format(f);
        txt += "</td></tr>";
    }
    $("#fields").html("<table cellpadding=0 width=100%%>"+txt+"</table>");
    if (!focusTo) {
        focusTo = 0;
    }
    if (focusTo >= 0) {
        $("#f"+focusTo).focus();
    }
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

var mouseDown = 0;

$(function () {
document.body.onmousedown = function () {
    mouseDown++;
}

document.body.onmouseup = function () {
    mouseDown--;
}

document.onclick = function (evt) {
    var src = window.event.srcElement;
    if (src.tagName == "IMG") {
        // image clicked; find contenteditable parent
        var p = src;
        while (p = p.parentNode) {
            if (p.className == "field") {
                $("#"+p.id).focus();
                break;
            }
        }
    }
}

});

</script></head><body>
<div id="fields"></div>
<div id="dupes"><a href="#" onclick="py.run('dupes');return false;">%s</a></div>
</body></html>
"""

def _filterHTML(html):
    doc = BeautifulSoup(html)
    # remove implicit regular font style from outermost element
    if doc.span:
        try:
            attrs = doc.span['style'].split(";")
        except (KeyError, TypeError):
            attrs = []
        if attrs:
            new = []
            for attr in attrs:
                sattr = attr.strip()
                if sattr and sattr not in ("font-style: normal", "font-weight: normal"):
                    new.append(sattr)
            doc.span['style'] = ";".join(new)
    # filter out implicit formatting from webkit
    for tag in doc("span", "Apple-style-span"):
        preserve = ""
        for item in tag['style'].split(";"):
            try:
                k, v = item.split(":")
            except ValueError:
                continue
            if k.strip() == "color" and not v.strip() == "rgb(0, 0, 0)":
                preserve += "color:%s;" % v
            if k.strip() in ("font-weight", "font-style"):
                preserve += item + ";"
        if preserve:
            # preserve colour attribute, delete implicit class
            tag['style'] = preserve
            del tag['class']
        else:
            # strip completely
            tag.replaceWithChildren()
    for tag in doc("font", "Apple-style-span"):
        # strip all but colour attr from implicit font tags
        if 'color' in dict(tag.attrs):
            for attr in tag.attrs:
                if attr != "color":
                    del tag[attr]
            # and apple class
            del tag['class']
        else:
            # remove completely
            tag.replaceWithChildren()
    # now images
    for tag in doc("img"):
        # turn file:/// links into relative ones
        try:
            if tag['src'].lower().startswith("file://"):
                tag['src'] = os.path.basename(tag['src'])
        except KeyError:
            # for some bizarre reason, mnemosyne removes src elements
            # from missing media
            pass
        # strip all other attributes, including implicit max-width
        for attr, val in tag.attrs:
            if attr != "src":
                del tag[attr]
    # strip superfluous elements
    for elem in "html", "head", "body", "meta":
        for tag in doc(elem):
            tag.replaceWithChildren()
    html = unicode(doc)
    return html

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
        self.currentField = 0
        # current card, for card layout
        self.card = None
        self.setupOuter()
        self.setupButtons()
        self.setupWeb()
        self.setupTags()
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
        if not self.plastiqueStyle:
            # plastique was removed in qt5
            self.plastiqueStyle = QStyleFactory.create("fusion")
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
        self.iconsBox.addItem(QSpacerItem(6,1, QSizePolicy.Fixed))
        b("layout", self.onCardLayout, _("Ctrl+L"),
          shortcut(_("Customize Cards (Ctrl+L)")),
          size=False, text=_("Cards..."), native=True, canDisable=False)
        # align to right
        self.iconsBox.addItem(QSpacerItem(20,1, QSizePolicy.Expanding))
        b("text_bold", self.toggleBold, _("Ctrl+B"), _("Bold text (Ctrl+B)"),
          check=True)
        b("text_italic", self.toggleItalic, _("Ctrl+I"), _("Italic text (Ctrl+I)"),
          check=True)
        b("text_under", self.toggleUnderline, _("Ctrl+U"),
          _("Underline text (Ctrl+U)"), check=True)
        b("text_super", self.toggleSuper, _("Ctrl+Shift+="),
          _("Superscript (Ctrl+Shift+=)"), check=True)
        b("text_sub", self.toggleSub, _("Ctrl+="),
          _("Subscript (Ctrl+=)"), check=True)
        b("text_clear", self.removeFormat, _("Ctrl+R"),
          _("Remove formatting (Ctrl+R)"))
        but = b("foreground", self.onForeground, _("F7"), text=" ")
        but.setToolTip(_("Set foreground colour (F7)"))
        self.setupForegroundButton(but)
        but = b("change_colour", self.onChangeCol, _("F8"),
          _("Change colour (F8)"), text=u"▾")
        but.setFixedWidth(12)
        but = b("cloze", self.onCloze, _("Ctrl+Shift+C"),
                _("Cloze deletion (Ctrl+Shift+C)"), text="[...]")
        but.setFixedWidth(24)
        s = self.clozeShortcut2 = QShortcut(
            QKeySequence(_("Ctrl+Alt+Shift+C")), self.parentWindow)
        s.connect(s, SIGNAL("activated()"), self.onCloze)
        # fixme: better image names
        b("mail-attachment", self.onAddMedia, _("F3"),
          _("Attach pictures/audio/video (F3)"))
        b("media-record", self.onRecSound, _("F5"), _("Record audio (F5)"))
        b("adv", self.onAdvanced, text=u"▾")
        s = QShortcut(QKeySequence("Ctrl+T, T"), self.widget)
        s.connect(s, SIGNAL("activated()"), self.insertLatex)
        s = QShortcut(QKeySequence("Ctrl+T, E"), self.widget)
        s.connect(s, SIGNAL("activated()"), self.insertLatexEqn)
        s = QShortcut(QKeySequence("Ctrl+T, M"), self.widget)
        s.connect(s, SIGNAL("activated()"), self.insertLatexMathEnv)
        s = QShortcut(QKeySequence("Ctrl+Shift+X"), self.widget)
        s.connect(s, SIGNAL("activated()"), self.onHtmlEdit)
        # tags
        s = QShortcut(QKeySequence("Ctrl+Shift+T"), self.widget)
        s.connect(s, SIGNAL("activated()"), lambda: self.tags.setFocus())
        runHook("setupEditorButtons", self)

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
            # reverse the url quoting we added to get images to display
            txt = unicode(urllib2.unquote(
                txt.encode("utf8")), "utf8", "replace")
            self.note.fields[self.currentField] = txt
            if not self.addMode:
                self.note.flush()
                self.mw.requireReset()
            if type == "blur":
                self.disableButtons()
                # run any filters
                if runFilter(
                    "editFocusLost", False, self.note, self.currentField):
                    # something updated the note; schedule reload
                    def onUpdate():
                        self.stealFocus = True
                        self.loadNote()
                        self.checkValid()
                    self.mw.progress.timer(100, onUpdate, False)
                else:
                    self.checkValid()
            else:
                runHook("editTimer", self.note)
                self.checkValid()
        # focused into field?
        elif str.startswith("focus"):
            (type, num) = str.split(":", 1)
            self.enableButtons()
            self.currentField = int(num)
            runHook("editFocusGained", self.note, self.currentField)
        # state buttons changed?
        elif str.startswith("state"):
            (cmd, txt) = str.split(":", 1)
            r = json.loads(txt)
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
        return _filterHTML(txt)

    # Setting/unsetting the current note
    ######################################################################

    def _loadFinished(self, w):
        self._loaded = True
        if self.note:
            self.loadNote()

    def setNote(self, note, hide=True, focus=False):
        "Make NOTE the current note."
        self.note = note
        self.currentField = 0
        self.disableButtons()
        if focus:
            self.stealFocus = True
        # change timer
        if self.note:
            self.web.setHtml(_html % (
                getBase(self.mw.col), anki.js.jquery,
                _("Show Duplicates")), loadCB=self._loadFinished)
            self.updateTags()
            self.updateKeyboard()
        else:
            self.hideCompleters()
            if hide:
                self.widget.hide()

    def loadNote(self):
        if not self.note:
            return
        if self.stealFocus:
            field = self.currentField
        else:
            field = -1
        if not self._loaded:
            # will be loaded when page is ready
            return
        data = []
        for fld, val in self.note.items():
            data.append((fld, self.mw.col.media.escapeImages(val)))
        self.web.eval("setFields(%s, %d);" % (
            json.dumps(data), field))
        self.web.eval("setFonts(%s);" % (
            json.dumps(self.fonts())))
        self.checkValid()
        self.widget.show()
        if self.stealFocus:
            self.web.setFocus()
            self.stealFocus = False

    def focus(self):
        self.web.setFocus()

    def fonts(self):
        return [(f['font'], f['size'], f['rtl'])
                for f in self.note.model()['flds']]

    def saveNow(self):
        "Must call this before adding cards, closing dialog, etc."
        if not self.note:
            return
        self.saveTags()
        if self.mw.app.focusWidget() != self.web:
            # if no fields are focused, there's nothing to save
            return
        # move focus out of fields and save tags
        self.parentWindow.setFocus()
        # and process events so any focus-lost hooks fire
        self.mw.app.processEvents()

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
        self.web.eval("setBackgrounds(%s);" % json.dumps(cols))

    def showDupes(self):
        contents = stripHTMLMedia(self.note.fields[0])
        browser = aqt.dialogs.open("Browser", self.mw)
        browser.form.searchEdit.lineEdit().setText(
            '"dupe:%s,%s"' % (self.note.model()['id'],
                              contents))
        browser.onSearch()

    def fieldsAreBlank(self):
        if not self.note:
            return True
        m = self.note.model()
        for c, f in enumerate(self.note.fields):
            if f and not m['flds'][c]['sticky']:
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
        # focus field so it's saved
        self.web.setFocus()
        self.web.eval("focusField(%d);" % self.currentField)

    # Tag handling
    ######################################################################

    def setupTags(self):
        import aqt.tagedit
        g = QGroupBox(self.widget)
        g.setFlat(True)
        tb = QGridLayout()
        tb.setSpacing(12)
        tb.setMargin(6)
        # tags
        l = QLabel(_("Tags"))
        tb.addWidget(l, 1, 0)
        self.tags = aqt.tagedit.TagEdit(self.widget)
        self.tags.connect(self.tags, SIGNAL("lostFocus"),
                          self.saveTags)
        self.tags.setToolTip(shortcut(_("Jump to tags with Ctrl+Shift+T")))
        tb.addWidget(self.tags, 1, 1)
        g.setLayout(tb)
        self.outerLayout.addWidget(g)

    def updateTags(self):
        if self.tags.col != self.mw.col:
            self.tags.setCol(self.mw.col)
        if not self.tags.text() or not self.addMode:
            self.tags.setText(self.note.stringTags().strip())

    def saveTags(self):
        if not self.note:
            return
        self.note.tags = self.mw.col.tags.canonify(
            self.mw.col.tags.split(self.tags.text()))
        self.tags.setText(self.mw.col.tags.join(self.note.tags).strip())
        if not self.addMode:
            self.note.flush()
        runHook("tagsUpdated", self.note)

    def saveAddModeVars(self):
        if self.addMode:
            # save tags to model
            m = self.note.model()
            m['tags'] = self.note.tags
            self.mw.col.models.save(m)

    def hideCompleters(self):
        self.tags.hideCompleter()

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
        if '{{cloze:' not in self.note.model()['tmpls'][0]['qfmt']:
            if self.addMode:
                tooltip(_("Warning, cloze deletions will not work until "
                "you switch the type at the top to Cloze."))
            else:
                showInfo(_("""\
To make a cloze deletion on an existing note, you need to change it \
to a cloze type first, via Edit>Change Note Type."""))
                return
        # find the highest existing cloze
        highest = 0
        for name, val in self.note.items():
            m = re.findall("\{\{c(\d+)::", val)
            if m:
                highest = max(highest, sorted([int(x) for x in m])[-1])
        # reuse last?
        if not self.mw.app.keyboardModifiers() & Qt.AltModifier:
            highest += 1
        # must start at 1
        highest = max(1, highest)
        self.web.eval("wrap('{{c%d::', '}}');" % highest)

    # Foreground colour
    ######################################################################

    def setupForegroundButton(self, but):
        self.foregroundFrame = QFrame()
        self.foregroundFrame.setAutoFillBackground(True)
        self.foregroundFrame.setFocusPolicy(Qt.NoFocus)
        self.fcolour = self.mw.pm.profile.get("lastColour", "#00f")
        self.onColourChanged()
        hbox = QHBoxLayout()
        hbox.addWidget(self.foregroundFrame)
        hbox.setMargin(5)
        but.setLayout(hbox)

    # use last colour
    def onForeground(self):
        self._wrapWithColour(self.fcolour)

    # choose new colour
    def onChangeCol(self):
        new = QColorDialog.getColor(QColor(self.fcolour), None)
        # native dialog doesn't refocus us for some reason
        self.parentWindow.activateWindow()
        if new.isValid():
            self.fcolour = new.name()
            self.onColourChanged()
            self._wrapWithColour(self.fcolour)

    def _updateForegroundButton(self):
        self.foregroundFrame.setPalette(QPalette(QColor(self.fcolour)))

    def onColourChanged(self):
        self._updateForegroundButton()
        self.mw.pm.profile['lastColour'] = self.fcolour

    def _wrapWithColour(self, colour):
        self.web.eval("setFormat('forecolor', '%s')" % colour)

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
        self.parentWindow.activateWindow()

    def addMedia(self, path, canDelete=False):
        html = self._addMedia(path, canDelete)
        self.web.eval("setFormat('inserthtml', %s);" % json.dumps(html))

    def _addMedia(self, path, canDelete=False):
        "Add to media folder and return basename."
        # copy to media folder
        name = self.mw.col.media.addFile(path)
        # remove original?
        if canDelete and self.mw.pm.profile['deleteMedia']:
            if os.path.abspath(name) != os.path.abspath(path):
                try:
                    os.unlink(path)
                except:
                    pass
        # return a local html link
        ext = name.split(".")[-1].lower()
        if ext in pics:
            name = urllib.quote(name.encode("utf8"))
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
        a.setShortcut(QKeySequence("Ctrl+T, T"))
        a.connect(a, SIGNAL("triggered()"), self.insertLatex)
        a = m.addAction(_("LaTeX equation"))
        a.setShortcut(QKeySequence("Ctrl+T, E"))
        a.connect(a, SIGNAL("triggered()"), self.insertLatexEqn)
        a = m.addAction(_("LaTeX math env."))
        a.setShortcut(QKeySequence("Ctrl+T, M"))
        a.connect(a, SIGNAL("triggered()"), self.insertLatexMathEnv)
        a = m.addAction(_("Edit HTML"))
        a.setShortcut(QKeySequence("Ctrl+Shift+X"))
        a.connect(a, SIGNAL("triggered()"), self.onHtmlEdit)
        m.exec_(QCursor.pos())

    # LaTeX
    ######################################################################

    def insertLatex(self):
        self.web.eval("wrap('[latex]', '[/latex]');")

    def insertLatexEqn(self):
        self.web.eval("wrap('[$]', '[/$]');")

    def insertLatexMathEnv(self):
        self.web.eval("wrap('[$$]', '[/$$]');")

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
        if evt.matches(QKeySequence.Paste):
            self.onPaste()
            return evt.accept()
        elif evt.matches(QKeySequence.Copy):
            self.onCopy()
            return evt.accept()
        elif evt.matches(QKeySequence.Cut):
            self.onCut()
            return evt.accept()
        QWebView.keyPressEvent(self, evt)

    def onCut(self):
        self.triggerPageAction(QWebPage.Cut)
        self._flagAnkiText()

    def onCopy(self):
        self.triggerPageAction(QWebPage.Copy)
        self._flagAnkiText()

    def onPaste(self):
        mime = self.prepareClip()
        self.triggerPageAction(QWebPage.Paste)
        self.restoreClip(mime)

    def mouseReleaseEvent(self, evt):
        if not isMac and not isWin and evt.button() == Qt.MidButton:
            # middle click on x11; munge the clipboard before standard
            # handling
            mime = self.prepareClip(mode=QClipboard.Selection)
            AnkiWebView.mouseReleaseEvent(self, evt)
            self.restoreClip(mime, mode=QClipboard.Selection)
        else:
            AnkiWebView.mouseReleaseEvent(self, evt)

    def focusInEvent(self, evt):
        window = False
        if evt.reason() in (Qt.ActiveWindowFocusReason, Qt.PopupFocusReason):
            # editor area got focus again; need to tell js not to adjust cursor
            self.eval("mouseDown++;")
            window = True
        AnkiWebView.focusInEvent(self, evt)
        if evt.reason() == Qt.TabFocusReason:
            self.eval("focusField(0);")
        elif evt.reason() == Qt.BacktabFocusReason:
            n = len(self.editor.note.fields) - 1
            self.eval("focusField(%d);" % n)
        elif window:
            self.eval("mouseDown--;")

    def dropEvent(self, evt):
        oldmime = evt.mimeData()
        # coming from this program?
        if evt.source():
            if oldmime.hasHtml():
                mime = QMimeData()
                mime.setHtml(_filterHTML(oldmime.html()))
            else:
                # old qt on linux won't give us html when dragging an image;
                # in that case just do the default action (which is to ignore
                # the drag)
                return AnkiWebView.dropEvent(self, evt)
        else:
            mime = self._processMime(oldmime)
        # create a new event with the new mime data and run it
        new = QDropEvent(evt.pos(), evt.possibleActions(), mime,
                         evt.mouseButtons(), evt.keyboardModifiers())
        evt.accept()
        QWebView.dropEvent(self, new)
        # tell the drop target to take focus so the drop contents are saved
        self.eval("dropTarget.focus();")
        self.setFocus()

    def prepareClip(self, mode=QClipboard.Clipboard):
        clip = self.editor.mw.app.clipboard()
        mime = clip.mimeData(mode=mode)
        if mime.hasHtml() and mime.html().startswith("<!--anki-->"):
            # pasting from another field, filter extraneous webkit formatting
            html = mime.html()[11:]
            html = _filterHTML(html)
            mime.setHtml(html)
            return
        self.saveClip(mode=mode)
        mime = self._processMime(mime)
        clip.setMimeData(mime, mode=mode)

    def restoreClip(self, mime, mode=QClipboard.Clipboard):
        if not mime:
            return
        clip = self.editor.mw.app.clipboard()
        clip.setMimeData(mime, mode=mode)

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
        return n

    def _processMime(self, mime):
        # print "html=%s image=%s urls=%s txt=%s" % (
        #     mime.hasHtml(), mime.hasImage(), mime.hasUrls(), mime.hasText())
        # print "html", mime.html()
        # print "urls", mime.urls()
        # print "text", mime.text()
        if mime.hasUrls():
            return self._processUrls(mime)
        elif mime.hasText() and (self.strip or not mime.hasHtml()):
            return self._processText(mime)
        # we currently aren't able to extract images from html, so we prioritize
        # images over html in cases where we have both. this is a hack until
        # issue 92 is implemented
        elif mime.hasImage():
            return self._processImage(mime)
        elif mime.hasHtml():
            return self._processHtml(mime)
        else:
            # nothing
            return QMimeData()

    def _processUrls(self, mime):
        url = mime.urls()[0].toString()
        # chrome likes to give us the URL twice with a \n
        url = url.splitlines()[0]
        link = self._localizedMediaLink(url)
        mime = QMimeData()
        if link:
            mime.setHtml(link)
        return mime

    def _localizedMediaLink(self, url):
        l = url.lower()
        for suffix in pics+audio:
            if l.endswith(suffix):
                return self._retrieveURL(url)
        # not a supported type; return link verbatim
        return url

    def _processText(self, mime):
        txt = unicode(mime.text())
        l = txt.lower()
        html = None
        # if the user is pasting an image or sound link, convert it to local
        if l.startswith("http://") or l.startswith("https://") or l.startswith("file://"):
            txt = txt.split("\r\n")[0]
            html = self._localizedMediaLink(txt)
            if not html:
                return QMimeData()
            if html == txt:
                # wasn't of a supported media type; don't change
                html = None
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
            html = _filterHTML(html)
        mime = QMimeData()
        mime.setHtml(html)
        return mime

    def _processImage(self, mime):
        im = QImage(mime.imageData())
        uname = namedtmp("paste-%d" % im.cacheKey())
        if self.editor.mw.pm.profile.get("pastePNG", False):
            ext = ".png"
            im.save(uname+ext, None, 50)
        else:
            ext = ".jpg"
            im.save(uname+ext, None, 80)
        # invalid image?
        if not os.path.exists(uname+ext):
            return QMimeData()
        mime = QMimeData()
        mime.setHtml(self.editor._addMedia(uname+ext))
        return mime

    def _retrieveURL(self, url):
        # is it media?
        ext = url.split(".")[-1].lower()
        if ext not in pics and ext not in audio:
            return
        if url.lower().startswith("file://"):
            url = url.replace("%", "%25")
            url = url.replace("#", "%23")
        # fetch it into a temporary folder
        self.editor.mw.progress.start(
            immediate=True, parent=self.editor.parentWindow)
        try:
            req = urllib2.Request(url, None, {
                'User-Agent': 'Mozilla/5.0 (compatible; Anki)'})
            filecontents = urllib2.urlopen(req).read()
        except urllib2.URLError, e:
            showWarning(self.errtxt % e)
            return
        finally:
            self.editor.mw.progress.finish()
        path = unicode(urllib2.unquote(url.encode("utf8")), "utf8")
        for badChar in "#%\"":
            path = path.replace(badChar, "")
        path = namedtmp(os.path.basename(path))
        file = open(path, "wb")
        file.write(filecontents)
        file.close()
        return self.editor._addMedia(path)

    def _flagAnkiText(self):
        # add a comment in the clipboard html so we can tell text is copied
        # from us and doesn't need to be stripped
        clip = self.editor.mw.app.clipboard()
        mime = clip.mimeData()
        if not mime.hasHtml():
            return
        html = mime.html()
        mime.setHtml("<!--anki-->" + mime.html())

    def contextMenuEvent(self, evt):
        m = QMenu(self)
        a = m.addAction(_("Cut"))
        a.connect(a, SIGNAL("triggered()"), self.onCut)
        a = m.addAction(_("Copy"))
        a.connect(a, SIGNAL("triggered()"), self.onCopy)
        a = m.addAction(_("Paste"))
        a.connect(a, SIGNAL("triggered()"), self.onPaste)
        m.popup(QCursor.pos())
