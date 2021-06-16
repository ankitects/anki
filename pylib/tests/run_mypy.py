# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import os
import subprocess
import sys

if __name__ == "__main__":
    (module, ini, extendsitepkgs) = sys.argv[1:]
    ini = os.path.abspath(ini)
    extendsitepkgs = os.path.abspath(extendsitepkgs)
    extra_site = os.path.abspath(os.getenv("EXTRA_SITE_PACKAGES"))

    folder = os.path.join(os.path.dirname(__file__), "..")
    os.chdir(folder)

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

    os.environ["EXTRA_SITE_PACKAGES"] = extra_site

    if sys.platform.startswith("win32"):
        # bazel passes in \\?\c:\... path; mypy can't handle it, so we
        # strip off prefix
        for entry in sys.path:
            if "__mypy_" in entry:
                typeshed = entry[4:] + "\\mypy\\typeshed"
                args.append("--custom-typeshed-dir")
                args.append(typeshed)

    sys.exit(subprocess.run(args, check=False).returncode)
