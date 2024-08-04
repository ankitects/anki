# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import os
import sys
from collections.abc import Callable
from dataclasses import dataclass
from functools import partial
from pathlib import Path
from typing import TextIO, cast

import anki.cards
import aqt
import aqt.forms
from aqt import gui_hooks
from aqt.profiles import ProfileManager
from aqt.qt import *
from aqt.utils import (
    disable_help_button,
    restoreGeom,
    restoreSplitter,
    saveGeom,
    saveSplitter,
    send_to_trash,
    tr,
)


def show_debug_console() -> None:
    assert aqt.mw
    console = DebugConsole(aqt.mw)
    gui_hooks.debug_console_will_show(console)
    console.show()


SCRIPT_FOLDER = "debug_scripts"
UNSAVED_SCRIPT = "Unsaved script"


@dataclass
class Action:
    name: str
    shortcut: str
    action: Callable[[], None]


class DebugConsole(QDialog):
    silentlyClose = True
    _last_index = 0

    def __init__(self, parent: QWidget) -> None:
        self._buffers: dict[int, str] = {}
        super().__init__(parent)
        self._setup_ui()
        disable_help_button(self)
        restoreGeom(self, "DebugConsoleWindow")
        restoreSplitter(self.frm.splitter, "DebugConsoleWindow")

    def _setup_ui(self):
        self.frm = aqt.forms.debug.Ui_Dialog()
        self.frm.setupUi(self)
        self._text: QPlainTextEdit = self.frm.text
        self._log: QPlainTextEdit = self.frm.log
        self._script: QComboBox = self.frm.script
        self._setup_text_edits()
        self._setup_scripts()
        self._setup_actions()
        self._setup_context_menu()
        qconnect(self.frm.widgetsButton.clicked, self._on_widgetGallery)
        qconnect(self._script.currentIndexChanged, self._on_script_change)

    def _setup_text_edits(self):
        font = QFontDatabase.systemFont(QFontDatabase.SystemFont.FixedFont)
        font.setPointSize(self._text.font().pointSize() + 1)
        self._text.setFont(font)
        self._log.setFont(font)

    def _setup_scripts(self) -> None:
        self._dir = ProfileManager.get_created_base_folder(None).joinpath(SCRIPT_FOLDER)
        self._dir.mkdir(exist_ok=True)
        self._script.addItem(UNSAVED_SCRIPT)
        self._script.addItems(os.listdir(self._dir))

    def _setup_actions(self) -> None:
        for action in self._actions():
            qconnect(
                QShortcut(QKeySequence(action.shortcut), self).activated, action.action
            )

    def _actions(self):
        return [
            Action("Execute", "ctrl+return", self.onDebugRet),
            Action("Execute and print", "ctrl+shift+return", self.onDebugPrint),
            Action("Clear log", "ctrl+l", self._log.clear),
            Action("Clear code", "ctrl+shift+l", self._text.clear),
            Action("Save script", "ctrl+s", self._save_script),
            Action("Open script", "ctrl+o", self._open_script),
            Action("Delete script", "ctrl+d", self._delete_script),
        ]

    def reject(self) -> None:
        super().reject()
        saveSplitter(self.frm.splitter, "DebugConsoleWindow")
        saveGeom(self, "DebugConsoleWindow")

    def _on_script_change(self, new_index: int) -> None:
        self._buffers[self._last_index] = self._text.toPlainText()
        self._text.setPlainText(self._get_script(new_index) or "")
        self._last_index = new_index

    def _get_script(self, idx: int) -> str | None:
        if script := self._buffers.get(idx, ""):
            return script
        if path := self._get_item(idx):
            return path.read_text(encoding="utf8")
        return None

    def _get_item(self, idx: int) -> Path | None:
        if not idx:
            return None
        path = Path(self._script.itemText(idx))
        return path if path.is_absolute() else self._dir.joinpath(path)

    def _get_index(self, path: Path) -> int:
        return self._script.findText(self._path_to_item(path))

    def _path_to_item(self, path: Path) -> str:
        return path.name if path.is_relative_to(self._dir) else str(path)

    def _current_script_path(self) -> Path | None:
        return self._get_item(self._script.currentIndex())

    def _save_script(self) -> None:
        if not (path := self._current_script_path()):
            new_file = QFileDialog.getSaveFileName(
                self, directory=str(self._dir), filter="Python file (*.py)"
            )[0]
            if not new_file:
                return
            path = Path(new_file)

        path.write_text(self._text.toPlainText(), encoding="utf8")

        item = self._path_to_item(path)
        if (idx := self._get_index(path)) == -1:
            self._script.addItem(item)
            idx = self._script.count() - 1
        # update existing buffer, so text edit doesn't change when index changes
        self._buffers[idx] = self._text.toPlainText()
        self._script.setCurrentIndex(idx)

    def _open_script(self) -> None:
        file = QFileDialog.getOpenFileName(
            self, directory=str(self._dir), filter="Python file (*.py)"
        )[0]
        if not file:
            return

        path = Path(file)
        item = self._path_to_item(path)
        if (idx := self._get_index(path)) == -1:
            self._script.addItem(item)
            idx = self._script.count() - 1
        elif idx in self._buffers:
            del self._buffers[idx]

        if idx == self._script.currentIndex():
            self._text.setPlainText(path.read_text(encoding="utf8"))
        else:
            self._script.setCurrentIndex(idx)

    def _delete_script(self) -> None:
        if not (path := self._current_script_path()):
            return
        send_to_trash(path)
        deleted_idx = self._script.currentIndex()
        self._script.setCurrentIndex(0)
        self._script.removeItem(deleted_idx)
        self._drop_buffer_and_shift_keys(deleted_idx)

    def _drop_buffer_and_shift_keys(self, idx: int) -> None:
        def shift(old_idx: int) -> int:
            return old_idx - 1 if old_idx > idx else old_idx

        self._buffers = {shift(i): val for i, val in self._buffers.items() if i != idx}

    def _setup_context_menu(self) -> None:
        for text_edit in (self._log, self._text):
            text_edit.setContextMenuPolicy(Qt.ContextMenuPolicy.CustomContextMenu)
            qconnect(
                text_edit.customContextMenuRequested,
                partial(self._on_context_menu, text_edit),
            )

    def _on_context_menu(self, text_edit: QPlainTextEdit) -> None:
        menu = text_edit.createStandardContextMenu()
        menu.addSeparator()
        for action in self._actions():
            entry = menu.addAction(action.name)
            entry.setShortcut(QKeySequence(action.shortcut))
            qconnect(entry.triggered, action.action)
        menu.exec(QCursor.pos())

    def _on_widgetGallery(self) -> None:
        from aqt.widgetgallery import WidgetGallery

        self.widgetGallery = WidgetGallery(self)
        self.widgetGallery.show()

    def _captureOutput(self, on: bool) -> None:
        mw2 = self

        class Stream:
            def write(self, data: str) -> None:
                mw2._output += data

        if on:
            self._output = ""
            self._oldStderr = sys.stderr
            self._oldStdout = sys.stdout
            s = cast(TextIO, Stream())
            sys.stderr = s
            sys.stdout = s
        else:
            sys.stderr = self._oldStderr
            sys.stdout = self._oldStdout

    def _card_repr(self, card: anki.cards.Card) -> None:
        import copy
        import pprint

        if not card:
            print("no card")
            return

        print("Front:", card.question())
        print("\n")
        print("Back:", card.answer())

        print("\nNote:")
        note = copy.copy(card.note())
        for k, v in note.items():
            print(f"- {k}:", v)

        print("\n")
        del note.fields
        del note._fmap
        pprint.pprint(note.__dict__)

        print("\nCard:")
        c = copy.copy(card)
        c._render_output = None
        pprint.pprint(c.__dict__)

    def _debugCard(self) -> anki.cards.Card | None:
        assert aqt.mw
        card = aqt.mw.reviewer.card
        self._card_repr(card)
        return card

    def _debugBrowserCard(self) -> anki.cards.Card | None:
        card = aqt.dialogs._dialogs["Browser"][1].card
        self._card_repr(card)
        return card

    def onDebugPrint(self) -> None:
        cursor = self._text.textCursor()
        position = cursor.position()
        cursor.select(QTextCursor.SelectionType.LineUnderCursor)
        line = cursor.selectedText()
        whitespace, stripped = _split_off_leading_whitespace(line)
        pfx, sfx = "pp(", ")"
        if not stripped.startswith(pfx):
            line = f"{whitespace}{pfx}{stripped}{sfx}"
            cursor.insertText(line)
            cursor.setPosition(position + len(pfx))
            self._text.setTextCursor(cursor)
        self.onDebugRet()

    def onDebugRet(self) -> None:
        import pprint
        import traceback

        text = self._text.toPlainText()
        vars = {
            "card": self._debugCard,
            "bcard": self._debugBrowserCard,
            "mw": aqt.mw,
            "pp": pprint.pprint,
        }
        self._captureOutput(True)
        try:
            # pylint: disable=exec-used
            exec(text, vars)
        except Exception:
            self._output += traceback.format_exc()
        self._captureOutput(False)
        buf = ""
        for c, line in enumerate(text.strip().split("\n")):
            if c == 0:
                buf += f">>> {line}\n"
            else:
                buf += f"... {line}\n"
        try:
            to_append = buf + (self._output or "<no output>")
            to_append = gui_hooks.debug_console_did_evaluate_python(
                to_append, text, self.frm
            )
            self._log.appendPlainText(to_append)
        except UnicodeDecodeError:
            to_append = tr.qt_misc_non_unicode_text()
            to_append = gui_hooks.debug_console_did_evaluate_python(
                to_append, text, self.frm
            )
            self._log.appendPlainText(to_append)
        slider = self._log.verticalScrollBar()
        slider.setValue(slider.maximum())
        self._log.ensureCursorVisible()


def _split_off_leading_whitespace(text: str) -> tuple[str, str]:
    stripped = text.lstrip()
    whitespace = text[: len(text) - len(stripped)]
    return whitespace, stripped
