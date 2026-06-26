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
from random import randrange
from typing import Any

import requests

import aqt
import aqt.forms
import aqt.operations
import aqt.sound
from anki._legacy import deprecated
from anki.cards import Card
from anki.decks import DeckId
from anki.hooks import runFilter
from anki.models import NotetypeId
from anki.notes import Note, NoteId
from anki.utils import is_win
from aqt import AnkiQt, gui_hooks
from aqt.editor_legacy import *
from aqt.qt import *
from aqt.sound import av_player
from aqt.utils import shortcut, showWarning
from aqt.webview import AnkiWebView, AnkiWebViewKind


def on_editor_ready(func: Callable) -> Callable:
    @functools.wraps(func)
    def decorated(self: NewEditor, *args: Any, **kwargs: Any) -> None:
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


class NewEditor:
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
        # current card, for card layout
        self.card: Card | None = None
        self.state: EditorState = EditorState.INITIAL
        self._ready = False
        self._ready_callbacks: list[Callable[[], None]] = []
        self._saved_callbacks: list[Callable[[], None]] = []
        self._init_links()
        self.setupOuter()
        self.add_webview()
        self.setupWeb()
        self.setupShortcuts()
        self.setupColourPalette()

    # Initial setup
    ############################################################

    def setupOuter(self) -> None:
        l = QVBoxLayout()
        l.setContentsMargins(0, 0, 0, 0)
        l.setSpacing(0)
        self.widget.setLayout(l)
        self.outerLayout = l

    def add_webview(self) -> None:
        self.web = NewEditorWebView(self.widget, self)
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
        func: Callable[[NewEditor], None],
        tip: str = "",
        label: str = "",
        id: str | None = None,
        toggleable: bool = False,
        keys: str | None = None,
        disables: bool = True,
        rightside: bool = True,
    ) -> str:
        """Assign func to bridge cmd, register shortcut, return button"""

        def wrapped_func(editor: NewEditor) -> None:
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
                keys, fn = row
                fn = self._addFocusCheck(fn)
            else:
                keys, fn, _ = row
            QShortcut(QKeySequence(keys), self.widget, activated=fn)  # type: ignore

    def setupColourPalette(self) -> None:
        if not (colors := self.mw.col.get_config("customColorPickerPalette")):
            return
        for i, colour in enumerate(colors[: QColorDialog.customCount()]):
            if not QColor.isValidColorName(colour):
                continue
            QColorDialog.setCustomColor(i, QColor.fromString(colour))

    def _addFocusCheck(self, fn: Callable) -> Callable:
        def checkFocus() -> None:
            if self.currentField is None:
                return
            fn()

        return checkFocus

    def onFields(self) -> None:
        from aqt.fields import FieldDialog

        def on_note_info(note_info: NoteInfo) -> None:
            note_type = self.mw.col.models.get(note_info.mid)
            assert note_type is not None
            FieldDialog(self.mw, note_type, parent=self.parentWindow)

        self.get_note_info(on_note_info)

    def onCardLayout(self) -> None:
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
            (type, _) = cmd.split(":", 1)
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
    def load_note(
        self,
        mid: int | None = None,
        deck_id: DeckId | None = None,
        original_note_id: NoteId | None = None,
        focus_to: int | None = None,
    ) -> None:
        self.widget.show()

        def oncallback(arg: Any) -> None:
            if not self.nid:
                return
            # we currently do this synchronously to ensure we load before the
            # sidebar on browser startup
            if focus_to is not None:
                self.web.setFocus()
            gui_hooks.editor_did_load_note(self)

        load_args = dict(
            nid=self.nid,
            notetypeId=mid,
            focusTo=focus_to,
            originalNoteId=original_note_id,
            reviewerCardId=self.mw.reviewer.card.id if self.mw.reviewer.card else None,
            deckId=deck_id,
            initial=True,
        )
        js = f"loadNote({json.dumps(load_args)});"
        self.web.evalWithCallback(
            f'require("anki/ui").loaded.then(() => {{ {js} }})', oncallback
        )

    def reload_note(self) -> None:
        self.web.eval("reloadNote();")

    def reload_note_if_empty(
        self, deck_id: DeckId | None = None, notetype_id: NotetypeId | None = None
    ) -> None:
        self.web.eval(
            f"reloadNoteIfEmpty({json.dumps(deck_id)}, {json.dumps(notetype_id)});"
        )

    def call_after_note_saved(
        self, callback: Callable, keepFocus: bool = False
    ) -> None:
        "Save unsaved edits then call callback()."

        if not self.nid:
            # calling code may not expect the callback to fire immediately
            self.mw.progress.single_shot(10, callback)
            return
        self._saved_callbacks.append(callback)
        self.web.eval("saveNow(%d)" % keepFocus)

    saveNow = call_after_note_saved

    def on_note_saved(self) -> None:
        for callback in self._saved_callbacks:
            callback()
        self._saved_callbacks = []

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

    removeTags = ["script", "iframe", "object", "style"]

    def _pastePreFilter(self, html: str, internal: bool) -> str:
        import bs4
        from bs4 import BeautifulSoup

        # https://anki.tenderapp.com/discussions/ankidesktop/39543-anki-is-replacing-the-character-by-when-i-exit-the-html-edit-mode-ctrlshiftx
        if html.find(">") < 0:
            return html

        with warnings.catch_warnings():
            warnings.simplefilter("ignore", UserWarning)
            doc = BeautifulSoup(html, "html.parser")

        if not internal:
            for tag_name in self.removeTags:
                for node in doc(tag_name):
                    node.decompose()

            # convert p tags to divs
            for node in doc("p"):
                if hasattr(node, "name"):
                    node.name = "div"

        for element in doc("img"):
            if not isinstance(element, bs4.Tag):
                continue
            tag = element
            try:
                src = tag["src"]
            except KeyError:
                # for some bizarre reason, mnemosyne removes src elements
                # from missing media
                continue

            # in internal pastes, rewrite mediasrv references to relative
            if internal:
                m = re.match(r"http://127.0.0.1:\d+/(.*)$", str(src))
                if m:
                    tag["src"] = m.group(1)
            # in external pastes, download remote media
            elif isinstance(src, str) and self.isURL(src):
                fname = self._retrieveURL(src)
                if fname:
                    tag["src"] = fname
            elif isinstance(src, str) and src.startswith("data:image/"):
                # and convert inlined data
                tag["src"] = self.inlinedImageToFilename(str(src))

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
        font = QFont("Courier")
        font.setStyleHint(QFont.StyleHint.TypeWriter)
        form.textEdit.setFont(font)
        form.textEdit.setPlainText(self.note.fields[field])
        d.show()
        form.textEdit.moveCursor(QTextCursor.MoveOperation.End)
        d.exec()
        html = form.textEdit.toPlainText()
        if html.find(">") > -1:
            from bs4 import BeautifulSoup

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
            paste=NewEditor.onPaste,
            cut=NewEditor.onCut,
            copy=NewEditor.onCopy,
            saved=NewEditor.on_note_saved,
        )

    def get_note_info(self, on_done: Callable[[NoteInfo], None]) -> None:
        def wrapped_on_done(note_info: dict[str, Any]) -> None:
            on_done(NoteInfo(**note_info))

        self.web.evalWithCallback("getNoteInfo()", wrapped_on_done)


# Pasting, drag & drop, and keyboard layouts
######################################################################


class NewEditorWebView(AnkiWebView):
    def __init__(self, parent: QWidget, editor: NewEditor) -> None:
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
