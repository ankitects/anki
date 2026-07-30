# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import os
import re

_BRAINLIFT_COMMIT_ENV = "ANKI_BRAINLIFT_COMMIT"
_FULL_GIT_COMMIT = re.compile(r"[0-9a-fA-F]{40}")


def brainlift_commit() -> str | None:
    """Return the packaged Brainlift source revision, when valid."""
    commit = os.environ.get(_BRAINLIFT_COMMIT_ENV)
    if commit and _FULL_GIT_COMMIT.fullmatch(commit):
        return commit.lower()
    return None


def app_name() -> str:
    """Return the packaged product name without changing upstream defaults."""
    return "Anki Brainlift" if brainlift_commit() else "Anki"
