import re
import sys
import io
import os
from PyQt6.uic import compileUi
from dataclasses import dataclass


def compile(ui_file: str) -> str:
    buf = io.StringIO()
    compileUi(open(ui_file), buf)
    return buf.getvalue()


def with_fixes_for_qt6(code: str) -> str:
    code = code.replace(
        "from PyQt6 import QtCore, QtGui, QtWidgets",
        "from PyQt6 import QtCore, QtGui, QtWidgets\nfrom aqt.utils import tr\n",
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
        line = line.replace(
            "QAction.PreferencesRole", "QAction.MenuRole.PreferencesRole"
        )
        line = line.replace("QAction.AboutRole", "QAction.MenuRole.AboutRole")
        line = line.replace(
            "QComboBox.AdjustToMinimumContentsLength",
            "QComboBox.SizeAdjustPolicy.AdjustToMinimumContentsLength",
        )
        outlines.append(line)
    return "\n".join(outlines)


def with_fixes_for_qt5(code: str) -> str:
    code = code.replace("Qt6", "Qt5")
    code = code.replace("QtGui.QAction", "QtWidgets.QAction")
    return code


@dataclass
class UiFileAndOutputs:
    ui_file: str
    qt5_file: str
    qt6_file: str


def get_files() -> list[UiFileAndOutputs]:
    ui_folder = os.path.dirname(sys.argv[1])
    py_folder = os.path.dirname(sys.argv[2])
    out = []
    for file in os.listdir(ui_folder):
        if file.endswith(".ui"):
            base = os.path.splitext(os.path.basename(file))[0]
            out.append(
                UiFileAndOutputs(
                    ui_file=os.path.join(ui_folder, file),
                    qt5_file=os.path.join(py_folder, base + "_qt5.py"),
                    qt6_file=os.path.join(py_folder, base + "_qt6.py"),
                )
            )
    return out


if __name__ == "__main__":
    for entry in get_files():
        stock = compile(entry.ui_file)
        for_qt6 = with_fixes_for_qt6(stock)
        for_qt5 = with_fixes_for_qt5(for_qt6)
        with open(entry.qt5_file, "w") as file:
            file.write(for_qt5)
        with open(entry.qt6_file, "w") as file:
            file.write(for_qt6)
