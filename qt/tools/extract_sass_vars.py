#!/usr/bin/env python3
# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import json
import re
import sys

# bazel genrule "srcs"
colors_scss = sys.argv[1]
vars_scss = sys.argv[2]

# bazel genrule "outs"
colors_py = sys.argv[3]
props_py = sys.argv[4]

palette = {}
colors = {}
props = {}

color = ""


for line in open(colors_scss):
    line = line.strip()
    if not line:
        continue

    if m := re.match(r"^([a-z]+): \($", line):
        color = m.group(1)
        palette[color] = {}

    elif m := re.match(r"(\d): (.+),$", line):
        palette[color][m.group(1)] = m.group(2)


# TODO: recursive extraction of arbitrarily nested Sass maps?
reached_colors = False
current_key = ""

for line in open(vars_scss):
    line = line.strip()

    if not line or line == "props: (":
        continue
    if line == ":root {":
        break
    if line == "colors: (":
        reached_colors = True
        continue

    if m := re.match(r"^([^$]+): \(", line):
        current_key = m.group(1)

        if reached_colors:
            colors[current_key] = {}
        else:
            props[current_key] = {}

        continue

    if reached_colors:
        line = re.sub(
            r"get\(\$color, (.+), (\d)\)",
            lambda m: palette[m.group(1)][m.group(2)],
            line,
        )

    if m := re.match(r"^(.+): (.+),$", line):
        theme = m.group(1)
        val = m.group(2)

        if reached_colors:
            colors[current_key][theme] = val
        else:
            props[current_key][theme] = val


copyright_notice = """\
# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
"""

with open(colors_py, "w") as buf:
    buf.write(copyright_notice)
    buf.write("# this file is auto-generated from _vars.scss and _colors.scss\n")

    for color, val in colors.items():
        day = val["light"] if "light" in val else val["default"]
        night = val["dark"] if len(val) > 1 else day

        color = color.replace("-", "_").upper()
        buf.write(f'{color} = ("{day}", "{night}")\n')


with open(props_py, "w") as buf:
    buf.write(copyright_notice)
    buf.write("# this file is auto-generated from _vars.scss\n")

    for prop, val in props.items():
        day = val["light"] if "light" in val else val["default"]
        night = val["dark"] if "dark" in val else day

        prop = prop.replace("-", "_").upper()
        buf.write(f'{prop} = ("{day}", "{night}")\n')
