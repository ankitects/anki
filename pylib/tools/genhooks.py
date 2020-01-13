# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""
Generate code for hook handling, and insert it into anki/hooks.py.

To add a new hook:
- update the hooks list below
- run 'make develop'
- send a pull request that includes the changes to this file and hooks.py
"""

import os
from anki.hooks_gen import Hook, update_file

# Hook list
######################################################################

hooks = [
    Hook(name="leech", cb_args="card: Card", legacy_hook="leech"),
    Hook(name="odue_invalid"),
    Hook(name="mod_schema", cb_args="proceed: bool", return_type="bool"),
]

if __name__ == "__main__":
    path = os.path.join(os.path.dirname(__file__), "..", "anki", "hooks.py")
    update_file(path, hooks)
