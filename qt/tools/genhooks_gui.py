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
        name="overview_will_render_content",
        args=[
            "overview: aqt.overview.Overview",
            "content: aqt.overview.OverviewContent",
        ],
        doc="""Used to modify HTML content sections in the overview body

        'content' contains the sections of HTML content the overview body
        will be updated with.

        When modifying the content of a particular section, please make sure your
        changes only perform the minimum required edits to make your add-on work.
        You should avoid overwriting or interfering with existing data as much
        as possible, instead opting to append your own changes, e.g.:

            def on_overview_will_render_content(overview, content):
                content.table += "\n<div>my html</div>"
        """,
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
    # Card layout
    ###################
    Hook(
        name="card_layout_will_show",
        args=["clayout: aqt.clayout.CardLayout"],
        doc="""Allow to change the display of the card layout. After most values are
         set and before the window is actually shown.""",
    ),
    # Multiple windows
    ###################
    # reviewer, clayout and browser
    Hook(
        name="card_will_show",
        args=["text: str", "card: Card", "kind: str"],
        return_type="str",
        legacy_hook="prepareQA",
        doc="Can modify card text before review/preview.",
    ),
    # Deck browser
    ###################
    Hook(
        name="deck_browser_did_render",
        args=["deck_browser: aqt.deckbrowser.DeckBrowser"],
        doc="""Allow to update the deck browser window. E.g. change its title.""",
    ),
    Hook(
        name="deck_browser_will_render_content",
        args=[
            "deck_browser: aqt.deckbrowser.DeckBrowser",
            "content: aqt.deckbrowser.DeckBrowserContent",
        ],
        doc="""Used to modify HTML content sections in the deck browser body
        
        'content' contains the sections of HTML content the deck browser body
        will be updated with.
        
        When modifying the content of a particular section, please make sure your
        changes only perform the minimum required edits to make your add-on work.
        You should avoid overwriting or interfering with existing data as much
        as possible, instead opting to append your own changes, e.g.:
        
            def on_deck_browser_will_render_content(deck_browser, content):
                content.stats += "\n<div>my html</div>"
        """,
    ),
    # Deck options
    ###################
    Hook(
        name="deck_conf_did_setup_ui_form",
        args=["deck_conf: aqt.deckconf.DeckConf"],
        doc="Allows modifying or adding widgets in the deck options UI form",
    ),
    Hook(
        name="deck_conf_will_show",
        args=["deck_conf: aqt.deckconf.DeckConf"],
        doc="Allows modifying the deck options dialog before it is shown",
    ),
    Hook(
        name="deck_conf_did_load_config",
        args=["deck_conf: aqt.deckconf.DeckConf", "deck: Any", "config: Any"],
        doc="Called once widget state has been set from deck config",
    ),
    Hook(
        name="deck_conf_will_save_config",
        args=["deck_conf: aqt.deckconf.DeckConf", "deck: Any", "config: Any"],
        doc="Called before widget state is saved to config",
    ),
    # Browser
    ###################
    Hook(name="browser_will_show", args=["browser: aqt.browser.Browser"]),
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
    Hook(
        name="browser_will_build_tree",
        args=[
            "handled: bool",
            "tree: aqt.browser.SidebarItem",
            "stage: aqt.browser.SidebarStage",
            "browser: aqt.browser.Browser",
        ],
        return_type="bool",
        doc="""Used to add or replace items in the browser sidebar tree
        
        'tree' is the root SidebarItem that all other items are added to.
        
        'stage' is an enum describing the different construction stages of
        the sidebar tree at which you can interject your changes.
        The different values can be inspected by looking at
        aqt.browser.SidebarStage.
        
        If you want Anki to proceed with the construction of the tree stage
        in question after your have performed your changes or additions,
        return the 'handled' boolean unchanged.
        
        On the other hand, if you want to prevent Anki from adding its own
        items at a particular construction stage (e.g. in case your add-on
        implements its own version of that particular stage), return 'True'.
        
        If you return 'True' at SidebarStage.ROOT, the sidebar will not be
        populated by any of the other construction stages. For any other stage
        the tree construction will just continue as usual.
        
        For example, if your code wishes to replace the tag tree, you could do:
        
            def on_browser_will_build_tree(handled, root, stage, browser):
                if stage != SidebarStage.TAGS:
                    # not at tag tree building stage, pass on
                    return handled
                
                # your tag tree construction code
                # root.addChild(...)
                
                # your code handled tag tree construction, no need for Anki
                # or other add-ons to build the tag tree
                return True
        """,
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

            def on_webview_will_set_content(web_content: WebContent, context):
                
                if not isinstance(context, aqt.reviewer.Reviewer):
                    # not reviewer, do not modify content
                    return
                
                # reviewer, perform changes to content
                
                context: aqt.reviewer.Reviewer
                
                addon_package = mw.addonManager.addonFromModule(__name__)
                
                web_content.css.append(
                    f"/_addons/{addon_package}/web/my-addon.css")
                web_content.js.append(
                    f"/_addons/{addon_package}/web/my-addon.js")

                web_content.head += "<script>console.log('my-addon')</script>"
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
    Hook(
        name="top_toolbar_did_init_links",
        args=["links: List[str]", "top_toolbar: aqt.toolbar.Toolbar"],
        doc="""Used to modify or add links in the top toolbar of Anki's main window
        
        'links' is a list of HTML link elements. Add-ons can generate their own links
        by using aqt.toolbar.Toolbar.create_link. Links created in that way can then be
        appended to the link list, e.g.:

            def on_top_toolbar_did_init_links(links, toolbar):
                my_link = toolbar.create_link(...)
                links.append(my_link)
        """,
    ),
    Hook(
        name="media_sync_did_progress", args=["entry: aqt.mediasync.LogEntryWithTime"],
    ),
    Hook(name="media_sync_did_start_or_stop", args=["running: bool"]),
    Hook(
        name="empty_cards_will_be_deleted",
        args=["cids: List[int]"],
        return_type="List[int]",
        doc="""Allow to change the list of cards to delete.

        For example, an add-on creating a method to delete only empty
        new cards would be done as follow:
```
from anki.consts import CARD_TYPE_NEW
from anki.utils import ids2str
from aqt import mw
from aqt import gui_hooks

def filter(cids, col):
    return col.db.list(
            f"select id from cards where (type={CARD_TYPE_NEW} and (id in {ids2str(cids)))")

def emptyNewCard():
    gui_hooks.append(filter)
    mw.onEmptyCards()
    gui_hooks.remove(filter)
```""",
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
    # Addon
    ###################
    Hook(
        name="addon_config_editor_will_display_json",
        args=["text: str"],
        return_type="str",
        doc="""Allows changing the text of the json configuration before actually
        displaying it to the user. For example, you can replace "\\\\n" by
        some actual new line. Then you can replace the new lines by "\\\\n"
        while reading the file and let the user uses real new line in
        string instead of its encoding.""",
    ),
    Hook(
        name="addon_config_editor_will_save_json",
        args=["text: str"],
        return_type="str",
        doc="""Allows changing the text of the json configuration that was
        received from the user before actually reading it. For
        example, you can replace new line in strings by some "\\\\n".""",
    ),
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
