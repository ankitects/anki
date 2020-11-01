#!/usr/bin/env python3
# -*- coding: UTF-8 -*-

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
    buf.write("# this file is auto-generated from _vars.scss\n")
    buf.write("colors = " + json.dumps(colors))
