# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import os
import sysconfig

cfg = sysconfig.get_config_var
base = cfg("installed_base") or cfg("installed_platbase")
lib = cfg("LDLIBRARY") or cfg("INSTSONAME")
print(os.path.join(base, "lib", lib))
