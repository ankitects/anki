#!/usr/bin/env python3
# -*- coding: UTF-8 -*-
import re

import json

colors = {}

for line in open("ts/scss/_vars.scss"):
    line = line.strip()
    if not line:
        continue
    m = re.match(r"^\$(.+): (.+);$", line)
    if not m:
        print("failed to match", line)
        continue

    var = m.group(1)
    val = m.group(2)

    colors[var] = val

with open("aqt/colors.py", "w") as buf:
    buf.write("# this file is auto-generated from _vars.scss\n")
    buf.write("colors = " + json.dumps(colors))
