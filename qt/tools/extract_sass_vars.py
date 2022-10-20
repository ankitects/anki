#!/usr/bin/env python3
# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import json
import re
import sys

# bazel genrule "srcs"
root_vars_css = sys.argv[1]

# bazel genrule "outs"
colors_py = sys.argv[2]
props_py = sys.argv[3]

colors = {}
props = {}
reached_props = False

for line in re.split(r"[;\{\}]", open(root_vars_css).read()):
    line = line.strip()

    if not line:
        continue
    if "props" in line:
        reached_props = True

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

    var = m.group(1)
    val = m.group(2)

    if reached_props:
        props.setdefault(var, []).append(val)
    else:
        colors.setdefault(var, []).append(val)


copyright_notice = """\
# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
"""

with open(colors_py, "w") as buf:
    buf.write(copyright_notice)
    buf.write("# this file is auto-generated from _root-vars.scss\n")

    for color, val in colors.items():
        day = val[0]
        night = val[1] if len(val) > 1 else day

        color = color.replace("-", "_").upper()
        buf.write(f'{color} = ("{day}", "{night}")\n')


with open(props_py, "w") as buf:
    buf.write(copyright_notice)
    buf.write("# this file is auto-generated from _root-vars.scss\n")

    for prop, val in props.items():
        day = val[0]
        night = val[1] if len(val) > 1 else day

        prop = prop.replace("-", "_").upper()
        buf.write(f'{prop} = ("{day}", "{night}")\n')
