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

create_exporters_list_hook: List[Callable[[List[Tuple[str, Any]]], None]] = []
deck_created_hook: List[Callable[[Dict[str, Any]], None]] = []
exported_media_files_hook: List[Callable[[int], None]] = []
field_replacement_filter: List[Callable[[str, str, str, Dict[str, str]], str]] = []
http_data_received_hook: List[Callable[[int], None]] = []
http_data_sent_hook: List[Callable[[int], None]] = []
leech_hook: List[Callable[[Card], None]] = []
mod_schema_filter: List[Callable[[bool], bool]] = []
modify_fields_for_rendering_hook: List[
    Callable[[Dict[str, str], Dict[str, Any], QAData], None]
] = []
note_type_created_hook: List[Callable[[Dict[str, Any]], None]] = []
odue_invalid_hook: List[Callable[[], None]] = []
prepare_searches_hook: List[Callable[[Dict[str, Callable]], None]] = []
remove_notes_hook: List[Callable[[anki.storage._Collection, List[int]], None]] = []
rendered_card_template_filter: List[
    Callable[
        [str, str, Dict[str, str], Dict[str, Any], QAData, anki.storage._Collection],
        str,
    ]
] = []
sync_progress_message_hook: List[Callable[[str], None]] = []
sync_stage_hook: List[Callable[[str], None]] = []
tag_created_hook: List[Callable[[str], None]] = []


def run_create_exporters_list_hook(exporters: List[Tuple[str, Any]]) -> None:
    for hook in create_exporters_list_hook:
        try:
            hook(exporters)
        except:
            # if the hook fails, remove it
            create_exporters_list_hook.remove(hook)
            raise
    # legacy support
    runHook("exportersList", exporters)


def run_deck_created_hook(deck: Dict[str, Any]) -> None:
    for hook in deck_created_hook:
        try:
            hook(deck)
        except:
            # if the hook fails, remove it
            deck_created_hook.remove(hook)
            raise
    # legacy support
    runHook("newDeck")


def run_exported_media_files_hook(count: int) -> None:
    for hook in exported_media_files_hook:
        try:
            hook(count)
        except:
            # if the hook fails, remove it
            exported_media_files_hook.remove(hook)
            raise


def run_field_replacement_filter(
    field_text: str, field_name: str, filter_name: str, fields: Dict[str, str]
) -> str:
    for filter in field_replacement_filter:
        try:
            field_text = filter(field_text, field_name, filter_name, fields)
        except:
            # if the hook fails, remove it
            field_replacement_filter.remove(filter)
            raise
    return field_text


def run_http_data_received_hook(bytes: int) -> None:
    for hook in http_data_received_hook:
        try:
            hook(bytes)
        except:
            # if the hook fails, remove it
            http_data_received_hook.remove(hook)
            raise


def run_http_data_sent_hook(bytes: int) -> None:
    for hook in http_data_sent_hook:
        try:
            hook(bytes)
        except:
            # if the hook fails, remove it
            http_data_sent_hook.remove(hook)
            raise


def run_leech_hook(card: Card) -> None:
    for hook in leech_hook:
        try:
            hook(card)
        except:
            # if the hook fails, remove it
            leech_hook.remove(hook)
            raise
    # legacy support
    runHook("leech", card)


def run_mod_schema_filter(proceed: bool) -> bool:
    for filter in mod_schema_filter:
        try:
            proceed = filter(proceed)
        except:
            # if the hook fails, remove it
            mod_schema_filter.remove(filter)
            raise
    return proceed


def run_modify_fields_for_rendering_hook(
    fields: Dict[str, str], notetype: Dict[str, Any], data: QAData
) -> None:
    for hook in modify_fields_for_rendering_hook:
        try:
            hook(fields, notetype, data)
        except:
            # if the hook fails, remove it
            modify_fields_for_rendering_hook.remove(hook)
            raise


def run_note_type_created_hook(notetype: Dict[str, Any]) -> None:
    for hook in note_type_created_hook:
        try:
            hook(notetype)
        except:
            # if the hook fails, remove it
            note_type_created_hook.remove(hook)
            raise
    # legacy support
    runHook("newModel")


def run_odue_invalid_hook() -> None:
    for hook in odue_invalid_hook:
        try:
            hook()
        except:
            # if the hook fails, remove it
            odue_invalid_hook.remove(hook)
            raise


def run_prepare_searches_hook(searches: Dict[str, Callable]) -> None:
    for hook in prepare_searches_hook:
        try:
            hook(searches)
        except:
            # if the hook fails, remove it
            prepare_searches_hook.remove(hook)
            raise
    # legacy support
    runHook("search", searches)


def run_remove_notes_hook(col: anki.storage._Collection, ids: List[int]) -> None:
    for hook in remove_notes_hook:
        try:
            hook(col, ids)
        except:
            # if the hook fails, remove it
            remove_notes_hook.remove(hook)
            raise
    # legacy support
    runHook("remNotes", col, ids)


def run_rendered_card_template_filter(
    text: str,
    side: str,
    fields: Dict[str, str],
    notetype: Dict[str, Any],
    data: QAData,
    col: anki.storage._Collection,
) -> str:
    for filter in rendered_card_template_filter:
        try:
            text = filter(text, side, fields, notetype, data, col)
        except:
            # if the hook fails, remove it
            rendered_card_template_filter.remove(filter)
            raise
    # legacy support
    runFilter("mungeQA", text, side, fields, notetype, data, col)
    return text


def run_sync_progress_message_hook(msg: str) -> None:
    for hook in sync_progress_message_hook:
        try:
            hook(msg)
        except:
            # if the hook fails, remove it
            sync_progress_message_hook.remove(hook)
            raise
    # legacy support
    runHook("syncMsg", msg)


def run_sync_stage_hook(stage: str) -> None:
    for hook in sync_stage_hook:
        try:
            hook(stage)
        except:
            # if the hook fails, remove it
            sync_stage_hook.remove(hook)
            raise
    # legacy support
    runHook("sync", stage)


def run_tag_created_hook(tag: str) -> None:
    for hook in tag_created_hook:
        try:
            hook(tag)
        except:
            # if the hook fails, remove it
            tag_created_hook.remove(hook)
            raise
    # legacy support
    runHook("newTag")


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
