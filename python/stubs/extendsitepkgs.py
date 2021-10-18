# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import os
from mypy import sitepkgs

pkgs = sitepkgs.getsitepackages()
pkgs.append(os.getenv("EXTRA_SITE_PACKAGES"))

print(pkgs)
