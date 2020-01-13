# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""
See pylib/tools/genhooks.py for more info.
"""

import os

from anki.hooks_gen import Hook, update_file

# Hook list
######################################################################

hooks = [
    Hook(name="mpv_idle"),
    Hook(name="mpv_will_play", args=["file: str"], legacy_hook="mpvWillPlay"),
    Hook(
        name="reviewer_showing_question",
        args=["card: Card"],
        legacy_hook="showQuestion",
        legacy_no_args=True,
    ),
    Hook(
        name="reviewer_showing_answer",
        args=["card: Card"],
        legacy_hook="showAnswer",
        legacy_no_args=True,
    ),
]

if __name__ == "__main__":
    path = os.path.join(os.path.dirname(__file__), "..", "aqt", "gui_hooks.py")
    update_file(path, hooks)
