import os
import subprocess
import sys

if __name__ == "__main__":
    (module, ini) = sys.argv[1:]
    ini = os.path.abspath(ini)

    folder = os.path.join(os.path.dirname(__file__), "..")
    os.chdir(folder)

    args = [sys.executable, "-m", "mypy", module, "--config-file", ini]

    if sys.platform.startswith("win32"):
        # bazel passes in \\?\c:\... path; mypy can't handle it, so we
        # strip off prefix
        for entry in sys.path:
            if "__mypy_" in entry:
                typeshed = entry[4:] + "\\mypy\\typeshed"
                args.append("--custom-typeshed-dir")
                args.append(typeshed)

    sys.exit(subprocess.run(args, check=False).returncode)
