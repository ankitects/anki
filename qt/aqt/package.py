# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""Helpers for the packaged version of Anki."""

from __future__ import annotations

import os
import sys


def _fix_pywin32() -> None:
    # extend sys.path with .pth files
    import site

    site.addsitedir(sys.path[0])

    # use updated sys.path to locate dll folder and add it to path
    path = sys.path[-1]
    path = path.replace("Pythonwin", "pywin32_system32")
    os.environ["PATH"] += ";" + path

    # import Python modules from .dll files
    import importlib.machinery

    for name in "pythoncom", "pywintypes":
        filename = os.path.join(path, name + "39.dll")
        loader = importlib.machinery.ExtensionFileLoader(name, filename)
        spec = importlib.machinery.ModuleSpec(name=name, loader=loader, origin=filename)
        _mod = importlib._bootstrap._load(spec)  # type: ignore


def _patch_pkgutil() -> None:
    """Teach pkgutil.get_data() how to read files from in-memory resources.

    This is required for jsonschema."""
    import importlib
    import pkgutil

    def get_data_custom(package: str, resource: str) -> bytes | None:
        try:
            module = importlib.import_module(package)
            reader = module.__loader__.get_resource_reader(package)  # type: ignore[attr-defined]
            with reader.open_resource(resource) as f:
                return f.read()
        except:
            return None

    pkgutil.get_data = get_data_custom


def packaged_build_setup() -> None:
    if not getattr(sys, "frozen", False):
        return

    print("Initial setup...")

    if sys.platform == "win32":
        _fix_pywin32()

    _patch_pkgutil()

    # escape hatch for debugging issues with packaged build startup
    if os.getenv("ANKI_STARTUP_REPL"):
        # mypy incorrectly thinks this does not exist on Windows
        is_tty = os.isatty(sys.stdin.fileno())  # type: ignore
        if is_tty:
            import code

            code.InteractiveConsole().interact()
            sys.exit(0)
