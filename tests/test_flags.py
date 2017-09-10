from tests.shared import assertException, getEmptyCol

def test_flags():
    col = getEmptyCol()
    n = col.newNote()
    n['Front'] = "one"; n['Back'] = "two"
    cnt = col.addNote(n)
    c = n.cards()[0]
    # make sure higher bits are preserved
    origBits = 0b101 << 3
    c.flags = origBits
    c.flush()
    # no flags to start with
    assert c.userFlag() == 0
    assert len(col.findCards("flag:0")) == 1
    assert len(col.findCards("flag:1")) == 0
    # set flag 2
    col.setUserFlag(2, [c.id])
    c.load()
    assert c.userFlag() == 2
    assert c.flags & origBits == origBits
    assert len(col.findCards("flag:0")) == 0
    assert len(col.findCards("flag:2")) == 1
    assert len(col.findCards("flag:3")) == 0
    # change to 3
    col.setUserFlag(3, [c.id])
    c.load()
    assert c.userFlag() == 3
    # unset
    col.setUserFlag(0, [c.id])
    c.load()
    assert c.userFlag() == 0

    # should work with Cards method as well
    c.setUserFlag(2)
    assert c.userFlag() == 2
    c.setUserFlag(3)
    assert c.userFlag() == 3
    c.setUserFlag(0)
    assert c.userFlag() == 0
