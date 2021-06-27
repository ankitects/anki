# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from tests.shared import getEmptyCol


def test_flags():
    col = getEmptyCol()
    n = col.newNote()
    n["Front"] = "one"
    n["Back"] = "two"
    cnt = col.addNote(n)
    c = n.cards()[0]
    # make sure higher bits are preserved
    origBits = 0b101 << 3
    c.flags = origBits
    c.flush()
    # no flags to start with
    assert c.user_flag() == 0
    assert len(col.find_cards("flag:0")) == 1
    assert len(col.find_cards("flag:1")) == 0
    # set flag 2
    col.set_user_flag_for_cards(2, [c.id])
    c.load()
    assert c.user_flag() == 2
    assert c.flags & origBits == origBits
    assert len(col.find_cards("flag:0")) == 0
    assert len(col.find_cards("flag:2")) == 1
    assert len(col.find_cards("flag:3")) == 0
    # change to 3
    col.set_user_flag_for_cards(3, [c.id])
    c.load()
    assert c.user_flag() == 3
    # unset
    col.set_user_flag_for_cards(0, [c.id])
    c.load()
    assert c.user_flag() == 0
