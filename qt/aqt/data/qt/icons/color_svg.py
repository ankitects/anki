# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import re
import sys
from pathlib import Path

from qt.aqt import colors

input_filename = sys.argv[1]
input_name = input_filename.replace(".svg", "")
color_names = sys.argv[2].split(":")

# two files created for each additional color
offset = len(color_names) * 2
svg_paths = sys.argv[3 : 3 + offset]

# as we've received a group of files, we need to manually join the path
input_folder = Path(sys.argv[4]).parent
input_svg = input_folder / input_filename

with open(input_svg, "r") as f:
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

        for (idx, (label, filename)) in enumerate(
            (("light", light_svg), ("dark", dark_svg))
        ):
            data = svg_data
            if "fill" in data:
                data = re.sub(r"fill=\"#.+?\"", f'fill="{color[idx]}"', data)
            else:
                data = re.sub(r"<svg", f'<svg fill="{color[idx]}"', data, 1)
            with open(filename, "w") as f:
                f.write(data)
