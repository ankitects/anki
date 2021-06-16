# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import os
import subprocess
import sys

if __name__ == "__main__":
    (module, ini, pyqt_init, extendsitepkgs) = sys.argv[1:]
    ini = os.path.abspath(ini)
    pyqt_init = os.path.abspath(pyqt_init)
    pyqt_folder = os.path.dirname(pyqt_init)
    extendsitepkgs = os.path.abspath(extendsitepkgs)
    extra_site = os.path.abspath(os.getenv("EXTRA_SITE_PACKAGES"))

    folder = os.path.join(os.path.dirname(__file__), "..")
    os.chdir(folder)

    if sys.platform.startswith("win32"):
        mypy_path = ".;..\\pylib;" + pyqt_folder
    else:
        mypy_path = ".:../pylib:" + pyqt_folder

    os.environ["MYPYPATH"] = mypy_path
    os.environ["EXTRA_SITE_PACKAGES"] = extra_site

    args = [
        sys.executable,
        "-m",
        "mypy",
        module,
        "--config-file",
        ini,
        "--python-executable",
        extendsitepkgs,
    ]

    if sys.platform.startswith("win32"):
        # bazel passes in \\?\c:\... path; mypy can't handle it, so we
        # strip off prefix
        for entry in sys.path:
            if "__mypy_" in entry:
                typeshed = entry[4:] + "\\mypy\\typeshed"
                args.append("--custom-typeshed-dir")
                args.append(typeshed)

    sys.exit(subprocess.run(args, check=False).returncode)
