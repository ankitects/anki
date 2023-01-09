#!/usr/bin/env python3
# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import re
import sys

sys.path.append("out/pylib")
sys.path.append("pylib/anki/_vendor")

import google.protobuf.descriptor
import stringcase

import anki.backend_pb2
import anki.card_rendering_pb2
import anki.cards_pb2
import anki.collection_pb2
import anki.config_pb2
import anki.deckconfig_pb2
import anki.decks_pb2
import anki.i18n_pb2
import anki.import_export_pb2
import anki.links_pb2
import anki.media_pb2
import anki.notes_pb2
import anki.notetypes_pb2
import anki.scheduler_pb2
import anki.search_pb2
import anki.stats_pb2
import anki.sync_pb2
import anki.tags_pb2

TYPE_DOUBLE = 1
TYPE_FLOAT = 2
TYPE_INT64 = 3
TYPE_UINT64 = 4
TYPE_INT32 = 5
TYPE_FIXED64 = 6
TYPE_FIXED32 = 7
TYPE_BOOL = 8
TYPE_STRING = 9
TYPE_GROUP = 10
TYPE_MESSAGE = 11
TYPE_BYTES = 12
TYPE_UINT32 = 13
TYPE_ENUM = 14
TYPE_SFIXED32 = 15
TYPE_SFIXED64 = 16
TYPE_SINT32 = 17
TYPE_SINT64 = 18

LABEL_OPTIONAL = 1
LABEL_REQUIRED = 2
LABEL_REPEATED = 3

RAW_ONLY = {"TranslateString"}


def python_type(field):
    type = python_type_inner(field)
    if field.label == LABEL_REPEATED:
        type = f"Sequence[{type}]"
    return type


def python_type_inner(field):
    type = field.type
    if type == TYPE_BOOL:
        return "bool"
    elif type in (1, 2):
        return "float"
    elif type in (3, 4, 5, 6, 7, 13, 15, 16, 17, 18):
        return "int"
    elif type == TYPE_STRING:
        return "str"
    elif type == TYPE_BYTES:
        return "bytes"
    elif type == TYPE_MESSAGE:
        return fullname(field.message_type.full_name)
    elif type == TYPE_ENUM:
        return fullname(field.enum_type.full_name) + ".V"
    else:
        raise Exception(f"unknown type: {type}")


def fullname(fullname: str) -> str:
    # eg anki.generic.Empty -> anki.generic_pb2.Empty
    components = fullname.split(".")
    components[1] += "_pb2"
    return ".".join(components)


# get_deck_i_d -> get_deck_id etc
def fix_snakecase(name):
    for fix in "a_v", "i_d":
        name = re.sub(
            rf"(\w)({fix})(\w)",
            lambda m: m.group(1) + m.group(2).replace("_", "") + m.group(3),
            name,
        )
    return name


def get_input_args(input_type):
    fields = sorted(input_type.fields, key=lambda x: x.number)
    self_star = ["self"]
    if len(fields) >= 2:
        self_star.append("*")
    return ", ".join(self_star + [f"{f.name}: {python_type(f)}" for f in fields])


def get_input_assign(input_type):
    fields = sorted(input_type.fields, key=lambda x: x.number)
    return ", ".join(f"{f.name}={f.name}" for f in fields)


def render_method(service_idx, method_idx, method):
    name = fix_snakecase(stringcase.snakecase(method.name))
    input_name = method.input_type.name

    if (
        input_name.endswith("Request") or len(method.input_type.fields) < 2
    ) and not method.input_type.oneofs:
        input_params = get_input_args(method.input_type)
        input_assign_full = f"message = {fullname(method.input_type.full_name)}({get_input_assign(method.input_type)})"
    else:
        input_params = f"self, message: {fullname(method.input_type.full_name)}"
        input_assign_full = ""

    if (
        len(method.output_type.fields) == 1
        and method.output_type.fields[0].type != TYPE_ENUM
    ):
        # unwrap single return arg
        f = method.output_type.fields[0]
        return_type = python_type(f)
        single_attribute = f".{f.name}"
    else:
        return_type = fullname(method.output_type.full_name)
        single_attribute = ""

    buf = f"""\
    def {name}_raw(self, message: bytes) -> bytes:
        return self._run_command({service_idx}, {method_idx}, message)

"""

    if not method.name in RAW_ONLY:
        buf += f"""\
    def {name}({input_params}) -> {return_type}:
        {input_assign_full}
        raw_bytes = self._run_command({service_idx}, {method_idx}, message.SerializeToString())
        output = {fullname(method.output_type.full_name)}()
        output.ParseFromString(raw_bytes)
        return output{single_attribute}

"""

    return buf


out: list[str] = []


def render_service(
    service: google.protobuf.descriptor.ServiceDescriptor, service_index: int
) -> None:
    for method_index, method in enumerate(service.methods):
        out.append(render_method(service_index, method_index, method))


service_modules = dict(
    I18N=anki.i18n_pb2,
    COLLECTION=anki.collection_pb2,
    CARDS=anki.cards_pb2,
    NOTES=anki.notes_pb2,
    DECKS=anki.decks_pb2,
    DECK_CONFIG=anki.deckconfig_pb2,
    NOTETYPES=anki.notetypes_pb2,
    SCHEDULER=anki.scheduler_pb2,
    SYNC=anki.sync_pb2,
    CONFIG=anki.config_pb2,
    SEARCH=anki.search_pb2,
    STATS=anki.stats_pb2,
    CARD_RENDERING=anki.card_rendering_pb2,
    TAGS=anki.tags_pb2,
    MEDIA=anki.media_pb2,
    LINKS=anki.links_pb2,
    IMPORT_EXPORT=anki.import_export_pb2,
)

for service in anki.backend_pb2.ServiceIndex.DESCRIPTOR.values:
    # SERVICE_INDEX_TEST -> _TESTSERVICE
    base = service.name.replace("SERVICE_INDEX_", "")
    service_pkg = service_modules.get(base)
    service_var = "_" + base.replace("_", "") + "SERVICE"
    if service_var == "_ANKIDROIDSERVICE":
        continue
    service_obj = getattr(service_pkg, service_var)
    service_index = service.number
    render_service(service_obj, service_index)

with open(sys.argv[1], "w", encoding="utf8") as f:
    f.write(
        '''# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
# pylint: skip-file

from __future__ import annotations

"""
THIS FILE IS AUTOMATICALLY GENERATED - DO NOT EDIT.

Please do not access methods on the backend directly - they may be changed
or removed at any time. Instead, please use the methods on the collection
instead. Eg, don't use col.backend.all_deck_config(), instead use
col.decks.all_config()
"""

from typing import *

import anki
import anki.backend_pb2
import anki.i18n_pb2
import anki.cards_pb2
import anki.collection_pb2
import anki.decks_pb2
import anki.deckconfig_pb2
import anki.links_pb2
import anki.notes_pb2
import anki.notetypes_pb2
import anki.scheduler_pb2
import anki.sync_pb2
import anki.config_pb2
import anki.search_pb2
import anki.stats_pb2
import anki.card_rendering_pb2
import anki.tags_pb2
import anki.media_pb2
import anki.import_export_pb2

class RustBackendGenerated:
    def _run_command(self, service: int, method: int, input: Any) -> bytes:
        raise Exception("not implemented")

'''
        + "\n".join(out)
    )
