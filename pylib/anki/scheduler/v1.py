# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

# pylint: disable=invalid-name

from __future__ import annotations

import anki
import anki.collection
from anki.cards import Card
from anki.consts import *
from anki.decks import DeckId

from .v2 import QueueConfig
from .v2 import Scheduler as V2


class Scheduler(V2):
    version = 1
    name = "std"
    haveCustomStudy = True
    _spreadRev = True
    _burySiblingsOnAnswer = True

    def __init__(  # pylint: disable=super-init-not-called
        self, col: anki.collection.Collection
    ) -> None:
        super().__init__(col)
        self.queueLimit = 0
        self.reportLimit = 0
        self.dynReportLimit = 0
        self.reps = 0
        self.lrnCount = 0
        self.revCount = 0
        self.newCount = 0
        self._haveQueues = False

    def reset(self) -> None:
        pass

    def getCard(self) -> Card | None:
        raise Exception("v1 scheduler no longer supported")

    def answerCard(self, card: Card, ease: int) -> None:
        raise Exception("v1 scheduler no longer supported")

    def _is_finished(self) -> bool:
        return False

    # stubs of v1-specific routines that add-ons may be overriding

    def _graduatingIvl(
        self, card: Card, conf: QueueConfig, early: bool, adj: bool = True
    ) -> int:
        return 0

    def removeLrn(self, ids: list[int] | None = None) -> None:
        pass

    def _lrnForDeck(self, did: DeckId) -> int:
        return 0

    def _deckRevLimit(self, did: DeckId) -> int:
        return 0

    def _nextLapseIvl(self, card: Card, conf: QueueConfig) -> int:
        return 0

    def _rescheduleRev(self, card: Card, ease: int) -> None:  # type: ignore[override]
        pass

    def _nextRevIvl(self, card: Card, ease: int) -> int:  # type: ignore[override]
        return 0

    def _constrainedIvl(self, ivl: float, conf: QueueConfig, prev: int) -> int:  # type: ignore[override]
        return 0

    def _adjRevIvl(self, card: Card, idealIvl: int) -> int:
        return 0

    def _dynIvlBoost(self, card: Card) -> int:
        return 0

    def _resched(self, card: Card) -> bool:
        return False
