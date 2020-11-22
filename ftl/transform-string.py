#!/usr/bin/env python3
# -*- coding: UTF-8 -*-

"""
Tool to apply transform to an ftl string and its translations.
"""

import os
import json
import glob
from fluent.syntax import parse, serialize
from fluent.syntax.ast import Junk

template_root = os.environ["BUILD_WORKSPACE_DIRECTORY"]
template_files = glob.glob(
    os.path.join(template_root, "ftl", "*", "*.ftl"), recursive=True
)
translation_root = os.path.join(template_root, "..", "anki-i18n")
translation_files = glob.glob(
    os.path.join(translation_root, "*", "*", "*", "*.ftl"), recursive=True
)

target_repls = [
    ["addons-downloaded-fnames", "%(fname)s", "{ $fname }"],
    ["addons-downloading-adbd-kb02fkb", "%(a)d", "{ $part }"],
    ["addons-downloading-adbd-kb02fkb", "%(b)d", "{ $total }"],
    ["addons-downloading-adbd-kb02fkb", "%(kb)0.2f", "{ $kilobytes }"],
    ["addons-error-downloading-ids-errors", "%(id)s", "{ $id }"],
    ["addons-error-downloading-ids-errors", "%(error)s", "{ $error }"],
    ["addons-error-installing-bases-errors", "%(base)s", "{ $base }"],
    ["addons-error-installing-bases-errors", "%(error)s", "{ $error }"],
    ["addons-important-as-addons-are-programs-downloaded", "%(name)s", "{ $name }"],
    ["addons-installed-names", "%(name)s", "{ $name }"],
    ["addons-the-following-addons-are-incompatible-with", "%(name)s", "{ $name }"],
    ["addons-the-following-addons-are-incompatible-with", "%(found)s", "{ $found }"],
    ["about-written-by-damien-elmes-with-patches", "%(cont)s", "{ $cont }"],
    ["importing-rows-had-num1d-fields-expected-num2d", "%(row)s", "{ $row }"],
    ["importing-rows-had-num1d-fields-expected-num2d", "%(num1)d", "{ $found }"],
    ["importing-rows-had-num1d-fields-expected-num2d", "%(num2)d", "{ $expected }"],
    ["card-templates-delete-the-as-card-type-and", "%(a)s", "{ $template }"],
    ["card-templates-delete-the-as-card-type-and", "%(b)s", "{ $cards }"],
    ["browsing-found-as-across-bs", "%(a)s", "{ $part }"],
    ["browsing-found-as-across-bs", "%(b)s", "{ $whole }"],
    ["browsing-nd-names", "%(n)d", "{ $num }"],
    ["browsing-nd-names", "%(name)s", "{ $name }"],
]


def transform_string_in_file(path):
    obj = parse(open(path).read(), with_spans=False)
    changed = False
    for ent in obj.body:
        if isinstance(ent, Junk):
            raise Exception(f"file had junk! {path} {ent}")
        if getattr(ent, "id", None):
            key = ent.id.name
            for (target_key, src, dst) in target_repls:
                if key == target_key:
                    for elem in ent.value.elements:
                        newval = elem.value.replace(src, dst)
                        if newval != elem.value:
                            elem.value = newval
                            changed = True

    if changed:
        open(path, "w", encoding="utf8").write(serialize(obj))
        print("updated", path)


for path in template_files + translation_files:
    transform_string_in_file(path)
