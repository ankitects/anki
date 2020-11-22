#!/usr/bin/env python3
# -*- coding: UTF-8 -*-
import os
import json
import re
import sys
import copy
import shutil
from fluent.syntax import parse, serialize
from fluent.syntax.ast import Message, TextElement, Identifier, Pattern, Junk

# clone an existing ftl string as a new key, optionally modifying it
# eg:
# $ python duplicate-string.py /path/to/templates/media-check.ftl window-title check-media-action

ftl_filename, old_key, new_key = sys.argv[1:]

# # split up replacements
# replacements = []
# for repl in repls.split(","):
#     if not repl:
#         continue
#     replacements.append(repl.split("="))

# add file as prefix to key
prefix = os.path.splitext(os.path.basename(ftl_filename))[0]
old_key = f"{prefix}-{old_key}"
new_key = f"{prefix}-{new_key}"

# def transform_string(msg):
#     for (old, new) in replacements:
#         msg = msg.replace(old, f"{new}")
#     # strip leading/trailing whitespace
#     return msg.strip()


def dupe_key(fname, old, new):
    if not os.path.exists(fname):
        return

    with open(fname) as file:
        orig = file.read()
    obj = parse(orig)
    for ent in obj.body:
        if isinstance(ent, Junk):
            raise Exception(f"file had junk! {fname} {ent}")

    # locate the existing key
    for item in obj.body:
        if isinstance(item, Message):
            if item.id.name == old:
                item2 = copy.deepcopy(item)
                item2.id.name = new
                obj.body.append(item2)
                break
            # print(item.id.name)
            # print(item.value.elements)
            # for e in item.value.elements:
            #     print(e.value)

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


i18ndir = os.path.join(os.path.dirname(ftl_filename), "..")
langs = os.listdir(i18ndir)

for lang in langs:
    if lang == "template":
        # template
        ftl_path = ftl_filename
    else:
        # translation
        ftl_path = ftl_filename.replace("templates", lang)
        ftl_dir = os.path.dirname(ftl_path)

        if not os.path.exists(ftl_dir):
            continue

    dupe_key(ftl_path, old_key, new_key)

# copy file from repo into src
srcdir = os.path.join(i18ndir, "..", "..")
src_filename = os.path.join(srcdir, os.path.basename(ftl_filename))
shutil.copy(ftl_filename, src_filename)

print("done")
