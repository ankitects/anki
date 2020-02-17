import os
import sys

import polib

# extract a translated string from the gettext catalogs and insert it into ftl
# eg:
# $ python extract-po-string.py media-check.ftl delete-unused "Delete Unused Media" 1
ftl_filename, key, msgid, dry_run = sys.argv[1:]

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
                print(lang, "has", entry.msgstr)
                to_insert.append((lang, entry.msgstr))
                break

for lang, translation in to_insert:
    dir = os.path.join("ftl", "core", lang)
    ftl_path = os.path.join(dir, ftl_filename)

    if dry_run == "1":
        continue

    if not os.path.exists(dir):
        os.mkdir(dir)

    open(ftl_path, "a").write(f"\n{key} = {translation}\n")

print("done")
