#!/usr/bin/env python3
# -*- coding: UTF-8 -*-

import os
import json
import polib
import pprint
import re

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


module_map = {
    "__init__": "qt-misc",
    "about": "about",
    "addcards": "adding",
    "addfield": "fields",
    "addmodel": "notetypes",
    "addonconf": "addons",
    "addons": "addons",
    "anki2": "importing",
    "browser": "browsing",
    "browserdisp": "browsing",
    "browseropts": "browsing",
    "changemap": "browsing",
    "changemodel": "browsing",
    "clayout_top": "card-templates",
    "clayout": "card-templates",
    "collection": "collection",
    "consts": "consts",
    "csvfile": "importing",
    "customstudy": "custom-study",
    "dconf": "scheduling",
    "debug": "qt-misc",
    "deckbrowser": "decks",
    "deckchooser": "qt-misc",
    "deckconf": "scheduling",
    "decks": "decks",
    "dyndconf": "decks",
    "dyndeckconf": "decks",
    "editaddon": "addons",
    "editcurrent": "editing",
    "edithtml": "editing",
    "editor": "editing",
    "errors": "qt-misc",
    "exporting": "exporting",
    "fields": "fields",
    "finddupes": "browsing",
    "findreplace": "browsing",
    "getaddons": "addons",
    "importing": "importing",
    "latex": "media",
    "main": "qt-misc",
    "mnemo": "importing",
    "modelchooser": "qt-misc",
    "modelopts": "notetypes",
    "models": "notetypes",
    "noteimp": "importing",
    "overview": "studying",
    "preferences": "preferences",
    "previewer": "qt-misc",
    "profiles": "profiles",
    "progress": "qt-misc",
    "reposition": "browsing",
    "reschedule": "browsing",
    "reviewer": "studying",
    "schedv2": "scheduling",
    "setgroup": "browsing",
    "setlang": "preferences",
    "sidebar": "browsing",
    "sound": "media",
    "studydeck": "decks",
    "taglimit": "custom-study",
    "template": "card-templates",
    "toolbar": "qt-misc",
    "update": "qt-misc",
    "utils": "qt-misc",
    "webview": "qt-misc",
    "stats": "stats",
}

text_remap = {
    "actions": [
        "Add",
        "Cancel",
        "Choose",
        "Close",
        "Copy",
        "Decks",
        "Delete",
        "Export",
        "Filter",
        "Help",
        "Import",
        "Manage...",
        "Name:",
        "New name:",
        "New",
        "Options for %s",
        "Options",
        "Preview",
        "Rebuild",
        "Rename Deck",
        "Rename",
        "Replay Audio",
        "Reposition",
        "Save",
        "Search",
        "Shortcut key: %s",
        "Suspend Card",
        "Blue Flag",
        "Green Flag",
        "Orange Flag",
        "Red Flag",
        "Custom Study",
    ],
    "decks": [
        "Deck",
        "New deck name:",
        "Decreasing intervals",
        "Increasing intervals",
        "Latest added first",
        "Most lapses",
        "Oldest seen first",
        "Order added",
        "Order due",
        "Random",
        "Relative overdueness",
    ],
    "scheduling": [
        "days",
        "Lapses",
        "Reviews",
        "At least one step is required.",
        "Steps must be numbers.",
        "Show new cards in order added",
        "Show new cards in random order",
        "Show new cards after reviews",
        "Show new cards before reviews",
        "Mix new cards and reviews",
        "Learning",
        "Review",
    ],
    "fields": ["Add Field"],
    "editing": ["Tags", "Cards", "Fields", "LaTeX"],
    "notetypes": ["Type", "Note Types"],
    "studying": ["Space"],
    "qt-misc": ["&Edit", "&Guide...", "&Help", "&Undo", "Unexpected response code: %s"],
    "adding": ["Added"],
}

blacklist = {"Anki", "%", "Dialog", "Center", "Left", "Right", "~", "&Cram..."}


def determine_module(text, files):
    if text in blacklist:
        return None

    if "&" in text:
        return "qt-accel"

    for (module, texts) in text_remap.items():
        if text in texts:
            return module

    if len(files) == 1:
        return list(files)[0]

    assert False


modules = dict()

remap_keys = {
    "browsing-": "browsing-type-here-to-search",
    "importing-": "importing-ignored",
    "qt-misc-": "qt-misc-non-unicode-text",
}


def generate_key(module: str, text: str) -> str:
    key = re.sub("<.*?>", "", text)
    key = re.sub("%[dsf.0-9]+", "", key)
    key = key.replace("+", "and")
    key = re.sub("[^a-z0-9 ]", "", key.lower())
    words = key.split(" ")
    if len(words) > 6:
        words = words[:6]
    key = "-".join(words)
    key = re.sub("--+", "-", key)
    key = re.sub("-$|^-", "", key)

    key = f"{module}-{key}"

    if key in remap_keys:
        key = remap_keys[key]

    return key


seen_keys = set()


def migrate_entry(entry):
    if entry.msgid_plural:
        # print("skip plural", entry.msgid)
        return

    text = entry.msgid
    files = set(
        [os.path.splitext(os.path.basename(e[0]))[0] for e in entry.occurrences]
    )
    # drop translations only used by old graphs
    if len(files) == 1 and "stats" in files:
        return None

    files2 = set()
    for file in files:
        if file == "stats":
            continue
        file = module_map[file]
        files2.add(file)
    module = determine_module(text, files2)
    if not module:
        return

    key = generate_key(module, text)

    if key in seen_keys:
        key += "2"
    assert key not in seen_keys
    seen_keys.add(key)

    modules.setdefault(module, [])
    modules[module].append((key, text))

    return None


langs = {}

# .pot first
base = "../../../anki-i18n/qtpo/desktop"
pot = os.path.join(base, "anki.pot")
pot_cat = polib.pofile(pot)

migration_map = []

for entry in pot_cat:
    if entry.msgid_plural:
        msgstr = [entry.msgid, entry.msgid_plural]
    else:
        msgstr = entry.msgid

    langs.setdefault("en", {})[entry.msgid] = msgstr

    if d := migrate_entry(entry):
        migration_map.append(d)


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

# old text -> (module, new key)
strings_by_module = {}
keys_by_text = {}
for (module, items) in modules.items():
    items.sort()
    strings_by_module[module] = items
    for item in items:
        (key, text) = item
        assert text not in keys_by_text
        keys_by_text[text] = (module, key)

with open("strings_by_module.json", "w") as file:
    file.write(json.dumps(strings_by_module))
with open("keys_by_text.json", "w") as file:
    file.write(json.dumps(keys_by_text))
