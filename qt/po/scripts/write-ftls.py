#!/usr/bin/env python

import json
import os

from fluent.syntax import parse, serialize
from fluent.syntax.ast import Message, TextElement, Identifier, Pattern, Junk

core = "../../../anki-i18n/core/core"
qt = "../../../anki-i18n/qtftl/desktop"

qt_modules = {"about", "qt-accel", "addons", "qt-misc"}

modules = json.load(open("strings_by_module.json"))
translations = json.load(open("strings.json"))

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


def transform_text(text: str) -> str:
    # fixme: automatically remap to %s in a compat wrapper? manually fix?
    text = (
        text.replace("%d", "{ $val }")
        .replace("%s", "{ $val }")
        .replace("{}", "{ $val }")
    )
    if "Clock drift" in text:
        text = text.replace("\n", "<br>")
    else:
        text = text.replace("\n", " ")
    return text


def check_parses(path: str):
    # make sure the file parses
    with open(path) as file:
        orig = file.read()
    obj = parse(orig)
    for ent in obj.body:
        if isinstance(ent, Junk):
            raise Exception(f"file had junk! {path} {ent}")


for module, items in modules.items():
    if module in qt_modules:
        folder = qt
    else:
        folder = core

    if not module.startswith("statistics"):
        continue

    # templates
    path = os.path.join(folder, "templates", module + ".ftl")
    print(path)
    with open(path, "a", encoding="utf8") as file:
        for (key, text) in items:
            text2 = transform_text(text)
            file.write(f"{key} = {text2}\n")

    check_parses(path)

    # translations
    for (lang, map) in translations.items():
        if lang == "en":
            continue

        out = []
        for (key, text) in items:
            if text in map:
                out.append((key, transform_text(map[text])))

        if out:
            path = os.path.join(folder, lang, module + ".ftl")
            dir = os.path.dirname(path)
            if not os.path.exists(dir):
                os.mkdir(dir)

            print(path)
            with open(path, "a", encoding="utf8") as file:
                for (key, text) in out:
                    file.write(f"{key} = {text}\n")

            check_parses(path)
