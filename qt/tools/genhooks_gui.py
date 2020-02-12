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
        name="overview_did_refresh",
        args=["overview: aqt.overview.Overview"],
        doc="""Allow to update the overview window. E.g. add the deck name in the
        title.""",
    ),
    Hook(
        name="deck_browser_did_render",
        args=["deck_browser: aqt.deckbrowser.DeckBrowser"],
        doc="""Allow to update the deck browser window. E.g. change its title.""",
    ),
    Hook(
        name="reviewer_did_show_question",
        args=["card: Card"],
        legacy_hook="showQuestion",
        legacy_no_args=True,
    ),
    Hook(
        name="reviewer_did_show_answer",
        args=["card: Card"],
        legacy_hook="showAnswer",
        legacy_no_args=True,
    ),
    Hook(
        name="reviewer_will_answer_card",
        args=[
            "ease_tuple: Tuple[bool, int]",
            "reviewer: aqt.reviewer.Reviewer",
            "card: Card",
        ],
        return_type="Tuple[bool, int]",
        doc="""Used to modify the ease at which a card is rated or to bypass
        rating the card completely.
        
        ease_tuple is a tuple consisting of a boolean expressing whether the reviewer
        should continue with rating the card, and an integer expressing the ease at
        which the card should be rated.
        
        If your code just needs to be notified of the card rating event, you should use
        the reviewer_did_answer_card hook instead.""",
    ),
    Hook(
        name="reviewer_did_answer_card",
        args=["reviewer: aqt.reviewer.Reviewer", "card: Card", "ease: int"],
    ),
    Hook(
        name="reviewer_will_show_context_menu",
        args=["reviewer: aqt.reviewer.Reviewer", "menu: QMenu"],
        legacy_hook="Reviewer.contextMenuEvent",
    ),
    Hook(
        name="reviewer_will_end",
        legacy_hook="reviewCleanup",
        doc="Called before Anki transitions from the review screen to another screen.",
    ),
    Hook(
        name="card_will_show",
        args=["text: str", "card: Card", "kind: str"],
        return_type="str",
        legacy_hook="prepareQA",
        doc="Can modify card text before review/preview.",
    ),
    # Browser
    ###################
    Hook(
        name="browser_menus_did_init",
        args=["browser: aqt.browser.Browser"],
        legacy_hook="browser.setupMenus",
    ),
    Hook(
        name="browser_will_show_context_menu",
        args=["browser: aqt.browser.Browser", "menu: QMenu"],
        legacy_hook="browser.onContextMenu",
    ),
    Hook(
        name="browser_did_change_row",
        args=["browser: aqt.browser.Browser"],
        legacy_hook="browser.rowChanged",
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
    # Webview
    ###################
    Hook(
        name="webview_did_receive_js_message",
        args=["handled: Tuple[bool, Any]", "message: str", "context: Any"],
        return_type="Tuple[bool, Any]",
        doc="""Used to handle pycmd() messages sent from Javascript.
        
        Message is the string passed to pycmd().

        For messages you don't want to handle, return 'handled' unchanged.
        
        If you handle a message and don't want it passed to the original
        bridge command handler, return (True, None).
        
        If you want to pass a value to pycmd's result callback, you can
        return it with (True, some_value).
                
        Context is the instance that was passed to set_bridge_command().
        It can be inspected to check which screen this hook is firing
        in, and to get a reference to the screen. For example, if your
        code wishes to function only in the review screen, you could do:

            if not isinstance(context, aqt.reviewer.Reviewer):
                # not reviewer, pass on message
                return handled
    
            if message == "my-mark-action":
                # our message, call onMark() on the reviewer instance
                context.onMark()
                # and don't pass message to other handlers
                return (True, None)
            else:
                # some other command, pass it on
                return handled
        """,
    ),
    Hook(
        name="webview_will_set_content",
        args=["web_content: aqt.webview.WebContent", "context: Optional[Any]",],
        doc="""Used to modify web content before it is rendered.

        Web_content contains the HTML, JS, and CSS the web view will be
        populated with.

        Context is the instance that was passed to stdHtml().
        It can be inspected to check which screen this hook is firing
        in, and to get a reference to the screen. For example, if your
        code wishes to function only in the review screen, you could do:

            if not isinstance(context, aqt.reviewer.Reviewer):
                # not reviewer, do not modify content
                return

            web_content.js.append("my_addon.js")
            web_content.css.append("my_addon.css")
            web_content.head += "<script>console.log('my_addon')</script>"
            web_content.body += "<div id='my-addon'></div>"
        """,
    ),
    Hook(
        name="webview_will_show_context_menu",
        args=["webview: aqt.webview.AnkiWebView", "menu: QMenu"],
        legacy_hook="AnkiWebView.contextMenuEvent",
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
        name="style_did_init",
        args=["style: str"],
        return_type="str",
        legacy_hook="setupStyle",
    ),
    # Adding cards
    ###################
    Hook(
        name="add_cards_will_show_history_menu",
        args=["addcards: aqt.addcards.AddCards", "menu: QMenu"],
        legacy_hook="AddCards.onHistory",
    ),
    Hook(
        name="add_cards_did_add_note",
        args=["note: anki.notes.Note"],
        legacy_hook="AddCards.noteAdded",
    ),
    # Editing
    ###################
    Hook(
        name="editor_did_init_buttons",
        args=["buttons: List", "editor: aqt.editor.Editor"],
    ),
    Hook(
        name="editor_did_init_shortcuts",
        args=["shortcuts: List[Tuple]", "editor: aqt.editor.Editor"],
        legacy_hook="setupEditorShortcuts",
    ),
    Hook(
        name="editor_will_show_context_menu",
        args=["editor_webview: aqt.editor.EditorWebView", "menu: QMenu"],
        legacy_hook="EditorWebView.contextMenuEvent",
    ),
    Hook(
        name="editor_did_fire_typing_timer",
        args=["note: anki.notes.Note"],
        legacy_hook="editTimer",
    ),
    Hook(
        name="editor_did_focus_field",
        args=["note: anki.notes.Note", "current_field_idx: int"],
        legacy_hook="editFocusGained",
    ),
    Hook(
        name="editor_did_unfocus_field",
        args=["changed: bool", "note: anki.notes.Note", "current_field_idx: int"],
        return_type="bool",
        legacy_hook="editFocusLost",
    ),
    Hook(
        name="editor_did_load_note",
        args=["editor: aqt.editor.Editor"],
        legacy_hook="loadNote",
    ),
    Hook(
        name="editor_did_update_tags",
        args=["note: anki.notes.Note"],
        legacy_hook="tagsUpdated",
    ),
    Hook(
        name="editor_will_use_font_for_field",
        args=["font: str"],
        return_type="str",
        legacy_hook="mungeEditingFontName",
    ),
    # Sound/video
    ###################
    Hook(name="av_player_will_play", args=["tag: anki.sound.AVTag"]),
    Hook(
        name="av_player_did_begin_playing",
        args=["player: aqt.sound.Player", "tag: anki.sound.AVTag"],
    ),
    Hook(name="av_player_did_end_playing", args=["player: aqt.sound.Player"]),
    # Other
    ###################
    Hook(
        name="current_note_type_did_change",
        args=["notetype: Dict[str, Any]"],
        legacy_hook="currentModelChanged",
        legacy_no_args=True,
    ),
    Hook(
        name="deck_browser_will_show_options_menu",
        args=["menu: QMenu", "deck_id: int"],
        legacy_hook="showDeckOptions",
    ),
]

if __name__ == "__main__":
    path = os.path.join(os.path.dirname(__file__), "..", "aqt", "gui_hooks.py")
    update_file(path, hooks)
