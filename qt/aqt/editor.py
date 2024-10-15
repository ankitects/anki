# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import base64
import functools
import html
import itertools
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
from typing import Any, Match, cast

import bs4
import requests
from bs4 import BeautifulSoup

import aqt
import aqt.forms
import aqt.operations
import aqt.sound
from anki._legacy import deprecated
from anki.cards import Card
from anki.collection import Config, SearchNode
from anki.consts import MODEL_CLOZE
from anki.hooks import runFilter
from anki.httpclient import HttpClient
from anki.models import NotetypeDict, NotetypeId, StockNotetype
from anki.notes import Note, NoteFieldsCheckResult, NoteId
from anki.utils import checksum, is_lin, is_mac, is_win, namedtmp
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
    openFolder,
    openHelp,
    qtMenuShortcutWorkaround,
    restoreGeom,
    saveGeom,
    shortcut,
    show_in_folder,
    showInfo,
    showWarning,
    tooltip,
    tr,
)
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
        self.note: Note | None = None
        # legacy argument provided?
        if addMode is not None:
            editor_mode = EditorMode.ADD_CARDS if addMode else EditorMode.EDIT_CURRENT
        self.addMode = editor_mode is EditorMode.ADD_CARDS
        self.editorMode = editor_mode
        self.currentField: int | None = None
        # Similar to currentField, but not set to None on a blur. May be
        # outside the bounds of the current notetype.
        self.last_field_index: int | None = None
        # current card, for card layout
        self.card: Card | None = None
        self.state: EditorState = EditorState.INITIAL
        self._init_links()
        self.setupOuter()
        self.add_webview()
        self.setupWeb()
        self.setupShortcuts()
        gui_hooks.editor_did_init(self)

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
        if self.editorMode == EditorMode.ADD_CARDS:
            mode = "add"
        elif self.editorMode == EditorMode.BROWSER:
            mode = "browse"
        else:
            mode = "review"

        # then load page
        self.web.stdHtml(
            "",
            css=["css/editor.css"],
            js=[
                "js/mathjax.js",
                "js/editor.js",
            ],
            context=self,
            default_css=False,
        )
        self.web.eval(f"setupEditor('{mode}')")
        self.web.show()

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
        cmd_to_toggle_button = "toggleEditorButton(this);" if toggleable else ""
        id_attribute_assignment = f"id={id}" if id else ""
        class_attribute = "linkb" if rightside else "rounded"
        if not disables:
            class_attribute += " perm"

        return f"""<button tabindex=-1
                        {id_attribute_assignment}
                        class="{class_attribute}"
                        type="button"
                        title="{title_attribute}"
                        onclick="pycmd('{cmd}');{cmd_to_toggle_button}return false;"
                        onmousedown="window.event.preventDefault();"
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

            try:
                self.note.fields[ord] = self.mungeHTML(txt)
            except IndexError:
                print("ignored late blur after notetype change")
                return

            if not self.addMode:
                self._save_current_note()
            if type == "blur":
                self.currentField = None
                # run any filters
                if gui_hooks.editor_did_unfocus_field(False, self.note, ord):
                    # something updated the note; update it after a subsequent focus
                    # event has had time to fire
                    self.mw.progress.timer(
                        100, self.loadNoteKeepingFocus, False, parent=self.widget
                    )
                else:
                    self._check_and_update_duplicate_display_async()
            else:
                gui_hooks.editor_did_fire_typing_timer(self.note)
                self._check_and_update_duplicate_display_async()

        # focused into field?
        elif cmd.startswith("focus"):
            (type, num) = cmd.split(":", 1)
            self.last_field_index = self.currentField = int(num)
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

        elif cmd.startswith("lastTextColor"):
            (_, textColor) = cmd.split(":", 1)
            assert self.mw.pm.profile is not None
            self.mw.pm.profile["lastTextColor"] = textColor

        elif cmd.startswith("lastHighlightColor"):
            (_, highlightColor) = cmd.split(":", 1)
            assert self.mw.pm.profile is not None
            self.mw.pm.profile["lastHighlightColor"] = highlightColor

        elif cmd.startswith("saveTags"):
            (type, tagsJson) = cmd.split(":", 1)
            self.note.tags = json.loads(tagsJson)

            gui_hooks.editor_did_update_tags(self.note)
            if not self.addMode:
                self._save_current_note()

        elif cmd.startswith("setTagsCollapsed"):
            (type, collapsed_string) = cmd.split(":", 1)
            collapsed = collapsed_string == "true"
            self.setTagsCollapsed(collapsed)

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

        elif cmd in self._links:
            return self._links[cmd](self)

        else:
            print("uncaught cmd", cmd)

    def mungeHTML(self, txt: str) -> str:
        return gui_hooks.editor_will_munge_html(txt, self)

    def signal_state_change(
        self, new_state: EditorState, old_state: EditorState
    ) -> None:
        self.state = new_state
        gui_hooks.editor_state_did_change(self, new_state, old_state)

    # Setting/unsetting the current note
    ######################################################################

    def set_note(
        self, note: Note | None, hide: bool = True, focusTo: int | None = None
    ) -> None:
        "Make NOTE the current note."
        self.note = note
        self.currentField = None
        if self.note:
            self.loadNote(focusTo=focusTo)
        elif hide:
            self.widget.hide()

    def loadNoteKeepingFocus(self) -> None:
        self.loadNote(self.currentField)

    def loadNote(self, focusTo: int | None = None) -> None:
        if not self.note:
            return

        data = [
            (fld, self.mw.col.media.escape_media_filenames(val))
            for fld, val in self.note.items()
        ]

        note_type = self.note_type()
        flds = note_type["flds"]
        collapsed = [fld["collapsed"] for fld in flds]
        plain_texts = [fld.get("plainText", False) for fld in flds]
        descriptions = [fld.get("description", "") for fld in flds]
        notetype_meta = {"id": self.note.mid, "modTime": note_type["mod"]}

        self.widget.show()

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

        assert self.mw.pm.profile is not None
        text_color = self.mw.pm.profile.get("lastTextColor", "#0000ff")
        highlight_color = self.mw.pm.profile.get("lastHighlightColor", "#0000ff")

        js = f"""
            saveSession();
            setFields({json.dumps(data)});
            setIsImageOcclusion({json.dumps(self.current_notetype_is_image_occlusion())});
            setNotetypeMeta({json.dumps(notetype_meta)});
            setCollapsed({json.dumps(collapsed)});
            setPlainTexts({json.dumps(plain_texts)});
            setDescriptions({json.dumps(descriptions)});
            setFonts({json.dumps(self.fonts())});
            focusField({json.dumps(focusTo)});
            setNoteId({json.dumps(self.note.id)});
            setColorButtons({json.dumps([text_color, highlight_color])});
            setTags({json.dumps(self.note.tags)});
            setTagsCollapsed({json.dumps(self.mw.pm.tags_collapsed(self.editorMode))});
            setMathjaxEnabled({json.dumps(self.mw.col.get_config("renderMathjax", True))});
            setShrinkImages({json.dumps(self.mw.col.get_config("shrinkEditorImages", True))});
            setCloseHTMLTags({json.dumps(self.mw.col.get_config("closeHTMLTags", True))});
            triggerChanges();
            """

        if self.addMode:
            sticky = [field["sticky"] for field in self.note_type()["flds"]]
            js += " setSticky(%s);" % json.dumps(sticky)

        if (
            self.editorMode != EditorMode.ADD_CARDS
            and self.current_notetype_is_image_occlusion()
        ):
            io_options = self._create_edit_io_options(note_id=self.note.id)
            js += " setupMaskEditor(%s);" % json.dumps(io_options)

        js = gui_hooks.editor_will_load_note(js, self.note, self)
        self.web.evalWithCallback(
            f'require("anki/ui").loaded.then(() => {{ {js} }})', oncallback
        )

    def _save_current_note(self) -> None:
        "Call after note is updated with data from webview."
        if not self.note:
            return

        update_note(parent=self.widget, note=self.note).run_in_background(
            initiator=self
        )

    def fonts(self) -> list[tuple[str, int, bool]]:
        return [
            (gui_hooks.editor_will_use_font_for_field(f["font"]), f["size"], f["rtl"])
            for f in self.note_type()["flds"]
        ]

    def call_after_note_saved(
        self, callback: Callable, keepFocus: bool = False
    ) -> None:
        "Save unsaved edits then call callback()."
        if not self.note:
            # calling code may not expect the callback to fire immediately
            self.mw.progress.single_shot(10, callback)
            return
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
        assert self.note is not None
        cols = [""] * len(self.note.fields)
        cloze_hint = ""
        if result == NoteFieldsCheckResult.DUPLICATE:
            cols[0] = "dupe"
        elif result == NoteFieldsCheckResult.NOTETYPE_NOT_CLOZE:
            cloze_hint = tr.adding_cloze_outside_cloze_notetype()
        elif result == NoteFieldsCheckResult.FIELD_NOT_CLOZE:
            cloze_hint = tr.adding_cloze_outside_cloze_field()

        self.web.eval(
            'require("anki/ui").loaded.then(() => {'
            f"setBackgrounds({json.dumps(cols)});\n"
            f"setClozeHint({json.dumps(cloze_hint)});\n"
            "}); "
        )

    def showDupes(self) -> None:
        assert self.note is not None
        aqt.dialogs.open(
            "Browser",
            self.mw,
            search=(
                SearchNode(
                    dupe=SearchNode.Dupe(
                        notetype_id=self.note_type()["id"],
                        first_field=self.note.fields[0],
                    )
                ),
            ),
        )

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
        self.set_note(None)
        # prevent any remaining evalWithCallback() events from firing after C++ object deleted
        if self.web:
            self.web.cleanup()
            self.web = None  # type: ignore

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
        border = theme_manager.var(colors.BORDER)
        self.tags.setStyleSheet(f"border: 1px solid {border}")
        tb.addWidget(self.tags, 1, 1)
        g.setLayout(tb)
        self.outerLayout.addWidget(g)

    def updateTags(self) -> None:
        if self.tags.col != self.mw.col:
            self.tags.setCol(self.mw.col)
        if not self.tags.text() or not self.addMode:
            assert self.note is not None
            self.tags.setText(self.note.string_tags().strip())

    def on_tag_focus_lost(self) -> None:
        assert self.note is not None
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
        """Show a file selection screen, then add the selected media.
        This expects initial setup to have been done by TemplateButtons.svelte."""
        extension_filter = " ".join(
            f"*.{extension}" for extension in sorted(itertools.chain(pics, audio))
        )
        filter = f"{tr.editing_media()} ({extension_filter})"

        def accept(file: str) -> None:
            self.resolve_media(file)

        file = getFile(
            parent=self.widget,
            title=tr.editing_add_media(),
            cb=cast(Callable[[Any], None], accept),
            filter=filter,
            key="media",
        )

        self.parentWindow.activateWindow()

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

    def urlToLink(self, url: str) -> str:
        fname = self.urlToFile(url)
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
            av_player.play_file(fname)
            return f"[sound:{html.escape(fname, quote=False)}]"

    def urlToFile(self, url: str) -> str | None:
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

        self.web.evalWithCallback(
            f"focusIfField({cursor_pos.x()}, {cursor_pos.y()});", pasteIfField
        )

    def onPaste(self) -> None:
        self.web.onPaste()

    def onCutOrCopy(self) -> None:
        self.web.user_cut_or_copied()

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
                self.setup_mask_editor_for_new_note(
                    image_path=image_path, notetype_id=0
                )
            else:
                assert self.note is not None
                self.setup_mask_editor_for_existing_note(
                    note_id=self.note.id, image_path=image_path
                )
        except Exception as e:
            showWarning(str(e))

    def select_image_and_occlude(self) -> None:
        """Show a file selection screen, then get selected image path."""
        extension_filter = " ".join(
            f"*.{extension}" for extension in sorted(itertools.chain(pics))
        )
        filter = f"{tr.editing_media()} ({extension_filter})"

        file = getFile(
            parent=self.widget,
            title=tr.editing_add_media(),
            cb=cast(Callable[[Any], None], self.setup_mask_editor),
            filter=filter,
            key="media",
        )

        self.parentWindow.activateWindow()

    def select_image_from_clipboard_and_occlude(self) -> None:
        """Set up the mask editor for the image in the clipboard."""

        clipboard = self.mw.app.clipboard()
        assert clipboard is not None
        mime = clipboard.mimeData()
        assert mime is not None
        if not mime.hasImage():
            showWarning(tr.editing_no_image_found_on_clipboard())
            return
        path = self._read_pasted_image(mime)
        self.setup_mask_editor(path)
        self.parentWindow.activateWindow()

    def setup_mask_editor_for_new_note(
        self,
        image_path: str,
        notetype_id: NotetypeId | int = 0,
    ):
        """Set-up IO mask editor for adding new notes
        Presupposes that active editor notetype is an image occlusion notetype
        Args:
            image_path: Absolute path to image.
            notetype_id: ID of note type to use. Provided ID must belong to an
              image occlusion notetype. Set this to 0 to auto-select the first
              found image occlusion notetype in the user's collection.
        """
        image_field_html = self._addMedia(image_path)
        io_options = self._create_add_io_options(
            image_path=image_path,
            image_field_html=image_field_html,
            notetype_id=notetype_id,
        )
        self._setup_mask_editor(io_options)

    def setup_mask_editor_for_existing_note(
        self, note_id: NoteId, image_path: str | None = None
    ):
        """Set-up IO mask editor for editing existing notes
        Presupposes that active editor notetype is an image occlusion notetype
        Args:
            note_id: ID of note to edit.
            image_path: (Optional) Absolute path to image that should replace current
              image
        """
        io_options = self._create_edit_io_options(note_id)
        if image_path:
            image_field_html = self._addMedia(image_path)
            self.web.eval(f"resetIOImage({json.dumps(image_path)})")
            self.web.eval(f"setImageField({json.dumps(image_field_html)})")
        self._setup_mask_editor(io_options)

    def reset_image_occlusion(self) -> None:
        self.web.eval("resetIOImageLoaded()")

    def update_occlusions_field(self) -> None:
        self.web.eval("saveOcclusions()")

    def _setup_mask_editor(self, io_options: dict):
        self.web.eval(
            'require("anki/ui").loaded.then(() =>'
            f"setupMaskEditor({json.dumps(io_options)})"
            "); "
        )

    @staticmethod
    def _create_add_io_options(
        image_path: str, image_field_html: str, notetype_id: NotetypeId | int = 0
    ) -> dict:
        return {
            "mode": {"kind": "add", "imagePath": image_path, "notetypeId": notetype_id},
            "html": image_field_html,
        }

    @staticmethod
    def _create_edit_io_options(note_id: NoteId) -> dict:
        return {"mode": {"kind": "edit", "noteId": note_id}}

    # Legacy editing routines
    ######################################################################

    _js_legacy = "this routine has been moved into JS, and will be removed soon"

    @deprecated(info=_js_legacy)
    def onHtmlEdit(self) -> None:
        field = self.currentField
        self.call_after_note_saved(lambda: self._onHtmlEdit(field))

    @deprecated(info=_js_legacy)
    def _onHtmlEdit(self, field: int) -> None:
        assert self.note is not None
        d = QDialog(self.widget, Qt.WindowType.Window)
        form = aqt.forms.edithtml.Ui_Dialog()
        form.setupUi(d)
        restoreGeom(d, "htmlEditor")
        disable_help_button(d)
        qconnect(
            form.buttonBox.helpRequested, lambda: openHelp(HelpPage.EDITING_FEATURES)
        )
        font = QFont("Courier")
        font.setStyleHint(QFont.StyleHint.TypeWriter)
        form.textEdit.setFont(font)
        form.textEdit.setPlainText(self.note.fields[field])
        d.show()
        form.textEdit.moveCursor(QTextCursor.MoveOperation.End)
        d.exec()
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
        if self.note_type()["type"] != MODEL_CLOZE:
            if self.addMode:
                tooltip(tr.editing_warning_cloze_deletions_will_not_work())
            else:
                showInfo(tr.editing_to_make_a_cloze_deletion_on())
                return
        # find the highest existing cloze
        highest = 0
        assert self.note is not None
        for _, val in list(self.note.items()):
            m = re.findall(r"\{\{c(\d+)::", val)
            if m:
                highest = max(highest, sorted(int(x) for x in m)[-1])
        # reuse last?
        if not KeyboardModifiersPressed().alt:
            highest += 1
        # must start at 1
        highest = max(1, highest)
        self.web.eval("wrap('{{c%d::', '}}');" % highest)

    def setupForegroundButton(self) -> None:
        assert self.mw.pm.profile is not None
        self.fcolour = self.mw.pm.profile.get("lastColour", "#00f")

    # use last colour
    @deprecated(info=_js_legacy)
    def onForeground(self) -> None:
        self._wrapWithColour(self.fcolour)

    # choose new colour
    @deprecated(info=_js_legacy)
    def onChangeCol(self) -> None:
        if is_lin:
            new = QColorDialog.getColor(
                QColor(self.fcolour),
                None,
                None,
                QColorDialog.ColorDialogOption.DontUseNativeDialog,
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
        assert self.mw.pm.profile is not None
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
            assert a is not None
            qconnect(a.triggered, handler)
            a.setShortcut(QKeySequence(shortcut))

        qtMenuShortcutWorkaround(m)

        m.exec(QCursor.pos())

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

    def toggleMathjax(self) -> None:
        self.mw.col.set_config(
            "renderMathjax", not self.mw.col.get_config("renderMathjax", False)
        )
        # hackily redraw the page
        self.setupWeb()
        self.loadNoteKeepingFocus()

    def toggleShrinkImages(self) -> None:
        self.mw.col.set_config(
            "shrinkEditorImages",
            not self.mw.col.get_config("shrinkEditorImages", True),
        )

    def toggleCloseHTMLTags(self) -> None:
        self.mw.col.set_config(
            "closeHTMLTags",
            not self.mw.col.get_config("closeHTMLTags", True),
        )

    def setTagsCollapsed(self, collapsed: bool) -> None:
        aqt.mw.pm.set_tags_collapsed(self.editorMode, collapsed)

    # Links from HTML
    ######################################################################

    def _init_links(self) -> None:
        self._links: dict[str, Callable] = dict(
            fields=Editor.onFields,
            cards=Editor.onCardLayout,
            bold=Editor.toggleBold,
            italic=Editor.toggleItalic,
            underline=Editor.toggleUnderline,
            super=Editor.toggleSuper,
            sub=Editor.toggleSub,
            clear=Editor.removeFormat,
            colour=Editor.onForeground,
            changeCol=Editor.onChangeCol,
            cloze=Editor.onCloze,
            attach=Editor.onAddMedia,
            record=Editor.onRecSound,
            more=Editor.onAdvanced,
            dupes=Editor.showDupes,
            paste=Editor.onPaste,
            cutOrCopy=Editor.onCutOrCopy,
            htmlEdit=Editor.onHtmlEdit,
            mathjaxInline=Editor.insertMathjaxInline,
            mathjaxBlock=Editor.insertMathjaxBlock,
            mathjaxChemistry=Editor.insertMathjaxChemistry,
            toggleMathjax=Editor.toggleMathjax,
            toggleShrinkImages=Editor.toggleShrinkImages,
            toggleCloseHTMLTags=Editor.toggleCloseHTMLTags,
            addImageForOcclusion=Editor.select_image_and_occlude,
            addImageForOcclusionFromClipboard=Editor.select_image_from_clipboard_and_occlude,
        )

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
        gui_hooks.editor_web_view_did_init(self)

    def user_cut_or_copied(self) -> None:
        self._store_field_content_on_next_clipboard_change = True
        self._internal_field_text_for_paste = None

    def _on_clipboard_change(self) -> None:
        self._last_known_clipboard_mime = self._clipboard().mimeData()
        if self._store_field_content_on_next_clipboard_change:
            # if the flag was set, save the field data
            self._internal_field_text_for_paste = self._get_clipboard_html_for_field()
            self._store_field_content_on_next_clipboard_change = False
        elif (
            self._internal_field_text_for_paste != self._get_clipboard_html_for_field()
        ):
            # if we've previously saved the field, blank it out if the clipboard state has changed
            self._internal_field_text_for_paste = None

    def _get_clipboard_html_for_field(self):
        clip = self._clipboard()
        mime = clip.mimeData()
        assert mime is not None
        if not mime.hasHtml():
            return
        return mime.html()

    def onCut(self) -> None:
        self.triggerPageAction(QWebEnginePage.WebAction.Cut)

    def onCopy(self) -> None:
        self.triggerPageAction(QWebEnginePage.WebAction.Copy)

    def on_copy_image(self) -> None:
        self.triggerPageAction(QWebEnginePage.WebAction.CopyImageToClipboard)

    def _opened_context_menu_on_image(self) -> bool:
        context_menu_request = self.lastContextMenuRequest()
        assert context_menu_request is not None
        return (
            context_menu_request.mediaType()
            == context_menu_request.MediaType.MediaTypeImage
        )

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
        if self._last_known_clipboard_mime != clipboard.mimeData():
            self._on_clipboard_change()
        extended = self._wantsExtendedPaste()
        if html := self._internal_field_text_for_paste:
            print("reuse internal")
            self.editor.doPaste(html, True, extended)
        else:
            print("use clipboard")
            mime = clipboard.mimeData(mode=mode)
            assert mime is not None
            html, internal = self._processMime(mime, extended)
            if html:
                self.editor.doPaste(html, internal, extended)

    def onPaste(self) -> None:
        self._onPaste(QClipboard.Mode.Clipboard)

    def onMiddleClickPaste(self) -> None:
        self._onPaste(QClipboard.Mode.Selection)

    def dragEnterEvent(self, evt: QDragEnterEvent | None) -> None:
        assert evt is not None
        evt.accept()

    def dropEvent(self, evt: QDropEvent | None) -> None:
        assert evt is not None
        extended = self._wantsExtendedPaste()
        mime = evt.mimeData()
        assert mime is not None
        cursor_pos = self.mapFromGlobal(QCursor.pos())

        if evt.source() and mime.hasHtml():
            # don't filter html from other fields
            html, internal = mime.html(), True
        else:
            html, internal = self._processMime(mime, extended, drop_event=True)

        if not html:
            return

        self.editor.doDrop(html, internal, extended, cursor_pos)

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

        # favour url if it's a local link
        if (
            mime.hasUrls()
            and (urls := mime.urls())
            and urls[0].toString().startswith("file://")
        ):
            types = (self._processUrls, self._processImage, self._processText)
        else:
            types = (self._processImage, self._processUrls, self._processText)

        for fn in types:
            html = fn(mime, extended)
            if html:
                return html, True
        return "", False

    def _processUrls(self, mime: QMimeData, extended: bool = False) -> str | None:
        if not mime.hasUrls():
            return None

        buf = ""
        for qurl in mime.urls():
            url = qurl.toString()
            # chrome likes to give us the URL twice with a \n
            if lines := url.splitlines():
                url = lines[0]
                buf += self.editor.urlToLink(url)

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
        if self.hasSelection():
            self._add_cut_action(m)
            self._add_copy_action(m)
        a = m.addAction(tr.editing_paste())
        assert a is not None
        qconnect(a.triggered, self.onPaste)
        if self._opened_context_menu_on_image():
            self._add_image_menu(m)
        gui_hooks.editor_will_show_context_menu(self, m)
        m.popup(QCursor.pos())

    def _add_cut_action(self, menu: QMenu) -> None:
        a = menu.addAction(tr.editing_cut())
        assert a is not None
        qconnect(a.triggered, self.onCut)

    def _add_copy_action(self, menu: QMenu) -> None:
        a = menu.addAction(tr.actions_copy())
        assert a is not None
        qconnect(a.triggered, self.onCopy)

    def _add_image_menu(self, menu: QMenu) -> None:
        a = menu.addAction(tr.editing_copy_image())
        assert a is not None
        qconnect(a.triggered, self.on_copy_image)

        context_menu_request = self.lastContextMenuRequest()
        assert context_menu_request is not None
        url = context_menu_request.mediaUrl()
        file_name = url.fileName()
        path = os.path.join(self.editor.mw.col.media.dir(), file_name)
        a = menu.addAction(tr.editing_open_image())
        assert a is not None
        qconnect(a.triggered, lambda: openFolder(path))

        if is_win or is_mac:
            a = menu.addAction(tr.editing_show_in_folder())
            assert a is not None
            qconnect(a.triggered, lambda: show_in_folder(path))

    def _clipboard(self) -> QClipboard:
        clipboard = self.editor.mw.app.clipboard()
        assert clipboard is not None
        return clipboard


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
    action = "show" if editor.note_type()["type"] == MODEL_CLOZE else "hide"
    editor.web.eval(
        'require("anki/ui").loaded.then(() =>'
        f'require("anki/NoteEditor").instances[0].toolbar.toolbar.{action}("cloze")'
        "); "
    )


def set_image_occlusion_button(editor: Editor) -> None:
    action = "show" if editor.current_notetype_is_image_occlusion() else "hide"
    editor.web.eval(
        'require("anki/ui").loaded.then(() =>'
        f'require("anki/NoteEditor").instances[0].toolbar.toolbar.{action}("image-occlusion-button")'
        "); "
    )


gui_hooks.editor_did_load_note.append(set_cloze_button)
gui_hooks.editor_did_load_note.append(set_image_occlusion_button)
