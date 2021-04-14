#!/usr/bin/env python3
# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import copy
import json
import os
import re
import shutil
import sys

from fluent.syntax import parse, serialize
from fluent.syntax.ast import Identifier, Junk, Message, Pattern, TextElement

# clone an existing ftl string as a new key
# eg:
# $ python duplicate-string.py \
#   /source/templates/media-check.ftl window-title \
#   /dest/templates/something.ftl key-name
#
# after running, you'll need to copy the output template file into Anki's source

src_filename, old_key, dst_filename, new_key = sys.argv[1:]

# add file as prefix to key
src_prefix = os.path.splitext(os.path.basename(src_filename))[0]
dst_prefix = os.path.splitext(os.path.basename(dst_filename))[0]
old_key = f"{src_prefix}-{old_key}"
new_key = f"{dst_prefix}-{new_key}"


def get_entry(fname, key):
    if not os.path.exists(fname):
        return

    with open(fname) as file:
        orig = file.read()
    obj = parse(orig)
    for ent in obj.body:
        if isinstance(ent, Junk):
            raise Exception(f"file had junk! {fname} {ent}")
        elif isinstance(ent, Message):
            if ent.id.name == old_key:
                return copy.deepcopy(ent)


def write_entry(fname, key, entry):
    assert entry
    entry.id.name = key

    if not os.path.exists(fname):
        orig = ""
    else:
        with open(fname) as file:
            orig = file.read()
    obj = parse(orig)
    for ent in obj.body:
        if isinstance(ent, Junk):
            raise Exception(f"file had junk! {fname} {ent}")

    obj.body.append(entry)
    modified = serialize(obj, with_junk=True)
    # escape leading dots
    modified = re.sub(r"(?ms)^( +)\.", '\\1{"."}', modified)

    # ensure the resulting serialized file is valid by parsing again
    obj = parse(modified)
    for ent in obj.body:
        if isinstance(ent, Junk):
            raise Exception(f"introduced junk! {fname} {ent}")

    # it's ok, write it out
    with open(fname, "w") as file:
        file.write(modified)


# get all existing entries into lang -> entry
entries = {}

i18ndir = os.path.join(os.path.dirname(src_filename), "..")
langs = os.listdir(i18ndir)

for lang in langs:
    if lang == "templates":
        # template
        ftl_path = src_filename
    else:
        # translation
        ftl_path = src_filename.replace("templates", lang)
        ftl_dir = os.path.dirname(ftl_path)

        if not os.path.exists(ftl_dir):
            continue

    entry = get_entry(ftl_path, old_key)
    if entry:
        entries[lang] = entry
    else:
        assert lang != "templates"

# write them into target files

i18ndir = os.path.join(os.path.dirname(dst_filename), "..")
langs = os.listdir(i18ndir)

for lang in langs:
    if lang == "templates":
        # template
        ftl_path = dst_filename
    else:
        # translation
        ftl_path = dst_filename.replace("templates", lang)
        ftl_dir = os.path.dirname(ftl_path)

        if not os.path.exists(ftl_dir):
            continue

    if lang in entries:
        entry = entries[lang]
        write_entry(ftl_path, new_key, entry)

print("done")
