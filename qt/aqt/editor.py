# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import base64
import html
import itertools
import json
import mimetypes
import re
import urllib.error
import urllib.parse
import urllib.request
import warnings
from random import randrange
from typing import Any, Callable, Dict, List, Match, Optional, Tuple, cast

import bs4
import requests
from bs4 import BeautifulSoup

import aqt
import aqt.sound
from anki.cards import Card
from anki.collection import SearchNode
from anki.consts import MODEL_CLOZE
from anki.hooks import runFilter
from anki.httpclient import HttpClient
from anki.notes import Note
from anki.utils import checksum, isLin, isWin, namedtmp
from aqt import AnkiQt, colors, gui_hooks
from aqt.main import ResetReason
from aqt.qt import *
from aqt.sound import av_player
from aqt.theme import theme_manager
from aqt.utils import (
    TR,
    HelpPage,
    disable_help_button,
    getFile,
    openHelp,
    qtMenuShortcutWorkaround,
    restoreGeom,
    saveGeom,
    shortcut,
    showInfo,
    showWarning,
    tooltip,
    tr,
)
from aqt.webview import AnkiWebView

pics = ("jpg", "jpeg", "png", "tif", "tiff", "gif", "svg", "webp", "ico")
audio = (
    "3gp",
    "avi",
    "flac",
    "flv",
    "m4a",
    "mkv",
    "mov",
    "mp3",
    "mp4",
    "mpeg",
    "mpg",
    "oga",
    "ogg",
    "ogv",
    "ogx",
    "opus",
    "spx",
    "swf",
    "wav",
    "webm",
)

_html = """
<style>
:root {
    --bg-color: %s;
}
</style>
<div>
    <div id="topbutsOuter">
        %s
    </div>
    <div id="fields">
    </div>
    <div id="dupes" class="is-inactive">
        <a href="#" onclick="pycmd('dupes');return false;">%s</a>
    </div>
</div>
"""

# caller is responsible for resetting note on reset
class Editor:
    def __init__(
        self, mw: AnkiQt, widget: QWidget, parentWindow: QWidget, addMode: bool = False
    ) -> None:
        self.mw = mw
        self.widget = widget
        self.parentWindow = parentWindow
        self.note: Optional[Note] = None
        self.addMode = addMode
        self.currentField: Optional[int] = None
        # current card, for card layout
        self.card: Optional[Card] = None
        self.setupOuter()
        self.setupWeb()
        self.setupShortcuts()
        self.setupTags()
        gui_hooks.editor_did_init(self)

    # Initial setup
    ############################################################

    def setupOuter(self) -> None:
        l = QVBoxLayout()
        l.setContentsMargins(0, 0, 0, 0)
        l.setSpacing(0)
        self.widget.setLayout(l)
        self.outerLayout = l

    def setupWeb(self) -> None:
        self.web = EditorWebView(self.widget, self)
        self.web.allowDrops = True
        self.web.set_bridge_command(self.onBridgeCmd, self)
        self.outerLayout.addWidget(self.web, 1)

        lefttopbtns: List[str] = [
            self._addButton(
                None,
                "fields",
                tr(TR.EDITING_CUSTOMIZE_FIELDS),
                f"{tr(TR.EDITING_FIELDS)}...",
                disables=False,
                rightside=False,
            ),
            self._addButton(
                None,
                "cards",
                tr(TR.EDITING_CUSTOMIZE_CARD_TEMPLATES_CTRLANDL),
                f"{tr(TR.EDITING_CARDS)}...",
                disables=False,
                rightside=False,
            ),
        ]

        gui_hooks.editor_did_init_left_buttons(lefttopbtns, self)

        righttopbtns: List[str] = [
            self._addButton(
                "text_bold", "bold", tr(TR.EDITING_BOLD_TEXT_CTRLANDB), id="bold"
            ),
            self._addButton(
                "text_italic",
                "italic",
                tr(TR.EDITING_ITALIC_TEXT_CTRLANDI),
                id="italic",
            ),
            self._addButton(
                "text_under",
                "underline",
                tr(TR.EDITING_UNDERLINE_TEXT_CTRLANDU),
                id="underline",
            ),
            self._addButton(
                "text_super",
                "super",
                tr(TR.EDITING_SUPERSCRIPT_CTRLANDAND),
                id="superscript",
            ),
            self._addButton(
                "text_sub", "sub", tr(TR.EDITING_SUBSCRIPT_CTRLAND), id="subscript"
            ),
            self._addButton(
                "text_clear", "clear", tr(TR.EDITING_REMOVE_FORMATTING_CTRLANDR)
            ),
            self._addButton(
                None,
                "colour",
                tr(TR.EDITING_SET_FOREGROUND_COLOUR_F7),
                """
<div id="forecolor"
     style="display: inline-block; background: #000; border-radius: 5px;"
     class="topbut"
>""",
            ),
            self._addButton(
                None,
                "changeCol",
                tr(TR.EDITING_CHANGE_COLOUR_F8),
                """
<div style="display: inline-block; border-radius: 5px;"
     class="topbut rainbow"
>""",
            ),
            self._addButton(
                "text_cloze", "cloze", tr(TR.EDITING_CLOZE_DELETION_CTRLANDSHIFTANDC)
            ),
            self._addButton(
                "paperclip", "attach", tr(TR.EDITING_ATTACH_PICTURESAUDIOVIDEO_F3)
            ),
            self._addButton("media-record", "record", tr(TR.EDITING_RECORD_AUDIO_F5)),
            self._addButton("more", "more"),
        ]

        gui_hooks.editor_did_init_buttons(righttopbtns, self)
        # legacy filter
        righttopbtns = runFilter("setupEditorButtons", righttopbtns, self)

        topbuts = """
            <div id="topbutsleft" class="topbuts">
                %(leftbts)s
            </div>
            <div id="topbutsright" class="topbuts">
                %(rightbts)s
            </div>
        """ % dict(
            leftbts="".join(lefttopbtns),
            rightbts="".join(righttopbtns),
        )
        bgcol = self.mw.app.palette().window().color().name()  # type: ignore
        # then load page
        self.web.stdHtml(
            _html % (bgcol, topbuts, tr(TR.EDITING_SHOW_DUPLICATES)),
            css=["css/editor.css"],
            js=["js/vendor/jquery.min.js", "js/editor.js"],
            context=self,
        )
        self.web.eval("preventButtonFocus();")

    # Top buttons
    ######################################################################

    def resourceToData(self, path: str) -> str:
        """Convert a file (specified by a path) into a data URI."""
        if not os.path.exists(path):
            raise FileNotFoundError
        mime, _ = mimetypes.guess_type(path)
        with open(path, "rb") as fp:
            data = fp.read()
            data64 = b"".join(base64.encodebytes(data).splitlines())
            return f"data:{mime};base64,{data64.decode('ascii')}"

    def addButton(
        self,
        icon: Optional[str],
        cmd: str,
        func: Callable[["Editor"], None],
        tip: str = "",
        label: str = "",
        id: str = None,
        toggleable: bool = False,
        keys: str = None,
        disables: bool = True,
        rightside: bool = True,
    ) -> str:
        """Assign func to bridge cmd, register shortcut, return button"""
        if func:
            self._links[cmd] = func

            if keys:

                def on_activated() -> None:
                    func(self)

                if toggleable:
                    # generate a random id for triggering toggle
                    id = id or str(randrange(1_000_000))

                    def on_hotkey() -> None:
                        on_activated()
                        self.web.eval(f'toggleEditorButton("#{id}");')

                else:
                    on_hotkey = on_activated

                QShortcut(  # type: ignore
                    QKeySequence(keys),
                    self.widget,
                    activated=on_hotkey,
                )

        btn = self._addButton(
            icon,
            cmd,
            tip=tip,
            label=label,
            id=id,
            toggleable=toggleable,
            disables=disables,
            rightside=rightside,
        )
        return btn

    def _addButton(
        self,
        icon: Optional[str],
        cmd: str,
        tip: str = "",
        label: str = "",
        id: Optional[str] = None,
        toggleable: bool = False,
        disables: bool = True,
        rightside: bool = True,
    ) -> str:
        if icon:
            if icon.startswith("qrc:/"):
                iconstr = icon
            elif os.path.isabs(icon):
                iconstr = self.resourceToData(icon)
            else:
                iconstr = f"/_anki/imgs/{icon}.png"
            imgelm = f"""<img class=topbut src="{iconstr}">"""
        else:
            imgelm = ""
        if label or not imgelm:
            labelelm = f"""<span class=blabel>{label or cmd}</span>"""
        else:
            labelelm = ""
        if id:
            idstr = f"id={id}"
        else:
            idstr = ""
        if toggleable:
            toggleScript = "toggleEditorButton(this);"
        else:
            toggleScript = ""
        tip = shortcut(tip)
        if rightside:
            class_ = "linkb"
        else:
            class_ = ""
        if not disables:
            class_ += " perm"
        return """<button tabindex=-1
                        {id}
                        class="{class_}"
                        type="button"
                        title="{tip}"
                        onclick="pycmd('{cmd}');{togglesc}return false;"
                >
                    {imgelm}
                    {labelelm}
                </button>""".format(
            imgelm=imgelm,
            cmd=cmd,
            tip=tip,
            labelelm=labelelm,
            id=idstr,
            togglesc=toggleScript,
            class_=class_,
        )

    def setupShortcuts(self) -> None:
        # if a third element is provided, enable shortcut even when no field selected
        cuts: List[Tuple] = [
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
            ("Ctrl+M, C", self.insertMathjaxChemistry),
            ("Ctrl+Shift+X", self.onHtmlEdit),
            ("Ctrl+Shift+T", self.onFocusTags, True),
        ]
        gui_hooks.editor_did_init_shortcuts(cuts, self)
        for row in cuts:
            if len(row) == 2:
                keys, fn = row  # pylint: disable=unbalanced-tuple-unpacking
                fn = self._addFocusCheck(fn)
            else:
                keys, fn, _ = row
            QShortcut(QKeySequence(keys), self.widget, activated=fn)  # type: ignore

    def _addFocusCheck(self, fn: Callable) -> Callable:
        def checkFocus() -> None:
            if self.currentField is None:
                return
            fn()

        return checkFocus

    def onFields(self) -> None:
        self.saveNow(self._onFields)

    def _onFields(self) -> None:
        from aqt.fields import FieldDialog

        FieldDialog(self.mw, self.note.model(), parent=self.parentWindow)

    def onCardLayout(self) -> None:
        self.saveNow(self._onCardLayout)

    def _onCardLayout(self) -> None:
        from aqt.clayout import CardLayout

        if self.card:
            ord = self.card.ord
        else:
            ord = 0
        CardLayout(
            self.mw,
            self.note,
            ord=ord,
            parent=self.parentWindow,
            fill_empty=self.addMode,
        )
        if isWin:
            self.parentWindow.activateWindow()

    # JS->Python bridge
    ######################################################################

    def onBridgeCmd(self, cmd: str) -> None:
        if not self.note:
            # shutdown
            return
        # focus lost or key/button pressed?
        if cmd.startswith("blur") or cmd.startswith("key"):
            (type, ord_str, nid_str, txt) = cmd.split(":", 3)
            ord = int(ord_str)
            try:
                nid = int(nid_str)
            except ValueError:
                nid = 0
            if nid != self.note.id:
                print("ignored late blur")
                return

            self.note.fields[ord] = self.mungeHTML(txt)

            if not self.addMode:
                self.note.flush()
                self.mw.requireReset(reason=ResetReason.EditorBridgeCmd, context=self)
            if type == "blur":
                self.currentField = None
                # run any filters
                if gui_hooks.editor_did_unfocus_field(False, self.note, ord):
                    # something updated the note; update it after a subsequent focus
                    # event has had time to fire
                    self.mw.progress.timer(100, self.loadNoteKeepingFocus, False)
                else:
                    self.checkValid()
            else:
                gui_hooks.editor_did_fire_typing_timer(self.note)
                self.checkValid()
        # focused into field?
        elif cmd.startswith("focus"):
            (type, num) = cmd.split(":", 1)
            self.currentField = int(num)
            gui_hooks.editor_did_focus_field(self.note, self.currentField)
        elif cmd in self._links:
            self._links[cmd](self)
        else:
            print("uncaught cmd", cmd)

    def mungeHTML(self, txt: str) -> str:
        return gui_hooks.editor_will_munge_html(txt, self)

    # Setting/unsetting the current note
    ######################################################################

    def setNote(
        self, note: Optional[Note], hide: bool = True, focusTo: Optional[int] = None
    ) -> None:
        "Make NOTE the current note."
        self.note = note
        self.currentField = None
        if self.note:
            self.loadNote(focusTo=focusTo)
        else:
            self.hideCompleters()
            if hide:
                self.widget.hide()

    def loadNoteKeepingFocus(self) -> None:
        self.loadNote(self.currentField)

    def loadNote(self, focusTo: Optional[int] = None) -> None:
        if not self.note:
            return

        data = [
            (fld, self.mw.col.media.escape_media_filenames(val))
            for fld, val in self.note.items()
        ]
        self.widget.show()
        self.updateTags()

        def oncallback(arg: Any) -> None:
            if not self.note:
                return
            self.setupForegroundButton()
            self.checkValid()
            if focusTo is not None:
                self.web.setFocus()
            gui_hooks.editor_did_load_note(self)

        js = "setFields(%s); setFonts(%s); focusField(%s); setNoteId(%s);" % (
            json.dumps(data),
            json.dumps(self.fonts()),
            json.dumps(focusTo),
            json.dumps(self.note.id),
        )
        js = gui_hooks.editor_will_load_note(js, self.note, self)
        self.web.evalWithCallback(js, oncallback)

    def fonts(self) -> List[Tuple[str, int, bool]]:
        return [
            (gui_hooks.editor_will_use_font_for_field(f["font"]), f["size"], f["rtl"])
            for f in self.note.model()["flds"]
        ]

    def saveNow(self, callback: Callable, keepFocus: bool = False) -> None:
        "Save unsaved edits then call callback()."
        if not self.note:
            # calling code may not expect the callback to fire immediately
            self.mw.progress.timer(10, callback, False)
            return
        self.saveTags()
        self.web.evalWithCallback("saveNow(%d)" % keepFocus, lambda res: callback())

    def checkValid(self) -> None:
        cols = [""] * len(self.note.fields)
        err = self.note.dupeOrEmpty()
        if err == 2:
            cols[0] = "dupe"

        self.web.eval(f"setBackgrounds({json.dumps(cols)});")

    def showDupes(self) -> None:
        aqt.dialogs.open(
            "Browser",
            self.mw,
            search=(
                SearchNode(
                    dupe=SearchNode.Dupe(
                        notetype_id=self.note.model()["id"],
                        first_field=self.note.fields[0],
                    )
                ),
            ),
        )

    def fieldsAreBlank(self, previousNote: Optional[Note] = None) -> bool:
        if not self.note:
            return True
        m = self.note.model()
        for c, f in enumerate(self.note.fields):
            f = f.replace("<br>", "").strip()
            notChangedvalues = {"", "<br>"}
            if previousNote and m["flds"][c]["sticky"]:
                notChangedvalues.add(previousNote.fields[c].replace("<br>", "").strip())
            if f not in notChangedvalues:
                return False
        return True

    def cleanup(self) -> None:
        self.setNote(None)
        # prevent any remaining evalWithCallback() events from firing after C++ object deleted
        self.web = None

    # HTML editing
    ######################################################################

    def onHtmlEdit(self) -> None:
        field = self.currentField
        self.saveNow(lambda: self._onHtmlEdit(field))

    def _onHtmlEdit(self, field: int) -> None:
        d = QDialog(self.widget, Qt.Window)
        form = aqt.forms.edithtml.Ui_Dialog()
        form.setupUi(d)
        restoreGeom(d, "htmlEditor")
        disable_help_button(d)
        qconnect(
            form.buttonBox.helpRequested, lambda: openHelp(HelpPage.EDITING_FEATURES)
        )
        font = QFont("Courier")
        font.setStyleHint(QFont.TypeWriter)
        form.textEdit.setFont(font)
        form.textEdit.setPlainText(self.note.fields[field])
        d.show()
        form.textEdit.moveCursor(QTextCursor.End)
        d.exec_()
        html = form.textEdit.toPlainText()
        if html.find(">") > -1:
            # filter html through beautifulsoup so we can strip out things like a
            # leading </div>
            html_escaped = self.mw.col.media.escape_media_filenames(html)
            with warnings.catch_warnings():
                warnings.simplefilter("ignore", UserWarning)
                html_escaped = str(BeautifulSoup(html_escaped, "html.parser"))
                html = self.mw.col.media.escape_media_filenames(
                    html_escaped, unescape=True
                )
        self.note.fields[field] = html
        if not self.addMode:
            self.note.flush()
        self.loadNote(focusTo=field)
        saveGeom(d, "htmlEditor")

    # Tag handling
    ######################################################################

    def setupTags(self) -> None:
        import aqt.tagedit

        g = QGroupBox(self.widget)
        g.setStyleSheet("border: 0")
        tb = QGridLayout()
        tb.setSpacing(12)
        tb.setContentsMargins(2, 6, 2, 6)
        # tags
        l = QLabel(tr(TR.EDITING_TAGS))
        tb.addWidget(l, 1, 0)
        self.tags = aqt.tagedit.TagEdit(self.widget)
        qconnect(self.tags.lostFocus, self.saveTags)
        self.tags.setToolTip(
            shortcut(tr(TR.EDITING_JUMP_TO_TAGS_WITH_CTRLANDSHIFTANDT))
        )
        border = theme_manager.color(colors.BORDER)
        self.tags.setStyleSheet(f"border: 1px solid {border}")
        tb.addWidget(self.tags, 1, 1)
        g.setLayout(tb)
        self.outerLayout.addWidget(g)

    def updateTags(self) -> None:
        if self.tags.col != self.mw.col:
            self.tags.setCol(self.mw.col)
        if not self.tags.text() or not self.addMode:
            self.tags.setText(self.note.stringTags().strip())

    def saveTags(self) -> None:
        if not self.note:
            return
        self.note.tags = self.mw.col.tags.split(self.tags.text())
        if not self.addMode:
            self.note.flush()
        gui_hooks.editor_did_update_tags(self.note)

    def saveAddModeVars(self) -> None:
        if self.addMode:
            # save tags to model
            m = self.note.model()
            m["tags"] = self.note.tags
            self.mw.col.models.save(m, updateReqs=False)

    def hideCompleters(self) -> None:
        self.tags.hideCompleter()

    def onFocusTags(self) -> None:
        self.tags.setFocus()

    # Format buttons
    ######################################################################

    def toggleBold(self) -> None:
        self.web.eval("setFormat('bold');")

    def toggleItalic(self) -> None:
        self.web.eval("setFormat('italic');")

    def toggleUnderline(self) -> None:
        self.web.eval("setFormat('underline');")

    def toggleSuper(self) -> None:
        self.web.eval("setFormat('superscript');")

    def toggleSub(self) -> None:
        self.web.eval("setFormat('subscript');")

    def removeFormat(self) -> None:
        self.web.eval("setFormat('removeFormat');")

    def onCloze(self) -> None:
        self.saveNow(self._onCloze, keepFocus=True)

    def _onCloze(self) -> None:
        # check that the model is set up for cloze deletion
        if self.note.model()["type"] != MODEL_CLOZE:
            if self.addMode:
                tooltip(tr(TR.EDITING_WARNING_CLOZE_DELETIONS_WILL_NOT_WORK))
            else:
                showInfo(tr(TR.EDITING_TO_MAKE_A_CLOZE_DELETION_ON))
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

    def setupForegroundButton(self) -> None:
        self.fcolour = self.mw.pm.profile.get("lastColour", "#00f")
        self.onColourChanged()

    # use last colour
    def onForeground(self) -> None:
        self._wrapWithColour(self.fcolour)

    # choose new colour
    def onChangeCol(self) -> None:
        if isLin:
            new = QColorDialog.getColor(
                QColor(self.fcolour), None, None, QColorDialog.DontUseNativeDialog
            )
        else:
            new = QColorDialog.getColor(QColor(self.fcolour), None)
        # native dialog doesn't refocus us for some reason
        self.parentWindow.activateWindow()
        if new.isValid():
            self.fcolour = new.name()
            self.onColourChanged()
            self._wrapWithColour(self.fcolour)

    def _updateForegroundButton(self) -> None:
        self.web.eval(f"setFGButton('{self.fcolour}')")

    def onColourChanged(self) -> None:
        self._updateForegroundButton()
        self.mw.pm.profile["lastColour"] = self.fcolour

    def _wrapWithColour(self, colour: str) -> None:
        self.web.eval(f"setFormat('forecolor', '{colour}')")

    # Audio/video/images
    ######################################################################

    def onAddMedia(self) -> None:
        extension_filter = " ".join(
            f"*.{extension}" for extension in sorted(itertools.chain(pics, audio))
        )
        filter = f"{tr(TR.EDITING_MEDIA)} ({extension_filter})"

        def accept(file: str) -> None:
            self.addMedia(file, canDelete=True)

        file = getFile(
            parent=self.widget,
            title=tr(TR.EDITING_ADD_MEDIA),
            cb=cast(Callable[[Any], None], accept),
            filter=filter,
            key="media",
        )
        self.parentWindow.activateWindow()

    def addMedia(self, path: str, canDelete: bool = False) -> None:
        try:
            html = self._addMedia(path, canDelete)
        except Exception as e:
            showWarning(str(e))
            return
        self.web.eval(f"setFormat('inserthtml', {json.dumps(html)});")

    def _addMedia(self, path: str, canDelete: bool = False) -> str:
        "Add to media folder and return local img or sound tag."
        # copy to media folder
        fname = self.mw.col.media.addFile(path)
        # remove original?
        if canDelete and self.mw.pm.profile["deleteMedia"]:
            if os.path.abspath(fname) != os.path.abspath(path):
                try:
                    os.unlink(path)
                except:
                    pass
        # return a local html link
        return self.fnameToLink(fname)

    def _addMediaFromData(self, fname: str, data: bytes) -> str:
        return self.mw.col.media.writeData(fname, data)

    def onRecSound(self) -> None:
        aqt.sound.record_audio(
            self.parentWindow,
            self.mw,
            True,
            lambda file: self.addMedia(file, canDelete=True),
        )

    # Media downloads
    ######################################################################

    def urlToLink(self, url: str) -> Optional[str]:
        fname = self.urlToFile(url)
        if not fname:
            return None
        return self.fnameToLink(fname)

    def fnameToLink(self, fname: str) -> str:
        ext = fname.split(".")[-1].lower()
        if ext in pics:
            name = urllib.parse.quote(fname.encode("utf8"))
            return f'<img src="{name}">'
        else:
            av_player.play_file(fname)
            return f"[sound:{html.escape(fname, quote=False)}]"

    def urlToFile(self, url: str) -> Optional[str]:
        l = url.lower()
        for suffix in pics + audio:
            if l.endswith(f".{suffix}"):
                return self._retrieveURL(url)
        # not a supported type
        return None

    def isURL(self, s: str) -> bool:
        s = s.lower()
        return (
            s.startswith("http://")
            or s.startswith("https://")
            or s.startswith("ftp://")
            or s.startswith("file://")
        )

    def inlinedImageToFilename(self, txt: str) -> str:
        prefix = "data:image/"
        suffix = ";base64,"
        for ext in ("jpg", "jpeg", "png", "gif"):
            fullPrefix = prefix + ext + suffix
            if txt.startswith(fullPrefix):
                b64data = txt[len(fullPrefix) :].strip()
                data = base64.b64decode(b64data, validate=True)
                if ext == "jpeg":
                    ext = "jpg"
                return self._addPastedImage(data, f".{ext}")

        return ""

    def inlinedImageToLink(self, src: str) -> str:
        fname = self.inlinedImageToFilename(src)
        if fname:
            return self.fnameToLink(fname)

        return ""

    # ext should include dot
    def _addPastedImage(self, data: bytes, ext: str) -> str:
        # hash and write
        csum = checksum(data)
        fname = f"paste-{csum}{ext}"
        return self._addMediaFromData(fname, data)

    def _retrieveURL(self, url: str) -> Optional[str]:
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
        self.mw.progress.start(immediate=not local, parent=self.parentWindow)
        content_type = None
        error_msg: Optional[str] = None
        try:
            if local:
                req = urllib.request.Request(
                    url, None, {"User-Agent": "Mozilla/5.0 (compatible; Anki)"}
                )
                with urllib.request.urlopen(req) as response:
                    filecontents = response.read()
            else:
                with HttpClient() as client:
                    client.timeout = 30
                    with client.get(url) as response:
                        if response.status_code != 200:
                            error_msg = tr(
                                TR.QT_MISC_UNEXPECTED_RESPONSE_CODE,
                                val=response.status_code,
                            )
                            return None
                        filecontents = response.content
                        content_type = response.headers.get("content-type")
        except (urllib.error.URLError, requests.exceptions.RequestException) as e:
            error_msg = tr(TR.EDITING_AN_ERROR_OCCURRED_WHILE_OPENING, val=str(e))
            return None
        finally:
            self.mw.progress.finish()
            if error_msg:
                showWarning(error_msg)
        # strip off any query string
        url = re.sub(r"\?.*?$", "", url)
        fname = os.path.basename(urllib.parse.unquote(url))
        if not fname.strip():
            fname = "paste"
        if content_type:
            fname = self.mw.col.media.add_extension_based_on_mime(fname, content_type)

        return self.mw.col.media.write_data(fname, filecontents)

    # Paste/drag&drop
    ######################################################################

    removeTags = ["script", "iframe", "object", "style"]

    def _pastePreFilter(self, html: str, internal: bool) -> str:
        # https://anki.tenderapp.com/discussions/ankidesktop/39543-anki-is-replacing-the-character-by-when-i-exit-the-html-edit-mode-ctrlshiftx
        if html.find(">") < 0:
            return html

        with warnings.catch_warnings() as w:
            warnings.simplefilter("ignore", UserWarning)
            doc = BeautifulSoup(html, "html.parser")

        tag: bs4.element.Tag
        if not internal:
            for tag in self.removeTags:
                for node in doc(tag):
                    node.decompose()

            # convert p tags to divs
            for node in doc("p"):
                node.name = "div"

        for tag in doc("img"):
            try:
                src = tag["src"]
            except KeyError:
                # for some bizarre reason, mnemosyne removes src elements
                # from missing media
                continue

            # in internal pastes, rewrite mediasrv references to relative
            if internal:
                m = re.match(r"http://127.0.0.1:\d+/(.*)$", src)
                if m:
                    tag["src"] = m.group(1)
            else:
                # in external pastes, download remote media
                if self.isURL(src):
                    fname = self._retrieveURL(src)
                    if fname:
                        tag["src"] = fname
                elif src.startswith("data:image/"):
                    # and convert inlined data
                    tag["src"] = self.inlinedImageToFilename(src)

        html = str(doc)
        return html

    def doPaste(self, html: str, internal: bool, extended: bool = False) -> None:
        html = self._pastePreFilter(html, internal)
        if extended:
            ext = "true"
        else:
            ext = "false"
        self.web.eval(f"pasteHTML({json.dumps(html)}, {json.dumps(internal)}, {ext});")

    def doDrop(self, html: str, internal: bool, extended: bool = False) -> None:
        def pasteIfField(ret: bool) -> None:
            if ret:
                self.doPaste(html, internal, extended)

        p = self.web.mapFromGlobal(QCursor.pos())
        self.web.evalWithCallback(f"focusIfField({p.x()}, {p.y()});", pasteIfField)

    def onPaste(self) -> None:
        self.web.onPaste()

    def onCutOrCopy(self) -> None:
        self.web.flagAnkiText()

    # Advanced menu
    ######################################################################

    def onAdvanced(self) -> None:
        m = QMenu(self.mw)

        for text, handler, shortcut in (
            (tr(TR.EDITING_MATHJAX_INLINE), self.insertMathjaxInline, "Ctrl+M, M"),
            (tr(TR.EDITING_MATHJAX_BLOCK), self.insertMathjaxBlock, "Ctrl+M, E"),
            (
                tr(TR.EDITING_MATHJAX_CHEMISTRY),
                self.insertMathjaxChemistry,
                "Ctrl+M, C",
            ),
            (tr(TR.EDITING_LATEX), self.insertLatex, "Ctrl+T, T"),
            (tr(TR.EDITING_LATEX_EQUATION), self.insertLatexEqn, "Ctrl+T, E"),
            (tr(TR.EDITING_LATEX_MATH_ENV), self.insertLatexMathEnv, "Ctrl+T, M"),
            (tr(TR.EDITING_EDIT_HTML), self.onHtmlEdit, "Ctrl+Shift+X"),
        ):
            a = m.addAction(text)
            qconnect(a.triggered, handler)
            a.setShortcut(QKeySequence(shortcut))

        qtMenuShortcutWorkaround(m)

        m.exec_(QCursor.pos())

    # LaTeX
    ######################################################################

    def insertLatex(self) -> None:
        self.web.eval("wrap('[latex]', '[/latex]');")

    def insertLatexEqn(self) -> None:
        self.web.eval("wrap('[$]', '[/$]');")

    def insertLatexMathEnv(self) -> None:
        self.web.eval("wrap('[$$]', '[/$$]');")

    def insertMathjaxInline(self) -> None:
        self.web.eval("wrap('\\\\(', '\\\\)');")

    def insertMathjaxBlock(self) -> None:
        self.web.eval("wrap('\\\\[', '\\\\]');")

    def insertMathjaxChemistry(self) -> None:
        self.web.eval("wrap('\\\\(\\\\ce{', '}\\\\)');")

    # Links from HTML
    ######################################################################

    _links: Dict[str, Callable] = dict(
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
    def __init__(self, parent: QWidget, editor: Editor) -> None:
        AnkiWebView.__init__(self, title="editor")
        self.editor = editor
        self.strip = self.editor.mw.pm.profile["stripHTML"]
        self.setAcceptDrops(True)
        self._markInternal = False
        clip = self.editor.mw.app.clipboard()
        qconnect(clip.dataChanged, self._onClipboardChange)
        gui_hooks.editor_web_view_did_init(self)

    def _onClipboardChange(self) -> None:
        if self._markInternal:
            self._markInternal = False
            self._flagAnkiText()

    def onCut(self) -> None:
        self.triggerPageAction(QWebEnginePage.Cut)

    def onCopy(self) -> None:
        self.triggerPageAction(QWebEnginePage.Copy)

    def _wantsExtendedPaste(self) -> bool:
        extended = not (self.editor.mw.app.queryKeyboardModifiers() & Qt.ShiftModifier)
        if self.editor.mw.pm.profile.get("pasteInvert", False):
            extended = not extended
        return extended

    def _onPaste(self, mode: QClipboard.Mode) -> None:
        extended = self._wantsExtendedPaste()
        mime = self.editor.mw.app.clipboard().mimeData(mode=mode)
        html, internal = self._processMime(mime, extended)
        if not html:
            return
        self.editor.doPaste(html, internal, extended)

    def onPaste(self) -> None:
        self._onPaste(QClipboard.Clipboard)

    def onMiddleClickPaste(self) -> None:
        self._onPaste(QClipboard.Selection)

    def dragEnterEvent(self, evt: QDragEnterEvent) -> None:
        evt.accept()

    def dropEvent(self, evt: QDropEvent) -> None:
        extended = self._wantsExtendedPaste()
        mime = evt.mimeData()

        if evt.source() and mime.hasHtml():
            # don't filter html from other fields
            html, internal = mime.html(), True
        else:
            html, internal = self._processMime(mime, extended)

        if not html:
            return

        self.editor.doDrop(html, internal, extended)

    # returns (html, isInternal)
    def _processMime(self, mime: QMimeData, extended: bool = False) -> Tuple[str, bool]:
        # print("html=%s image=%s urls=%s txt=%s" % (
        #     mime.hasHtml(), mime.hasImage(), mime.hasUrls(), mime.hasText()))
        # print("html", mime.html())
        # print("urls", mime.urls())
        # print("text", mime.text())

        # try various content types in turn
        html, internal = self._processHtml(mime)
        if html:
            return html, internal

        # favour url if it's a local link
        if mime.hasUrls() and mime.urls()[0].toString().startswith("file://"):
            types = (self._processUrls, self._processImage, self._processText)
        else:
            types = (self._processImage, self._processUrls, self._processText)

        for fn in types:
            html = fn(mime, extended)
            if html:
                return html, True
        return "", False

    def _processUrls(self, mime: QMimeData, extended: bool = False) -> Optional[str]:
        if not mime.hasUrls():
            return None

        buf = ""
        for qurl in mime.urls():
            url = qurl.toString()
            # chrome likes to give us the URL twice with a \n
            url = url.splitlines()[0]
            buf += self.editor.urlToLink(url) or ""

        return buf

    def _processText(self, mime: QMimeData, extended: bool = False) -> Optional[str]:
        if not mime.hasText():
            return None

        txt = mime.text()
        processed = []
        lines = txt.split("\n")

        for line in lines:
            for token in re.split(r"(\S+)", line):
                # inlined data in base64?
                if extended and token.startswith("data:image/"):
                    processed.append(self.editor.inlinedImageToLink(token))
                elif extended and self.editor.isURL(token):
                    # if the user is pasting an image or sound link, convert it to local
                    link = self.editor.urlToLink(token)
                    if link:
                        processed.append(link)
                    else:
                        # not media; add it as a normal link
                        link = '<a href="{}">{}</a>'.format(
                            token, html.escape(urllib.parse.unquote(token))
                        )
                        processed.append(link)
                else:
                    token = html.escape(token).replace("\t", " " * 4)
                    # if there's more than one consecutive space,
                    # use non-breaking spaces for the second one on
                    def repl(match: Match) -> str:
                        return f"{match.group(1).replace(' ', '&nbsp;')} "

                    token = re.sub(" ( +)", repl, token)
                    processed.append(token)

            processed.append("<br>")
        # remove last <br>
        processed.pop()
        return "".join(processed)

    def _processHtml(self, mime: QMimeData) -> Tuple[Optional[str], bool]:
        if not mime.hasHtml():
            return None, False
        html = mime.html()

        # no filtering required for internal pastes
        if html.startswith("<!--anki-->"):
            return html[11:], True

        return html, False

    def _processImage(self, mime: QMimeData, extended: bool = False) -> Optional[str]:
        if not mime.hasImage():
            return None
        im = QImage(mime.imageData())
        uname = namedtmp("paste")
        if self.editor.mw.pm.profile.get("pastePNG", False):
            ext = ".png"
            im.save(uname + ext, None, 50)
        else:
            ext = ".jpg"
            im.save(uname + ext, None, 80)

        # invalid image?
        path = uname + ext
        if not os.path.exists(path):
            return None

        with open(path, "rb") as file:
            data = file.read()
        fname = self.editor._addPastedImage(data, ext)
        if fname:
            return self.editor.fnameToLink(fname)
        return None

    def flagAnkiText(self) -> None:
        # be ready to adjust when clipboard event fires
        self._markInternal = True

    def _flagAnkiText(self) -> None:
        # add a comment in the clipboard html so we can tell text is copied
        # from us and doesn't need to be stripped
        clip = self.editor.mw.app.clipboard()
        if not isMac and not clip.ownsClipboard():
            return
        mime = clip.mimeData()
        if not mime.hasHtml():
            return
        html = mime.html()
        mime.setHtml(f"<!--anki-->{html}")
        clip.setMimeData(mime)

    def contextMenuEvent(self, evt: QContextMenuEvent) -> None:
        m = QMenu(self)
        a = m.addAction(tr(TR.EDITING_CUT))
        qconnect(a.triggered, self.onCut)
        a = m.addAction(tr(TR.ACTIONS_COPY))
        qconnect(a.triggered, self.onCopy)
        a = m.addAction(tr(TR.EDITING_PASTE))
        qconnect(a.triggered, self.onPaste)
        gui_hooks.editor_will_show_context_menu(self, m)
        m.popup(QCursor.pos())


# QFont returns "Kozuka Gothic Pro L" but WebEngine expects "Kozuka Gothic Pro Light"
# - there may be other cases like a trailing 'Bold' that need fixing, but will
# wait for further reports first.
def fontMungeHack(font: str) -> str:
    return re.sub(" L$", " Light", font)


def munge_html(txt: str, editor: Editor) -> str:
    return "" if txt in ("<br>", "<div><br></div>") else txt


def remove_null_bytes(txt: str, editor: Editor) -> str:
    # misbehaving apps may include a null byte in the text
    return txt.replace("\x00", "")


def reverse_url_quoting(txt: str, editor: Editor) -> str:
    # reverse the url quoting we added to get images to display
    return editor.mw.col.media.escape_media_filenames(txt, unescape=True)


gui_hooks.editor_will_use_font_for_field.append(fontMungeHack)
gui_hooks.editor_will_munge_html.append(munge_html)
gui_hooks.editor_will_munge_html.append(remove_null_bytes)
gui_hooks.editor_will_munge_html.append(reverse_url_quoting)
