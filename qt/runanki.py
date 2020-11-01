#!/usr/bin/env python3

import os
import sys

def fix_pywin32_in_bazel():
    if sys.platform != "win32":
        return
    if "BAZEL_SH" not in os.environ:
        return

    import imp

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
    filename = os.path.join(path, "pythoncom38.dll")
    mod = imp.load_module("pythoncom", None, filename, 
                          ('.dll', 'rb', imp.C_EXTENSION))


def fix_extraneous_path_in_bazel():
    # source folder conflicts with bazel-out source
    if sys.path[0].endswith("qt"):
        del sys.path[0]

fix_pywin32_in_bazel()
fix_extraneous_path_in_bazel()

import aqt
aqt.run()
