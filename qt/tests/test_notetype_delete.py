# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from dataclasses import dataclass

from anki.models import NotetypeId
from aqt.operations.notetype import selected_notetype_ids_to_remove


@dataclass
class Notetype:
    id: int
    use_count: int


def test_selected_notetype_ids_to_remove() -> None:
    notetypes = [Notetype(1, 2), Notetype(2, 0), Notetype(3, 0)]

    assert selected_notetype_ids_to_remove(
        notetypes,
        [NotetypeId(1), NotetypeId(3)],
    ) == [1, 3]


def test_selected_notetype_ids_to_remove_keeps_one() -> None:
    notetypes = [Notetype(1, 0), Notetype(2, 0), Notetype(3, 0)]

    assert selected_notetype_ids_to_remove(
        notetypes,
        [NotetypeId(1), NotetypeId(2), NotetypeId(3)],
    ) == [1, 2]


def test_selected_notetype_ids_to_remove_keeps_protected_notetype() -> None:
    notetypes = [Notetype(1, 0), Notetype(2, 0), Notetype(3, 0)]

    assert selected_notetype_ids_to_remove(
        notetypes,
        [NotetypeId(1), NotetypeId(2), NotetypeId(3)],
        protected_notetype_id=NotetypeId(2),
    ) == [1, 3]


def test_selected_notetype_ids_to_remove_ignores_stale_ids() -> None:
    notetypes = [Notetype(1, 0), Notetype(2, 0), Notetype(3, 0)]

    assert selected_notetype_ids_to_remove(
        notetypes,
        [NotetypeId(2), NotetypeId(4)],
    ) == [2]
