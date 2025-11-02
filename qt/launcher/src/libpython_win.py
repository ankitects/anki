# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import json
import os
import sys
import sysconfig

cfg = sysconfig.get_config_var
base = cfg("installed_base") or cfg("installed_platbase")
version = cfg("py_version_nodot")
lib = "python" + version + ".dll"
print(json.dumps([version, os.path.join(base, lib), sys.executable]))
