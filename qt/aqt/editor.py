# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

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
from anki._legacy import deprecated
from anki.cards import Card
from anki.collection import Config, SearchNode
from anki.consts import MODEL_CLOZE
from anki.hooks import runFilter
from anki.httpclient import HttpClient
from anki.notes import Note, NoteFieldsCheckResult
from anki.utils import checksum, isLin, isWin, namedtmp
from aqt import AnkiQt, colors, gui_hooks
from aqt.operations import QueryOp
from aqt.operations.note import update_note
from aqt.operations.notetype import update_notetype_legacy
from aqt.qt import *
from aqt.sound import av_player
from aqt.theme import theme_manager
from aqt.utils import (
    HelpPage,
    KeyboardModifiersPressed,
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
<div id="fields"></div>
<div id="dupes" class="is-inactive">
    <a href="#" onclick="pycmd('dupes');return false;">%s</a>
</div>
<div id="cloze-hint"></div>
"""


class Editor:
    """The screen that embeds an editing widget should listen for changes via
    the `operation_did_execute` hook, and call set_note() when the editor needs
    redrawing.

    The editor will cause that hook to be fired when it saves changes. To avoid
    an unwanted refresh, the parent widget should check if handler
    corresponds to this editor instance, and ignore the change if it does.
    """

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

        # then load page
        self.web.stdHtml(
            _html % tr.editing_show_duplicates(),
            css=[
                "css/editor.css",
            ],
            js=[
                "js/vendor/jquery.min.js",
                "js/editor.js",
            ],
            context=self,
            default_css=True,
        )

        lefttopbtns: List[str] = []
        gui_hooks.editor_did_init_left_buttons(lefttopbtns, self)

        lefttopbtns_defs = [
            f"$editorToolbar.then(({{ notetypeButtons }}) => notetypeButtons.appendButton({{ component: editorToolbar.Raw, props: {{ html: {json.dumps(button)} }} }}, -1));"
            for button in lefttopbtns
        ]
        lefttopbtns_js = "\n".join(lefttopbtns_defs)

        righttopbtns: List[str] = []
        gui_hooks.editor_did_init_buttons(righttopbtns, self)
        # legacy filter
        righttopbtns = runFilter("setupEditorButtons", righttopbtns, self)

        righttopbtns_defs = ", ".join([json.dumps(button) for button in righttopbtns])
        righttopbtns_js = (
            f"""
$editorToolbar.then(({{ toolbar }}) => toolbar.appendGroup({{
    component: editorToolbar.AddonButtons,
    id: "addons",
    props: {{ buttons: [ {righttopbtns_defs} ] }},
}}));
"""
            if len(righttopbtns) > 0
            else ""
        )

        self.web.eval(f"{lefttopbtns_js} {righttopbtns_js}")

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
            imgelm = f"""<img class="topbut" src="{iconstr}">"""
        else:
            imgelm = ""
        if label or not imgelm:
            labelelm = label or cmd
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
            class_ = "rounded"
        if not disables:
            class_ += " perm"
        return """<button tabindex=-1
                        {id}
                        class="{class_}"
                        type="button"
                        title="{tip}"
                        onclick="pycmd('{cmd}');{togglesc}return false;"
                        onmousedown="window.event.preventDefault();"
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
        self.call_after_note_saved(self._onFields)

    def _onFields(self) -> None:
        from aqt.fields import FieldDialog

        FieldDialog(self.mw, self.note.note_type(), parent=self.parentWindow)

    def onCardLayout(self) -> None:
        self.call_after_note_saved(self._onCardLayout)

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

    def onBridgeCmd(self, cmd: str) -> Any:
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
                self._save_current_note()
            if type == "blur":
                self.currentField = None
                # run any filters
                if gui_hooks.editor_did_unfocus_field(False, self.note, ord):
                    # something updated the note; update it after a subsequent focus
                    # event has had time to fire
                    self.mw.progress.timer(100, self.loadNoteKeepingFocus, False)
                else:
                    self._check_and_update_duplicate_display_async()
            else:
                gui_hooks.editor_did_fire_typing_timer(self.note)
                self._check_and_update_duplicate_display_async()

        # focused into field?
        elif cmd.startswith("focus"):
            (type, num) = cmd.split(":", 1)
            self.currentField = int(num)
            gui_hooks.editor_did_focus_field(self.note, self.currentField)

        elif cmd.startswith("toggleStickyAll"):
            model = self.note.note_type()
            flds = model["flds"]

            any_sticky = any([fld["sticky"] for fld in flds])
            result = []
            for fld in flds:
                if not any_sticky or fld["sticky"]:
                    fld["sticky"] = not fld["sticky"]

                result.append(fld["sticky"])

            update_notetype_legacy(parent=self.mw, notetype=model).run_in_background()

            return result

        elif cmd.startswith("toggleSticky"):
            (type, num) = cmd.split(":", 1)
            ord = int(num)

            model = self.note.note_type()
            fld = model["flds"][ord]
            new_state = not fld["sticky"]
            fld["sticky"] = new_state

            update_notetype_legacy(parent=self.mw, notetype=model).run_in_background()

            return new_state

        elif cmd.startswith("lastTextColor"):
            (_, textColor) = cmd.split(":", 1)
            self.mw.pm.profile["lastTextColor"] = textColor

        elif cmd.startswith("lastHighlightColor"):
            (_, highlightColor) = cmd.split(":", 1)
            self.mw.pm.profile["lastHighlightColor"] = highlightColor

        elif cmd in self._links:
            self._links[cmd](self)

        else:
            print("uncaught cmd", cmd)

    def mungeHTML(self, txt: str) -> str:
        return gui_hooks.editor_will_munge_html(txt, self)

    # Setting/unsetting the current note
    ######################################################################

    def set_note(
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

        note_fields_status = self.note.fields_check()

        def oncallback(arg: Any) -> None:
            if not self.note:
                return
            self.setupForegroundButton()
            # we currently do this synchronously to ensure we load before the
            # sidebar on browser startup
            self._update_duplicate_display(note_fields_status)
            if focusTo is not None:
                self.web.setFocus()
            gui_hooks.editor_did_load_note(self)

        text_color = self.mw.pm.profile.get("lastTextColor", "#00f")
        highlight_color = self.mw.pm.profile.get("lastHighlightColor", "#00f")

        js = "setFields(%s); setFonts(%s); focusField(%s); setNoteId(%s); setColorButtons(%s);" % (
            json.dumps(data),
            json.dumps(self.fonts()),
            json.dumps(focusTo),
            json.dumps(self.note.id),
            json.dumps([text_color, highlight_color]),
        )

        if self.addMode:
            sticky = [field["sticky"] for field in self.note.note_type()["flds"]]
            js += " setSticky(%s);" % json.dumps(sticky)

        js = gui_hooks.editor_will_load_note(js, self.note, self)
        self.web.evalWithCallback(js, oncallback)

    def _save_current_note(self) -> None:
        "Call after note is updated with data from webview."
        update_note(parent=self.widget, note=self.note).run_in_background(
            initiator=self
        )

    def fonts(self) -> List[Tuple[str, int, bool]]:
        return [
            (gui_hooks.editor_will_use_font_for_field(f["font"]), f["size"], f["rtl"])
            for f in self.note.note_type()["flds"]
        ]

    def call_after_note_saved(
        self, callback: Callable, keepFocus: bool = False
    ) -> None:
        "Save unsaved edits then call callback()."
        if not self.note:
            # calling code may not expect the callback to fire immediately
            self.mw.progress.timer(10, callback, False)
            return
        self.blur_tags_if_focused()
        self.web.evalWithCallback("saveNow(%d)" % keepFocus, lambda res: callback())

    saveNow = call_after_note_saved

    def _check_and_update_duplicate_display_async(self) -> None:
        note = self.note
        if not note:
            return

        def on_done(result: NoteFieldsCheckResult.V) -> None:
            if self.note != note:
                return
            self._update_duplicate_display(result)

        QueryOp(
            parent=self.parentWindow,
            op=lambda _: note.fields_check(),
            success=on_done,
        ).run_in_background()

    checkValid = _check_and_update_duplicate_display_async

    def _update_duplicate_display(self, result: NoteFieldsCheckResult.V) -> None:
        cols = [""] * len(self.note.fields)
        cloze_hint = ""
        if result == NoteFieldsCheckResult.DUPLICATE:
            cols[0] = "dupe"
        elif result == NoteFieldsCheckResult.NOTETYPE_NOT_CLOZE:
            cloze_hint = tr.adding_cloze_outside_cloze_notetype()
        elif result == NoteFieldsCheckResult.FIELD_NOT_CLOZE:
            cloze_hint = tr.adding_cloze_outside_cloze_field()

        self.web.eval(f"setBackgrounds({json.dumps(cols)});")
        self.web.eval(f"setClozeHint({json.dumps(cloze_hint)});")

    def showDupes(self) -> None:
        aqt.dialogs.open(
            "Browser",
            self.mw,
            search=(
                SearchNode(
                    dupe=SearchNode.Dupe(
                        notetype_id=self.note.note_type()["id"],
                        first_field=self.note.fields[0],
                    )
                ),
            ),
        )

    def fieldsAreBlank(self, previousNote: Optional[Note] = None) -> bool:
        if not self.note:
            return True
        m = self.note.note_type()
        for c, f in enumerate(self.note.fields):
            f = f.replace("<br>", "").strip()
            notChangedvalues = {"", "<br>"}
            if previousNote and m["flds"][c]["sticky"]:
                notChangedvalues.add(previousNote.fields[c].replace("<br>", "").strip())
            if f not in notChangedvalues:
                return False
        return True

    def cleanup(self) -> None:
        self.set_note(None)
        # prevent any remaining evalWithCallback() events from firing after C++ object deleted
        self.web = None

    # legacy

    setNote = set_note

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
        l = QLabel(tr.editing_tags())
        tb.addWidget(l, 1, 0)
        self.tags = aqt.tagedit.TagEdit(self.widget)
        qconnect(self.tags.lostFocus, self.on_tag_focus_lost)
        self.tags.setToolTip(shortcut(tr.editing_jump_to_tags_with_ctrlandshiftandt()))
        border = theme_manager.color(colors.BORDER)
        self.tags.setStyleSheet(f"border: 1px solid {border}")
        tb.addWidget(self.tags, 1, 1)
        g.setLayout(tb)
        self.outerLayout.addWidget(g)

    def updateTags(self) -> None:
        if self.tags.col != self.mw.col:
            self.tags.setCol(self.mw.col)
        if not self.tags.text() or not self.addMode:
            self.tags.setText(self.note.string_tags().strip())

    def on_tag_focus_lost(self) -> None:
        self.note.tags = self.mw.col.tags.split(self.tags.text())
        gui_hooks.editor_did_update_tags(self.note)
        if not self.addMode:
            self._save_current_note()

    def blur_tags_if_focused(self) -> None:
        if not self.note:
            return
        if self.tags.hasFocus():
            self.widget.setFocus()

    def hideCompleters(self) -> None:
        self.tags.hideCompleter()

    def onFocusTags(self) -> None:
        self.tags.setFocus()

    # legacy

    def saveAddModeVars(self) -> None:
        pass

    saveTags = blur_tags_if_focused

    # Audio/video/images
    ######################################################################

    def onAddMedia(self) -> None:
        extension_filter = " ".join(
            f"*.{extension}" for extension in sorted(itertools.chain(pics, audio))
        )
        filter = f"{tr.editing_media()} ({extension_filter})"

        def accept(file: str) -> None:
            self.addMedia(file)

        file = getFile(
            parent=self.widget,
            title=tr.editing_add_media(),
            cb=cast(Callable[[Any], None], accept),
            filter=filter,
            key="media",
        )
        self.parentWindow.activateWindow()

    def addMedia(self, path: str, canDelete: bool = False) -> None:
        """canDelete is a legacy arg and is ignored."""
        try:
            html = self._addMedia(path)
        except Exception as e:
            showWarning(str(e))
            return
        self.web.eval(f"setFormat('inserthtml', {json.dumps(html)});")

    def _addMedia(self, path: str, canDelete: bool = False) -> str:
        """Add to media folder and return local img or sound tag."""
        # copy to media folder
        fname = self.mw.col.media.addFile(path)
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
                            error_msg = tr.qt_misc_unexpected_response_code(
                                val=response.status_code,
                            )
                            return None
                        filecontents = response.content
                        content_type = response.headers.get("content-type")
        except (urllib.error.URLError, requests.exceptions.RequestException) as e:
            error_msg = tr.editing_an_error_occurred_while_opening(val=str(e))
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
        gui_hooks.editor_did_paste(self, html, internal, extended)

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

    # Legacy editing routines
    ######################################################################

    _js_legacy = "this routine has been moved into JS, and will be removed soon"

    @deprecated(info=_js_legacy)
    def onHtmlEdit(self) -> None:
        field = self.currentField
        self.call_after_note_saved(lambda: self._onHtmlEdit(field))

    @deprecated(info=_js_legacy)
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
            self._save_current_note()
        self.loadNote(focusTo=field)
        saveGeom(d, "htmlEditor")

    @deprecated(info=_js_legacy)
    def toggleBold(self) -> None:
        self.web.eval("setFormat('bold');")

    @deprecated(info=_js_legacy)
    def toggleItalic(self) -> None:
        self.web.eval("setFormat('italic');")

    @deprecated(info=_js_legacy)
    def toggleUnderline(self) -> None:
        self.web.eval("setFormat('underline');")

    @deprecated(info=_js_legacy)
    def toggleSuper(self) -> None:
        self.web.eval("setFormat('superscript');")

    @deprecated(info=_js_legacy)
    def toggleSub(self) -> None:
        self.web.eval("setFormat('subscript');")

    @deprecated(info=_js_legacy)
    def removeFormat(self) -> None:
        self.web.eval("setFormat('removeFormat');")

    @deprecated(info=_js_legacy)
    def onCloze(self) -> None:
        self.call_after_note_saved(self._onCloze, keepFocus=True)

    @deprecated(info=_js_legacy)
    def _onCloze(self) -> None:
        # check that the model is set up for cloze deletion
        if self.note.note_type()["type"] != MODEL_CLOZE:
            if self.addMode:
                tooltip(tr.editing_warning_cloze_deletions_will_not_work())
            else:
                showInfo(tr.editing_to_make_a_cloze_deletion_on())
                return
        # find the highest existing cloze
        highest = 0
        for name, val in list(self.note.items()):
            m = re.findall(r"\{\{c(\d+)::", val)
            if m:
                highest = max(highest, sorted([int(x) for x in m])[-1])
        # reuse last?
        if not KeyboardModifiersPressed().alt:
            highest += 1
        # must start at 1
        highest = max(1, highest)
        self.web.eval("wrap('{{c%d::', '}}');" % highest)

    def setupForegroundButton(self) -> None:
        self.fcolour = self.mw.pm.profile.get("lastColour", "#00f")

    # use last colour
    @deprecated(info=_js_legacy)
    def onForeground(self) -> None:
        self._wrapWithColour(self.fcolour)

    # choose new colour
    @deprecated(info=_js_legacy)
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

    @deprecated(info=_js_legacy)
    def _updateForegroundButton(self) -> None:
        pass

    @deprecated(info=_js_legacy)
    def onColourChanged(self) -> None:
        self._updateForegroundButton()
        self.mw.pm.profile["lastColour"] = self.fcolour

    @deprecated(info=_js_legacy)
    def _wrapWithColour(self, colour: str) -> None:
        self.web.eval(f"setFormat('forecolor', '{colour}')")

    @deprecated(info=_js_legacy)
    def onAdvanced(self) -> None:
        m = QMenu(self.mw)

        for text, handler, shortcut in (
            (tr.editing_mathjax_inline(), self.insertMathjaxInline, "Ctrl+M, M"),
            (tr.editing_mathjax_block(), self.insertMathjaxBlock, "Ctrl+M, E"),
            (
                tr.editing_mathjax_chemistry(),
                self.insertMathjaxChemistry,
                "Ctrl+M, C",
            ),
            (tr.editing_latex(), self.insertLatex, "Ctrl+T, T"),
            (tr.editing_latex_equation(), self.insertLatexEqn, "Ctrl+T, E"),
            (tr.editing_latex_math_env(), self.insertLatexMathEnv, "Ctrl+T, M"),
            (tr.editing_edit_html(), self.onHtmlEdit, "Ctrl+Shift+X"),
        ):
            a = m.addAction(text)
            qconnect(a.triggered, handler)
            a.setShortcut(QKeySequence(shortcut))

        qtMenuShortcutWorkaround(m)

        m.exec_(QCursor.pos())

    @deprecated(info=_js_legacy)
    def insertLatex(self) -> None:
        self.web.eval("wrap('[latex]', '[/latex]');")

    @deprecated(info=_js_legacy)
    def insertLatexEqn(self) -> None:
        self.web.eval("wrap('[$]', '[/$]');")

    @deprecated(info=_js_legacy)
    def insertLatexMathEnv(self) -> None:
        self.web.eval("wrap('[$$]', '[/$$]');")

    @deprecated(info=_js_legacy)
    def insertMathjaxInline(self) -> None:
        self.web.eval("wrap('\\\\(', '\\\\)');")

    @deprecated(info=_js_legacy)
    def insertMathjaxBlock(self) -> None:
        self.web.eval("wrap('\\\\[', '\\\\]');")

    @deprecated(info=_js_legacy)
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
        htmlEdit=onHtmlEdit,
        mathjaxInline=insertMathjaxInline,
        mathjaxBlock=insertMathjaxBlock,
        mathjaxChemistry=insertMathjaxChemistry,
    )


# Pasting, drag & drop, and keyboard layouts
######################################################################


class EditorWebView(AnkiWebView):
    def __init__(self, parent: QWidget, editor: Editor) -> None:
        AnkiWebView.__init__(self, title="editor")
        self.editor = editor
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
        strip_html = self.editor.mw.col.get_config_bool(
            Config.Bool.PASTE_STRIPS_FORMATTING
        )
        if KeyboardModifiersPressed().shift:
            strip_html = not strip_html
        return not strip_html

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
        if self.editor.mw.col.get_config_bool(Config.Bool.PASTE_IMAGES_AS_PNG):
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
        a = m.addAction(tr.editing_cut())
        qconnect(a.triggered, self.onCut)
        a = m.addAction(tr.actions_copy())
        qconnect(a.triggered, self.onCopy)
        a = m.addAction(tr.editing_paste())
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


def set_cloze_button(editor: Editor) -> None:
    if editor.note.note_type()["type"] == MODEL_CLOZE:
        editor.web.eval(
            '$editorToolbar.then(({ templateButtons }) => templateButtons.showButton("cloze")); '
        )
    else:
        editor.web.eval(
            '$editorToolbar.then(({ templateButtons }) => templateButtons.hideButton("cloze")); '
        )


gui_hooks.editor_did_load_note.append(set_cloze_button)
