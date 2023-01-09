# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import io
import re
import sys
from pathlib import Path

try:
    from PyQt6.uic import compileUi
except ImportError:
    # ARM64 Linux builds may not have access to PyQt6, and may have aliased
    # it to PyQt5. We allow fallback, but the _qt6.py files will not be valid.
    from PyQt5.uic import compileUi  # type: ignore

from dataclasses import dataclass


def compile(ui_file: str | Path) -> str:
    buf = io.StringIO()
    with open(ui_file) as f:
        compileUi(f, buf)
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
        outlines.append(line)
    return "\n".join(outlines)


def with_fixes_for_qt5(code: str) -> str:
    code = code.replace(
        "from PyQt5 import QtCore, QtGui, QtWidgets",
        "from PyQt5 import QtCore, QtGui, QtWidgets\nfrom aqt.utils import tr\n",
    )
    code = code.replace("Qt6", "Qt5")
    code = code.replace("QtGui.QAction", "QtWidgets.QAction")
    code = code.replace("import icons_rc", "")
    return code


@dataclass
class UiFileAndOutputs:
    ui_file: Path
    qt5_file: str
    qt6_file: str


def get_files() -> list[UiFileAndOutputs]:
    "The ui->py map, and output __init__.py path"
    ui_folder = Path("qt/aqt/forms")
    out_folder = Path(sys.argv[1]).parent
    out = []
    for path in ui_folder.iterdir():
        if path.suffix == ".ui":
            outpath = str(out_folder / path.name)
            out.append(
                UiFileAndOutputs(
                    ui_file=path,
                    qt5_file=outpath.replace(".ui", "_qt5.py"),
                    qt6_file=outpath.replace(".ui", "_qt6.py"),
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
