# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import os
import sys

template_path = os.path.abspath(sys.argv[1])
page_path = os.path.abspath(sys.argv[2])
page_name = ".".join(os.path.basename(page_path).split(".")[:1])

with open(template_path, "r") as template_file:
    template = template_file.read()

with open(page_path, "w") as page_file:
    page_file.write(template.replace("{PAGE}", page_name))
