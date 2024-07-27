# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""Helpers for serializing third-party collections to a common JSON form.
"""

from __future__ import annotations

import json
from dataclasses import asdict, dataclass, field
from typing import Union

from anki.consts import STARTING_FACTOR_FRACTION
from anki.decks import DeckId
from anki.models import NotetypeId


@dataclass
class ForeignCardType:
    name: str
    qfmt: str
    afmt: str

    @staticmethod
    def front_back() -> ForeignCardType:
        return ForeignCardType(
            "Card 1",
            qfmt="{{Front}}",
            afmt="{{FrontSide}}\n\n<hr id=answer>\n\n{{Back}}",
        )

    @staticmethod
    def back_front() -> ForeignCardType:
        return ForeignCardType(
            "Card 2",
            qfmt="{{Back}}",
            afmt="{{FrontSide}}\n\n<hr id=answer>\n\n{{Front}}",
        )

    @staticmethod
    def cloze() -> ForeignCardType:
        return ForeignCardType(
            "Cloze", qfmt="{{cloze:Text}}", afmt="{{cloze:Text}}<br>\n{{Back Extra}}"
        )


@dataclass
class ForeignNotetype:
    name: str
    fields: list[str]
    templates: list[ForeignCardType]
    is_cloze: bool = False

    @staticmethod
    def basic(name: str) -> ForeignNotetype:
        return ForeignNotetype(name, ["Front", "Back"], [ForeignCardType.front_back()])

    @staticmethod
    def basic_reverse(name: str) -> ForeignNotetype:
        return ForeignNotetype(
            name,
            ["Front", "Back"],
            [ForeignCardType.front_back(), ForeignCardType.back_front()],
        )

    @staticmethod
    def cloze(name: str) -> ForeignNotetype:
        return ForeignNotetype(
            name, ["Text", "Back Extra"], [ForeignCardType.cloze()], is_cloze=True
        )


@dataclass
class ForeignCard:
    """Data for creating an Anki card.

    Usually a review card, as the default card generation routine will take care
    of missing new cards.

    due          --  UNIX timestamp
    interval     --  days
    ease_factor  --  decimal fraction (2.5 corresponds to default ease)
    """

    # TODO: support new and learning cards?
    due: int = 0
    interval: int = 1
    ease_factor: float = STARTING_FACTOR_FRACTION
    reps: int = 0
    lapses: int = 0


@dataclass
class ForeignNote:
    fields: list[str] = field(default_factory=list)
    tags: list[str] = field(default_factory=list)
    notetype: str | NotetypeId = ""
    deck: str | DeckId = ""
    cards: list[ForeignCard] = field(default_factory=list)


@dataclass
class ForeignData:
    notes: list[ForeignNote] = field(default_factory=list)
    notetypes: list[ForeignNotetype] = field(default_factory=list)
    default_deck: str | DeckId = ""

    def serialize(self) -> str:
        return json.dumps(self, cls=ForeignDataEncoder, separators=(",", ":"))


class ForeignDataEncoder(json.JSONEncoder):
    def default(self, obj: object) -> dict:
        if isinstance(
            obj,
            (ForeignData, ForeignNote, ForeignCard, ForeignNotetype, ForeignCardType),
        ):
            return asdict(obj)
        return json.JSONEncoder.default(self, obj)
