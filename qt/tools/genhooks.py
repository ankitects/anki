# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""
See pylib/tools/genhooks.py for more info.
"""

import os

from anki.hooks_gen import Hook, update_file

# Hook list
######################################################################

hooks = [
    Hook(name="mpv_idle"),
    Hook(name="mpv_will_play", args=["file: str"], legacy_hook="mpvWillPlay"),
    Hook(
        name="reviewer_showing_question",
        args=["card: Card"],
        legacy_hook="showQuestion",
        legacy_no_args=True,
    ),
    Hook(
        name="reviewer_showing_answer",
        args=["card: Card"],
        legacy_hook="showAnswer",
        legacy_no_args=True,
    ),
    Hook(
        name="current_note_type_changed",
        args=["notetype: Dict[str, Any]"],
        legacy_hook="currentModelChanged",
        legacy_no_args=True,
    ),
    Hook(
        name="browser_setup_menus",
        args=["browser: aqt.browser.Browser"],
        legacy_hook="browser.setupMenus",
    ),
    Hook(
        name="browser_context_menu",
        args=["browser: aqt.browser.Browser", "menu: QMenu"],
        legacy_hook="browser.onContextMenu",
    ),
    Hook(
        name="browser_row_changed",
        args=["browser: aqt.browser.Browser"],
        legacy_hook="browser.rowChanged",
    ),
    Hook(
        name="card_text",
        args=["text: str", "card: Card", "kind: str"],
        return_type="str",
        legacy_hook="prepareQA",
    ),
    Hook(
        name="webview_context_menu",
        args=["webview: aqt.webview.AnkiWebView", "menu: QMenu"],
        legacy_hook="AnkiWebView.contextMenuEvent",
    ),
]

if __name__ == "__main__":
    path = os.path.join(os.path.dirname(__file__), "..", "aqt", "gui_hooks.py")
    update_file(path, hooks)
