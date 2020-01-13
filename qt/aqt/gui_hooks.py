# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""
See pylib/anki/hooks.py
"""

from __future__ import annotations

from typing import Any, Callable, Dict, List  # pylint: disable=unused-import

from anki.hooks import runFilter, runHook  # pylint: disable=unused-import

# New hook/filter handling
##############################################################################
# The code in this section is automatically generated - any edits you make
# will be lost. To add new hooks, see ../tools/genhooks.py
#
# @@AUTOGEN@@

mpv_idle_hook: List[Callable[[], None]] = []
mpv_will_play_hook: List[Callable[[str], None]] = []


def run_mpv_idle_hook() -> None:
    for hook in mpv_idle_hook:
        try:
            hook()
        except:
            # if the hook fails, remove it
            mpv_idle_hook.remove(hook)
            raise


def run_mpv_will_play_hook(file: str) -> None:
    for hook in mpv_will_play_hook:
        try:
            hook(file)
        except:
            # if the hook fails, remove it
            mpv_will_play_hook.remove(hook)
            raise
    # legacy support
    runHook("mpvWillPlay", file)


# @@AUTOGEN@@
