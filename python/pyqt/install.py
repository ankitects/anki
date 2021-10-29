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
    assert not cmd.main(pip_args)


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
        install_packages(requirements_file, base, [])

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
