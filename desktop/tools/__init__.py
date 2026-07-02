# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
from __future__ import annotations

from pathlib import Path

# TODO: This is a hack to prevent the coverage bot from erroring because it imports this module instead of qt/tools for some reason
_pkg_dir = Path(__file__).resolve().parent
_qt_tools_dir = _pkg_dir.parent / "qt" / "tools"
# Prepend so the local repo takes precedence over any installed package.
__path__.insert(0, str(_qt_tools_dir))
