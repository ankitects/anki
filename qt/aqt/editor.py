# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import base64
import functools
import html
import json
import mimetypes
import os
import re
import urllib.error
import urllib.parse
import urllib.request
import warnings
from collections.abc import Callable
from enum import Enum
from random import randrange
from typing import Any, Iterable, Match

import bs4
import requests
from bs4 import BeautifulSoup

import aqt
import aqt.sound
from anki._legacy import deprecated
from anki.cards import Card
from anki.collection import Config
from anki.hooks import runFilter
from anki.httpclient import HttpClient
from anki.models import NotetypeDict, StockNotetype
from anki.notes import Note, NoteId
from anki.utils import checksum, is_win, namedtmp
from aqt import AnkiQt, gui_hooks
from aqt.operations.notetype import update_notetype_legacy
from aqt.qt import *
from aqt.sound import av_player
from aqt.utils import KeyboardModifiersPressed, shortcut, showWarning, tr
from aqt.webview import AnkiWebView, AnkiWebViewKind

pics = ("jpg", "jpeg", "png", "gif", "svg", "webp", "ico", "avif")
audio = (
    "3gp",
    "aac",
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


class EditorMode(Enum):
    ADD_CARDS = 0
    EDIT_CURRENT = 1
    BROWSER = 2


class EditorState(Enum):
    """
    Current input state of the editing UI.
    """

    INITIAL = -1
    FIELDS = 0
    IO_PICKER = 1
    IO_MASKS = 2
    IO_FIELDS = 3


def on_editor_ready(func: Callable) -> Callable:
    @functools.wraps(func)
    def decorated(self: Editor, *args: Any, **kwargs: Any) -> None:
        if self._ready:
            func(self, *args, **kwargs)
        else:
            self._ready_callbacks.append(lambda: func(self, *args, **kwargs))

    return decorated


class Editor:
    """The screen that embeds an editing widget should listen for changes via
    the `operation_did_execute` hook, and call set_note() when the editor needs
    redrawing.

    The editor will cause that hook to be fired when it saves changes. To avoid
    an unwanted refresh, the parent widget should check if handler
    corresponds to this editor instance, and ignore the change if it does.
    """

    def __init__(
        self,
        mw: AnkiQt,
        widget: QWidget,
        parentWindow: QWidget,
        addMode: bool | None = None,
        *,
        editor_mode: EditorMode = EditorMode.EDIT_CURRENT,
    ) -> None:
        self.mw = mw
        self.widget = widget
        self.parentWindow = parentWindow
        self.nid: NoteId | None = None
        # legacy argument provided?
        if addMode is not None:
            editor_mode = EditorMode.ADD_CARDS if addMode else EditorMode.EDIT_CURRENT
        self.addMode = editor_mode is EditorMode.ADD_CARDS
        self.editorMode = editor_mode
        self.currentField: int | None = None
        # Similar to currentField, but not set to None on a blur. May be
        # outside the bounds of the current notetype.
        self.last_field_index: int | None = None
        # used when creating a copy of an existing note
        self.orig_note_id: NoteId | None = None
        # current card, for card layout
        self.card: Card | None = None
        self.state: EditorState = EditorState.INITIAL
        # used for the io mask editor's context menu
        self.last_io_image_path: str | None = None
        self._ready = False
        self._ready_callbacks: list[Callable[[], None]] = []
        self._init_links()
        self.setupOuter()
        self.add_webview()
        self.setupWeb()
        self.setupShortcuts()
        # gui_hooks.editor_did_init(self)

    # Initial setup
    ############################################################

    def setupOuter(self) -> None:
        l = QVBoxLayout()
        l.setContentsMargins(0, 0, 0, 0)
        l.setSpacing(0)
        self.widget.setLayout(l)
        self.outerLayout = l

    def add_webview(self) -> None:
        self.web = EditorWebView(self.widget, self)
        self.web.set_bridge_command(self.onBridgeCmd, self)
        self.outerLayout.addWidget(self.web, 1)

    def setupWeb(self) -> None:
        editor_key = self.mw.pm.editor_key(self.editorMode)
        self.web.load_sveltekit_page(f"editor/?mode={editor_key}")
        self.web.allow_drops = True

    def _set_ready(self) -> None:
        lefttopbtns: list[str] = []
        gui_hooks.editor_did_init_left_buttons(lefttopbtns, self)

        lefttopbtns_defs = [
            f"uiPromise.then((noteEditor) => noteEditor.toolbar.notetypeButtons.appendButton({{ component: editorToolbar.Raw, props: {{ html: {json.dumps(button)} }} }}, -1));"
            for button in lefttopbtns
        ]
        lefttopbtns_js = "\n".join(lefttopbtns_defs)

        righttopbtns: list[str] = []
        gui_hooks.editor_did_init_buttons(righttopbtns, self)
        # legacy filter
        righttopbtns = runFilter("setupEditorButtons", righttopbtns, self)

        righttopbtns_defs = ", ".join([json.dumps(button) for button in righttopbtns])
        righttopbtns_js = (
            f"""
require("anki/ui").loaded.then(() => require("anki/NoteEditor").instances[0].toolbar.toolbar.append({{
    component: editorToolbar.AddonButtons,
    id: "addons",
    props: {{ buttons: [ {righttopbtns_defs} ] }},
}}));
"""
            if len(righttopbtns) > 0
            else ""
        )

        self.web.eval(f"{lefttopbtns_js} {righttopbtns_js}")
        gui_hooks.editor_did_init(self)
        self._ready = True
        for cb in self._ready_callbacks:
            cb()

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
        icon: str | None,
        cmd: str,
        func: Callable[[Editor], None],
        tip: str = "",
        label: str = "",
        id: str | None = None,
        toggleable: bool = False,
        keys: str | None = None,
        disables: bool = True,
        rightside: bool = True,
    ) -> str:
        """Assign func to bridge cmd, register shortcut, return button"""

        def wrapped_func(editor: Editor) -> None:
            self.call_after_note_saved(functools.partial(func, editor), keepFocus=True)

        self._links[cmd] = wrapped_func

        if keys:

            def on_activated() -> None:
                wrapped_func(self)

            if toggleable:
                # generate a random id for triggering toggle
                id = id or str(randrange(1_000_000))

                def on_hotkey() -> None:
                    on_activated()
                    self.web.eval(
                        f'toggleEditorButton(document.getElementById("{id}"));'
                    )

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
        icon: str | None,
        cmd: str,
        tip: str = "",
        label: str = "",
        id: str | None = None,
        toggleable: bool = False,
        disables: bool = True,
        rightside: bool = True,
    ) -> str:
        title_attribute = tip

        if icon:
            if icon.startswith("qrc:/"):
                iconstr = icon
            elif os.path.isabs(icon):
                iconstr = self.resourceToData(icon)
            else:
                iconstr = f"/_anki/imgs/{icon}.png"
            image_element = f'<img class="topbut" src="{iconstr}">'
        else:
            image_element = ""

        if not label and icon:
            label_element = ""
        elif label:
            label_element = label
        else:
            label_element = cmd

        title_attribute = shortcut(title_attribute)
        id_attribute_assignment = f"id={id}" if id else ""
        class_attribute = "linkb" if rightside else "rounded"
        if not disables:
            class_attribute += " perm"

        return f"""<button tabindex=-1
                        {id_attribute_assignment}
                        class="anki-addon-button {class_attribute}"
                        type="button"
                        title="{title_attribute}"
                        data-cantoggle="{int(toggleable)}"
                        data-command="{cmd}"
                >
                    {image_element}
                    {label_element}
                </button>"""

    def setupShortcuts(self) -> None:
        # if a third element is provided, enable shortcut even when no field selected
        cuts: list[tuple] = []
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

        FieldDialog(self.mw, self.note_type(), parent=self.parentWindow)

    def onCardLayout(self) -> None:
        self.call_after_note_saved(self._onCardLayout)

    def _onCardLayout(self) -> None:
        from aqt.clayout import CardLayout

        if self.card:
            ord = self.card.ord
        else:
            ord = 0

        assert self.note is not None
        CardLayout(
            self.mw,
            self.note,
            ord=ord,
            parent=self.parentWindow,
            fill_empty=False,
        )
        if is_win:
            self.parentWindow.activateWindow()

    # JS->Python bridge
    ######################################################################

    def onBridgeCmd(self, cmd: str) -> Any:
        # focus lost or key/button pressed?
        if cmd.startswith("blur") or cmd.startswith("key"):
            (type, ord_str) = cmd.split(":", 1)
            ord = int(ord_str)
            if type == "blur":
                self.currentField = None
                # run any filters
                if self.note and gui_hooks.editor_did_unfocus_field(
                    False, self.note, ord
                ):
                    # something updated the note; update it after a subsequent focus
                    # event has had time to fire
                    self.mw.progress.timer(
                        100, self.loadNoteKeepingFocus, False, parent=self.widget
                    )
            else:
                if self.note:
                    gui_hooks.editor_did_fire_typing_timer(self.note)

        # focused into field?
        elif cmd.startswith("focus"):
            (type, num) = cmd.split(":", 1)
            self.last_field_index = self.currentField = int(num)
            if self.note:
                gui_hooks.editor_did_focus_field(self.note, self.currentField)

        elif cmd.startswith("toggleStickyAll"):
            model = self.note_type()
            flds = model["flds"]

            any_sticky = any([fld["sticky"] for fld in flds])
            result = []
            for fld in flds:
                if not any_sticky or fld["sticky"]:
                    fld["sticky"] = not fld["sticky"]

                result.append(fld["sticky"])

            update_notetype_legacy(parent=self.mw, notetype=model).run_in_background(
                initiator=self
            )

            return result

        elif cmd.startswith("toggleSticky"):
            (type, num) = cmd.split(":", 1)
            ord = int(num)

            model = self.note_type()
            fld = model["flds"][ord]
            new_state = not fld["sticky"]
            fld["sticky"] = new_state

            update_notetype_legacy(parent=self.mw, notetype=model).run_in_background(
                initiator=self
            )

            return new_state

        elif cmd.startswith("saveTags"):
            if self.note:
                gui_hooks.editor_did_update_tags(self.note)

        elif cmd.startswith("editorState"):
            (_, new_state_id, old_state_id) = cmd.split(":", 2)
            self.signal_state_change(
                EditorState(int(new_state_id)), EditorState(int(old_state_id))
            )

        elif cmd.startswith("ioImageLoaded"):
            (_, path_or_nid_data) = cmd.split(":", 1)
            path_or_nid = json.loads(path_or_nid_data)
            if self.addMode:
                gui_hooks.editor_mask_editor_did_load_image(self, path_or_nid)
            else:
                gui_hooks.editor_mask_editor_did_load_image(
                    self, NoteId(int(path_or_nid))
                )
        elif cmd == "editorReady":
            self._set_ready()

        elif cmd in self._links:
            return self._links[cmd](self)

        else:
            print("uncaught cmd", cmd)

    def signal_state_change(
        self, new_state: EditorState, old_state: EditorState
    ) -> None:
        self.state = new_state
        gui_hooks.editor_state_did_change(self, new_state, old_state)

    # Setting/unsetting the current note
    ######################################################################

    def set_nid(
        self,
        nid: NoteId | None,
        mid: int,
        focus_to: int | None = None,
    ) -> None:
        "Make note with ID `nid` the current note."
        self.nid = nid
        self.currentField = None
        self.load_note(mid, focus_to=focus_to)

    @deprecated(replaced_by=set_nid)
    def set_note(
        self,
        note: Note | None,
        hide: bool = True,
        focusTo: int | None = None,
    ) -> None:
        "Make NOTE the current note."
        self.currentField = None
        if note:
            self.nid = note.id
            self.load_note(mid=note.mid, focus_to=focusTo)
        elif hide:
            self.widget.hide()

    def loadNoteKeepingFocus(self) -> None:
        self.loadNote(self.currentField)

    @on_editor_ready
    def load_note(self, mid: int, focus_to: int | None = None) -> None:

        self.widget.show()

        def oncallback(arg: Any) -> None:
            if not self.nid:
                return
            # we currently do this synchronously to ensure we load before the
            # sidebar on browser startup
            if focus_to is not None:
                self.web.setFocus()
            gui_hooks.editor_did_load_note(self)

        assert self.mw.pm.profile is not None
        js = f"loadNote({json.dumps(self.nid)}, {mid}, {json.dumps(focus_to)}, {json.dumps(self.orig_note_id)});"
        if self.note:
            js = gui_hooks.editor_will_load_note(js, self.note, self)
        self.web.evalWithCallback(
            f'require("anki/ui").loaded.then(() => {{ {js} }})', oncallback
        )

    @deprecated(replaced_by=load_note)
    def loadNote(self, focusTo: int | None = None) -> None:
        assert self.note is not None
        self.load_note(self.note.mid, focus_to=focusTo)

    def call_after_note_saved(
        self, callback: Callable, keepFocus: bool = False
    ) -> None:
        "Save unsaved edits then call callback()."
        if not self.nid:
            # calling code may not expect the callback to fire immediately
            self.mw.progress.single_shot(10, callback)
            return
        self.web.evalWithCallback("saveNow(%d)" % keepFocus, lambda res: callback())

    saveNow = call_after_note_saved

    def fieldsAreBlank(self, previousNote: Note | None = None) -> bool:
        if not self.note:
            return True
        m = self.note_type()
        for c, f in enumerate(self.note.fields):
            f = f.replace("<br>", "").strip()
            notChangedvalues = {"", "<br>"}
            if previousNote and m["flds"][c]["sticky"]:
                notChangedvalues.add(previousNote.fields[c].replace("<br>", "").strip())
            if f not in notChangedvalues:
                return False
        return True

    def cleanup(self) -> None:
        av_player.stop_and_clear_queue_if_caller(self.editorMode)
        self.set_note(None)
        # prevent any remaining evalWithCallback() events from firing after C++ object deleted
        if self.web:
            self.web.cleanup()
            self.web = None  # type: ignore

    # legacy

    setNote = set_note

    # legacy

    def saveAddModeVars(self) -> None:
        pass

    # Audio/video/images
    ######################################################################

    def addMedia(self, path: str, canDelete: bool = False) -> None:
        """Legacy routine used by add-ons to add a media file and update the current field.
        canDelete is ignored."""

        try:
            html = self._addMedia(path)
        except Exception as e:
            showWarning(str(e))
            return

        self.web.eval(f"setFormat('inserthtml', {json.dumps(html)});")

    def resolve_media(self, path: str) -> None:
        """Finish inserting media into a field.
        This expects initial setup to have been done by TemplateButtons.svelte."""
        try:
            html = self._addMedia(path)
        except Exception as e:
            showWarning(str(e))
            return

        self.web.eval(
            f'require("anki/TemplateButtons").resolveMedia({json.dumps(html)})'
        )

    def _addMedia(self, path: str, canDelete: bool = False) -> str:
        """Add to media folder and return local img or sound tag."""
        # copy to media folder
        fname = self.mw.col.media.add_file(path)
        # return a local html link
        return self.fnameToLink(fname)

    def _addMediaFromData(self, fname: str, data: bytes) -> str:
        return self.mw.col.media._legacy_write_data(fname, data)

    def onRecSound(self) -> None:
        aqt.sound.record_audio(
            self.parentWindow,
            self.mw,
            True,
            self.resolve_media,
        )

    # Media downloads
    ######################################################################

    def urlToLink(self, url: str, allowed_suffixes: Iterable[str] = ()) -> str:
        fname = (
            self.urlToFile(url, allowed_suffixes)
            if allowed_suffixes
            else self.urlToFile(url)
        )
        if not fname:
            return '<a href="{}">{}</a>'.format(
                url, html.escape(urllib.parse.unquote(url))
            )
        return self.fnameToLink(fname)

    def fnameToLink(self, fname: str) -> str:
        ext = fname.split(".")[-1].lower()
        if ext in pics:
            name = urllib.parse.quote(fname.encode("utf8"))
            return f'<img src="{name}">'
        else:
            av_player.play_file_with_caller(fname, self.editorMode)
            return f"[sound:{html.escape(fname, quote=False)}]"

    def urlToFile(
        self, url: str, allowed_suffixes: Iterable[str] = pics + audio
    ) -> str | None:
        l = url.lower()
        for suffix in allowed_suffixes:
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
                return self._addPastedImage(data, ext)

        return ""

    def inlinedImageToLink(self, src: str) -> str:
        fname = self.inlinedImageToFilename(src)
        if fname:
            return self.fnameToLink(fname)

        return ""

    def _pasted_image_filename(self, data: bytes, ext: str) -> str:
        csum = checksum(data)
        return f"paste-{csum}.{ext}"

    def _read_pasted_image(self, mime: QMimeData) -> str:
        image = QImage(mime.imageData())
        buffer = QBuffer()
        buffer.open(QBuffer.OpenModeFlag.ReadWrite)
        if self.mw.col.get_config_bool(Config.Bool.PASTE_IMAGES_AS_PNG):
            ext = "png"
            quality = 50
        else:
            ext = "jpg"
            quality = 80
        image.save(buffer, ext, quality)
        buffer.reset()
        data = bytes(buffer.readAll())  # type: ignore
        fname = self._pasted_image_filename(data, ext)
        path = namedtmp(fname)
        with open(path, "wb") as file:
            file.write(data)

        return path

    def _addPastedImage(self, data: bytes, ext: str) -> str:
        # hash and write
        fname = self._pasted_image_filename(data, ext)
        return self._addMediaFromData(fname, data)

    def _retrieveURL(self, url: str) -> str | None:
        "Download file into media folder and return local filename or None."
        local = url.lower().startswith("file://")
        # fetch it into a temporary folder
        self.mw.progress.start(immediate=not local, parent=self.parentWindow)
        content_type = None
        error_msg: str | None = None
        try:
            if local:
                # urllib doesn't understand percent-escaped utf8, but requires things like
                # '#' to be escaped.
                url = urllib.parse.unquote(url)
                url = url.replace("%", "%25")
                url = url.replace("#", "%23")
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

    def doDrop(
        self, html: str, internal: bool, extended: bool, cursor_pos: QPoint
    ) -> None:
        def pasteIfField(ret: bool) -> None:
            if ret:
                self.doPaste(html, internal, extended)

        zoom = self.web.zoomFactor()
        x, y = int(cursor_pos.x() / zoom), int(cursor_pos.y() / zoom)

        self.web.evalWithCallback(f"focusIfField({x}, {y});", pasteIfField)

    def onPaste(self) -> None:
        self.web.onPaste()

    def onCut(self) -> None:
        self.web.onCut()

    def onCopy(self) -> None:
        self.web.onCopy()

    # Image occlusion
    ######################################################################

    def current_notetype_is_image_occlusion(self) -> bool:
        if not self.note:
            return False

        return (
            self.note_type().get("originalStockKind", None)
            == StockNotetype.OriginalStockKind.ORIGINAL_STOCK_KIND_IMAGE_OCCLUSION
        )

    def setup_mask_editor(self, image_path: str) -> None:
        try:
            if self.editorMode == EditorMode.ADD_CARDS:
                self.setup_mask_editor_for_new_note(image_path=image_path)
            else:
                assert self.note is not None
                self.setup_mask_editor_for_existing_note(image_path=image_path)
        except Exception as e:
            showWarning(str(e))

    def setup_mask_editor_for_new_note(self, image_path: str):
        """Set-up IO mask editor for adding new notes
        Presupposes that active editor notetype is an image occlusion notetype
        Args:
            image_path: Absolute path to image.
        """
        self.web.eval(
            'require("anki/ui").loaded.then(() =>'
            f"setupMaskEditorForNewNote({json.dumps(image_path)})"
            "); "
        )

    def setup_mask_editor_for_existing_note(self, image_path: str | None = None):
        """Set-up IO mask editor for editing existing notes
        Presupposes that active editor notetype is an image occlusion notetype
        Args:
            image_path: (Optional) Absolute path to image that should replace current
              image
        """
        self.web.eval(
            'require("anki/ui").loaded.then(() =>'
            f"setupMaskEditorForExistingNote({json.dumps(image_path)})"
            "); "
        )

    # Links from HTML
    ######################################################################

    def _init_links(self) -> None:
        self._links: dict[str, Callable] = dict(
            fields=Editor.onFields,
            cards=Editor.onCardLayout,
            record=Editor.onRecSound,
            paste=Editor.onPaste,
            cut=Editor.onCut,
            copy=Editor.onCopy,
        )

    @property
    def note(self) -> Note | None:
        if self.nid is None:
            return None
        return self.mw.col.get_note(self.nid)

    def note_type(self) -> NotetypeDict:
        assert self.note is not None
        note_type = self.note.note_type()
        assert note_type is not None
        return note_type


# Pasting, drag & drop, and keyboard layouts
######################################################################


class EditorWebView(AnkiWebView):
    def __init__(self, parent: QWidget, editor: Editor) -> None:
        AnkiWebView.__init__(self, kind=AnkiWebViewKind.EDITOR)
        self.editor = editor
        self.setAcceptDrops(True)
        self._store_field_content_on_next_clipboard_change = False
        # when we detect the user copying from a field, we store the content
        # here, and use it when they paste, so we avoid filtering field content
        self._internal_field_text_for_paste: str | None = None
        self._last_known_clipboard_mime: QMimeData | None = None
        clip = self.editor.mw.app.clipboard()
        assert clip is not None
        clip.dataChanged.connect(self._on_clipboard_change)
        self.settings().setAttribute(  # type: ignore
            QWebEngineSettings.WebAttribute.JavascriptCanPaste, True
        )
        self.settings().setAttribute(  # type: ignore
            QWebEngineSettings.WebAttribute.JavascriptCanAccessClipboard, True
        )
        gui_hooks.editor_web_view_did_init(self)

    def _on_clipboard_change(
        self, mode: QClipboard.Mode = QClipboard.Mode.Clipboard
    ) -> None:
        self._last_known_clipboard_mime = self._clipboard().mimeData(mode)
        if self._store_field_content_on_next_clipboard_change:
            # if the flag was set, save the field data
            self._internal_field_text_for_paste = self._get_clipboard_html_for_field(
                mode
            )
            self._store_field_content_on_next_clipboard_change = False
        elif self._internal_field_text_for_paste != self._get_clipboard_html_for_field(
            mode
        ):
            # if we've previously saved the field, blank it out if the clipboard state has changed
            self._internal_field_text_for_paste = None

    def _get_clipboard_html_for_field(self, mode: QClipboard.Mode) -> str | None:
        clip = self._clipboard()
        if not (mime := clip.mimeData(mode)):
            return None
        if not mime.hasHtml():
            return None
        return mime.html()

    def onCut(self) -> None:
        self.triggerPageAction(QWebEnginePage.WebAction.Cut)

    def onCopy(self) -> None:
        self.triggerPageAction(QWebEnginePage.WebAction.Copy)

    def _wantsExtendedPaste(self) -> bool:
        strip_html = self.editor.mw.col.get_config_bool(
            Config.Bool.PASTE_STRIPS_FORMATTING
        )
        if KeyboardModifiersPressed().shift:
            strip_html = not strip_html
        return not strip_html

    def _onPaste(self, mode: QClipboard.Mode) -> None:
        # Since _on_clipboard_change doesn't always trigger properly on macOS, we do a double check if any changes were made before pasting
        clipboard = self._clipboard()
        if self._last_known_clipboard_mime != clipboard.mimeData(mode):
            self._on_clipboard_change(mode)
        extended = self._wantsExtendedPaste()
        if html := self._internal_field_text_for_paste:
            print("reuse internal")
            self.editor.doPaste(html, True, extended)
        else:
            if not (mime := clipboard.mimeData(mode=mode)):
                return
            print("use clipboard")
            html, internal = self._processMime(mime, extended)
            if html:
                self.editor.doPaste(html, internal, extended)

    def onPaste(self) -> None:
        self.triggerPageAction(QWebEnginePage.WebAction.Paste)

    def onMiddleClickPaste(self) -> None:
        self._onPaste(QClipboard.Mode.Selection)

    # def dragEnterEvent(self, evt: QDragEnterEvent | None) -> None:
    #     assert evt is not None
    #     evt.accept()

    # def dropEvent(self, evt: QDropEvent | None) -> None:
    #     assert evt is not None
    #     extended = self._wantsExtendedPaste()
    #     mime = evt.mimeData()
    #     assert mime is not None

    #     if (
    #         self.editor.state is EditorState.IO_PICKER
    #         and (html := self._processUrls(mime, allowed_suffixes=pics))
    #         and (path := self.editor.extract_img_path_from_html(html))
    #     ):
    #         self.editor.setup_mask_editor(path)
    #         return

    #     evt_pos = evt.position()
    #     cursor_pos = QPoint(int(evt_pos.x()), int(evt_pos.y()))

    #     if evt.source() and mime.hasHtml():
    #         # don't filter html from other fields
    #         html, internal = mime.html(), True
    #     else:
    #         html, internal = self._processMime(mime, extended, drop_event=True)

    #     if not html:
    #         return

    #     self.editor.doDrop(html, internal, extended, cursor_pos)

    # returns (html, isInternal)
    def _processMime(
        self, mime: QMimeData, extended: bool = False, drop_event: bool = False
    ) -> tuple[str, bool]:
        # print("html=%s image=%s urls=%s txt=%s" % (
        #     mime.hasHtml(), mime.hasImage(), mime.hasUrls(), mime.hasText()))
        # print("html", mime.html())
        # print("urls", mime.urls())
        # print("text", mime.text())

        internal = False

        mime = gui_hooks.editor_will_process_mime(
            mime, self, internal, extended, drop_event
        )

        # try various content types in turn
        if mime.hasHtml():
            html_content = mime.html()[11:] if internal else mime.html()
            return html_content, internal

        # given _processUrls' extra allowed_suffixes kwarg, placate the typechecker
        def process_url(mime: QMimeData, extended: bool = False) -> str | None:
            return self._processUrls(mime, extended)

        # favour url if it's a local link
        if (
            mime.hasUrls()
            and (urls := mime.urls())
            and urls[0].toString().startswith("file://")
        ):
            types = (process_url, self._processImage, self._processText)
        else:
            types = (self._processImage, process_url, self._processText)

        for fn in types:
            html = fn(mime, extended)
            if html:
                return html, True
        return "", False

    def _processUrls(
        self,
        mime: QMimeData,
        extended: bool = False,
        allowed_suffixes: Iterable[str] = (),
    ) -> str | None:
        if not mime.hasUrls():
            return None

        buf = ""
        for qurl in mime.urls():
            url = qurl.toString()
            # chrome likes to give us the URL twice with a \n
            if lines := url.splitlines():
                url = lines[0]
                buf += self.editor.urlToLink(url, allowed_suffixes=allowed_suffixes)

        return buf

    def _processText(self, mime: QMimeData, extended: bool = False) -> str | None:
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
                    # if the user is pasting an image or sound link, convert it to local, otherwise paste as a hyperlink
                    link = self.editor.urlToLink(token)
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

    def _processImage(self, mime: QMimeData, extended: bool = False) -> str | None:
        if not mime.hasImage():
            return None
        path = self.editor._read_pasted_image(mime)
        fname = self.editor._addMedia(path)

        return fname

    def contextMenuEvent(self, evt: QContextMenuEvent | None) -> None:
        m = QMenu(self)
        gui_hooks.editor_will_show_context_menu(self, m)
        m.popup(QCursor.pos())

    def _clipboard(self) -> QClipboard:
        clipboard = self.editor.mw.app.clipboard()
        assert clipboard is not None
        return clipboard
