import os
import sys

import polib

# extract a translated string from the gettext catalogs and insert it into ftl
# eg:
# $ python extract-po-string.py media-check.ftl delete-unused "Delete Unused Media" "", 1
# $ python extract-po-string.py media-check.ftl delete-unused "%(a)s %(b)s" "%(a)s=val1,%(b)s=val2" 0
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
                        msg = msg.replace(old, f"{{${new}}}")
                print(lang, "has", msg)
                to_insert.append((lang, msg))
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
