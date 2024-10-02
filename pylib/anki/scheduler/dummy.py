# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

# pylint: disable=invalid-name

from __future__ import annotations

from anki.cards import Card
from anki.decks import DeckId
from anki.scheduler.legacy import SchedulerBaseWithLegacy


class DummyScheduler(SchedulerBaseWithLegacy):
    reps = 0

    def reset(self) -> None:
        pass

    def getCard(self) -> Card | None:
        raise Exception("v1/v2 scheduler no longer supported")

    def answerCard(self, card: Card, ease: int) -> None:
        raise Exception("v1/v2 scheduler no longer supported")

    def _is_finished(self) -> bool:
        return False

    @property
    def active_decks(self) -> list[DeckId]:
        return []

    def counts(self) -> tuple[int, int, int]:
        return (0, 0, 0)
