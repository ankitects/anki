#!/usr/bin/env python3
# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import json
import re
import sys

input_scss = sys.argv[1]
output_py = sys.argv[2]

colors = {}

for line in open(input_scss):
    line = line.strip()
    if not line:
        continue
    m = re.match(r"--(.+): (.+);$", line)
    if not m:
        if (
            line != "}"
            and not ":root" in line
            and "Copyright" not in line
            and "License" not in line
        ):
            print("failed to match", line)
        continue

    var = m.group(1)
    val = m.group(2)

    colors.setdefault(var, []).append(val)

with open(output_py, "w") as buf:
    buf.write(
        """\
# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
"""
    )
    buf.write("# this file is auto-generated from _vars.scss\n")
    for color, (day, night) in colors.items():
        color = color.replace("-", "_").upper()
        buf.write(f'{color} = ("{day}", "{night}")\n')
