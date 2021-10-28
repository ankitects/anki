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
    filename = os.path.join(path, "pythoncom39.dll")
    loader = importlib.machinery.ExtensionFileLoader(name, filename)
    spec = importlib.machinery.ModuleSpec(name=name, loader=loader, origin=filename)
    _mod = importlib._bootstrap._load(spec)


def fix_extraneous_path_in_bazel():
    # source folder conflicts with bazel-out source
    if sys.path[0].endswith("qt"):
        del sys.path[0]


def fix_run_on_macos():
    if not sys.platform.startswith("darwin"):
        return
    exec_folder = os.path.dirname(sys.argv[0])
    if "runanki_qt515" in exec_folder:
        qt_version = 515
    elif "runanki_qt514" in exec_folder:
        qt_version = 514
    else:
        qt_version = 6
    pyqt_repo = os.path.join(exec_folder, f"../../../../../../../external/pyqt{qt_version}")
    if os.path.exists(pyqt_repo):
        # pyqt must point to real folder, not a symlink
        sys.path.insert(0, pyqt_repo)
            # set the correct data folder base
        data = os.path.join(exec_folder, "aqt", "data")
        os.environ["AQT_DATA_FOLDER"] = data
