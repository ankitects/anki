#!/usr/bin/env python3
# -*- coding: UTF-8 -*-

import os
import json
import polib

# Read strings from all .po and .pot files and store them in a JSON file
# for quick access.

# returns a string, an array of plurals, or None if there's no translation
def get_msgstr(entry):
    # non-empty single string?
    if entry.msgstr:
        return entry.msgstr
    # plural string and non-empty?
    elif entry.msgstr_plural and entry.msgstr_plural[0]:
        # convert the dict into a list in the correct order
        plurals = list(entry.msgstr_plural.items())
        plurals.sort()
        # update variables and discard keys
        adjusted = []
        for _k, msg in plurals:
            assert msg
            adjusted.append(msg)
        if len(adjusted) > 1 and adjusted[0]:
            return adjusted
        else:
            if adjusted[0]:
                return adjusted[0]
    return None

langs = {}

# .pot first
base = "repo/desktop"
pot = os.path.join(base, "anki.pot")
pot_cat = polib.pofile(pot)

for entry in pot_cat:
    if entry.msgid_plural:
        msgstr = [entry.msgid, entry.msgid_plural]
    else:
        msgstr = entry.msgid
    langs.setdefault("en", {})[entry.msgid] = msgstr

# then .po files
folders = (d for d in os.listdir(base) if d != "anki.pot")
for lang in folders:
    po_path = os.path.join(base, lang, "anki.po")
    cat = polib.pofile(po_path)
    for entry in cat:
        msgstr = get_msgstr(entry)
        if not msgstr:
            continue
        langs.setdefault(lang, {})[entry.msgid] = msgstr

with open("strings.json", "w") as file:
    file.write(json.dumps(langs))
print("wrote to strings.json")
