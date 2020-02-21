import os

import json

import re
import sys

import polib

# extract a translated string from the gettext catalogs and insert it into ftl
# eg:
# $ python extract-po-string.py media-check.ftl delete-unused "Delete Unused Media" "", 1
# $ python extract-po-string.py media-check.ftl delete-unused "%(a)s %(b)s" "%(a)s=$val1,%(b)s=$val2" 0
ftl_filename, key, msgid, repls, dry_run = sys.argv[1:]

replacements = []
for repl in repls.split(","):
    replacements.append(repl.split("="))

print("Loading catalogs...")
base = "po/desktop"
langs = [d for d in os.listdir(base) if d != "anki.pot"]
cats = []
for lang in langs:
    po_path = os.path.join(base, lang, "anki.po")
    cat = polib.pofile(po_path)
    cats.append((lang, cat))

to_insert = []
for (lang, cat) in cats:
    for entry in cat:
        if entry.msgid == msgid:
            if entry.msgstr:
                msg = entry.msgstr
                if replacements:
                    for (old,new) in replacements:
                        msg = msg.replace(old, f"{{{new}}}")
                print(lang, "has", msg)
                to_insert.append((lang, msg))
                break
            elif entry.msgstr_plural and entry.msgstr_plural[0]:
                # convert the dict into a list in the correct order
                plurals = list(entry.msgstr_plural.items())
                plurals.sort()
                # update variables and discard keys
                adjusted = []
                for _k, msg in plurals:
                    if replacements:
                        for (old,new) in replacements:
                            msg = msg.replace(old, f"{{{new}}}")
                    adjusted.append(msg)
                print(lang, "has", adjusted)
                if len(adjusted) > 1:
                    to_insert.append((lang, adjusted))
                else:
                    # language doesn't have plurals
                    to_insert.append((lang, adjusted[0]))

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

for lang, translation in to_insert:
    dir = os.path.join("ftl", "core", lang)
    ftl_path = os.path.join(dir, ftl_filename)

    if dry_run == "1":
        continue

    if not os.path.exists(dir):
        os.mkdir(dir)

    # simple, non-plural form?
    if isinstance(translation, str):
        buf = f"{key} = {translation}\n"
    else:
        buf = plural_text(key, lang, translation)

    if buf:
        open(ftl_path, "a").write(buf)

print("done")
