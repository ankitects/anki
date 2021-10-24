# based on https://github.com/ali5h/rules_pip/blob/master/src/whl.py
# MIT

import os
import platform
import subprocess
import sys

from pip._internal.commands import create_command


def install_packages(requirements_path, directory, pip_args):
    pip_args = [
        "--isolated",
        "--disable-pip-version-check",
        "--target",
        directory,
        "--no-deps",
        "--ignore-requires-python",
        "--require-hashes",
        "-r",
        requirements_path
    ] + pip_args
    cmd = create_command("install")
    cmd.main(pip_args)


def fix_pyi_types():
    "Fix broken PyQt types."
    for dirpath, dirnames, fnames in os.walk("."):
        for fname in fnames:
            if not fname.endswith(".pyi"):
                continue
            path = os.path.join(dirpath, fname)

            with open(path, "r+") as file:
                lines = file.readlines()
                file.seek(0)
                for line in lines:
                    # # remove blanket getattr in QObject which also causes missing
                    # # attributes not to be detected
                    if "def __getattr__(self, name: str) -> typing.Any" in line:
                        continue
                    file.write(line + "\n")

def fix_webengine_codesigning():
    "Fix a codesigning issue in the 6.2.0 release."
    path = "PyQt6/Qt6/lib/QtWebEngineCore.framework/Helpers/QtWebEngineProcess.app/Contents/MacOS/QtWebEngineProcess"
    if os.path.exists(path):
        subprocess.run(["codesign", "-s", "-", path], check=True)

def main():
    base = sys.argv[1]
    requirements_file = sys.argv[2]

    # has user told us to use a custom existing folder instead?
    if local_site_packages := os.environ.get("PYTHON_SITE_PACKAGES"):
        subprocess.run(
            [
                "rsync",
                "-ai",
                "--include=PyQt**",
                "--exclude=*",
                local_site_packages,
                base + "/",
            ],
            check=True,
        )
        with open(os.path.join(base, "__init__.py"), "w") as file:
            pass

    else:
        arm_darwin = sys.platform.startswith("darwin") and platform.machine() == "arm64"
        pip_args = []
        if arm_darwin:
            # pyqt messed up the architecture tags in the 6.2.0 release
            pip_args.extend(
                [
                    "--platform=macosx_10_14_arm64",
                    "--only-binary=pyqt6-qt6,pyqt6-webengine-qt6",
                ])

        install_packages(requirements_file, base, pip_args)
        fix_pyi_types()
        fix_webengine_codesigning()

        with open(os.path.join(base, "__init__.py"), "w") as file:
            file.write("__path__ = __import__('pkgutil').extend_path(__path__, __name__)")

    pkg_name = os.path.basename(base)
    result = """
load("@rules_python//python:defs.bzl", "py_library")

package(default_visibility = ["//visibility:public"])

py_library(
    name = "{}",
    srcs = glob(["**/*.py"]),
    data = glob(["**/*"], exclude = [
        "**/*.py",
        "**/*.pyc",
        "**/* *",
        "BUILD",
        "WORKSPACE",
        "bin/*",
        "__pycache__",
        # these make building slower
        "Qt/qml/**",
        "**/*.sip",
        "**/*.png",
    ]),
    # This makes this directory a top-level in the python import
    # search path for anything that depends on this.
    imports = ["."],
)
""".format(pkg_name)

    with open(os.path.join(base, "BUILD"), "w") as f:
        f.write(result)


if __name__ == "__main__":
    main()
