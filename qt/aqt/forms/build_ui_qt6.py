import re
import sys
import io
from PyQt6.uic import compileUi

ui_file = sys.argv[1]
py_file = sys.argv[2]
buf = io.StringIO()
compileUi(open(ui_file), buf)

outdata = buf.getvalue()
outdata = outdata.replace(
    "from PyQt6 import QtCore, QtGui, QtWidgets",
    "from PyQt6 import QtCore, QtGui, QtWidgets\nfrom aqt.utils import tr\n"
)
outdata = re.sub(
    r'(?:QtGui\.QApplication\.)?_?translate\(".*?", "(.*?)"', "tr.\\1(", outdata
)


outlines = []
qt_bad_types = [
    ".connect(",
]
for line in outdata.splitlines():
    for substr in qt_bad_types:
        if substr in line:
            line = line + "  # type: ignore"
            break
    if line == "from . import icons_rc":
        continue
    line = line.replace(":/icons/", "icons:")
    line = line.replace("QAction.PreferencesRole", "QAction.MenuRole.PreferencesRole")
    line = line.replace("QAction.AboutRole", "QAction.MenuRole.AboutRole")
    line = line.replace("QComboBox.AdjustToMinimumContentsLength", "QComboBox.SizeAdjustPolicy.AdjustToMinimumContentsLength")
    outlines.append(line)

with open(py_file, "w") as file:
    file.write("\n".join(outlines))
