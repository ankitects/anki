# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import re
import sys
from pathlib import Path

sys.path.append("out/qt")
from _aqt import colors

input_path = Path(sys.argv[1])
input_name = input_path.stem
color_names = sys.argv[2].split(":")

# two files created for each additional color
offset = len(color_names) * 2
svg_paths = sys.argv[3 : 3 + offset]

with open(input_path, "r") as f:
    svg_data = f.read()

    for color_name in color_names:
        color = getattr(colors, color_name)
        light_svg = dark_svg = ""

        if color_name == "FG":
            prefix = input_name
        else:
            prefix = f"{input_name}-{color_name}"

        for path in svg_paths:
            if f"{prefix}-light.svg" in path:
                light_svg = path
            elif f"{prefix}-dark.svg" in path:
                dark_svg = path

        def substitute(data: str, filename: str, mode: str) -> None:
            if "fill" in data:
                data = re.sub(r"fill=\"#.+?\"", f'fill="{color[mode]}"', data)
            else:
                data = re.sub(r"<svg", f'<svg fill="{color[mode]}"', data, count=1)
            with open(filename, "w") as f:
                f.write(data)

        substitute(svg_data, light_svg, "light")
        substitute(svg_data, dark_svg, "dark")
