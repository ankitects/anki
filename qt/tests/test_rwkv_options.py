# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import os
import tempfile

from anki.collection import Collection
from aqt.rwkv_options import RwkvOptions, load_options, save_options


def _collection() -> Collection:
    (fd, path) = tempfile.mkstemp(suffix=".anki2")
    os.close(fd)
    os.unlink(path)
    return Collection(path)


def test_options_round_trip() -> None:
    col = _collection()
    try:
        assert load_options(col) == RwkvOptions(all_decks=False)
        save_options(col, RwkvOptions(all_decks=True))
        assert load_options(col) == RwkvOptions(all_decks=True)
        assert col.get_config("rwkvOptions") == {"allDecks": True}
        save_options(col, RwkvOptions())
        assert not load_options(col).all_decks
    finally:
        col.close()
