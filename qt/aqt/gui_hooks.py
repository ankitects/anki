# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""
See pylib/anki/hooks.py
"""

from __future__ import annotations

from typing import Any, Callable, Dict, List  # pylint: disable=unused-import

import aqt
from anki.cards import Card
from anki.hooks import runFilter, runHook  # pylint: disable=unused-import
from aqt.qt import QMenu

# New hook/filter handling
##############################################################################
# The code in this section is automatically generated - any edits you make
# will be lost. To add new hooks, see ../tools/genhooks.py
#
# @@AUTOGEN@@


class _BrowserContextMenuHook:
    _hooks: List[Callable[["aqt.browser.Browser", QMenu], None]] = []

    def append(self, cb: Callable[["aqt.browser.Browser", QMenu], None]) -> None:
        """(browser: aqt.browser.Browser, menu: QMenu)"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[["aqt.browser.Browser", QMenu], None]) -> None:
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


browser_context_menu_hook = _BrowserContextMenuHook()


class _BrowserRowChangedHook:
    _hooks: List[Callable[["aqt.browser.Browser"], None]] = []

    def append(self, cb: Callable[["aqt.browser.Browser"], None]) -> None:
        """(browser: aqt.browser.Browser)"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[["aqt.browser.Browser"], None]) -> None:
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


browser_row_changed_hook = _BrowserRowChangedHook()


class _BrowserSetupMenusHook:
    _hooks: List[Callable[["aqt.browser.Browser"], None]] = []

    def append(self, cb: Callable[["aqt.browser.Browser"], None]) -> None:
        """(browser: aqt.browser.Browser)"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[["aqt.browser.Browser"], None]) -> None:
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


browser_setup_menus_hook = _BrowserSetupMenusHook()


class _CardTextFilter:
    _hooks: List[Callable[[str, Card, str], str]] = []

    def append(self, cb: Callable[[str, Card, str], str]) -> None:
        """(text: str, card: Card, kind: str)"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[[str, Card, str], str]) -> None:
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


card_text_filter = _CardTextFilter()


class _CurrentNoteTypeChangedHook:
    _hooks: List[Callable[[Dict[str, Any]], None]] = []

    def append(self, cb: Callable[[Dict[str, Any]], None]) -> None:
        """(notetype: Dict[str, Any])"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[[Dict[str, Any]], None]) -> None:
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


current_note_type_changed_hook = _CurrentNoteTypeChangedHook()


class _MpvIdleHook:
    _hooks: List[Callable[[], None]] = []

    def append(self, cb: Callable[[], None]) -> None:
        """()"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[[], None]) -> None:
        self._hooks.remove(cb)

    def __call__(self) -> None:
        for hook in self._hooks:
            try:
                hook()
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise


mpv_idle_hook = _MpvIdleHook()


class _MpvWillPlayHook:
    _hooks: List[Callable[[str], None]] = []

    def append(self, cb: Callable[[str], None]) -> None:
        """(file: str)"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[[str], None]) -> None:
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


mpv_will_play_hook = _MpvWillPlayHook()


class _ReviewerShowingAnswerHook:
    _hooks: List[Callable[[Card], None]] = []

    def append(self, cb: Callable[[Card], None]) -> None:
        """(card: Card)"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[[Card], None]) -> None:
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


reviewer_showing_answer_hook = _ReviewerShowingAnswerHook()


class _ReviewerShowingQuestionHook:
    _hooks: List[Callable[[Card], None]] = []

    def append(self, cb: Callable[[Card], None]) -> None:
        """(card: Card)"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[[Card], None]) -> None:
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


reviewer_showing_question_hook = _ReviewerShowingQuestionHook()


class _WebviewContextMenuHook:
    _hooks: List[Callable[["aqt.webview.AnkiWebView", QMenu], None]] = []

    def append(self, cb: Callable[["aqt.webview.AnkiWebView", QMenu], None]) -> None:
        """(webview: aqt.webview.AnkiWebView, menu: QMenu)"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[["aqt.webview.AnkiWebView", QMenu], None]) -> None:
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


webview_context_menu_hook = _WebviewContextMenuHook()
# @@AUTOGEN@@
