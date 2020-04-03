# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""
See pylib/anki/hooks.py
"""

from __future__ import annotations

from typing import Any, Callable, Dict, List, Optional, Tuple

import anki
import aqt
from anki.cards import Card
from anki.hooks import runFilter, runHook
from aqt.qt import QDialog, QEvent, QMenu
from aqt.tagedit import TagEdit

# New hook/filter handling
##############################################################################
# The code in this section is automatically generated - any edits you make
# will be lost. To add new hooks, see ../tools/genhooks_gui.py
#
# @@AUTOGEN@@


class _AddCardsDidAddNoteHook:
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


add_cards_did_add_note = _AddCardsDidAddNoteHook()


class _AddCardsDidInitHook:
    _hooks: List[Callable[["aqt.addcards.AddCards"], None]] = []

    def append(self, cb: Callable[["aqt.addcards.AddCards"], None]) -> None:
        """(addcards: aqt.addcards.AddCards)"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[["aqt.addcards.AddCards"], None]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self, addcards: aqt.addcards.AddCards) -> None:
        for hook in self._hooks:
            try:
                hook(addcards)
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise


add_cards_did_init = _AddCardsDidInitHook()


class _AddCardsWillAddNoteFilter:
    """Decides whether the note should be added to the collection or
        not. It is assumed to come from the addCards window.

        reason_to_already_reject is the first reason to reject that
        was found, or None. If your filter wants to reject, it should
        replace return the reason to reject. Otherwise return the
        input."""

    _hooks: List[Callable[[Optional[str], "anki.notes.Note"], Optional[str]]] = []

    def append(
        self, cb: Callable[[Optional[str], "anki.notes.Note"], Optional[str]]
    ) -> None:
        """(problem: Optional[str], note: anki.notes.Note)"""
        self._hooks.append(cb)

    def remove(
        self, cb: Callable[[Optional[str], "anki.notes.Note"], Optional[str]]
    ) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self, problem: Optional[str], note: anki.notes.Note) -> Optional[str]:
        for filter in self._hooks:
            try:
                problem = filter(problem, note)
            except:
                # if the hook fails, remove it
                self._hooks.remove(filter)
                raise
        return problem


add_cards_will_add_note = _AddCardsWillAddNoteFilter()


class _AddCardsWillShowHistoryMenuHook:
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


add_cards_will_show_history_menu = _AddCardsWillShowHistoryMenuHook()


class _AddonConfigEditorWillDisplayJsonFilter:
    """Allows changing the text of the json configuration before actually
        displaying it to the user. For example, you can replace "\n" by
        some actual new line. Then you can replace the new lines by "\n"
        while reading the file and let the user uses real new line in
        string instead of its encoding."""

    _hooks: List[Callable[[str], str]] = []

    def append(self, cb: Callable[[str], str]) -> None:
        """(text: str)"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[[str], str]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self, text: str) -> str:
        for filter in self._hooks:
            try:
                text = filter(text)
            except:
                # if the hook fails, remove it
                self._hooks.remove(filter)
                raise
        return text


addon_config_editor_will_display_json = _AddonConfigEditorWillDisplayJsonFilter()


class _AddonConfigEditorWillSaveJsonFilter:
    """Allows changing the text of the json configuration that was
        received from the user before actually reading it. For
        example, you can replace new line in strings by some "\n"."""

    _hooks: List[Callable[[str], str]] = []

    def append(self, cb: Callable[[str], str]) -> None:
        """(text: str)"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[[str], str]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self, text: str) -> str:
        for filter in self._hooks:
            try:
                text = filter(text)
            except:
                # if the hook fails, remove it
                self._hooks.remove(filter)
                raise
        return text


addon_config_editor_will_save_json = _AddonConfigEditorWillSaveJsonFilter()


class _AddonsDialogDidChangeSelectedAddonHook:
    """Allows doing an action when a single add-on is selected."""

    _hooks: List[
        Callable[["aqt.addons.AddonsDialog", "aqt.addons.AddonMeta"], None]
    ] = []

    def append(
        self, cb: Callable[["aqt.addons.AddonsDialog", "aqt.addons.AddonMeta"], None]
    ) -> None:
        """(dialog: aqt.addons.AddonsDialog, add_on: aqt.addons.AddonMeta)"""
        self._hooks.append(cb)

    def remove(
        self, cb: Callable[["aqt.addons.AddonsDialog", "aqt.addons.AddonMeta"], None]
    ) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(
        self, dialog: aqt.addons.AddonsDialog, add_on: aqt.addons.AddonMeta
    ) -> None:
        for hook in self._hooks:
            try:
                hook(dialog, add_on)
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise


addons_dialog_did_change_selected_addon = _AddonsDialogDidChangeSelectedAddonHook()


class _AddonsDialogWillShowHook:
    """Allows changing the add-on dialog before it is shown. E.g. add
        buttons."""

    _hooks: List[Callable[["aqt.addons.AddonsDialog"], None]] = []

    def append(self, cb: Callable[["aqt.addons.AddonsDialog"], None]) -> None:
        """(dialog: aqt.addons.AddonsDialog)"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[["aqt.addons.AddonsDialog"], None]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self, dialog: aqt.addons.AddonsDialog) -> None:
        for hook in self._hooks:
            try:
                hook(dialog)
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise


addons_dialog_will_show = _AddonsDialogWillShowHook()


class _AvPlayerDidBeginPlayingHook:
    _hooks: List[Callable[["aqt.sound.Player", "anki.sound.AVTag"], None]] = []

    def append(
        self, cb: Callable[["aqt.sound.Player", "anki.sound.AVTag"], None]
    ) -> None:
        """(player: aqt.sound.Player, tag: anki.sound.AVTag)"""
        self._hooks.append(cb)

    def remove(
        self, cb: Callable[["aqt.sound.Player", "anki.sound.AVTag"], None]
    ) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self, player: aqt.sound.Player, tag: anki.sound.AVTag) -> None:
        for hook in self._hooks:
            try:
                hook(player, tag)
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise


av_player_did_begin_playing = _AvPlayerDidBeginPlayingHook()


class _AvPlayerDidEndPlayingHook:
    _hooks: List[Callable[["aqt.sound.Player"], None]] = []

    def append(self, cb: Callable[["aqt.sound.Player"], None]) -> None:
        """(player: aqt.sound.Player)"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[["aqt.sound.Player"], None]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self, player: aqt.sound.Player) -> None:
        for hook in self._hooks:
            try:
                hook(player)
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise


av_player_did_end_playing = _AvPlayerDidEndPlayingHook()


class _AvPlayerWillPlayHook:
    _hooks: List[Callable[["anki.sound.AVTag"], None]] = []

    def append(self, cb: Callable[["anki.sound.AVTag"], None]) -> None:
        """(tag: anki.sound.AVTag)"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[["anki.sound.AVTag"], None]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self, tag: anki.sound.AVTag) -> None:
        for hook in self._hooks:
            try:
                hook(tag)
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise


av_player_will_play = _AvPlayerWillPlayHook()


class _BackupDidCompleteHook:
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


backup_did_complete = _BackupDidCompleteHook()


class _BrowserDidChangeRowHook:
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


browser_did_change_row = _BrowserDidChangeRowHook()


class _BrowserDidSearchHook:
    """Allows you to modify the list of returned card ids from a search."""

    _hooks: List[Callable[["aqt.browser.SearchContext"], None]] = []

    def append(self, cb: Callable[["aqt.browser.SearchContext"], None]) -> None:
        """(context: aqt.browser.SearchContext)"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[["aqt.browser.SearchContext"], None]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self, context: aqt.browser.SearchContext) -> None:
        for hook in self._hooks:
            try:
                hook(context)
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise


browser_did_search = _BrowserDidSearchHook()


class _BrowserHeaderWillShowContextMenuHook:
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


browser_header_will_show_context_menu = _BrowserHeaderWillShowContextMenuHook()


class _BrowserMenusDidInitHook:
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


browser_menus_did_init = _BrowserMenusDidInitHook()


class _BrowserWillBuildTreeFilter:
    """Used to add or replace items in the browser sidebar tree
        
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
        """

    _hooks: List[
        Callable[
            [
                bool,
                "aqt.browser.SidebarItem",
                "aqt.browser.SidebarStage",
                "aqt.browser.Browser",
            ],
            bool,
        ]
    ] = []

    def append(
        self,
        cb: Callable[
            [
                bool,
                "aqt.browser.SidebarItem",
                "aqt.browser.SidebarStage",
                "aqt.browser.Browser",
            ],
            bool,
        ],
    ) -> None:
        """(handled: bool, tree: aqt.browser.SidebarItem, stage: aqt.browser.SidebarStage, browser: aqt.browser.Browser)"""
        self._hooks.append(cb)

    def remove(
        self,
        cb: Callable[
            [
                bool,
                "aqt.browser.SidebarItem",
                "aqt.browser.SidebarStage",
                "aqt.browser.Browser",
            ],
            bool,
        ],
    ) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(
        self,
        handled: bool,
        tree: aqt.browser.SidebarItem,
        stage: aqt.browser.SidebarStage,
        browser: aqt.browser.Browser,
    ) -> bool:
        for filter in self._hooks:
            try:
                handled = filter(handled, tree, stage, browser)
            except:
                # if the hook fails, remove it
                self._hooks.remove(filter)
                raise
        return handled


browser_will_build_tree = _BrowserWillBuildTreeFilter()


class _BrowserWillSearchHook:
    """Allows you to modify the search text, or perform your own search.
         
         You can modify context.search to change the text that is sent to the
         searching backend.
         
         If you set context.card_ids to a list of ids, the regular search will
         not be performed, and the provided ids will be used instead.
         
         Your add-on should check if context.card_ids is not None, and return
         without making changes if it has been set.
         """

    _hooks: List[Callable[["aqt.browser.SearchContext"], None]] = []

    def append(self, cb: Callable[["aqt.browser.SearchContext"], None]) -> None:
        """(context: aqt.browser.SearchContext)"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[["aqt.browser.SearchContext"], None]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self, context: aqt.browser.SearchContext) -> None:
        for hook in self._hooks:
            try:
                hook(context)
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise


browser_will_search = _BrowserWillSearchHook()


class _BrowserWillShowHook:
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


browser_will_show = _BrowserWillShowHook()


class _BrowserWillShowContextMenuHook:
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


browser_will_show_context_menu = _BrowserWillShowContextMenuHook()


class _CardLayoutWillShowHook:
    """Allow to change the display of the card layout. After most values are
         set and before the window is actually shown."""

    _hooks: List[Callable[["aqt.clayout.CardLayout"], None]] = []

    def append(self, cb: Callable[["aqt.clayout.CardLayout"], None]) -> None:
        """(clayout: aqt.clayout.CardLayout)"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[["aqt.clayout.CardLayout"], None]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self, clayout: aqt.clayout.CardLayout) -> None:
        for hook in self._hooks:
            try:
                hook(clayout)
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise


card_layout_will_show = _CardLayoutWillShowHook()


class _CardWillShowFilter:
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
        text = runFilter("prepareQA", text, card, kind)
        return text


card_will_show = _CardWillShowFilter()


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


class _DebugConsoleDidEvaluatePythonFilter:
    """Allows processing the debug result. E.g. logging queries and
        result, saving last query to display it later..."""

    _hooks: List[Callable[[str, str, QDialog], str]] = []

    def append(self, cb: Callable[[str, str, QDialog], str]) -> None:
        """(output: str, query: str, debug_window: QDialog)"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[[str, str, QDialog], str]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self, output: str, query: str, debug_window: QDialog) -> str:
        for filter in self._hooks:
            try:
                output = filter(output, query, debug_window)
            except:
                # if the hook fails, remove it
                self._hooks.remove(filter)
                raise
        return output


debug_console_did_evaluate_python = _DebugConsoleDidEvaluatePythonFilter()


class _DebugConsoleWillShowHook:
    """Allows editing the debug window. E.g. setting a default code, or
        previous code."""

    _hooks: List[Callable[[QDialog], None]] = []

    def append(self, cb: Callable[[QDialog], None]) -> None:
        """(debug_window: QDialog)"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[[QDialog], None]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self, debug_window: QDialog) -> None:
        for hook in self._hooks:
            try:
                hook(debug_window)
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise


debug_console_will_show = _DebugConsoleWillShowHook()


class _DeckBrowserDidRenderHook:
    """Allow to update the deck browser window. E.g. change its title."""

    _hooks: List[Callable[["aqt.deckbrowser.DeckBrowser"], None]] = []

    def append(self, cb: Callable[["aqt.deckbrowser.DeckBrowser"], None]) -> None:
        """(deck_browser: aqt.deckbrowser.DeckBrowser)"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[["aqt.deckbrowser.DeckBrowser"], None]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self, deck_browser: aqt.deckbrowser.DeckBrowser) -> None:
        for hook in self._hooks:
            try:
                hook(deck_browser)
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise


deck_browser_did_render = _DeckBrowserDidRenderHook()


class _DeckBrowserWillRenderContentHook:
    """Used to modify HTML content sections in the deck browser body
        
        'content' contains the sections of HTML content the deck browser body
        will be updated with.
        
        When modifying the content of a particular section, please make sure your
        changes only perform the minimum required edits to make your add-on work.
        You should avoid overwriting or interfering with existing data as much
        as possible, instead opting to append your own changes, e.g.:
        
            def on_deck_browser_will_render_content(deck_browser, content):
                content.stats += "
<div>my html</div>"
        """

    _hooks: List[
        Callable[
            ["aqt.deckbrowser.DeckBrowser", "aqt.deckbrowser.DeckBrowserContent"], None
        ]
    ] = []

    def append(
        self,
        cb: Callable[
            ["aqt.deckbrowser.DeckBrowser", "aqt.deckbrowser.DeckBrowserContent"], None
        ],
    ) -> None:
        """(deck_browser: aqt.deckbrowser.DeckBrowser, content: aqt.deckbrowser.DeckBrowserContent)"""
        self._hooks.append(cb)

    def remove(
        self,
        cb: Callable[
            ["aqt.deckbrowser.DeckBrowser", "aqt.deckbrowser.DeckBrowserContent"], None
        ],
    ) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(
        self,
        deck_browser: aqt.deckbrowser.DeckBrowser,
        content: aqt.deckbrowser.DeckBrowserContent,
    ) -> None:
        for hook in self._hooks:
            try:
                hook(deck_browser, content)
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise


deck_browser_will_render_content = _DeckBrowserWillRenderContentHook()


class _DeckBrowserWillShowOptionsMenuHook:
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


deck_browser_will_show_options_menu = _DeckBrowserWillShowOptionsMenuHook()


class _DeckConfDidLoadConfigHook:
    """Called once widget state has been set from deck config"""

    _hooks: List[Callable[["aqt.deckconf.DeckConf", Any, Any], None]] = []

    def append(self, cb: Callable[["aqt.deckconf.DeckConf", Any, Any], None]) -> None:
        """(deck_conf: aqt.deckconf.DeckConf, deck: Any, config: Any)"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[["aqt.deckconf.DeckConf", Any, Any], None]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(
        self, deck_conf: aqt.deckconf.DeckConf, deck: Any, config: Any
    ) -> None:
        for hook in self._hooks:
            try:
                hook(deck_conf, deck, config)
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise


deck_conf_did_load_config = _DeckConfDidLoadConfigHook()


class _DeckConfDidSetupUiFormHook:
    """Allows modifying or adding widgets in the deck options UI form"""

    _hooks: List[Callable[["aqt.deckconf.DeckConf"], None]] = []

    def append(self, cb: Callable[["aqt.deckconf.DeckConf"], None]) -> None:
        """(deck_conf: aqt.deckconf.DeckConf)"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[["aqt.deckconf.DeckConf"], None]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self, deck_conf: aqt.deckconf.DeckConf) -> None:
        for hook in self._hooks:
            try:
                hook(deck_conf)
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise


deck_conf_did_setup_ui_form = _DeckConfDidSetupUiFormHook()


class _DeckConfWillSaveConfigHook:
    """Called before widget state is saved to config"""

    _hooks: List[Callable[["aqt.deckconf.DeckConf", Any, Any], None]] = []

    def append(self, cb: Callable[["aqt.deckconf.DeckConf", Any, Any], None]) -> None:
        """(deck_conf: aqt.deckconf.DeckConf, deck: Any, config: Any)"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[["aqt.deckconf.DeckConf", Any, Any], None]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(
        self, deck_conf: aqt.deckconf.DeckConf, deck: Any, config: Any
    ) -> None:
        for hook in self._hooks:
            try:
                hook(deck_conf, deck, config)
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise


deck_conf_will_save_config = _DeckConfWillSaveConfigHook()


class _DeckConfWillShowHook:
    """Allows modifying the deck options dialog before it is shown"""

    _hooks: List[Callable[["aqt.deckconf.DeckConf"], None]] = []

    def append(self, cb: Callable[["aqt.deckconf.DeckConf"], None]) -> None:
        """(deck_conf: aqt.deckconf.DeckConf)"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[["aqt.deckconf.DeckConf"], None]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self, deck_conf: aqt.deckconf.DeckConf) -> None:
        for hook in self._hooks:
            try:
                hook(deck_conf)
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise


deck_conf_will_show = _DeckConfWillShowHook()


class _EditorDidFireTypingTimerHook:
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


editor_did_fire_typing_timer = _EditorDidFireTypingTimerHook()


class _EditorDidFocusFieldHook:
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


editor_did_focus_field = _EditorDidFocusFieldHook()


class _EditorDidInitHook:
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


editor_did_init = _EditorDidInitHook()


class _EditorDidInitButtonsHook:
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


editor_did_init_buttons = _EditorDidInitButtonsHook()


class _EditorDidInitShortcutsHook:
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


editor_did_init_shortcuts = _EditorDidInitShortcutsHook()


class _EditorDidLoadNoteHook:
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


editor_did_load_note = _EditorDidLoadNoteHook()


class _EditorDidUnfocusFieldFilter:
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
        changed = runFilter("editFocusLost", changed, note, current_field_idx)
        return changed


editor_did_unfocus_field = _EditorDidUnfocusFieldFilter()


class _EditorDidUpdateTagsHook:
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


editor_did_update_tags = _EditorDidUpdateTagsHook()


class _EditorWebViewDidInitHook:
    _hooks: List[Callable[["aqt.editor.EditorWebView"], None]] = []

    def append(self, cb: Callable[["aqt.editor.EditorWebView"], None]) -> None:
        """(editor_web_view: aqt.editor.EditorWebView)"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[["aqt.editor.EditorWebView"], None]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self, editor_web_view: aqt.editor.EditorWebView) -> None:
        for hook in self._hooks:
            try:
                hook(editor_web_view)
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise


editor_web_view_did_init = _EditorWebViewDidInitHook()


class _EditorWillLoadNoteFilter:
    """Allows changing the javascript commands to load note before
        executing it and do change in the QT editor."""

    _hooks: List[Callable[[str, "anki.notes.Note", "aqt.editor.Editor"], str]] = []

    def append(
        self, cb: Callable[[str, "anki.notes.Note", "aqt.editor.Editor"], str]
    ) -> None:
        """(js: str, note: anki.notes.Note, editor: aqt.editor.Editor)"""
        self._hooks.append(cb)

    def remove(
        self, cb: Callable[[str, "anki.notes.Note", "aqt.editor.Editor"], str]
    ) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(
        self, js: str, note: anki.notes.Note, editor: aqt.editor.Editor
    ) -> str:
        for filter in self._hooks:
            try:
                js = filter(js, note, editor)
            except:
                # if the hook fails, remove it
                self._hooks.remove(filter)
                raise
        return js


editor_will_load_note = _EditorWillLoadNoteFilter()


class _EditorWillShowContextMenuHook:
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


editor_will_show_context_menu = _EditorWillShowContextMenuHook()


class _EditorWillUseFontForFieldFilter:
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
        font = runFilter("mungeEditingFontName", font)
        return font


editor_will_use_font_for_field = _EditorWillUseFontForFieldFilter()


class _EmptyCardsWillBeDeletedFilter:
    """Allow to change the list of cards to delete.

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
```"""

    _hooks: List[Callable[[List[int]], List[int]]] = []

    def append(self, cb: Callable[[List[int]], List[int]]) -> None:
        """(cids: List[int])"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[[List[int]], List[int]]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self, cids: List[int]) -> List[int]:
        for filter in self._hooks:
            try:
                cids = filter(cids)
            except:
                # if the hook fails, remove it
                self._hooks.remove(filter)
                raise
        return cids


empty_cards_will_be_deleted = _EmptyCardsWillBeDeletedFilter()


class _MediaSyncDidProgressHook:
    _hooks: List[Callable[["aqt.mediasync.LogEntryWithTime"], None]] = []

    def append(self, cb: Callable[["aqt.mediasync.LogEntryWithTime"], None]) -> None:
        """(entry: aqt.mediasync.LogEntryWithTime)"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[["aqt.mediasync.LogEntryWithTime"], None]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self, entry: aqt.mediasync.LogEntryWithTime) -> None:
        for hook in self._hooks:
            try:
                hook(entry)
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise


media_sync_did_progress = _MediaSyncDidProgressHook()


class _MediaSyncDidStartOrStopHook:
    _hooks: List[Callable[[bool], None]] = []

    def append(self, cb: Callable[[bool], None]) -> None:
        """(running: bool)"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[[bool], None]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self, running: bool) -> None:
        for hook in self._hooks:
            try:
                hook(running)
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise


media_sync_did_start_or_stop = _MediaSyncDidStartOrStopHook()


class _ModelsAdvancedWillShowHook:
    _hooks: List[Callable[[QDialog], None]] = []

    def append(self, cb: Callable[[QDialog], None]) -> None:
        """(advanced: QDialog)"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[[QDialog], None]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self, advanced: QDialog) -> None:
        for hook in self._hooks:
            try:
                hook(advanced)
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise


models_advanced_will_show = _ModelsAdvancedWillShowHook()


class _OverviewDidRefreshHook:
    """Allow to update the overview window. E.g. add the deck name in the
        title."""

    _hooks: List[Callable[["aqt.overview.Overview"], None]] = []

    def append(self, cb: Callable[["aqt.overview.Overview"], None]) -> None:
        """(overview: aqt.overview.Overview)"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[["aqt.overview.Overview"], None]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self, overview: aqt.overview.Overview) -> None:
        for hook in self._hooks:
            try:
                hook(overview)
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise


overview_did_refresh = _OverviewDidRefreshHook()


class _OverviewWillRenderContentHook:
    """Used to modify HTML content sections in the overview body

        'content' contains the sections of HTML content the overview body
        will be updated with.

        When modifying the content of a particular section, please make sure your
        changes only perform the minimum required edits to make your add-on work.
        You should avoid overwriting or interfering with existing data as much
        as possible, instead opting to append your own changes, e.g.:

            def on_overview_will_render_content(overview, content):
                content.table += "
<div>my html</div>"
        """

    _hooks: List[
        Callable[["aqt.overview.Overview", "aqt.overview.OverviewContent"], None]
    ] = []

    def append(
        self,
        cb: Callable[["aqt.overview.Overview", "aqt.overview.OverviewContent"], None],
    ) -> None:
        """(overview: aqt.overview.Overview, content: aqt.overview.OverviewContent)"""
        self._hooks.append(cb)

    def remove(
        self,
        cb: Callable[["aqt.overview.Overview", "aqt.overview.OverviewContent"], None],
    ) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(
        self, overview: aqt.overview.Overview, content: aqt.overview.OverviewContent
    ) -> None:
        for hook in self._hooks:
            try:
                hook(overview, content)
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise


overview_will_render_content = _OverviewWillRenderContentHook()


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


class _ReviewerDidAnswerCardHook:
    _hooks: List[Callable[["aqt.reviewer.Reviewer", Card, int], None]] = []

    def append(self, cb: Callable[["aqt.reviewer.Reviewer", Card, int], None]) -> None:
        """(reviewer: aqt.reviewer.Reviewer, card: Card, ease: int)"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[["aqt.reviewer.Reviewer", Card, int], None]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self, reviewer: aqt.reviewer.Reviewer, card: Card, ease: int) -> None:
        for hook in self._hooks:
            try:
                hook(reviewer, card, ease)
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise


reviewer_did_answer_card = _ReviewerDidAnswerCardHook()


class _ReviewerDidShowAnswerHook:
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


reviewer_did_show_answer = _ReviewerDidShowAnswerHook()


class _ReviewerDidShowQuestionHook:
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


reviewer_did_show_question = _ReviewerDidShowQuestionHook()


class _ReviewerWillAnswerCardFilter:
    """Used to modify the ease at which a card is rated or to bypass
        rating the card completely.
        
        ease_tuple is a tuple consisting of a boolean expressing whether the reviewer
        should continue with rating the card, and an integer expressing the ease at
        which the card should be rated.
        
        If your code just needs to be notified of the card rating event, you should use
        the reviewer_did_answer_card hook instead."""

    _hooks: List[
        Callable[[Tuple[bool, int], "aqt.reviewer.Reviewer", Card], Tuple[bool, int]]
    ] = []

    def append(
        self,
        cb: Callable[
            [Tuple[bool, int], "aqt.reviewer.Reviewer", Card], Tuple[bool, int]
        ],
    ) -> None:
        """(ease_tuple: Tuple[bool, int], reviewer: aqt.reviewer.Reviewer, card: Card)"""
        self._hooks.append(cb)

    def remove(
        self,
        cb: Callable[
            [Tuple[bool, int], "aqt.reviewer.Reviewer", Card], Tuple[bool, int]
        ],
    ) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(
        self, ease_tuple: Tuple[bool, int], reviewer: aqt.reviewer.Reviewer, card: Card
    ) -> Tuple[bool, int]:
        for filter in self._hooks:
            try:
                ease_tuple = filter(ease_tuple, reviewer, card)
            except:
                # if the hook fails, remove it
                self._hooks.remove(filter)
                raise
        return ease_tuple


reviewer_will_answer_card = _ReviewerWillAnswerCardFilter()


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


class _ReviewerWillShowContextMenuHook:
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


reviewer_will_show_context_menu = _ReviewerWillShowContextMenuHook()


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


class _StyleDidInitFilter:
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
        style = runFilter("setupStyle", style)
        return style


style_did_init = _StyleDidInitFilter()


class _TagEditorDidProcessKeyHook:
    _hooks: List[Callable[[TagEdit, QEvent], None]] = []

    def append(self, cb: Callable[[TagEdit, QEvent], None]) -> None:
        """(tag_edit: TagEdit, evt: QEvent)"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[[TagEdit, QEvent], None]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self, tag_edit: TagEdit, evt: QEvent) -> None:
        for hook in self._hooks:
            try:
                hook(tag_edit, evt)
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise


tag_editor_did_process_key = _TagEditorDidProcessKeyHook()


class _TopToolbarDidInitLinksHook:
    """Used to modify or add links in the top toolbar of Anki's main window
        
        'links' is a list of HTML link elements. Add-ons can generate their own links
        by using aqt.toolbar.Toolbar.create_link. Links created in that way can then be
        appended to the link list, e.g.:

            def on_top_toolbar_did_init_links(links, toolbar):
                my_link = toolbar.create_link(...)
                links.append(my_link)
        """

    _hooks: List[Callable[[List[str], "aqt.toolbar.Toolbar"], None]] = []

    def append(self, cb: Callable[[List[str], "aqt.toolbar.Toolbar"], None]) -> None:
        """(links: List[str], top_toolbar: aqt.toolbar.Toolbar)"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[[List[str], "aqt.toolbar.Toolbar"], None]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self, links: List[str], top_toolbar: aqt.toolbar.Toolbar) -> None:
        for hook in self._hooks:
            try:
                hook(links, top_toolbar)
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise


top_toolbar_did_init_links = _TopToolbarDidInitLinksHook()


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


class _WebviewDidReceiveJsMessageFilter:
    """Used to handle pycmd() messages sent from Javascript.
        
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
        """

    _hooks: List[Callable[[Tuple[bool, Any], str, Any], Tuple[bool, Any]]] = []

    def append(
        self, cb: Callable[[Tuple[bool, Any], str, Any], Tuple[bool, Any]]
    ) -> None:
        """(handled: Tuple[bool, Any], message: str, context: Any)"""
        self._hooks.append(cb)

    def remove(
        self, cb: Callable[[Tuple[bool, Any], str, Any], Tuple[bool, Any]]
    ) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(
        self, handled: Tuple[bool, Any], message: str, context: Any
    ) -> Tuple[bool, Any]:
        for filter in self._hooks:
            try:
                handled = filter(handled, message, context)
            except:
                # if the hook fails, remove it
                self._hooks.remove(filter)
                raise
        return handled


webview_did_receive_js_message = _WebviewDidReceiveJsMessageFilter()


class _WebviewWillSetContentHook:
    """Used to modify web content before it is rendered.

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
        """

    _hooks: List[Callable[["aqt.webview.WebContent", Optional[Any]], None]] = []

    def append(
        self, cb: Callable[["aqt.webview.WebContent", Optional[Any]], None]
    ) -> None:
        """(web_content: aqt.webview.WebContent, context: Optional[Any])"""
        self._hooks.append(cb)

    def remove(
        self, cb: Callable[["aqt.webview.WebContent", Optional[Any]], None]
    ) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(
        self, web_content: aqt.webview.WebContent, context: Optional[Any]
    ) -> None:
        for hook in self._hooks:
            try:
                hook(web_content, context)
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise


webview_will_set_content = _WebviewWillSetContentHook()


class _WebviewWillShowContextMenuHook:
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


webview_will_show_context_menu = _WebviewWillShowContextMenuHook()
# @@AUTOGEN@@
