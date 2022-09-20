# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from qt.aqt import colors

import sys
import re
from pathlib import Path

color_name = sys.argv[1]
input_svg = sys.argv[2]
day_svg, night_svg = sys.argv[3:5]
# as we've received a group of files, we need to manually join the path
input_folder = Path(sys.argv[5]).parent
input_svg = input_folder / input_svg
color = getattr(colors, color_name)

with open(input_svg, "r") as f:
    data = f.read()

for (idx, (label, filename)) in enumerate((("light", day_svg), ("dark", night_svg))):
    if "fill" in data:
        data = re.sub(r"fill=\"#.+?\"", f'fill="{color[idx]}"', data)
    else:
        data = re.sub(r"<svg", f'<svg fill="{color[idx]}" ', data, 1)
    with open(filename, "w") as f:
        f.write(data)
