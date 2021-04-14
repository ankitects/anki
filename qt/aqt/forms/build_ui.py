import re
import sys
import io
from PyQt5.uic import compileUi

ui_file = sys.argv[1]
py_file = sys.argv[2]
buf = io.StringIO()
compileUi(open(ui_file), buf, from_imports=True)

outdata = buf.getvalue()
outdata = outdata.replace(
    "# -*- coding: utf-8 -*-", "# -*- coding: utf-8 -*-\nfrom aqt.utils import tr\n"
)
outdata = re.sub(
    r'(?:QtGui\.QApplication\.)?_?translate\(".*?", "(.*?)"', "tr.\\1(", outdata
)


outlines = []
qt_bad_types = [
    ".connect(",
    "setStandardButtons",
    "setTextInteractionFlags",
    "setAlignment",
]
for line in outdata.splitlines():
    for substr in qt_bad_types:
        if substr in line:
            line = line + "  # type: ignore"
            break
    outlines.append(line)

with open(py_file, "w") as file:
    file.write("\n".join(outlines))
