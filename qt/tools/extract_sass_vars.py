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

colors: dict[str, dict[str, str]] = {}
props: dict[str, dict[str, str]] = {}
reached_props = False
comment = ""

with open(root_vars_css) as f:
    data = f.read()
for line in re.split(r"[;\{\}]|\*\/", data):
    line = line.strip()

    if not line:
        continue
    if line.startswith("/*!"):
        if "props" in line:
            reached_props = True
        elif "rest" in line:
            break
        else:
            comment = re.match(r"\/\*!\s*(.*)$", line)[1]
        continue

    m = re.match(r"--(.+):(.+)$", line)

    if not m:
        if (
            line != "}"
            and ":root" not in line
            and "Copyright" not in line
            and "License" not in line
            and "color-scheme" not in line
            and "sourceMappingURL" not in line
        ):
            print("failed to match", line)
        continue

    # convert variable names to Qt style
    var = m.group(1).replace("-", "_").upper()
    val = m.group(2)

    if reached_props:
        # remove trailing ms from time props
        val = re.sub(r"^(\d+)ms$", r"\1", val)

        if var not in props:
            props.setdefault(var, {})["comment"] = comment
            props[var]["light"] = val
        else:
            props[var]["dark"] = val
    else:
        if var not in colors:
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
        if "dark" not in val:
            val["dark"] = val["light"]

        buf.write(re.sub(r"\"\n", '",\n', f"{color} = {json.dumps(val, indent=4)}\n"))


with open(props_py, "w") as buf:
    buf.write(copyright_notice)
    buf.write("# This file was automatically generated from _root-vars.scss\n")

    for prop, val in props.items():
        if "dark" not in val:
            val["dark"] = val["light"]

        buf.write(re.sub(r"\"\n", '",\n', f"{prop} = {json.dumps(val, indent=4)}\n"))
