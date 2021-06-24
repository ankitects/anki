# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import os
import subprocess
import sys
import re
import shutil

stubs_remap = {"protobuf": "google", "futures": "concurrent"}


def copy_folder(pkgname, path, outbase):
    stubname = stubs_remap.get(pkgname, pkgname)
    os.listdir(path)
    path = f"{path}/{stubname}-stubs"
    shutil.copytree(path, os.path.join(outbase, f"{stubname}-stubs"))


name_re = re.compile("__types_(.+?)_\d")

if __name__ == "__main__":
    outbase = os.path.abspath(sys.argv[1])

    # copy stubs into top level folder, renaming
    folder = os.path.join(os.path.dirname(__file__), "../../external")
    os.chdir(folder)
    for folder in os.listdir("."):
        if match := name_re.search(folder):
            copy_folder(match.group(1), folder, outbase)
