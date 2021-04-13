# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import os
import subprocess
import sys

if __name__ == "__main__":
    (module, ini) = sys.argv[1:]
    ini = os.path.abspath(ini)

    folder = os.path.join(os.path.dirname(__file__), "..")
    os.chdir(folder)

    sys.exit(
        subprocess.run(
            [sys.executable, "-m", "pylint", module, "-j", "0", "--rcfile", ini],
            check=False,
        ).returncode
    )
