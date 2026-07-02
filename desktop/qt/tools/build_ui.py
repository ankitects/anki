# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import io
import re
import sys
from dataclasses import dataclass
from pathlib import Path

from PyQt6.uic import compileUi


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


@dataclass
class UiFileAndOutputs:
    ui_file: Path
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
                    qt6_file=outpath.replace(".ui", "_qt6.py"),
                )
            )
    return out


if __name__ == "__main__":
    for entry in get_files():
        stock = compile(entry.ui_file)
        for_qt6 = with_fixes_for_qt6(stock)
        with open(entry.qt6_file, "w") as file:
            file.write(for_qt6)
