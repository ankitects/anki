# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""
Tools for extending Anki.

A hook takes a function that does not return a value.

A filter takes a function that returns its first argument, optionally
modifying it.
"""

from __future__ import annotations

from typing import Any, Callable, Dict, List, Tuple

import decorator

import anki
from anki.cards import Card
from anki.notes import Note

# New hook/filter handling
##############################################################################
# The code in this section is automatically generated - any edits you make
# will be lost. To add new hooks, see ../tools/genhooks.py
#
# @@AUTOGEN@@


class _BgThreadProgressCallbackFilter:
    """Warning: this is called on a background thread."""

    _hooks: List[Callable[[bool, "anki.rsbackend.Progress"], bool]] = []

    def append(self, cb: Callable[[bool, "anki.rsbackend.Progress"], bool]) -> None:
        """(proceed: bool, progress: anki.rsbackend.Progress)"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[[bool, "anki.rsbackend.Progress"], bool]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self, proceed: bool, progress: anki.rsbackend.Progress) -> bool:
        for filter in self._hooks:
            try:
                proceed = filter(proceed, progress)
            except:
                # if the hook fails, remove it
                self._hooks.remove(filter)
                raise
        return proceed


bg_thread_progress_callback = _BgThreadProgressCallbackFilter()


class _CardDidLeechHook:
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
        runHook("leech", card)


card_did_leech = _CardDidLeechHook()


class _CardDidRenderHook:
    """Can modify the resulting text after rendering completes."""

    _hooks: List[
        Callable[
            [
                "anki.template.TemplateRenderOutput",
                "anki.template.TemplateRenderContext",
            ],
            None,
        ]
    ] = []

    def append(
        self,
        cb: Callable[
            [
                "anki.template.TemplateRenderOutput",
                "anki.template.TemplateRenderContext",
            ],
            None,
        ],
    ) -> None:
        """(output: anki.template.TemplateRenderOutput, ctx: anki.template.TemplateRenderContext)"""
        self._hooks.append(cb)

    def remove(
        self,
        cb: Callable[
            [
                "anki.template.TemplateRenderOutput",
                "anki.template.TemplateRenderContext",
            ],
            None,
        ],
    ) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(
        self,
        output: anki.template.TemplateRenderOutput,
        ctx: anki.template.TemplateRenderContext,
    ) -> None:
        for hook in self._hooks:
            try:
                hook(output, ctx)
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise


card_did_render = _CardDidRenderHook()


class _CardOdueWasInvalidHook:
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


card_odue_was_invalid = _CardOdueWasInvalidHook()


class _CardWillFlushHook:
    """Allow to change a card before it is added/updated in the database."""

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


card_will_flush = _CardWillFlushHook()


class _DeckAddedHook:
    _hooks: List[Callable[[Dict[str, Any]], None]] = []

    def append(self, cb: Callable[[Dict[str, Any]], None]) -> None:
        """(deck: Dict[str, Any])"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[[Dict[str, Any]], None]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self, deck: Dict[str, Any]) -> None:
        for hook in self._hooks:
            try:
                hook(deck)
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise
        # legacy support
        runHook("newDeck")


deck_added = _DeckAddedHook()


class _ExportersListCreatedHook:
    _hooks: List[Callable[[List[Tuple[str, Any]]], None]] = []

    def append(self, cb: Callable[[List[Tuple[str, Any]]], None]) -> None:
        """(exporters: List[Tuple[str, Any]])"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[[List[Tuple[str, Any]]], None]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self, exporters: List[Tuple[str, Any]]) -> None:
        for hook in self._hooks:
            try:
                hook(exporters)
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise
        # legacy support
        runHook("exportersList", exporters)


exporters_list_created = _ExportersListCreatedHook()


class _FieldFilterFilter:
    """Allows you to define custom {{filters:..}}
        
        Your add-on can check filter_name to decide whether it should modify
        field_text or not before returning it."""

    _hooks: List[
        Callable[[str, str, str, "anki.template.TemplateRenderContext"], str]
    ] = []

    def append(
        self, cb: Callable[[str, str, str, "anki.template.TemplateRenderContext"], str]
    ) -> None:
        """(field_text: str, field_name: str, filter_name: str, ctx: anki.template.TemplateRenderContext)"""
        self._hooks.append(cb)

    def remove(
        self, cb: Callable[[str, str, str, "anki.template.TemplateRenderContext"], str]
    ) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(
        self,
        field_text: str,
        field_name: str,
        filter_name: str,
        ctx: anki.template.TemplateRenderContext,
    ) -> str:
        for filter in self._hooks:
            try:
                field_text = filter(field_text, field_name, filter_name, ctx)
            except:
                # if the hook fails, remove it
                self._hooks.remove(filter)
                raise
        return field_text


field_filter = _FieldFilterFilter()


class _MediaFilesDidExportHook:
    _hooks: List[Callable[[int], None]] = []

    def append(self, cb: Callable[[int], None]) -> None:
        """(count: int)"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[[int], None]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self, count: int) -> None:
        for hook in self._hooks:
            try:
                hook(count)
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise


media_files_did_export = _MediaFilesDidExportHook()


class _NoteTypeAddedHook:
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
        runHook("newModel")


note_type_added = _NoteTypeAddedHook()


class _NoteWillFlushHook:
    """Allow to change a note before it is added/updated in the database."""

    _hooks: List[Callable[[Note], None]] = []

    def append(self, cb: Callable[[Note], None]) -> None:
        """(note: Note)"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[[Note], None]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self, note: Note) -> None:
        for hook in self._hooks:
            try:
                hook(note)
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise


note_will_flush = _NoteWillFlushHook()


class _NotesWillBeDeletedHook:
    _hooks: List[Callable[["anki.storage._Collection", List[int]], None]] = []

    def append(
        self, cb: Callable[["anki.storage._Collection", List[int]], None]
    ) -> None:
        """(col: anki.storage._Collection, ids: List[int])"""
        self._hooks.append(cb)

    def remove(
        self, cb: Callable[["anki.storage._Collection", List[int]], None]
    ) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self, col: anki.storage._Collection, ids: List[int]) -> None:
        for hook in self._hooks:
            try:
                hook(col, ids)
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise
        # legacy support
        runHook("remNotes", col, ids)


notes_will_be_deleted = _NotesWillBeDeletedHook()


class _SchedulerNewLimitForSingleDeckFilter:
    """Allows changing the number of new card for this deck (without
        considering descendants)."""

    _hooks: List[Callable[[int, Dict[str, Any]], int]] = []

    def append(self, cb: Callable[[int, Dict[str, Any]], int]) -> None:
        """(count: int, deck: Dict[str, Any])"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[[int, Dict[str, Any]], int]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self, count: int, deck: Dict[str, Any]) -> int:
        for filter in self._hooks:
            try:
                count = filter(count, deck)
            except:
                # if the hook fails, remove it
                self._hooks.remove(filter)
                raise
        return count


scheduler_new_limit_for_single_deck = _SchedulerNewLimitForSingleDeckFilter()


class _SchedulerReviewLimitForSingleDeckFilter:
    """Allows changing the number of rev card for this deck (without
        considering descendants)."""

    _hooks: List[Callable[[int, Dict[str, Any]], int]] = []

    def append(self, cb: Callable[[int, Dict[str, Any]], int]) -> None:
        """(count: int, deck: Dict[str, Any])"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[[int, Dict[str, Any]], int]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self, count: int, deck: Dict[str, Any]) -> int:
        for filter in self._hooks:
            try:
                count = filter(count, deck)
            except:
                # if the hook fails, remove it
                self._hooks.remove(filter)
                raise
        return count


scheduler_review_limit_for_single_deck = _SchedulerReviewLimitForSingleDeckFilter()


class _Schedv2DidAnswerReviewCardHook:
    _hooks: List[Callable[["anki.cards.Card", int, bool], None]] = []

    def append(self, cb: Callable[["anki.cards.Card", int, bool], None]) -> None:
        """(card: anki.cards.Card, ease: int, early: bool)"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[["anki.cards.Card", int, bool], None]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self, card: anki.cards.Card, ease: int, early: bool) -> None:
        for hook in self._hooks:
            try:
                hook(card, ease, early)
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise


schedv2_did_answer_review_card = _Schedv2DidAnswerReviewCardHook()


class _SchemaWillChangeFilter:
    _hooks: List[Callable[[bool], bool]] = []

    def append(self, cb: Callable[[bool], bool]) -> None:
        """(proceed: bool)"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[[bool], bool]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self, proceed: bool) -> bool:
        for filter in self._hooks:
            try:
                proceed = filter(proceed)
            except:
                # if the hook fails, remove it
                self._hooks.remove(filter)
                raise
        return proceed


schema_will_change = _SchemaWillChangeFilter()


class _SyncProgressDidChangeHook:
    _hooks: List[Callable[[str], None]] = []

    def append(self, cb: Callable[[str], None]) -> None:
        """(msg: str)"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[[str], None]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self, msg: str) -> None:
        for hook in self._hooks:
            try:
                hook(msg)
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise
        # legacy support
        runHook("syncMsg", msg)


sync_progress_did_change = _SyncProgressDidChangeHook()


class _SyncStageDidChangeHook:
    _hooks: List[Callable[[str], None]] = []

    def append(self, cb: Callable[[str], None]) -> None:
        """(stage: str)"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[[str], None]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self, stage: str) -> None:
        for hook in self._hooks:
            try:
                hook(stage)
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise
        # legacy support
        runHook("sync", stage)


sync_stage_did_change = _SyncStageDidChangeHook()


class _TagListDidUpdateHook:
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


tag_list_did_update = _TagListDidUpdateHook()
# @@AUTOGEN@@

# Legacy hook handling
##############################################################################

_hooks: Dict[str, List[Callable[..., Any]]] = {}


def runHook(hook: str, *args) -> None:
    "Run all functions on hook."
    hookFuncs = _hooks.get(hook, None)
    if hookFuncs:
        for func in hookFuncs:
            try:
                func(*args)
            except:
                hookFuncs.remove(func)
                raise


def runFilter(hook: str, arg: Any, *args) -> Any:
    hookFuncs = _hooks.get(hook, None)
    if hookFuncs:
        for func in hookFuncs:
            try:
                arg = func(arg, *args)
            except:
                hookFuncs.remove(func)
                raise
    return arg


def addHook(hook: str, func: Callable) -> None:
    "Add a function to hook. Ignore if already on hook."
    if not _hooks.get(hook, None):
        _hooks[hook] = []
    if func not in _hooks[hook]:
        _hooks[hook].append(func)


def remHook(hook, func) -> None:
    "Remove a function if is on hook."
    hook = _hooks.get(hook, [])
    if func in hook:
        hook.remove(func)


# Monkey patching
##############################################################################
# Please only use this for prototyping or for when hooks are not practical,
# as add-ons that use monkey patching are more likely to break when Anki is
# updated.
#
# If you call wrap() with pos='around', the original function will not be called
# automatically but can be called with _old().
def wrap(old, new, pos="after") -> Callable:
    "Override an existing function."

    def repl(*args, **kwargs):
        if pos == "after":
            old(*args, **kwargs)
            return new(*args, **kwargs)
        elif pos == "before":
            new(*args, **kwargs)
            return old(*args, **kwargs)
        else:
            return new(_old=old, *args, **kwargs)

    def decorator_wrapper(f, *args, **kwargs):
        return repl(*args, **kwargs)

    return decorator.decorator(decorator_wrapper)(old)
