# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""Serializer for Mnemosyne collections.

Some notes about their structure:
https://github.com/mnemosyne-proj/mnemosyne/blob/master/mnemosyne/libmnemosyne/docs/source/index.rst

Anki      | Mnemosyne
----------+-----------
Note      | Fact
Card Type | Fact View
Card      | Card
Notetype  | Card Type
"""

import re
from abc import ABC, abstractmethod
from dataclasses import dataclass, field

from anki.db import DB
from anki.decks import DeckId
from anki.foreign_data import (
    ForeignCard,
    ForeignCardType,
    ForeignData,
    ForeignNote,
    ForeignNotetype,
)


def serialize(db_path: str, deck_id: DeckId) -> str:
    db = open_mnemosyne_db(db_path)
    return gather_data(db, deck_id).serialize()


def gather_data(db: DB, deck_id: DeckId) -> ForeignData:
    facts = gather_facts(db)
    gather_cards_into_facts(db, facts)
    used_fact_views: dict[type[MnemoFactView], bool] = {}
    notes = [fact.foreign_note(used_fact_views) for fact in facts.values()]
    notetypes = [fact_view.foreign_notetype() for fact_view in used_fact_views]
    return ForeignData(notes, notetypes, deck_id)


def open_mnemosyne_db(db_path: str) -> DB:
    db = DB(db_path)
    ver = db.scalar("SELECT value FROM global_variables WHERE key='version'")
    if not ver.startswith("Mnemosyne SQL 1") and ver not in ("2", "3"):
        print("Mnemosyne version unknown, trying to import anyway")
    return db


class MnemoFactView(ABC):
    notetype: str
    field_keys: tuple[str, ...]

    @classmethod
    @abstractmethod
    def foreign_notetype(cls) -> ForeignNotetype:
        pass


class FrontOnly(MnemoFactView):
    notetype = "Mnemosyne-FrontOnly"
    field_keys = ("f", "b")

    @classmethod
    def foreign_notetype(cls) -> ForeignNotetype:
        return ForeignNotetype.basic(cls.notetype)


class FrontBack(MnemoFactView):
    notetype = "Mnemosyne-FrontBack"
    field_keys = ("f", "b")

    @classmethod
    def foreign_notetype(cls) -> ForeignNotetype:
        return ForeignNotetype.basic_reverse(cls.notetype)


class Vocabulary(MnemoFactView):
    notetype = "Mnemosyne-Vocabulary"
    field_keys = ("f", "p_1", "m_1", "n")

    @classmethod
    def foreign_notetype(cls) -> ForeignNotetype:
        return ForeignNotetype(
            cls.notetype,
            ["Expression", "Pronunciation", "Meaning", "Notes"],
            [cls._recognition_card_type(), cls._production_card_type()],
        )

    @staticmethod
    def _recognition_card_type() -> ForeignCardType:
        return ForeignCardType(
            name="Recognition",
            qfmt="{{Expression}}",
            afmt="{{Expression}}\n\n<hr id=answer>\n\n{{{{Pronunciation}}}}"
            "<br>\n{{{{Meaning}}}}<br>\n{{{{Notes}}}}",
        )

    @staticmethod
    def _production_card_type() -> ForeignCardType:
        return ForeignCardType(
            name="Production",
            qfmt="{{Meaning}}",
            afmt="{{Meaning}}\n\n<hr id=answer>\n\n{{{{Expression}}}}"
            "<br>\n{{{{Pronunciation}}}}<br>\n{{{{Notes}}}}",
        )


class Cloze(MnemoFactView):
    notetype = "Mnemosyne-Cloze"
    field_keys = ("text",)

    @classmethod
    def foreign_notetype(cls) -> ForeignNotetype:
        return ForeignNotetype.cloze(cls.notetype)


@dataclass
class MnemoCard:
    fact_view_id: str
    tags: str
    next_rep: int
    last_rep: int
    easiness: float
    reps: int
    lapses: int

    def card_ord(self) -> int:
        ord = self.fact_view_id.rsplit(".", maxsplit=1)[-1]
        try:
            return int(ord) - 1
        except ValueError as err:
            raise Exception(
                f"Fact view id '{self.fact_view_id}' has unknown format"
            ) from err

    def is_new(self) -> bool:
        return self.last_rep == -1

    def foreign_card(self) -> ForeignCard:
        return ForeignCard(
            ease_factor=self.easiness,
            reps=self.reps,
            lapses=self.lapses,
            interval=self.anki_interval(),
            due=int(self.next_rep),
        )

    def anki_interval(self) -> int:
        return int(max(1, (self.next_rep - self.last_rep) // 86400))


@dataclass
class MnemoFact:
    id: int
    fields: dict[str, str] = field(default_factory=dict)
    cards: list[MnemoCard] = field(default_factory=list)

    def foreign_note(
        self, used_fact_views: dict[type[MnemoFactView], bool]
    ) -> ForeignNote:
        fact_view = self.fact_view()
        used_fact_views[fact_view] = True
        return ForeignNote(
            fields=self.anki_fields(fact_view),
            tags=self.anki_tags(),
            notetype=fact_view.notetype,
            cards=self.foreign_cards(),
        )

    def fact_view(self) -> type[MnemoFactView]:
        try:
            fact_view = self.cards[0].fact_view_id
        except IndexError as err:
            raise Exception(f"Fact {id} has no cards") from err

        if fact_view.startswith("1.") or fact_view.startswith("1::"):
            return FrontOnly
        elif fact_view.startswith("2.") or fact_view.startswith("2::"):
            return FrontBack
        elif fact_view.startswith("3.") or fact_view.startswith("3::"):
            return Vocabulary
        elif fact_view.startswith("5.1"):
            return Cloze

        raise Exception(f"Fact {id} has unknown fact view: {fact_view}")

    def anki_fields(self, fact_view: type[MnemoFactView]) -> list[str]:
        return [munge_field(self.fields.get(k, "")) for k in fact_view.field_keys]

    def anki_tags(self) -> list[str]:
        tags: list[str] = []
        for card in self.cards:
            if not card.tags:
                continue
            tags.extend(
                t.replace(" ", "_").replace("\u3000", "_")
                for t in card.tags.split(", ")
            )
        return tags

    def foreign_cards(self) -> list[ForeignCard]:
        # generate defaults for new cards
        return [card.foreign_card() for card in self.cards if not card.is_new()]


def munge_field(field: str) -> str:
    # \n -> br
    field = re.sub("\r?\n", "<br>", field)
    # latex differences
    field = re.sub(r"(?i)<(/?(\$|\$\$|latex))>", "[\\1]", field)
    # audio differences
    field = re.sub('<audio src="(.+?)">(</audio>)?', "[sound:\\1]", field)
    return field


def gather_facts(db: DB) -> dict[int, MnemoFact]:
    facts: dict[int, MnemoFact] = {}
    for id, key, value in db.execute(
        """
SELECT _id, key, value
FROM facts, data_for_fact
WHERE facts._id=data_for_fact._fact_id"""
    ):
        if not (fact := facts.get(id)):
            facts[id] = fact = MnemoFact(id)
        fact.fields[key] = value
    return facts


def gather_cards_into_facts(db: DB, facts: dict[int, MnemoFact]) -> None:
    for fact_id, *row in db.execute(
        """
SELECT
    _fact_id,
    fact_view_id,
    tags,
    next_rep,
    last_rep,
    easiness,
    acq_reps + ret_reps,
    lapses
FROM cards"""
    ):
        facts[fact_id].cards.append(MnemoCard(*row))
    for fact in facts.values():
        fact.cards.sort(key=lambda c: c.card_ord())
