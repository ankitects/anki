# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import os
import subprocess

os.environ["REPO_ROOT"] = os.path.abspath(".")
subprocess.run(["out/pyenv/bin/sphinx-apidoc", "-o", "out/python/sphinx", "pylib", "qt"], check=True)
subprocess.run(["out/pyenv/bin/sphinx-build", "out/python/sphinx", "out/python/sphinx/html"], check=True)
