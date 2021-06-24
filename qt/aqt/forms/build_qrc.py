import sys
import os

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
    alias = ""
    path = os.path.relpath(icon, start=os.path.dirname(qrc_file))
    alias = "" if os.path.dirname(path) == "icons" else f' alias="icons/{os.path.basename(path)}"'
    line = f"{indent}<file{alias}>{path}</file>"
    lines.append(line)

with open(qrc_file, "w") as file:
    file.write(file_skeleton.replace("FILES", "\n".join(lines)))
