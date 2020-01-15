# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""
See pylib/tools/genhooks.py for more info.
"""

import os
import sys

pylib = os.path.join(os.path.dirname(__file__), "..", "..", "pylib")
sys.path.append(pylib)

from tools.hookslib import Hook, update_file

# Hook list
######################################################################

hooks = [
    # Reviewing
    ###################
    Hook(
        name="reviewer_question_did_show",
        args=["card: Card"],
        legacy_hook="showQuestion",
        legacy_no_args=True,
    ),
    Hook(
        name="reviewer_answer_did_show",
        args=["card: Card"],
        legacy_hook="showAnswer",
        legacy_no_args=True,
    ),
    Hook(
        name="reviewer_context_menu_will_show",
        args=["reviewer: aqt.reviewer.Reviewer", "menu: QMenu"],
        legacy_hook="Reviewer.contextMenuEvent",
    ),
    Hook(
        name="reviewer_will_end",
        legacy_hook="reviewCleanup",
        doc="Called before Anki transitions from the review screen to another screen.",
    ),
    Hook(
        name="card_text",
        args=["text: str", "card: Card", "kind: str"],
        return_type="str",
        legacy_hook="prepareQA",
        doc="Can modify card text before review/preview.",
    ),
    # Browser
    ###################
    Hook(
        name="browser_menus_did_setup",
        args=["browser: aqt.browser.Browser"],
        legacy_hook="browser.setupMenus",
    ),
    Hook(
        name="browser_context_menu_will_show",
        args=["browser: aqt.browser.Browser", "menu: QMenu"],
        legacy_hook="browser.onContextMenu",
    ),
    Hook(
        name="browser_row_did_change",
        args=["browser: aqt.browser.Browser"],
        legacy_hook="browser.rowChanged",
    ),
    Hook(
        name="webview_context_menu_will_show",
        args=["webview: aqt.webview.AnkiWebView", "menu: QMenu"],
        legacy_hook="AnkiWebView.contextMenuEvent",
    ),
    # States
    ###################
    Hook(
        name="state_will_change",
        args=["new_state: str", "old_state: str"],
        legacy_hook="beforeStateChange",
    ),
    Hook(
        name="state_did_change",
        args=["new_state: str", "old_state: str"],
        legacy_hook="afterStateChange",
    ),
    # different sig to original
    Hook(
        name="state_shortcuts_will_change",
        args=["state: str", "shortcuts: List[Tuple[str, Callable]]"],
    ),
    Hook(
        name="state_did_revert",
        args=["action: str"],
        legacy_hook="revertedState",
        doc="Called when user used the undo option to restore to an earlier database state.",
    ),
    Hook(
        name="state_did_reset",
        legacy_hook="reset",
        doc="Called when the interface needs to be redisplayed after non-trivial changes have been made.",
    ),
    # Main
    ###################
    Hook(name="profile_did_open", legacy_hook="profileLoaded"),
    Hook(name="profile_will_close", legacy_hook="unloadProfile"),
    Hook(
        name="collection_did_load",
        args=["col: anki.storage._Collection"],
        legacy_hook="colLoading",
    ),
    Hook(
        name="undo_state_did_change", args=["can_undo: bool"], legacy_hook="undoState"
    ),
    Hook(name="review_did_undo", args=["card_id: int"], legacy_hook="revertedCard"),
    Hook(
        name="style_did_setup",
        args=["style: str"],
        return_type="str",
        legacy_hook="setupStyle",
    ),
    # Adding cards
    ###################
    Hook(
        name="add_cards_history_menu_will_show",
        args=["addcards: aqt.addcards.AddCards", "menu: QMenu"],
        legacy_hook="AddCards.onHistory",
    ),
    Hook(
        name="add_cards_note_did_add",
        args=["note: anki.notes.Note"],
        legacy_hook="AddCards.noteAdded",
    ),
    # Editing
    ###################
    Hook(
        name="editor_buttons_did_setup",
        args=["buttons: List", "editor: aqt.editor.Editor"],
    ),
    Hook(
        name="editor_shortcuts_did_setup",
        args=["shortcuts: List[Tuple]", "editor: aqt.editor.Editor"],
        legacy_hook="setupEditorShortcuts",
    ),
    Hook(
        name="editor_context_menu_will_show",
        args=["editor_webview: aqt.editor.EditorWebView", "menu: QMenu"],
        legacy_hook="EditorWebView.contextMenuEvent",
    ),
    Hook(
        name="editor_typing_timer_did_fire",
        args=["note: anki.notes.Note"],
        legacy_hook="editTimer",
    ),
    Hook(
        name="editor_field_did_gain_focus",
        args=["note: anki.notes.Note", "current_field_idx: int"],
        legacy_hook="editFocusGained",
    ),
    Hook(
        name="editor_field_did_lose_focus",
        args=["changed: bool", "note: anki.notes.Note", "current_field_idx: int"],
        return_type="bool",
        legacy_hook="editFocusLost",
    ),
    Hook(
        name="editor_note_did_load",
        args=["editor: aqt.editor.Editor"],
        legacy_hook="loadNote",
    ),
    Hook(
        name="editor_tags_did_update",
        args=["note: anki.notes.Note"],
        legacy_hook="tagsUpdated",
    ),
    Hook(
        name="editor_font_for_field",
        args=["font: str"],
        return_type="str",
        legacy_hook="mungeEditingFontName",
    ),
    # Other
    ###################
    Hook(name="mpv_did_idle"),
    Hook(name="mpv_will_play", args=["file: str"], legacy_hook="mpvWillPlay"),
    Hook(
        name="current_note_type_did_change",
        args=["notetype: Dict[str, Any]"],
        legacy_hook="currentModelChanged",
        legacy_no_args=True,
    ),
    Hook(
        name="deck_browser_options_menu_will_show",
        args=["menu: QMenu", "deck_id: int"],
        legacy_hook="showDeckOptions",
    ),
]

if __name__ == "__main__":
    path = os.path.join(os.path.dirname(__file__), "..", "aqt", "gui_hooks.py")
    update_file(path, hooks)
