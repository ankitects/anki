#!/usr/bin/env python3
# -*- coding: UTF-8 -*-
import os
import json
import re
import sys
import polib
from fluent.syntax import parse, serialize
from fluent.syntax.ast import Message, TextElement, Identifier, Pattern

# extract a translated string from the gettext catalogs and insert it into ftl
# eg:
# $ python extract-po-string.py /path/to/templates/media-check.ftl delete-unused "Delete Unused Media" ""
# $ python extract-po-string.py /path/to/templates/media-check.ftl delete-unused "%(a)s %(b)s" "%(a)s=$val1,%(b)s=$val2"
#
# NOTE: the English text is written into the templates folder of the repo, so must be copied
# into Anki's source tree

ftl_filename, key, msgid, repls = sys.argv[1:]

# split up replacements
replacements = []
for repl in repls.split(","):
    if not repl:
        continue
    replacements.append(repl.split("="))

# add file as prefix to key
prefix = os.path.splitext(os.path.basename(ftl_filename))[0]
key = f"{prefix}-{key}"

# returns a string, an array of plurals, or None if there's no translation
def get_msgstr(entry):
    # non-empty single string?
    if entry.msgstr:
        msg = entry.msgstr
        if replacements:
            for (old, new) in replacements:
                msg = msg.replace(old, f"{{{new}}}")
        return msg.strip()
    # plural string and non-empty?
    elif entry.msgstr_plural and entry.msgstr_plural[0]:
        # convert the dict into a list in the correct order
        plurals = list(entry.msgstr_plural.items())
        plurals.sort()
        # update variables and discard keys
        adjusted = []
        for _k, msg in plurals:
            if replacements:
                for (old, new) in replacements:
                    msg = msg.replace(old, f"{{{new}}}")
            adjusted.append(msg.strip())
        if len(adjusted) > 1 and adjusted[0]:
            return adjusted
        else:
            if adjusted[0]:
                return adjusted[0]
    return None


# start by checking the .pot file for the message
base = "repo/desktop"
pot = os.path.join(base, "anki.pot")
pot_cat = polib.pofile(pot)
catalogs = []

# is the ID an exact match?
resolved_entry = None
for entry in pot_cat:
    if entry.msgid == msgid:
        resolved_entry = entry

# try a substring match, but make sure it doesn't match
# multiple items
if resolved_entry is None:
    for entry in pot_cat:
        if msgid in entry.msgid:
            if resolved_entry is not None:
                print("aborting, matched both", resolved_entry.msgid)
                print("and", entry.msgid)
                sys.exit(1)
            resolved_entry = entry

if resolved_entry is None:
    print("no IDs matched")
    sys.exit(1)

msgid = resolved_entry.msgid
print("matched id", msgid)

print("loading translations...")
# load the translations from each language
langs = [d for d in os.listdir(base) if d != "anki.pot"]
for lang in langs:
    po_path = os.path.join(base, lang, "anki.po")
    cat = polib.pofile(po_path)
    catalogs.append((lang, cat))

to_insert = []
for (lang, cat) in catalogs:
    for entry in cat:
        if entry.msgid == msgid:
            translation = get_msgstr(entry)
            if translation:
                print(f"{lang} had translation {translation}")
                to_insert.append((lang, translation))
            break

plurals = json.load(open("plurals.json"))


def plural_text(key, lang, translation):
    lang = re.sub("(_|-).*", "", lang)

    # extract the variable - there should be only one
    var = re.findall(r"{(\$.*?)}", translation[0])
    if not len(var) == 1:
        return None
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
    obj.body.append(Message(Identifier(key), Pattern([TextElement(message)])))

    modified = serialize(obj)
    open(fname, "w").write(modified)


def add_message(fname, key, translation):
    # simple, non-plural form?
    if isinstance(translation, str):
        add_simple_message(fname, key, translation)
    else:
        raise
        return plural_text(key, lang, translation)


print()
input("proceed? ctrl+c to abort")

# add template first
if resolved_entry.msgid_plural:
    original = [resolved_entry.msgid, resolved_entry.msgid_plural]
else:
    original = resolved_entry.msgid

add_message(ftl_filename, key, original)

# the each language's translation
for lang, translation in to_insert:
    ftl_path = ftl_filename.replace("templates", lang)
    ftl_dir = os.path.dirname(ftl_path)

    if not os.path.exists(ftl_dir):
        os.mkdir(ftl_dir)

    add_message(ftl_path, key, translation)

print("done")
