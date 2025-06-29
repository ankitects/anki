# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import base64
import functools
import json
import mimetypes
import os
from collections.abc import Callable
from dataclasses import dataclass
from enum import Enum
from random import randrange
from typing import Any

from anki._legacy import deprecated
from anki.cards import Card
from anki.hooks import runFilter
from anki.models import NotetypeId
from anki.notes import Note, NoteId
from anki.utils import is_win
from aqt import AnkiQt, gui_hooks
from aqt.qt import *
from aqt.sound import av_player
from aqt.utils import shortcut, showWarning
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


@dataclass
class NoteInfo:
    "Used to hold partial note info fetched from the webview"

    id: NoteId | None
    mid: NotetypeId
    fields: list[str]

    def __post_init__(self) -> None:
        if self.id is not None:
            self.id = NoteId(int(self.id))
        if self.mid is not None:
            self.mid = NotetypeId(int(self.mid))


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
        self._ready = False
        self._ready_callbacks: list[Callable[[], None]] = []
        self._init_links()
        self.setupOuter()
        self.add_webview()
        self.setupWeb()
        self.setupShortcuts()

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
        self.web.hide_while_preserving_layout()
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

        def on_note_info(note_info: NoteInfo) -> None:
            note_type = self.mw.col.models.get(note_info.mid)
            assert note_type is not None
            FieldDialog(self.mw, note_type, parent=self.parentWindow)

        self.get_note_info(on_note_info)

    def onCardLayout(self) -> None:
        self.call_after_note_saved(self._onCardLayout)

    def _onCardLayout(self) -> None:
        from aqt.clayout import CardLayout

        if self.card:
            ord = self.card.ord
        else:
            ord = 0

        def on_note_info(note_info: NoteInfo) -> None:
            if note_info.id:
                note = self.mw.col.get_note(note_info.id)
            else:
                note = Note(self.mw.col, note_info.mid)
                note.fields = note_info.fields
            CardLayout(
                self.mw,
                note,
                ord=ord,
                parent=self.parentWindow,
                fill_empty=False,
            )
            if is_win:
                self.parentWindow.activateWindow()

        self.get_note_info(on_note_info)

    # JS->Python bridge
    ######################################################################

    def onBridgeCmd(self, cmd: str) -> Any:
        # focus lost or key/button pressed?
        if cmd.startswith("blur") or cmd.startswith("key"):
            (type, ord_str) = cmd.split(":", 1)
            ord = int(ord_str)
            if type == "blur":
                self.currentField = None
            else:
                pass

        # focused into field?
        elif cmd.startswith("focus"):
            (type, num) = cmd.split(":", 1)
            self.last_field_index = self.currentField = int(num)

        elif cmd.startswith("saveTags"):
            pass

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
        self.web.evalWithCallback(
            f'require("anki/ui").loaded.then(() => {{ {js} }})', oncallback
        )

    def reload_note(self) -> None:
        self.web.eval("reloadNote();")

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

    def cleanup(self) -> None:
        av_player.stop_and_clear_queue_if_caller(self.editorMode)
        self.set_note(None)
        # prevent any remaining evalWithCallback() events from firing after C++ object deleted
        if self.web:
            self.web.cleanup()
            self.web = None  # type: ignore

    setNote = set_note

    # Paste/drag&drop
    ######################################################################

    def onPaste(self) -> None:
        self.web.onPaste()

    def onCut(self) -> None:
        self.web.onCut()

    def onCopy(self) -> None:
        self.web.onCopy()

    # Image occlusion
    ######################################################################

    def setup_mask_editor(self, image_path: str) -> None:
        try:
            if self.editorMode == EditorMode.ADD_CARDS:
                self.setup_mask_editor_for_new_note(image_path=image_path)
            else:
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
            paste=Editor.onPaste,
            cut=Editor.onCut,
            copy=Editor.onCopy,
        )

    def get_note_info(self, on_done: Callable[[NoteInfo], None]) -> None:
        def wrapped_on_done(note_info: dict[str, Any]) -> None:
            on_done(NoteInfo(**note_info))

        self.web.evalWithCallback("getNoteInfo()", wrapped_on_done)


# Pasting, drag & drop, and keyboard layouts
######################################################################


class EditorWebView(AnkiWebView):
    def __init__(self, parent: QWidget, editor: Editor) -> None:
        AnkiWebView.__init__(self, kind=AnkiWebViewKind.EDITOR)
        self.editor = editor
        self.setAcceptDrops(True)
        self.settings().setAttribute(  # type: ignore
            QWebEngineSettings.WebAttribute.JavascriptCanPaste, True
        )
        self.settings().setAttribute(  # type: ignore
            QWebEngineSettings.WebAttribute.JavascriptCanAccessClipboard, True
        )
        gui_hooks.editor_web_view_did_init(self)

    def onCut(self) -> None:
        self.triggerPageAction(QWebEnginePage.WebAction.Cut)

    def onCopy(self) -> None:
        self.triggerPageAction(QWebEnginePage.WebAction.Copy)

    def onPaste(self) -> None:
        self.triggerPageAction(QWebEnginePage.WebAction.Paste)
        self.triggerPageAction(QWebEnginePage.WebAction.Paste)
