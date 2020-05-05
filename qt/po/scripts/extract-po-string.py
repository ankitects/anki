#!/usr/bin/env python3
# -*- coding: UTF-8 -*-
import os
import json
import re
import sys
import polib
import shutil
from fluent.syntax import parse, serialize
from fluent.syntax.ast import Message, TextElement, Identifier, Pattern, Junk

# extract a translated string from strings.json and insert it into ftl
# eg:
# $ python extract-po-string.py strings.json /path/to/templates/media-check.ftl delete-unused "Delete Unused Media" ""
# $ python extract-po-string.py strings.json /path/to/templates/media-check.ftl delete-unused "%(a)s %(b)s" "%(a)s=$val1,%(b)s=$val2"

json_filename, ftl_filename, key, msgid_substring, repls = sys.argv[1:]

# split up replacements
replacements = []
for repl in repls.split(","):
    if not repl:
        continue
    replacements.append(repl.split("="))

# add file as prefix to key
prefix = os.path.splitext(os.path.basename(ftl_filename))[0]
key = f"{prefix}-{key}"

strings = json.load(open(json_filename, "r"))

msgids = []
if msgid_substring in strings["en"]:
    # is the ID an exact match?
    msgids.append(msgid_substring)
else:
    for id in strings["en"].keys():
        if msgid_substring in id:
            msgids.append(id)

msgid = None
if len(msgids) == 0:
    print("no IDs matched")
    sys.exit(1)
elif len(msgids) == 1:
    msgid = msgids[0]
else:
    for c, id in enumerate(msgids):
        print(f"* {c}: {id}")
    msgid = msgids[int(input("number to use? "))]


def transform_entry(entry):
    if isinstance(entry, str):
        return transform_string(entry)
    else:
        return [transform_string(e) for e in entry]


def transform_string(msg):
    for (old, new) in replacements:
        msg = msg.replace(old, f"{new}")
    # strip leading/trailing whitespace
    return msg.strip()


to_insert = []
for lang in strings.keys():
    entry = strings[lang].get(msgid)
    if not entry:
        continue
    entry = transform_entry(entry)
    if entry:
        print(f"{lang} had translation {entry}")
        to_insert.append((lang, entry))

plurals = json.load(open("plurals.json"))


def plural_text(key, lang, translation):
    lang = re.sub("(_|-).*", "", lang)

    # extract the variable - if there's more than one, use the first one
    var = re.findall(r"{(\$.*?)}", translation[0])
    if not len(var) == 1:
        print("multiple variables found, using first replacement")
        var = replacements[0][1].replace("{", "").replace("}", "")
    else:
        var = var[0]

    buf = f"{key} = {{ {var} ->\n"

    # for each of the plural forms except the last
    for idx, msg in enumerate(translation[:-1]):
        plural_form = plurals[lang][idx]
        buf += f"    [{plural_form}] {msg}\n"

    # add the catchall
    msg = translation[-1]
    buf += f"   *[other] {msg}\n"
    buf += "  }\n"
    return buf


# add a non-pluralized message. works via fluent.syntax, so can automatically
# indent, etc
def add_simple_message(fname, key, message):
    orig = ""
    if os.path.exists(fname):
        orig = open(fname).read()

    obj = parse(orig)
    for ent in obj.body:
        if isinstance(ent, Junk):
            raise Exception(f"file had junk! {fname} {ent}")
    obj.body.append(Message(Identifier(key), Pattern([TextElement(message)])))

    modified = serialize(obj, with_junk=True)
    # escape leading dots
    modified = re.sub(r"(?ms)^( +)\.", '\\1{"."}', modified)

    # ensure the resulting serialized file is valid by parsing again
    obj = parse(modified)
    for ent in obj.body:
        if isinstance(ent, Junk):
            raise Exception(f"introduced junk! {fname} {ent}")

    # it's ok, write it out
    open(fname, "w").write(modified)


def add_message(fname, key, translation):
    # simple, non-plural form?
    if isinstance(translation, str):
        add_simple_message(fname, key, translation)
    else:
        text = plural_text(key, lang, translation)
        open(fname, "a").write(text)

print()
input("proceed? ctrl+c to abort")

i18ndir = os.path.join(os.path.dirname(ftl_filename), "..")

# for each language's translation
for lang, translation in to_insert:
    if lang == "en":
        # template
        ftl_path = ftl_filename
    else:
        # translation
        ftl_path = ftl_filename.replace("templates", lang)
        ftl_dir = os.path.dirname(ftl_path)

        if not os.path.exists(ftl_dir):
            os.mkdir(ftl_dir)

    add_message(ftl_path, key, translation)

# copy file from repo into src
srcdir = os.path.join(i18ndir, "..", "..")
src_filename = os.path.join(srcdir, os.path.basename(ftl_filename))
shutil.copy(ftl_filename, src_filename)

print("done")
