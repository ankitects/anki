# based on https://github.com/ali5h/rules_pip/blob/master/src/whl.py
# MIT

"""downloads and parses info of a pkg and generates a BUILD file for it"""
import glob
import os
import re
import shutil
import subprocess
import sys
import platform

from pip._internal.commands import create_command

def install_package(pkg, directory, pip_args):
    """Downloads wheel for a package. Assumes python binary provided has
    pip and wheel package installed.

    Args:
        pkg: package name
        directory: destination directory to download the wheel file in
        python: python binary path used to run pip command
        pip_args: extra pip args sent to pip
    Returns:
        str: path to the wheel file
    """
    pip_args = [
        "--isolated",
        "--disable-pip-version-check",
        "--target",
        directory,
        "--no-deps",
        "--ignore-requires-python",
        pkg,
    ] + pip_args
    cmd = create_command("install")
    cmd.main(pip_args)

def _cleanup(directory, pattern):
    for p in glob.glob(os.path.join(directory, pattern)):
        shutil.rmtree(p)


def copy_and_fix_pyi(source, dest):
    "Fix broken PyQt types."
    with open(source) as input_file:
        with open(dest, "w") as output_file:
            for line in input_file.readlines():
                # inheriting from the missing sip.sipwrapper definition
                # causes missing attributes not to be detected, as it's treating
                # the class as inheriting from Any
                line = line.replace("PyQt6.sip.wrapper", "object")
                # # remove blanket getattr in QObject which also causes missing
                # # attributes not to be detected
                if "def __getattr__(self, name: str) -> typing.Any" in line:
                    continue
                output_file.write(line)


def merge_files(root, source):
    for dirpath, _dirnames, filenames in os.walk(source):
        target_dir = os.path.join(root, os.path.relpath(dirpath, source))
        if not os.path.exists(target_dir):
            os.mkdir(target_dir)
        for fname in filenames:
            source_path = os.path.join(dirpath, fname)
            target_path = os.path.join(target_dir, fname)
            if not os.path.exists(target_path):
                if fname.endswith(".pyi"):
                    copy_and_fix_pyi(source_path, target_path)
                else:
                    shutil.copy2(source_path, target_path)

def fix_webengine_codesigning(base: str):
    "Fix a codesigning issue in the 6.2.0 release."
    path = os.path.join(base, "PyQt6/Qt6/lib/QtWebEngineCore.framework/Helpers/QtWebEngineProcess.app/Contents/MacOS/QtWebEngineProcess")
    subprocess.run(["codesign", "-s", "-", path], check=True)

def main():
    base = sys.argv[1]

    local_site_packages = os.environ.get("PYTHON_SITE_PACKAGES")
    if local_site_packages:
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
        packages = [
            ("pyqt6", "pyqt6==6.2.0"),
            ("pyqt6-qt6", "pyqt6-qt6==6.2.0"),
            ("pyqt6-webengine", "pyqt6-webengine==6.2.0"),
            ("pyqt6-webengine-qt6", "pyqt6-webengine-qt6==6.2.0"),
            ("pyqt6-sip", "pyqt6_sip==13.1.0"),
        ]

        arm_darwin = sys.platform.startswith("darwin") and platform.machine() == "arm64"

        for (name, with_version) in packages:
            # install package in subfolder
            folder = os.path.join(base, "temp")
            pip_args = []
            if arm_darwin:
                if name in ("pyqt6-qt6", "pyqt6-webengine-qt6"):
                    # pyqt messed up the architecture tags
                    pip_args.extend(
                        [
                            "--platform=macosx_10_14_arm64",
                            "--only-binary=:all:",
                        ])
            install_package(with_version, folder, pip_args)
            # merge into parent
            merge_files(base, folder)
            shutil.rmtree(folder)

        with open(os.path.join(base, "__init__.py"), "w") as file:
            file.write("__path__ = __import__('pkgutil').extend_path(__path__, __name__)")

        if arm_darwin:
            fix_webengine_codesigning(base)

    result = """
load("@rules_python//python:defs.bzl", "py_library")

package(default_visibility = ["//visibility:public"])

py_library(
    name = "pkg",
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
"""

    # clean up
    _cleanup(base, "__pycache__")

    with open(os.path.join(base, "BUILD"), "w") as f:
        f.write(result)


if __name__ == "__main__":
    main()
