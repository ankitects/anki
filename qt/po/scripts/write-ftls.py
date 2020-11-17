#!/usr/bin/env python

import json
import os
import re

from typing import List

from fluent.syntax import parse, serialize
from fluent.syntax.ast import Message, TextElement, Identifier, Pattern, Junk

core = "../../../anki-i18n/core/core"
qt = "../../../anki-i18n/qtftl/desktop"

qt_modules = {"about", "qt-accel", "addons", "qt-misc"}

modules = json.load(open("strings_by_module.json"))
translations = json.load(open("strings.json"))
plurals = json.load(open("plurals.json"))


# # fixme:
# addons addons-downloaded-fnames Downloaded %(fname)s
# addons addons-downloading-adbd-kb02fkb Downloading %(a)d/%(b)d (%(kb)0.2fKB)...
# addons addons-error-downloading-ids-errors Error downloading <i>%(id)s</i>: %(error)s
# addons addons-error-installing-bases-errors Error installing <i>%(base)s</i>: %(error)s
# addons addons-important-as-addons-are-programs-downloaded <b>Important</b>: As add-ons are programs downloaded from the internet, they are potentially malicious.<b>You should only install add-ons you trust.</b><br><br>Are you sure you want to proceed with the installation of the following Anki add-on(s)?<br><br>%(names)s
# addons addons-installed-names Installed %(name)s
# addons addons-the-following-addons-are-incompatible-with The following add-ons are incompatible with %(name)s and have been disabled: %(found)s
# card-templates card-templates-delete-the-as-card-type-and Delete the '%(a)s' card type, and its %(b)s?
# browsing browsing-found-as-across-bs Found %(a)s across %(b)s.
# browsing browsing-nd-names %(n)d: %(name)s
# importing importing-rows-had-num1d-fields-expected-num2d '%(row)s' had %(num1)d fields, expected %(num2)d
# about about-written-by-damien-elmes-with-patches Written by Damien Elmes, with patches, translation,    testing and design from:<p>%(cont)s
# media media-recordingtime Recording...<br>Time: %0.1f
# addons-unknown-error = Unknown error: {}

# fixme: isolation chars


def plural_text(key, lang, translation):
    lang = re.sub("(_|-).*", "", lang)

    var = re.findall(r"{ (\$.*?) }", translation[0])
    try:
        var = var[0]
    except:
        print(key, lang, translation)
        raise

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


def transform_text(key: str, texts: List[str], lang: str) -> str:
    texts = [
        (
            text.replace("%d", "{ $count }")
            .replace("%s", "{ $count }")
            .replace("%(num)d", "{ $count }")
        )
        for text in texts
    ]

    text = plural_text(key, lang, texts)
    print(text)

    return text


def check_parses(path: str):
    # make sure the file parses
    with open(path) as file:
        orig = file.read()
    obj = parse(orig)
    for ent in obj.body:
        if isinstance(ent, Junk):
            raise Exception(f"file had junk! {path} {ent}")


def munge_key(key):
    if key == "browsing-note":
        return "browsing-note-count"
    if key == "card-templates-card":
        return "card-templates-card-count"
    return key


for module, items in modules.items():
    if module in qt_modules:
        folder = qt
    else:
        folder = core

    # templates
    path = os.path.join(folder, "templates", module + ".ftl")
    print(path)
    with open(path, "a", encoding="utf8") as file:
        for (key, text) in items:
            key = munge_key(key)
            text2 = transform_text(key, text, "en")
            file.write(text2)

    check_parses(path)

    # translations
    for (lang, map) in translations.items():
        if lang == "en":
            continue

        out = []
        for (key, text) in items:
            if text[0] in map:
                forms = map[text[0]]
                if isinstance(forms, str):
                    forms = [forms]
                key = munge_key(key)
                out.append(transform_text(key, forms, lang))

        if out:
            path = os.path.join(folder, lang, module + ".ftl")
            dir = os.path.dirname(path)
            if not os.path.exists(dir):
                os.mkdir(dir)

            print(path)
            with open(path, "a", encoding="utf8") as file:
                for o in out:
                    file.write(o)

            check_parses(path)
