# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import os
import sys

qrc_file = os.path.abspath(sys.argv[1])
icons = sys.argv[2:]

file_skeleton = """
<RCC>
    <qresource prefix="/">
FILES
    </qresource>
</RCC>
""".strip()

indent = " " * 8
lines = []
for icon in icons:
    base = os.path.basename(icon)
    path = os.path.relpath(icon, start=os.path.dirname(qrc_file))
    line = f'{indent}<file alias="icons/{base}">{path}</file>'
    lines.append(line)

with open(qrc_file, "w") as file:
    file.write(file_skeleton.replace("FILES", "\n".join(lines)))
