# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import os
import sys


def fix_pywin32_in_bazel(force=False):
    if sys.platform != "win32":
        return
    if not force and "BAZEL_SH" not in os.environ:
        return

    # get path to pywin32 package
    path = None
    for path in sys.path:
        if "pywin32" in path:
            break

    # trigger pywin32 bootstrap
    import site

    site.addsitedir(path)

    # sys.path has been extended; use final
    # path to locate dll folder and add it to path
    path = sys.path[-1]
    path = path.replace("Pythonwin", "pywin32_system32")
    os.environ["PATH"] += ";" + path

    # import pythoncom module
    import importlib
    import importlib.machinery

    name = "pythoncom"
    filename = os.path.join(path, "pythoncom38.dll")
    loader = importlib.machinery.ExtensionFileLoader(name, filename)
    spec = importlib.machinery.ModuleSpec(name=name, loader=loader, origin=filename)
    _mod = importlib._bootstrap._load(spec)


def fix_extraneous_path_in_bazel():
    # source folder conflicts with bazel-out source
    if sys.path[0].endswith("qt"):
        del sys.path[0]
