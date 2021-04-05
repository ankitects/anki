# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from dataclasses import dataclass
from typing import Optional


@dataclass
class OpMeta:
    """Metadata associated with an operation.

    The `handler` field can be used by screens to ignore change
    events they initiated themselves, if they have already made
    the required changes."""

    handler: Optional[object] = None
