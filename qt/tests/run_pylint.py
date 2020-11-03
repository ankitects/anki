import os
import subprocess
import sys

import PyQt5.QtCore

if __name__ == "__main__":
    (module, ini) = sys.argv[1:]
    ini = os.path.abspath(ini)

    sys.exit(
        subprocess.run(
            [
                sys.executable,
                "-m",
                "pylint",
                "qt/aqt/qt.py",
                "--rcfile",
                ini,
                "--extension-pkg-whitelist=PyQt5",
                "-v",
            ],
            check=False,
        ).returncode
    )
