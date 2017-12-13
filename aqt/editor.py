# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import re
import os
import urllib.request, urllib.error, urllib.parse
import ctypes
import urllib.request, urllib.parse, urllib.error
import warnings
import html
import mimetypes
import base64
import unicodedata

from anki.lang import _
from aqt.qt import *
from anki.utils import stripHTML, isWin, isMac, namedtmp, json, stripHTMLMedia, \
    checksum
import anki.sound
from anki.hooks import runHook, runFilter
from aqt.sound import getAudio
from aqt.webview import AnkiWebView
from aqt.utils import shortcut, showInfo, showWarning, getFile, \
    openHelp, tooltip, downArrow
import aqt
from bs4 import BeautifulSoup
import requests

pics = ("jpg", "jpeg", "png", "tif", "tiff", "gif", "svg", "webp")
audio =  ("wav", "mp3", "ogg", "flac", "mp4", "swf", "mov", "mpeg", "mkv", "m4a", "3gp", "spx", "oga")

_html = """
<style>
html { background: %s; }
#topbuts { background: %s; }
</style>
<div id="topbuts">%s</div>
<div id="fields"></div>
<div id="dupes" style="display:none;"><a href="#" onclick="pycmd('dupes');return false;">%s</a></div>
"""

# caller is responsible for resetting note on reset
class Editor:
    def __init__(self, mw, widget, parentWindow, addMode=False):
        self.mw = mw
        self.widget = widget
        self.parentWindow = parentWindow
        self.note = None
        self.addMode = addMode
        self.currentField = None
        # current card, for card layout
        self.card = None
        self.setupOuter()
        self.setupWeb()
        self.setupShortcuts()
        self.setupTags()

    # Initial setup
    ############################################################

    def setupOuter(self):
        l = QVBoxLayout()
        l.setContentsMargins(0,0,0,0)
        l.setSpacing(0)
        self.widget.setLayout(l)
        self.outerLayout = l

    def setupWeb(self):
        self.web = EditorWebView(self.widget, self)
        self.web.title = "editor"
        self.web.allowDrops = True
        self.web.onBridgeCmd = self.onBridgeCmd
        self.outerLayout.addWidget(self.web, 1)

        righttopbtns = list()
        righttopbtns.append(self._addButton('text_bold', 'bold', _("Bold text (Ctrl+B)"), id='bold'))
        righttopbtns.append(self._addButton('text_italic', 'italic', _("Italic text (Ctrl+I)"), id='italic'))
        righttopbtns.append(self._addButton('text_under', 'underline', _("Underline text (Ctrl+U)"), id='underline'))
        righttopbtns.append(self._addButton('text_super', 'super', _("Superscript (Ctrl++)"), id='superscript'))
        righttopbtns.append(self._addButton('text_sub', 'sub', _("Subscript (Ctrl+=)"), id='subscript'))
        righttopbtns.append(self._addButton('text_clear', 'clear', _("Remove formatting (Ctrl+R)")))
        # The color selection buttons do not use an icon so the HTML must be specified manually
        tip = _("Set foreground colour (F7)")
        righttopbtns.append(f'''<button tabindex=-1 class=linkb title="{tip}"
            type="button" onclick="pycmd('colour');return false;">
            <div id=forecolor style="display:inline-block; background: #000;border-radius: 5px;"
            class=topbut></div></button>''')
        tip = _("Change colour (F8)")
        righttopbtns.append(f'''<button tabindex=-1 class=linkb title="{tip}"
            type="button" onclick="pycmd('changeCol');return false;">
            <div style="display:inline-block; border-radius: 5px;"
            class="topbut rainbow"></div></button>''')
        righttopbtns.append(self._addButton('text_cloze', 'cloze', _("Cloze deletion (Ctrl+Shift+C)")))
        righttopbtns.append(self._addButton('paperclip', 'attach', _("Attach pictures/audio/video (F3)")))
        righttopbtns.append(self._addButton('media-record', 'record', _("Record audio (F5)")))
        righttopbtns.append(self._addButton('more', 'more'))
        righttopbtns = runFilter("setupEditorButtons", righttopbtns, self)
        topbuts = """
            <div id="topbutsleft" style="float:left;">
                <button onclick="pycmd('fields')">%(flds)s...</button>
                <button onclick="pycmd('cards')">%(cards)s...</button>
            </div>
            <div id="topbutsright" style="float:right;">
                %(rightbts)s
            </div>
        """ % dict(flds=_("Fields"), cards=_("Cards"), rightbts="".join(righttopbtns))
        bgcol = self.mw.app.palette().window().color().name()
        # then load page
        self.web.stdHtml(_html % (
            bgcol, bgcol,
            topbuts,
            _("Show Duplicates")),
                         css=["editor.css"],
                         js=["jquery.js", "editor.js"])

    # Top buttons
    ######################################################################

    def resourceToData(self, path):
        """Convert a file (specified by a path) into a data URI."""
        if not os.path.exists(path):
            raise FileNotFoundError
        mime, _ = mimetypes.guess_type(path)
        with open(path, 'rb') as fp:
            data = fp.read()
            data64 = b''.join(base64.encodestring(data).splitlines())
            return 'data:%s;base64,%s' % (mime, data64.decode('ascii'))


    def addButton(self, icon, cmd, func, tip="", label="", 
                  id=None, toggleable=False, keys=None, disables=True):
        """Assign func to bridge cmd, register shortcut, return button"""
        if cmd not in self._links:
            self._links[cmd] = func
        if keys:
            QShortcut(QKeySequence(keys), self.widget,
                      activated = lambda s=self: func(s))
        btn = self._addButton(icon, cmd, tip=tip, label=label,
                              id=id, toggleable=toggleable, disables=disables)
        return btn

    def _addButton(self, icon, cmd, tip="", label="", id=None, toggleable=False,
                   disables=True):
        if icon:
            if os.path.isabs(icon):
                iconstr = self.resourceToData(icon)
            else:
                iconstr = "/_anki/imgs/{}.png".format(icon)
            imgelm = '''<img class=topbut src="{}">'''.format(iconstr)
        else:
            imgelm = ""
        if label or not imgelm:
            labelelm = '''<span class=blabel>{}</span>'''.format(label or cmd)
        else:
            labelelm = ""
        if id:
            idstr = 'id={}'.format(id)
        else:
            idstr = ""
        if toggleable:
            toggleScript = 'toggleEditorButton(this);'
        else:
            toggleScript = ''
        tip = shortcut(tip)
        theclass = "linkb"
        if not disables:
            theclass += " perm"
        return ('''<button tabindex=-1 {id} class="{theclass}" type="button" title="{tip}"'''
                ''' onclick="pycmd('{cmd}');{togglesc}return false;">'''
                '''{imgelm}{labelelm}</button>'''.format(
                        imgelm=imgelm, cmd=cmd, tip=tip, labelelm=labelelm, id=idstr,
                        togglesc=toggleScript, theclass=theclass)
                )

    def setupShortcuts(self):
        # if a third element is provided, enable shortcut even when no field selected
        cuts = [
            ("Ctrl+L", self.onCardLayout, True),
            ("Ctrl+B", self.toggleBold),
            ("Ctrl+I", self.toggleItalic),
            ("Ctrl+U", self.toggleUnderline),
            ("Ctrl++", self.toggleSuper),
            ("Ctrl+=", self.toggleSub),
            ("Ctrl+R", self.removeFormat),
            ("F7", self.onForeground),
            ("F8", self.onChangeCol),
            ("Ctrl+Shift+C", self.onCloze),
            ("Ctrl+Shift+Alt+C", self.onCloze),
            ("F3", self.onAddMedia),
            ("F5", self.onRecSound),
            ("Ctrl+T, T", self.insertLatex),
            ("Ctrl+T, E", self.insertLatexEqn),
            ("Ctrl+T, M", self.insertLatexMathEnv),
            ("Ctrl+M, M", self.insertMathjaxInline),
            ("Ctrl+M, E", self.insertMathjaxBlock),
            ("Ctrl+Shift+X", self.onHtmlEdit),
            ("Ctrl+Shift+T", self.onFocusTags, True)
        ]
        runHook("setupEditorShortcuts", cuts, self)
        for row in cuts:
            if len(row) == 2:
                keys, fn = row
                fn = self._addFocusCheck(fn)
            else:
                keys, fn, _ = row
            QShortcut(QKeySequence(keys), self.widget, activated=fn)

    def _addFocusCheck(self, fn):
        def checkFocus():
            if self.currentField is None:
                return
            fn()
        return checkFocus

    def onFields(self):
        self.saveNow(self._onFields)

    def _onFields(self):
        from aqt.fields import FieldDialog
        FieldDialog(self.mw, self.note, parent=self.parentWindow)

    def onCardLayout(self):
        self.saveNow(self._onCardLayout)

    def _onCardLayout(self):
        from aqt.clayout import CardLayout
        if self.card:
            ord = self.card.ord
        else:
            ord = 0
        CardLayout(self.mw, self.note, ord=ord, parent=self.parentWindow,
               addMode=self.addMode)
        if isWin:
            self.parentWindow.activateWindow()

    # JS->Python bridge
    ######################################################################

    def onBridgeCmd(self, cmd):
        if not self.note or not runHook:
            # shutdown
            return
        # focus lost or key/button pressed?
        if cmd.startswith("blur") or cmd.startswith("key"):
            (type, ord, txt) = cmd.split(":", 2)
            ord = int(ord)
            txt = urllib.parse.unquote(txt)
            txt = unicodedata.normalize("NFC", txt)
            txt = self.mungeHTML(txt)
            # misbehaving apps may include a null byte in the text
            txt = txt.replace("\x00", "")
            # reverse the url quoting we added to get images to display
            txt = self.mw.col.media.escapeImages(txt, unescape=True)
            self.note.fields[ord] = txt
            if not self.addMode:
                self.note.flush()
                self.mw.requireReset()
            if type == "blur":
                self.currentField = None
                # run any filters
                if runFilter(
                    "editFocusLost", False, self.note, ord):
                    # something updated the note; update it after a subsequent focus
                    # event has had time to fire
                    self.mw.progress.timer(100, self.loadNoteKeepingFocus, False)
                else:
                    self.checkValid()
            else:
                runHook("editTimer", self.note)
                self.checkValid()
        # focused into field?
        elif cmd.startswith("focus"):
            (type, num) = cmd.split(":", 1)
            self.currentField = int(num)
            runHook("editFocusGained", self.note, self.currentField)
        elif cmd in self._links:
            self._links[cmd](self)
        else:
            print("uncaught cmd", cmd)

    def mungeHTML(self, txt):
        txt = re.sub(r"<br>$", "", txt)
        return txt

    # Setting/unsetting the current note
    ######################################################################

    def setNote(self, note, hide=True, focusTo=None):
        "Make NOTE the current note."
        self.note = note
        self.currentField = None
        if self.note:
            self.loadNote(focusTo=focusTo)
        else:
            self.hideCompleters()
            if hide:
                self.widget.hide()

    def loadNoteKeepingFocus(self):
        self.loadNote(self.currentField)

    def loadNote(self, focusTo=None):
        if not self.note:
            return

        data = []
        for fld, val in list(self.note.items()):
            data.append((fld, self.mw.col.media.escapeImages(val)))
        self.widget.show()
        self.updateTags()

        def oncallback(arg):
            if not self.note:
                return
            self.setupForegroundButton()
            self.checkValid()
            if focusTo is not None:
                self.web.setFocus()
            runHook("loadNote", self)

        self.web.evalWithCallback("setFields(%s); setFonts(%s); focusField(%s)" % (
            json.dumps(data),
            json.dumps(self.fonts()), json.dumps(focusTo)),
                                  oncallback)

    def fonts(self):
        return [(f['font'], f['size'], f['rtl'])
                for f in self.note.model()['flds']]

    def saveNow(self, callback):
        "Save unsaved edits then call callback()."
        if not self.note:
             # calling code may not expect the callback to fire immediately
            self.mw.progress.timer(10, callback, False)
            return
        self.saveTags()
        self.web.evalWithCallback("saveNow()", lambda res: callback())

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
        browser.onSearchActivated()

    def fieldsAreBlank(self):
        if not self.note:
            return True
        m = self.note.model()
        for c, f in enumerate(self.note.fields):
            if f and not m['flds'][c]['sticky']:
                return False
        return True

    def cleanup(self):
        self.setNote(None)
        # prevent any remaining evalWithCallback() events from firing after C++ object deleted
        self.web = None

    # HTML editing
    ######################################################################

    def onHtmlEdit(self):
        field = self.currentField
        self.saveNow(lambda: self._onHtmlEdit(field))

    def _onHtmlEdit(self, field):
        d = QDialog(self.widget)
        form = aqt.forms.edithtml.Ui_Dialog()
        form.setupUi(d)
        form.buttonBox.helpRequested.connect(lambda: openHelp("editor"))
        form.textEdit.setPlainText(self.note.fields[field])
        form.textEdit.moveCursor(QTextCursor.End)
        d.exec_()
        html = form.textEdit.toPlainText()
        # filter html through beautifulsoup so we can strip out things like a
        # leading </div>
        with warnings.catch_warnings() as w:
            warnings.simplefilter('ignore', UserWarning)
            html = str(BeautifulSoup(html, "html.parser"))
        self.note.fields[field] = html
        self.loadNote(focusTo=field)

    # Tag handling
    ######################################################################

    def setupTags(self):
        import aqt.tagedit
        g = QGroupBox(self.widget)
        g.setFlat(True)
        tb = QGridLayout()
        tb.setSpacing(12)
        tb.setContentsMargins(6,6,6,6)
        # tags
        l = QLabel(_("Tags"))
        tb.addWidget(l, 1, 0)
        self.tags = aqt.tagedit.TagEdit(self.widget)
        self.tags.lostFocus.connect(self.saveTags)
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
        tagsTxt = unicodedata.normalize("NFC", self.tags.text())
        self.note.tags = self.mw.col.tags.canonify(
            self.mw.col.tags.split(tagsTxt))
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

    def onFocusTags(self):
        self.tags.setFocus()

    # Format buttons
    ######################################################################

    def toggleBold(self):
        self.web.eval("setFormat('bold');")

    def toggleItalic(self):
        self.web.eval("setFormat('italic');")

    def toggleUnderline(self):
        self.web.eval("setFormat('underline');")

    def toggleSuper(self):
        self.web.eval("setFormat('superscript');")

    def toggleSub(self):
        self.web.eval("setFormat('subscript');")

    def removeFormat(self):
        self.web.eval("setFormat('removeFormat');")

    def onCloze(self):
        # check that the model is set up for cloze deletion
        if not re.search('{{(.*:)*cloze:',self.note.model()['tmpls'][0]['qfmt']):
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
        for name, val in list(self.note.items()):
            m = re.findall(r"\{\{c(\d+)::", val)
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

    def setupForegroundButton(self):
        self.fcolour = self.mw.pm.profile.get("lastColour", "#00f")
        self.onColourChanged()

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
        self.web.eval("setFGButton('%s')" % self.fcolour)

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
               "*.mkv *.ogx *.ogv *.oga *.flv *.swf *.flac *.webp *.m4a)")
        def accept(file):
            self.addMedia(file, canDelete=True)
        file = getFile(self.widget, _("Add Media"), accept, key, key="media")
        self.parentWindow.activateWindow()

    def addMedia(self, path, canDelete=False):
        html = self._addMedia(path, canDelete)
        self.web.eval("setFormat('inserthtml', %s);" % json.dumps(html))

    def _addMedia(self, path, canDelete=False):
        "Add to media folder and return local img or sound tag."
        # copy to media folder
        fname = self.mw.col.media.addFile(path)
        # remove original?
        if canDelete and self.mw.pm.profile['deleteMedia']:
            if os.path.abspath(fname) != os.path.abspath(path):
                try:
                    os.unlink(path)
                except:
                    pass
        # return a local html link
        return self.fnameToLink(fname)

    def onRecSound(self):
        try:
            file = getAudio(self.widget)
        except Exception as e:
            showWarning(_(
                "Couldn't record audio. Have you installed lame and sox?") +
                        "\n\n" + repr(str(e)))
            return
        if file:
            self.addMedia(file)

    # Media downloads
    ######################################################################

    def urlToLink(self, url):
        fname = self.urlToFile(url)
        if not fname:
            return url
        return self.fnameToLink(fname)

    def fnameToLink(self, fname):
        ext = fname.split(".")[-1].lower()
        if ext in pics:
            name = urllib.parse.quote(fname.encode("utf8"))
            return '<img src="%s">' % name
        else:
            anki.sound.clearAudioQueue()
            anki.sound.play(fname)
            return '[sound:%s]' % fname

    def urlToFile(self, url):
        l = url.lower()
        for suffix in pics+audio:
            if l.endswith("." + suffix):
                return self._retrieveURL(url)
        # not a supported type
        return

    def isURL(self, s):
        s = s.lower()
        return (s.startswith("http://")
            or s.startswith("https://")
            or s.startswith("ftp://")
            or s.startswith("file://"))

    def _retrieveURL(self, url):
        "Download file into media folder and return local filename or None."
        # urllib doesn't understand percent-escaped utf8, but requires things like
        # '#' to be escaped.
        url = urllib.parse.unquote(url)
        if url.lower().startswith("file://"):
            url = url.replace("%", "%25")
            url = url.replace("#", "%23")
            local = True
        else:
            local = False
        # fetch it into a temporary folder
        self.mw.progress.start(
            immediate=True, parent=self.parentWindow)
        ct = None
        try:
            if local:
                req = urllib.request.Request(url, None, {
                    'User-Agent': 'Mozilla/5.0 (compatible; Anki)'})
                filecontents = urllib.request.urlopen(req).read()
            else:
                r = requests.get(url, timeout=30)
                if r.status_code != 200:
                    showWarning(_("Unexpected response code: %s") % r.status_code)
                    return
                filecontents = r.content
                ct = r.headers.get("content-type")
        except urllib.error.URLError as e:
            showWarning(_("An error occurred while opening %s") % e)
            return
        except requests.exceptions.RequestException as e:
            showWarning(_("An error occurred while opening %s") % e)
            return
        finally:
            self.mw.progress.finish()
        # strip off any query string
        url = re.sub(r"\?.*?$", "", url)
        path = urllib.parse.unquote(url)
        return self.mw.col.media.writeData(path, filecontents, typeHint=ct)

    # Paste/drag&drop
    ######################################################################

    removeTags = ["script", "iframe", "object", "style"]

    def _pastePreFilter(self, html, internal):
        with warnings.catch_warnings() as w:
            warnings.simplefilter('ignore', UserWarning)
            doc = BeautifulSoup(html, "html.parser")

        if not internal:
            for tag in self.removeTags:
                for node in doc(tag):
                    node.decompose()

            # convert p tags to divs
            for node in doc("p"):
                node.name = "div"

        for tag in doc("img"):
            try:
                src = tag['src']
            except KeyError:
                # for some bizarre reason, mnemosyne removes src elements
                # from missing media
                continue

            # in internal pastes, rewrite mediasrv references to relative
            if internal:
                m = re.match(r"http://127.0.0.1:\d+/(.*)$", src)
                if m:
                    tag['src'] = m.group(1)
            else:
                # in external pastes, download remote media
                if self.isURL(src):
                    fname = self._retrieveURL(src)
                    if fname:
                        tag['src'] = fname

        html = str(doc)
        return html

    def doPaste(self, html, internal):
        html = self._pastePreFilter(html, internal)
        extended = self.mw.app.queryKeyboardModifiers() & Qt.ShiftModifier
        if extended:
            extended = "true"
        else:
            extended = "false"
        self.web.eval("pasteHTML(%s, %s, %s);" % (
            json.dumps(html), json.dumps(internal), extended))

    def doDrop(self, html, internal):
        self.web.evalWithCallback("makeDropTargetCurrent();",
                                  lambda _: self.doPaste(html, internal))

    def onPaste(self):
        self.web.onPaste()

    def onCutOrCopy(self):
        self.web.flagAnkiText()

    # Advanced menu
    ######################################################################

    def onAdvanced(self):
        m = QMenu(self.mw)
        a = m.addAction(_("MathJax inline"))
        a.triggered.connect(self.insertMathjaxInline)
        a.setShortcut(QKeySequence("Ctrl+M, M"))
        a = m.addAction(_("MathJax block"))
        a.triggered.connect(self.insertMathjaxBlock)
        a.setShortcut(QKeySequence("Ctrl+M, E"))
        a = m.addAction(_("LaTeX"))
        a.triggered.connect(self.insertLatex)
        a.setShortcut(QKeySequence("Ctrl+T, T"))
        a = m.addAction(_("LaTeX equation"))
        a.triggered.connect(self.insertLatexEqn)
        a.setShortcut(QKeySequence("Ctrl+T, E"))
        a = m.addAction(_("LaTeX math env."))
        a.triggered.connect(self.insertLatexMathEnv)
        a.setShortcut(QKeySequence("Ctrl+T, M"))
        a = m.addAction(_("Edit HTML"))
        a.triggered.connect(self.onHtmlEdit)
        a.setShortcut(QKeySequence("Ctrl+Shift+X"))
        m.exec_(QCursor.pos())

    # LaTeX
    ######################################################################

    def insertLatex(self):
        self.web.eval("wrap('[latex]', '[/latex]');")

    def insertLatexEqn(self):
        self.web.eval("wrap('[$]', '[/$]');")

    def insertLatexMathEnv(self):
        self.web.eval("wrap('[$$]', '[/$$]');")

    def insertMathjaxInline(self):
        self.web.eval("wrap('\\\\(', '\\\\)');")

    def insertMathjaxBlock(self):
        self.web.eval("wrap('\\\\[', '\\\\]');")

    # Links from HTML
    ######################################################################

    _links = dict(
        fields=onFields,
        cards=onCardLayout,
        bold=toggleBold,
        italic=toggleItalic,
        underline=toggleUnderline,
        super=toggleSuper,
        sub=toggleSub,
        clear=removeFormat,
        colour=onForeground,
        changeCol=onChangeCol,
        cloze=onCloze,
        attach=onAddMedia,
        record=onRecSound,
        more=onAdvanced,
        dupes=showDupes,
        paste=onPaste,
        cutOrCopy=onCutOrCopy,
    )

# Pasting, drag & drop, and keyboard layouts
######################################################################

class EditorWebView(AnkiWebView):

    def __init__(self, parent, editor):
        AnkiWebView.__init__(self)
        self.editor = editor
        self.strip = self.editor.mw.pm.profile['stripHTML']
        self.setAcceptDrops(True)
        self._markInternal = False
        clip = self.editor.mw.app.clipboard()
        clip.dataChanged.connect(self._onClipboardChange)

    def _onClipboardChange(self):
        if self._markInternal:
            self._markInternal = False
            self._flagAnkiText()

    def onCut(self):
        self.triggerPageAction(QWebEnginePage.Cut)

    def onCopy(self):
        self.triggerPageAction(QWebEnginePage.Copy)

    def onPaste(self):
        mime = self.editor.mw.app.clipboard().mimeData(mode=QClipboard.Clipboard)
        html, internal = self._processMime(mime)
        if not html:
            return
        self.editor.doPaste(html, internal)

    def dropEvent(self, evt):
        mime = evt.mimeData()

        if evt.source() and mime.hasHtml():
            # don't filter html from other fields
            html, internal = mime.html(), True
        else:
            html, internal = self._processMime(mime)

        if not html:
            return

        self.editor.doDrop(html, internal)

    # returns (html, isInternal)
    def _processMime(self, mime):
        # print("html=%s image=%s urls=%s txt=%s" % (
        #     mime.hasHtml(), mime.hasImage(), mime.hasUrls(), mime.hasText()))
        # print("html", mime.html())
        # print("urls", mime.urls())
        # print("text", mime.text())

        # try various content types in turn
        html, internal = self._processHtml(mime)
        if html:
            return html, internal
        for fn in (self._processUrls, self._processImage, self._processText):
            html = fn(mime)
            if html:
                return html, False
        return "", False

    def _processUrls(self, mime):
        if not mime.hasUrls():
            return

        url = mime.urls()[0].toString()
        # chrome likes to give us the URL twice with a \n
        url = url.splitlines()[0]
        return self.editor.urlToLink(url)

    def _processText(self, mime):
        if not mime.hasText():
            return

        txt = mime.text()

        # if the user is pasting an image or sound link, convert it to local
        if self.editor.isURL(txt):
            txt = txt.split("\r\n")[0]
            return self.editor.urlToLink(txt)

        # normal text; convert it to HTML
        txt = html.escape(txt)
        return txt

    def _processHtml(self, mime):
        if not mime.hasHtml():
            return None, False
        html = mime.html()

        # no filtering required for internal pastes
        if html.startswith("<!--anki-->"):
            return html[11:], True

        return html, False

    def _processImage(self, mime):
        im = QImage(mime.imageData())
        uname = namedtmp("paste")
        if self.editor.mw.pm.profile.get("pastePNG", False):
            ext = ".png"
            im.save(uname+ext, None, 50)
        else:
            ext = ".jpg"
            im.save(uname+ext, None, 80)

        # invalid image?
        path = uname+ext
        if not os.path.exists(path):
            return

        # hash and rename
        csum = checksum(open(path, "rb").read())
        newpath = "{}-{}{}".format(uname, csum, ext)
        if os.path.exists(newpath):
            os.unlink(newpath)
        os.rename(path, newpath)

        # add to media and return resulting html link
        return self.editor._addMedia(newpath)

    def flagAnkiText(self):
        # be ready to adjust when clipboard event fires
        self._markInternal = True

    def _flagAnkiText(self):
        # add a comment in the clipboard html so we can tell text is copied
        # from us and doesn't need to be stripped
        clip = self.editor.mw.app.clipboard()
        mime = clip.mimeData()
        if not mime.hasHtml():
            return
        html = mime.html()
        mime.setHtml("<!--anki-->" + html)
        clip.setMimeData(mime)

    def contextMenuEvent(self, evt):
        m = QMenu(self)
        a = m.addAction(_("Cut"))
        a.triggered.connect(self.onCut)
        a = m.addAction(_("Copy"))
        a.triggered.connect(self.onCopy)
        a = m.addAction(_("Paste"))
        a.triggered.connect(self.onPaste)
        runHook("EditorWebView.contextMenuEvent", self, m)
        m.popup(QCursor.pos())
