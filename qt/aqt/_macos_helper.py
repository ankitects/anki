# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import sys

if sys.platform == "darwin":
    from anki_mac_helper import (  # pylint:disable=unused-import,import-error
        macos_helper,
    )
else:
    macos_helper = None
