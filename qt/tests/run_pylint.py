import multiprocessing
import os
import subprocess
import sys

import PyQt5
from pylint.lint import Run

if __name__ == "__main__":
    (module, ini) = sys.argv[1:]
    ini = os.path.abspath(ini)

    Run(
        [
            "qt/aqt",
            "--rcfile",
            ini,
            "-j",
            str(min(4, multiprocessing.cpu_count())),
            "-v",
        ]
    )
