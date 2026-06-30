# Copyright: Aryan Verma and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""Lets ``pytest qt/aqt/mcat/tests`` run on a bare checkout.

``memory_score.py`` is pure Python (no Anki/Qt), but it lives inside the
``aqt`` package. When pytest collects a test under here it builds ``Package``
collectors for the ancestor ``aqt`` and ``aqt.mcat`` packages and imports
their ``__init__`` modules during setup. ``aqt/__init__.py`` pulls in the
compiled ``anki`` backend and Qt, which aren't present outside a full build,
so collection would fail.

Inside a real build these modules are already importable and this conftest is
a no-op. Outside one, we pre-seed ``sys.modules`` with light stubs for the
``aqt`` / ``aqt.mcat`` packages so pytest's Package setup finds them already
imported and never executes the heavy real ``__init__`` files. The test itself
imports the pure ``memory_score`` module by file path, so the stubs don't
affect what is under test.

(The test is also runnable with no pytest at all: ``python test_memory_score.py``.)
"""

from __future__ import annotations

import importlib.util
import os
import sys
import types

_HERE = os.path.dirname(os.path.abspath(__file__))
_MCAT_DIR = os.path.dirname(_HERE)


def _stub_package(name: str, path: str) -> None:
    """Register a stub package only if the real one can't be imported."""
    if name in sys.modules:
        return
    try:
        # If a real build is present, prefer it and do nothing.
        importlib.import_module(name)
        return
    except Exception:
        pass
    mod = types.ModuleType(name)
    mod.__path__ = [path]  # mark as a package
    sys.modules[name] = mod


# Order matters: parent before child.
_stub_package("aqt", os.path.dirname(_MCAT_DIR))
_stub_package("aqt.mcat", _MCAT_DIR)
