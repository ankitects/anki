# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import os
import re
import sys

root = sys.argv[1]

type_re = re.compile(r'(make(Enum|MessageType))\(\n\s+".*",')
for dirpath, dirnames, filenames in os.walk(root):
    for filename in filenames:
        if filename.endswith(".js"):
            file = os.path.join(dirpath, filename)
            with open(file, "r", encoding="utf8") as f:
                contents = f.read()

            # allow tree shaking on proto messages
            contents = contents.replace(
                "= proto3.make", "= /* @__PURE__ */ proto3.make"
            )
            # strip out typeName info, which appears to only be required for
            # certain JSON functionality (though this only saves a few hundred
            # bytes)
            contents = type_re.sub('\\1("",', contents)

            with open(file, "w", encoding="utf8") as f:
                f.write(contents)
