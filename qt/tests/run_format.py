# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import os
import subprocess
import sys

if __name__ == "__main__":
    isort_ini = sys.argv[1]
    isort_ini = os.path.abspath(isort_ini)
    fix = len(sys.argv) > 2

    if fix:
        os.chdir(os.path.join(os.environ["BUILD_WORKSPACE_DIRECTORY"], "qt"))
        args = []
    else:
        folder = os.path.join(os.path.dirname(__file__), "..")
        os.chdir(folder)
        args = ["--diff", "--check"]

    # work around issue with latest black
    if sys.platform == "win32" and "HOME" in os.environ:
        os.environ["USERPROFILE"] = os.environ["HOME"]

    retcode = subprocess.run(
        [
            sys.executable,
            "-m",
            "black",
            "-t",
            "py38",
            "--exclude=aqt/forms|colors|_gen",
            "aqt",
            "tests",
            "tools",
        ]
        + args,
        check=False,
    ).returncode
    if retcode != 0:
        sys.exit(retcode)

    retcode = subprocess.run(
        [
            sys.executable,
            "-m",
            "isort",
            "--settings-path",
            isort_ini,
            "aqt",
            "tests",
            "tools",
        ]
        + args,
        check=False,
    ).returncode
    if retcode != 0:
        sys.exit(retcode)
