import re
import sys
import io
from PyQt6.uic import compileUi

def compile(ui_file: str) -> str:
    buf = io.StringIO()
    compileUi(open(ui_file), buf)
    return buf.getvalue()

def with_fixes_for_qt6(code: str) -> str:
    code = code.replace(
        "from PyQt6 import QtCore, QtGui, QtWidgets",
        "from PyQt6 import QtCore, QtGui, QtWidgets\nfrom aqt.utils import tr\n"
    )
    code = re.sub(
        r'(?:QtGui\.QApplication\.)?_?translate\(".*?", "(.*?)"', "tr.\\1(", code
    )
    outlines = []
    qt_bad_types = [
        ".connect(",
    ]
    for line in code.splitlines():
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
    return "\n".join(outlines)

def with_fixes_for_qt5(code: str) -> str:
    code = code.replace("Qt6", "Qt5")
    code = code.replace("QtGui.QAction", "QtWidgets.QAction")
    return code

if __name__ == "__main__":
    ui_file = sys.argv[1]
    py5_file = sys.argv[2]
    py6_file = sys.argv[3]

    stock = compile(ui_file)
    for_qt6 = with_fixes_for_qt6(stock)
    for_qt5 = with_fixes_for_qt5(for_qt6)

    with open(py5_file, "w") as file:
        file.write(for_qt5)

    with open(py6_file, "w") as file:
        file.write(for_qt6)
