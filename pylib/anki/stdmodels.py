# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from typing import Any, Callable, no_type_check

import anki.collection
import anki.models
from anki import notetypes_pb2
from anki._legacy import DeprecatedNamesMixinForModule
from anki.utils import from_json_bytes

# pylint: disable=no-member
StockNotetypeKind = notetypes_pb2.StockNotetype.Kind

# add-on authors can add ("note type name", function)
# to this list to have it shown in the add/clone note type screen
models: list[tuple] = []


def _get_stock_notetype(
    col: anki.collection.Collection, kind: StockNotetypeKind.V
) -> anki.models.NotetypeDict:
    return from_json_bytes(col._backend.get_stock_notetype_legacy(kind))


def get_stock_notetypes(
    col: anki.collection.Collection,
) -> list[tuple[str, Callable[[anki.collection.Collection], anki.models.NotetypeDict]]]:
    out: list[
        tuple[str, Callable[[anki.collection.Collection], anki.models.NotetypeDict]]
    ] = []
    # add standard
    for kind in [
        StockNotetypeKind.BASIC,
        StockNotetypeKind.BASIC_TYPING,
        StockNotetypeKind.BASIC_AND_REVERSED,
        StockNotetypeKind.BASIC_OPTIONAL_REVERSED,
        StockNotetypeKind.CLOZE,
    ]:
        note_type = from_json_bytes(col._backend.get_stock_notetype_legacy(kind))

        def instance_getter(
            model: Any,
        ) -> Callable[[anki.collection.Collection], anki.models.NotetypeDict]:
            return lambda col: model

        out.append((note_type["name"], instance_getter(note_type)))
    # add extras from add-ons
    for (name_or_func, func) in models:
        if not isinstance(name_or_func, str):
            name = name_or_func()
        else:
            name = name_or_func
        out.append((name, func))
    return out


#
# Legacy functions that added the notetype before returning it
#


def _legacy_add_basic_model(
    col: anki.collection.Collection,
) -> anki.models.NotetypeDict:
    note_type = _get_stock_notetype(col, StockNotetypeKind.BASIC)
    col.models.add(note_type)
    return note_type


def _legacy_add_basic_typing_model(
    col: anki.collection.Collection,
) -> anki.models.NotetypeDict:
    note_type = _get_stock_notetype(col, StockNotetypeKind.BASIC_TYPING)
    col.models.add(note_type)
    return note_type


def _legacy_add_forward_reverse(
    col: anki.collection.Collection,
) -> anki.models.NotetypeDict:
    note_type = _get_stock_notetype(col, StockNotetypeKind.BASIC_AND_REVERSED)
    col.models.add(note_type)
    return note_type


def _legacy_add_forward_optional_reverse(
    col: anki.collection.Collection,
) -> anki.models.NotetypeDict:
    note_type = _get_stock_notetype(col, StockNotetypeKind.BASIC_OPTIONAL_REVERSED)
    col.models.add(note_type)
    return note_type


def _legacy_add_cloze_model(
    col: anki.collection.Collection,
) -> anki.models.NotetypeDict:
    note_type = _get_stock_notetype(col, StockNotetypeKind.CLOZE)
    col.models.add(note_type)
    return note_type


_deprecated_names = DeprecatedNamesMixinForModule(globals())
_deprecated_names.register_deprecated_attributes(
    addBasicModel=(_legacy_add_basic_model, get_stock_notetypes),
    addBasicTypingModel=(_legacy_add_basic_typing_model, get_stock_notetypes),
    addForwardReverse=(_legacy_add_forward_reverse, get_stock_notetypes),
    addForwardOptionalReverse=(
        _legacy_add_forward_optional_reverse,
        get_stock_notetypes,
    ),
    addClozeModel=(_legacy_add_cloze_model, get_stock_notetypes),
)


@no_type_check
def __getattr__(name: str) -> Any:
    return _deprecated_names.__getattr__(name)
