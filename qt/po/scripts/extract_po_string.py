#!/usr/bin/env python3
# -*- coding: UTF-8 -*-

import os
import json
import re
import sys
import polib
import shutil
import sys
import subprocess
from PyQt5.QtWidgets import *
from PyQt5.QtCore import *
from PyQt5.QtGui import *
from extract_po_string_diag import Ui_Dialog
from fluent.syntax import parse, serialize
from fluent.syntax.ast import Message, TextElement, Identifier, Pattern, Junk

# the templates folder inside the ftl repo
repo_templates_dir = sys.argv[1]
assert os.path.abspath(repo_templates_dir).endswith("templates")
strings = json.load(open("strings.json"))
plurals = json.load(open("plurals.json"))


def transform_entry(entry, replacements):
    if isinstance(entry, str):
        return transform_string(entry, replacements)
    else:
        return [transform_string(e, replacements) for e in entry]


def transform_string(msg, replacements):
    try:
        for (old, new) in replacements:
            msg = msg.replace(old, f"{new}")
    except ValueError:
        pass
    # strip leading/trailing whitespace
    return msg.strip()


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


def key_from_search(search):
    return search.replace(" ", "-").replace("'", "")


# add a non-pluralized message. works via fluent.syntax, so can automatically
# indent, etc
def add_simple_message(fname, key, message):
    orig = ""
    if os.path.exists(fname):
        with open(fname) as file:
            orig = file.read()

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
    with open(fname, "w") as file:
        file.write(modified)


def add_message(fname, key, translation):
    # simple, non-plural form?
    if isinstance(translation, str):
        add_simple_message(fname, key, translation)
    else:
        text = plural_text(key, lang, translation)
        open(fname, "a").write(text)


def key_already_used(key: str) -> bool:
    return not subprocess.call(["grep", "-r", f"{key} =", repo_templates_dir])


class Window(QDialog, Ui_Dialog):
    def __init__(self):
        QDialog.__init__(self)
        self.setupUi(self)

        self.matched_strings = []

        self.files = sorted(os.listdir(repo_templates_dir))
        self.filenames.addItems(self.files)

        self.search.textChanged.connect(self.on_search)
        self.replacements.textChanged.connect(self.update_preview)
        self.key.textEdited.connect(self.update_preview)
        self.filenames.currentIndexChanged.connect(self.update_preview)
        self.searchMatches.currentItemChanged.connect(self.update_preview)
        self.replacementsTemplateButton.clicked.connect(self.on_template)
        self.addButton.clicked.connect(self.on_add)

    def on_template(self):
        self.replacements.setText("%d={ $value }")
        # qt macos bug
        self.replacements.repaint()

    def on_search(self):
        msgid_substring = self.search.text()
        self.key.setText(key_from_search(msgid_substring))

        msgids = []
        exact_idx = None
        for n, id in enumerate(strings["en"].keys()):
            if msgid_substring.lower() in id.lower():
                msgids.append(id)

            # is the ID an exact match?
            if msgid_substring in strings["en"]:
                exact_idx = n

        self.matched_strings = msgids
        self.searchMatches.clear()
        self.searchMatches.addItems(self.matched_strings)
        if exact_idx is not None:
            self.searchMatches.setCurrentRow(exact_idx)
        elif self.matched_strings:
            self.searchMatches.setCurrentRow(0)

        self.update_preview()

    def update_preview(self):
        self.preview.clear()
        if not self.matched_strings:
            return

        strings = self.get_adjusted_strings()
        key = self.get_key()
        self.preview.setPlainText(
            f"Key: {key}\n\n"
            + "\n".join([f"{lang}: {value}" for (lang, value) in strings])
        )

    # returns list of (lang, entry)
    def get_adjusted_strings(self):
        msgid = self.matched_strings[self.searchMatches.currentRow()]

        # split up replacements
        replacements = []
        for repl in self.replacements.text().split(","):
            if not repl:
                continue
            replacements.append(repl.split("="))

        to_insert = []
        for lang in strings.keys():
            entry = strings[lang].get(msgid)
            if not entry:
                continue
            entry = transform_entry(entry, replacements)
            if entry:
                to_insert.append((lang, entry))

        return to_insert

    def get_key(self):
        # add file as prefix to key
        prefix = os.path.splitext(self.filenames.currentText())[0]
        return f"{prefix}-{self.key.text()}"

    def on_add(self):
        to_insert = self.get_adjusted_strings()
        key = self.get_key()
        if key_already_used(key):
            QMessageBox.warning(None, "Error", "Duplicate Key")
            return

        # for each language's translation
        for lang, translation in to_insert:
            ftl_path = self.filename_for_lang(lang)
            add_message(ftl_path, key, translation)

            if lang == "en":
                # copy file from repo into src
                srcdir = os.path.join(repo_templates_dir, "..", "..", "..")
                src_filename = os.path.join(srcdir, os.path.basename(ftl_path))
                shutil.copy(ftl_path, src_filename)

        subprocess.check_call(
            f"cd {repo_templates_dir} && git add .. && git commit -m 'add {key}'",
            shell=True,
        )

        self.preview.setPlainText(f"Added {key}.")
        self.preview.repaint()

    def filename_for_lang(self, lang):
        fname = self.filenames.currentText()
        if lang == "en":
            return os.path.join(repo_templates_dir, fname)
        else:
            ftl_dir = os.path.join(repo_templates_dir, "..", lang)
            if not os.path.exists(ftl_dir):
                os.mkdir(ftl_dir)
            return os.path.join(ftl_dir, fname)


print("Remember to pull-i18n before making changes.")
if subprocess.check_output(f"git status --porcelain {repo_templates_dir}", shell=True):
    print("Repo has uncommitted changes.")
    sys.exit(1)

app = QApplication(sys.argv)
window = Window()
window.show()
sys.exit(app.exec_())
