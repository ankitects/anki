# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""
See pylib/anki/hooks.py
"""

from __future__ import annotations

from typing import Any, Callable, Dict, List, Tuple

import anki
import aqt
from anki.cards import Card
from anki.hooks import runFilter, runHook
from aqt.qt import QMenu

# New hook/filter handling
##############################################################################
# The code in this section is automatically generated - any edits you make
# will be lost. To add new hooks, see ../tools/genhooks_gui.py
#
# @@AUTOGEN@@


class _AddCardsHistoryMenuWillShowHook:
    _hooks: List[Callable[["aqt.addcards.AddCards", QMenu], None]] = []

    def append(self, cb: Callable[["aqt.addcards.AddCards", QMenu], None]) -> None:
        """(addcards: aqt.addcards.AddCards, menu: QMenu)"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[["aqt.addcards.AddCards", QMenu], None]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self, addcards: aqt.addcards.AddCards, menu: QMenu) -> None:
        for hook in self._hooks:
            try:
                hook(addcards, menu)
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise
        # legacy support
        runHook("AddCards.onHistory", addcards, menu)


add_cards_history_menu_will_show = _AddCardsHistoryMenuWillShowHook()


class _AddCardsNoteDidAddHook:
    _hooks: List[Callable[["anki.notes.Note"], None]] = []

    def append(self, cb: Callable[["anki.notes.Note"], None]) -> None:
        """(note: anki.notes.Note)"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[["anki.notes.Note"], None]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self, note: anki.notes.Note) -> None:
        for hook in self._hooks:
            try:
                hook(note)
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise
        # legacy support
        runHook("AddCards.noteAdded", note)


add_cards_note_did_add = _AddCardsNoteDidAddHook()


class _BrowserContextMenuWillShowHook:
    _hooks: List[Callable[["aqt.browser.Browser", QMenu], None]] = []

    def append(self, cb: Callable[["aqt.browser.Browser", QMenu], None]) -> None:
        """(browser: aqt.browser.Browser, menu: QMenu)"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[["aqt.browser.Browser", QMenu], None]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self, browser: aqt.browser.Browser, menu: QMenu) -> None:
        for hook in self._hooks:
            try:
                hook(browser, menu)
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise
        # legacy support
        runHook("browser.onContextMenu", browser, menu)


browser_context_menu_will_show = _BrowserContextMenuWillShowHook()


class _BrowserMenusDidSetupHook:
    _hooks: List[Callable[["aqt.browser.Browser"], None]] = []

    def append(self, cb: Callable[["aqt.browser.Browser"], None]) -> None:
        """(browser: aqt.browser.Browser)"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[["aqt.browser.Browser"], None]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self, browser: aqt.browser.Browser) -> None:
        for hook in self._hooks:
            try:
                hook(browser)
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise
        # legacy support
        runHook("browser.setupMenus", browser)


browser_menus_did_setup = _BrowserMenusDidSetupHook()


class _BrowserRowDidChangeHook:
    _hooks: List[Callable[["aqt.browser.Browser"], None]] = []

    def append(self, cb: Callable[["aqt.browser.Browser"], None]) -> None:
        """(browser: aqt.browser.Browser)"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[["aqt.browser.Browser"], None]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self, browser: aqt.browser.Browser) -> None:
        for hook in self._hooks:
            try:
                hook(browser)
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise
        # legacy support
        runHook("browser.rowChanged", browser)


browser_row_did_change = _BrowserRowDidChangeHook()


class _CardTextFilter:
    """Can modify card text before review/preview."""

    _hooks: List[Callable[[str, Card, str], str]] = []

    def append(self, cb: Callable[[str, Card, str], str]) -> None:
        """(text: str, card: Card, kind: str)"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[[str, Card, str], str]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self, text: str, card: Card, kind: str) -> str:
        for filter in self._hooks:
            try:
                text = filter(text, card, kind)
            except:
                # if the hook fails, remove it
                self._hooks.remove(filter)
                raise
        # legacy support
        runFilter("prepareQA", text, card, kind)
        return text


card_text = _CardTextFilter()


class _CollectionDidLoadHook:
    _hooks: List[Callable[["anki.storage._Collection"], None]] = []

    def append(self, cb: Callable[["anki.storage._Collection"], None]) -> None:
        """(col: anki.storage._Collection)"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[["anki.storage._Collection"], None]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self, col: anki.storage._Collection) -> None:
        for hook in self._hooks:
            try:
                hook(col)
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise
        # legacy support
        runHook("colLoading", col)


collection_did_load = _CollectionDidLoadHook()


class _CurrentNoteTypeDidChangeHook:
    _hooks: List[Callable[[Dict[str, Any]], None]] = []

    def append(self, cb: Callable[[Dict[str, Any]], None]) -> None:
        """(notetype: Dict[str, Any])"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[[Dict[str, Any]], None]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self, notetype: Dict[str, Any]) -> None:
        for hook in self._hooks:
            try:
                hook(notetype)
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise
        # legacy support
        runHook("currentModelChanged")


current_note_type_did_change = _CurrentNoteTypeDidChangeHook()


class _DeckBrowserOptionsMenuWillShowHook:
    _hooks: List[Callable[[QMenu, int], None]] = []

    def append(self, cb: Callable[[QMenu, int], None]) -> None:
        """(menu: QMenu, deck_id: int)"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[[QMenu, int], None]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self, menu: QMenu, deck_id: int) -> None:
        for hook in self._hooks:
            try:
                hook(menu, deck_id)
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise
        # legacy support
        runHook("showDeckOptions", menu, deck_id)


deck_browser_options_menu_will_show = _DeckBrowserOptionsMenuWillShowHook()


class _EditorButtonsDidSetupHook:
    _hooks: List[Callable[[List, "aqt.editor.Editor"], None]] = []

    def append(self, cb: Callable[[List, "aqt.editor.Editor"], None]) -> None:
        """(buttons: List, editor: aqt.editor.Editor)"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[[List, "aqt.editor.Editor"], None]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self, buttons: List, editor: aqt.editor.Editor) -> None:
        for hook in self._hooks:
            try:
                hook(buttons, editor)
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise


editor_buttons_did_setup = _EditorButtonsDidSetupHook()


class _EditorContextMenuWillShowHook:
    _hooks: List[Callable[["aqt.editor.EditorWebView", QMenu], None]] = []

    def append(self, cb: Callable[["aqt.editor.EditorWebView", QMenu], None]) -> None:
        """(editor_webview: aqt.editor.EditorWebView, menu: QMenu)"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[["aqt.editor.EditorWebView", QMenu], None]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self, editor_webview: aqt.editor.EditorWebView, menu: QMenu) -> None:
        for hook in self._hooks:
            try:
                hook(editor_webview, menu)
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise
        # legacy support
        runHook("EditorWebView.contextMenuEvent", editor_webview, menu)


editor_context_menu_will_show = _EditorContextMenuWillShowHook()


class _EditorFieldDidGainFocusHook:
    _hooks: List[Callable[["anki.notes.Note", int], None]] = []

    def append(self, cb: Callable[["anki.notes.Note", int], None]) -> None:
        """(note: anki.notes.Note, current_field_idx: int)"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[["anki.notes.Note", int], None]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self, note: anki.notes.Note, current_field_idx: int) -> None:
        for hook in self._hooks:
            try:
                hook(note, current_field_idx)
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise
        # legacy support
        runHook("editFocusGained", note, current_field_idx)


editor_field_did_gain_focus = _EditorFieldDidGainFocusHook()


class _EditorFieldDidLoseFocusFilter:
    _hooks: List[Callable[[bool, "anki.notes.Note", int], bool]] = []

    def append(self, cb: Callable[[bool, "anki.notes.Note", int], bool]) -> None:
        """(changed: bool, note: anki.notes.Note, current_field_idx: int)"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[[bool, "anki.notes.Note", int], bool]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(
        self, changed: bool, note: anki.notes.Note, current_field_idx: int
    ) -> bool:
        for filter in self._hooks:
            try:
                changed = filter(changed, note, current_field_idx)
            except:
                # if the hook fails, remove it
                self._hooks.remove(filter)
                raise
        # legacy support
        runFilter("editFocusLost", changed, note, current_field_idx)
        return changed


editor_field_did_lose_focus = _EditorFieldDidLoseFocusFilter()


class _EditorFontForFieldFilter:
    _hooks: List[Callable[[str], str]] = []

    def append(self, cb: Callable[[str], str]) -> None:
        """(font: str)"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[[str], str]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self, font: str) -> str:
        for filter in self._hooks:
            try:
                font = filter(font)
            except:
                # if the hook fails, remove it
                self._hooks.remove(filter)
                raise
        # legacy support
        runFilter("mungeEditingFontName", font)
        return font


editor_font_for_field = _EditorFontForFieldFilter()


class _EditorNoteDidLoadHook:
    _hooks: List[Callable[["aqt.editor.Editor"], None]] = []

    def append(self, cb: Callable[["aqt.editor.Editor"], None]) -> None:
        """(editor: aqt.editor.Editor)"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[["aqt.editor.Editor"], None]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self, editor: aqt.editor.Editor) -> None:
        for hook in self._hooks:
            try:
                hook(editor)
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise
        # legacy support
        runHook("loadNote", editor)


editor_note_did_load = _EditorNoteDidLoadHook()


class _EditorShortcutsDidSetupHook:
    _hooks: List[Callable[[List[Tuple], "aqt.editor.Editor"], None]] = []

    def append(self, cb: Callable[[List[Tuple], "aqt.editor.Editor"], None]) -> None:
        """(shortcuts: List[Tuple], editor: aqt.editor.Editor)"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[[List[Tuple], "aqt.editor.Editor"], None]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self, shortcuts: List[Tuple], editor: aqt.editor.Editor) -> None:
        for hook in self._hooks:
            try:
                hook(shortcuts, editor)
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise
        # legacy support
        runHook("setupEditorShortcuts", shortcuts, editor)


editor_shortcuts_did_setup = _EditorShortcutsDidSetupHook()


class _EditorTagsDidUpdateHook:
    _hooks: List[Callable[["anki.notes.Note"], None]] = []

    def append(self, cb: Callable[["anki.notes.Note"], None]) -> None:
        """(note: anki.notes.Note)"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[["anki.notes.Note"], None]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self, note: anki.notes.Note) -> None:
        for hook in self._hooks:
            try:
                hook(note)
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise
        # legacy support
        runHook("tagsUpdated", note)


editor_tags_did_update = _EditorTagsDidUpdateHook()


class _EditorTypingTimerDidFireHook:
    _hooks: List[Callable[["anki.notes.Note"], None]] = []

    def append(self, cb: Callable[["anki.notes.Note"], None]) -> None:
        """(note: anki.notes.Note)"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[["anki.notes.Note"], None]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self, note: anki.notes.Note) -> None:
        for hook in self._hooks:
            try:
                hook(note)
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise
        # legacy support
        runHook("editTimer", note)


editor_typing_timer_did_fire = _EditorTypingTimerDidFireHook()


class _MpvDidIdleHook:
    _hooks: List[Callable[[], None]] = []

    def append(self, cb: Callable[[], None]) -> None:
        """()"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[[], None]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self) -> None:
        for hook in self._hooks:
            try:
                hook()
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise


mpv_did_idle = _MpvDidIdleHook()


class _MpvWillPlayHook:
    _hooks: List[Callable[[str], None]] = []

    def append(self, cb: Callable[[str], None]) -> None:
        """(file: str)"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[[str], None]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self, file: str) -> None:
        for hook in self._hooks:
            try:
                hook(file)
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise
        # legacy support
        runHook("mpvWillPlay", file)


mpv_will_play = _MpvWillPlayHook()


class _ProfileDidOpenHook:
    _hooks: List[Callable[[], None]] = []

    def append(self, cb: Callable[[], None]) -> None:
        """()"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[[], None]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self) -> None:
        for hook in self._hooks:
            try:
                hook()
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise
        # legacy support
        runHook("profileLoaded")


profile_did_open = _ProfileDidOpenHook()


class _ProfileWillCloseHook:
    _hooks: List[Callable[[], None]] = []

    def append(self, cb: Callable[[], None]) -> None:
        """()"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[[], None]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self) -> None:
        for hook in self._hooks:
            try:
                hook()
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise
        # legacy support
        runHook("unloadProfile")


profile_will_close = _ProfileWillCloseHook()


class _ReviewDidUndoHook:
    _hooks: List[Callable[[int], None]] = []

    def append(self, cb: Callable[[int], None]) -> None:
        """(card_id: int)"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[[int], None]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self, card_id: int) -> None:
        for hook in self._hooks:
            try:
                hook(card_id)
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise
        # legacy support
        runHook("revertedCard", card_id)


review_did_undo = _ReviewDidUndoHook()


class _ReviewerAnswerDidShowHook:
    _hooks: List[Callable[[Card], None]] = []

    def append(self, cb: Callable[[Card], None]) -> None:
        """(card: Card)"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[[Card], None]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self, card: Card) -> None:
        for hook in self._hooks:
            try:
                hook(card)
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise
        # legacy support
        runHook("showAnswer")


reviewer_answer_did_show = _ReviewerAnswerDidShowHook()


class _ReviewerContextMenuWillShowHook:
    _hooks: List[Callable[["aqt.reviewer.Reviewer", QMenu], None]] = []

    def append(self, cb: Callable[["aqt.reviewer.Reviewer", QMenu], None]) -> None:
        """(reviewer: aqt.reviewer.Reviewer, menu: QMenu)"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[["aqt.reviewer.Reviewer", QMenu], None]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self, reviewer: aqt.reviewer.Reviewer, menu: QMenu) -> None:
        for hook in self._hooks:
            try:
                hook(reviewer, menu)
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise
        # legacy support
        runHook("Reviewer.contextMenuEvent", reviewer, menu)


reviewer_context_menu_will_show = _ReviewerContextMenuWillShowHook()


class _ReviewerQuestionDidShowHook:
    _hooks: List[Callable[[Card], None]] = []

    def append(self, cb: Callable[[Card], None]) -> None:
        """(card: Card)"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[[Card], None]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self, card: Card) -> None:
        for hook in self._hooks:
            try:
                hook(card)
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise
        # legacy support
        runHook("showQuestion")


reviewer_question_did_show = _ReviewerQuestionDidShowHook()


class _ReviewerWillEndHook:
    """Called before Anki transitions from the review screen to another screen."""

    _hooks: List[Callable[[], None]] = []

    def append(self, cb: Callable[[], None]) -> None:
        """()"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[[], None]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self) -> None:
        for hook in self._hooks:
            try:
                hook()
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise
        # legacy support
        runHook("reviewCleanup")


reviewer_will_end = _ReviewerWillEndHook()


class _StateDidChangeHook:
    _hooks: List[Callable[[str, str], None]] = []

    def append(self, cb: Callable[[str, str], None]) -> None:
        """(new_state: str, old_state: str)"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[[str, str], None]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self, new_state: str, old_state: str) -> None:
        for hook in self._hooks:
            try:
                hook(new_state, old_state)
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise
        # legacy support
        runHook("afterStateChange", new_state, old_state)


state_did_change = _StateDidChangeHook()


class _StateDidResetHook:
    """Called when the interface needs to be redisplayed after non-trivial changes have been made."""

    _hooks: List[Callable[[], None]] = []

    def append(self, cb: Callable[[], None]) -> None:
        """()"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[[], None]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self) -> None:
        for hook in self._hooks:
            try:
                hook()
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise
        # legacy support
        runHook("reset")


state_did_reset = _StateDidResetHook()


class _StateDidRevertHook:
    """Called when user used the undo option to restore to an earlier database state."""

    _hooks: List[Callable[[str], None]] = []

    def append(self, cb: Callable[[str], None]) -> None:
        """(action: str)"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[[str], None]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self, action: str) -> None:
        for hook in self._hooks:
            try:
                hook(action)
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise
        # legacy support
        runHook("revertedState", action)


state_did_revert = _StateDidRevertHook()


class _StateShortcutsWillChangeHook:
    _hooks: List[Callable[[str, List[Tuple[str, Callable]]], None]] = []

    def append(self, cb: Callable[[str, List[Tuple[str, Callable]]], None]) -> None:
        """(state: str, shortcuts: List[Tuple[str, Callable]])"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[[str, List[Tuple[str, Callable]]], None]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self, state: str, shortcuts: List[Tuple[str, Callable]]) -> None:
        for hook in self._hooks:
            try:
                hook(state, shortcuts)
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise


state_shortcuts_will_change = _StateShortcutsWillChangeHook()


class _StateWillChangeHook:
    _hooks: List[Callable[[str, str], None]] = []

    def append(self, cb: Callable[[str, str], None]) -> None:
        """(new_state: str, old_state: str)"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[[str, str], None]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self, new_state: str, old_state: str) -> None:
        for hook in self._hooks:
            try:
                hook(new_state, old_state)
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise
        # legacy support
        runHook("beforeStateChange", new_state, old_state)


state_will_change = _StateWillChangeHook()


class _StyleDidSetupFilter:
    _hooks: List[Callable[[str], str]] = []

    def append(self, cb: Callable[[str], str]) -> None:
        """(style: str)"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[[str], str]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self, style: str) -> str:
        for filter in self._hooks:
            try:
                style = filter(style)
            except:
                # if the hook fails, remove it
                self._hooks.remove(filter)
                raise
        # legacy support
        runFilter("setupStyle", style)
        return style


style_did_setup = _StyleDidSetupFilter()


class _UndoStateDidChangeHook:
    _hooks: List[Callable[[bool], None]] = []

    def append(self, cb: Callable[[bool], None]) -> None:
        """(can_undo: bool)"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[[bool], None]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self, can_undo: bool) -> None:
        for hook in self._hooks:
            try:
                hook(can_undo)
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise
        # legacy support
        runHook("undoState", can_undo)


undo_state_did_change = _UndoStateDidChangeHook()


class _WebviewContextMenuWillShowHook:
    _hooks: List[Callable[["aqt.webview.AnkiWebView", QMenu], None]] = []

    def append(self, cb: Callable[["aqt.webview.AnkiWebView", QMenu], None]) -> None:
        """(webview: aqt.webview.AnkiWebView, menu: QMenu)"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[["aqt.webview.AnkiWebView", QMenu], None]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self, webview: aqt.webview.AnkiWebView, menu: QMenu) -> None:
        for hook in self._hooks:
            try:
                hook(webview, menu)
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise
        # legacy support
        runHook("AnkiWebView.contextMenuEvent", webview, menu)


webview_context_menu_will_show = _WebviewContextMenuWillShowHook()
# @@AUTOGEN@@
