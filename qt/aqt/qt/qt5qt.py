# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

# make sure not to optimize imports on this file
# pylint: skip-file

"""
Compatibility shim for PyQt5.Qt
"""

from typing import Any

from .qt5 import *


def __getattr__(name: str) -> Any:
    return getattr(Qt, name)  # type: ignore
