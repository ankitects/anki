# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import sys

import anki.scheduler.base as _base

UnburyDeck = _base.UnburyDeck
CongratsInfo = _base.CongratsInfo
BuryOrSuspend = _base.BuryOrSuspend
FilteredDeckForUpdate = _base.FilteredDeckForUpdate
CustomStudyRequest = _base.CustomStudyRequest

# add aliases to the legacy pathnames
import anki.scheduler.v1
import anki.scheduler.v2

sys.modules["anki.sched"] = sys.modules["anki.scheduler.v1"]
sys.modules["anki.schedv2"] = sys.modules["anki.scheduler.v2"]
anki.sched = sys.modules["anki.scheduler.v1"]  # type: ignore
anki.schedv2 = sys.modules["anki.scheduler.v2"]  # type: ignore
