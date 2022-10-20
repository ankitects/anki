#!/usr/bin/env python3
# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import json
import re
import sys
import pprint

# bazel genrule "srcs"
root_vars_css = sys.argv[1]

# bazel genrule "outs"
colors_py = sys.argv[2]
props_py = sys.argv[3]

class Var(str):
    def __new__(self, value: str, comment: str):
        self.comment = comment
        return str.__new__(self, value)

    def comment(self):
        return self.comment

colors = {}
props = {}
reached_props = False
comment = ""

for line in re.split(r"[;\{\}]|\*\/", open(root_vars_css).read()):
    line = line.strip()

    if not line:
        continue
    if line.startswith("/*!"):
        if "props" in line:
            reached_props = True
        else:
            comment = re.match(r"\/\*!\s*(.*)$", line)[1]
        continue

    m = re.match(r"--(.+):(.+)$", line)

    if not m:
        if (
            line != "}"
            and not ":root" in line
            and "Copyright" not in line
            and "License" not in line
            and "color-scheme" not in line
        ):
            print("failed to match", line)
        continue

    var = m.group(1).replace("-", "_").upper()
    val = m.group(2)

    if reached_props:
        if not var in props:
            props.setdefault(var, {})["comment"] = comment
            props[var]["light"] = val
        else:
            props[var]["dark"] = val
    else:
        if not var in colors:
            colors.setdefault(var, {})["comment"] = comment
            colors[var]["light"] = val
        else:
            colors[var]["dark"] = val

    comment = ""


copyright_notice = """\
# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html\n
"""

with open(colors_py, "w") as buf:
    buf.write(copyright_notice)
    buf.write("# This file was automatically generated from _root-vars.scss\n")

    for color, val in colors.items():
        if not "dark" in val:
            val["dark"] = val.light

        buf.write(f'{color} = {pprint.pformat(val, width=50, sort_dicts=False)}\n')


with open(props_py, "w") as buf:
    buf.write(copyright_notice)
    buf.write("# This file was automatically generated from _root-vars.scss\n")

    for prop, val in props.items():
        if not "dark" in val:
            val["dark"] = val.light

        buf.write(f'{prop} = {pprint.pformat(val, width=50, sort_dicts=False)}\n')
