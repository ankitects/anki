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
