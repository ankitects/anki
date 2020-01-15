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
from anki.types import QAData

# New hook/filter handling
##############################################################################
# The code in this section is automatically generated - any edits you make
# will be lost. To add new hooks, see ../tools/genhooks.py
#
# To use an existing hook such as leech_hook, you would call the following
# in your code:
#
# from anki import hooks
# hooks.leech_hook.append(myfunc)
#
# @@AUTOGEN@@


class _CreateExportersListHook:
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


create_exporters_list_hook = _CreateExportersListHook()


class _DeckCreatedHook:
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


deck_created_hook = _DeckCreatedHook()


class _ExportedMediaFilesHook:
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


exported_media_files_hook = _ExportedMediaFilesHook()


class _FieldReplacementFilter:
    _hooks: List[Callable[[str, str, str, Dict[str, str]], str]] = []

    def append(self, cb: Callable[[str, str, str, Dict[str, str]], str]) -> None:
        """(field_text: str, field_name: str, filter_name: str, fields: Dict[str, str])"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[[str, str, str, Dict[str, str]], str]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(
        self, field_text: str, field_name: str, filter_name: str, fields: Dict[str, str]
    ) -> str:
        for filter in self._hooks:
            try:
                field_text = filter(field_text, field_name, filter_name, fields)
            except:
                # if the hook fails, remove it
                self._hooks.remove(filter)
                raise
        return field_text


field_replacement_filter = _FieldReplacementFilter()


class _HttpDataReceivedHook:
    _hooks: List[Callable[[int], None]] = []

    def append(self, cb: Callable[[int], None]) -> None:
        """(bytes: int)"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[[int], None]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self, bytes: int) -> None:
        for hook in self._hooks:
            try:
                hook(bytes)
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise


http_data_received_hook = _HttpDataReceivedHook()


class _HttpDataSentHook:
    _hooks: List[Callable[[int], None]] = []

    def append(self, cb: Callable[[int], None]) -> None:
        """(bytes: int)"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[[int], None]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self, bytes: int) -> None:
        for hook in self._hooks:
            try:
                hook(bytes)
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise


http_data_sent_hook = _HttpDataSentHook()


class _LeechHook:
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


leech_hook = _LeechHook()


class _ModSchemaFilter:
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


mod_schema_filter = _ModSchemaFilter()


class _ModifyFieldsForRenderingHook:
    _hooks: List[Callable[[Dict[str, str], Dict[str, Any], QAData], None]] = []

    def append(
        self, cb: Callable[[Dict[str, str], Dict[str, Any], QAData], None]
    ) -> None:
        """(fields: Dict[str, str], notetype: Dict[str, Any], data: QAData)"""
        self._hooks.append(cb)

    def remove(
        self, cb: Callable[[Dict[str, str], Dict[str, Any], QAData], None]
    ) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(
        self, fields: Dict[str, str], notetype: Dict[str, Any], data: QAData
    ) -> None:
        for hook in self._hooks:
            try:
                hook(fields, notetype, data)
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise


modify_fields_for_rendering_hook = _ModifyFieldsForRenderingHook()


class _NoteTypeCreatedHook:
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


note_type_created_hook = _NoteTypeCreatedHook()


class _OdueInvalidHook:
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


odue_invalid_hook = _OdueInvalidHook()


class _OriginalCardTemplateFilter:
    _hooks: List[Callable[[str, bool], str]] = []

    def append(self, cb: Callable[[str, bool], str]) -> None:
        """(template: str, question_side: bool)"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[[str, bool], str]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self, template: str, question_side: bool) -> str:
        for filter in self._hooks:
            try:
                template = filter(template, question_side)
            except:
                # if the hook fails, remove it
                self._hooks.remove(filter)
                raise
        return template


original_card_template_filter = _OriginalCardTemplateFilter()


class _PrepareSearchesHook:
    _hooks: List[Callable[[Dict[str, Callable]], None]] = []

    def append(self, cb: Callable[[Dict[str, Callable]], None]) -> None:
        """(searches: Dict[str, Callable])"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[[Dict[str, Callable]], None]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self, searches: Dict[str, Callable]) -> None:
        for hook in self._hooks:
            try:
                hook(searches)
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise
        # legacy support
        runHook("search", searches)


prepare_searches_hook = _PrepareSearchesHook()


class _RemoveNotesHook:
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


remove_notes_hook = _RemoveNotesHook()


class _RenderedCardTemplateFilter:
    _hooks: List[
        Callable[
            [
                str,
                str,
                Dict[str, str],
                Dict[str, Any],
                QAData,
                "anki.storage._Collection",
            ],
            str,
        ]
    ] = []

    def append(
        self,
        cb: Callable[
            [
                str,
                str,
                Dict[str, str],
                Dict[str, Any],
                QAData,
                "anki.storage._Collection",
            ],
            str,
        ],
    ) -> None:
        """(text: str, side: str, fields: Dict[str, str], notetype: Dict[str, Any], data: QAData, col: anki.storage._Collection)"""
        self._hooks.append(cb)

    def remove(
        self,
        cb: Callable[
            [
                str,
                str,
                Dict[str, str],
                Dict[str, Any],
                QAData,
                "anki.storage._Collection",
            ],
            str,
        ],
    ) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(
        self,
        text: str,
        side: str,
        fields: Dict[str, str],
        notetype: Dict[str, Any],
        data: QAData,
        col: anki.storage._Collection,
    ) -> str:
        for filter in self._hooks:
            try:
                text = filter(text, side, fields, notetype, data, col)
            except:
                # if the hook fails, remove it
                self._hooks.remove(filter)
                raise
        # legacy support
        runFilter("mungeQA", text, side, fields, notetype, data, col)
        return text


rendered_card_template_filter = _RenderedCardTemplateFilter()


class _SyncProgressMessageHook:
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


sync_progress_message_hook = _SyncProgressMessageHook()


class _SyncStageHook:
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


sync_stage_hook = _SyncStageHook()


class _TagCreatedHook:
    _hooks: List[Callable[[str], None]] = []

    def append(self, cb: Callable[[str], None]) -> None:
        """(tag: str)"""
        self._hooks.append(cb)

    def remove(self, cb: Callable[[str], None]) -> None:
        if cb in self._hooks:
            self._hooks.remove(cb)

    def __call__(self, tag: str) -> None:
        for hook in self._hooks:
            try:
                hook(tag)
            except:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise
        # legacy support
        runHook("newTag")


tag_created_hook = _TagCreatedHook()
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
