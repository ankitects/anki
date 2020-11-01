import os
import subprocess
import sys

import PyQt5.QtCore

if __name__ == "__main__":
    (module, ini, pyqt5_file) = sys.argv[1:]
    ini = os.path.abspath(ini)

    # pyqt5_dir = os.path.abspath(os.path.join(os.path.dirname(pyqt5_file), ".."))

    # os.environ["PYTHONPATH"] += ":" + pyqt5_dir
    # print(pyqt5_dir)
    # print(os.listdir(pyqt5_dir))
    # print(os.listdir(pyqt5_dir + "/PyQt5"))

    #    print(os.environ["PYTHONPATH"])

    print(os.listdir("."))

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
