# coding: utf-8

from tests.shared import getEmptyDeck

def test_findCards():
    deck = getEmptyDeck()
    f = deck.newNote()
    f['Front'] = u'dog'
    f['Back'] = u'cat'
    f.tags.append(u"monkey")
    f1id = f.id
    deck.addNote(f)
    firstCardId = f.cards()[0].id
    f = deck.newNote()
    f['Front'] = u'goats are fun'
    f['Back'] = u'sheep'
    f.tags.append(u"sheep goat horse")
    deck.addNote(f)
    f2id = f.id
    f = deck.newNote()
    f['Front'] = u'cat'
    f['Back'] = u'sheep'
    deck.addNote(f)
    catCard = f.cards()[0]
    m = deck.models.current(); mm = deck.models
    t = mm.newTemplate("Reverse")
    t['qfmt'] = "{{Back}}"
    t['afmt'] = "{{Front}}"
    mm.addTemplate(m, t)
    mm.save(m)
    f = deck.newNote()
    f['Front'] = u'template test'
    f['Back'] = u'foo bar'
    deck.addNote(f)
    latestCardIds = [c.id for c in f.cards()]
    # tag searches
    assert not deck.findCards("tag:donkey")
    assert len(deck.findCards("tag:sheep")) == 1
    assert len(deck.findCards("tag:sheep tag:goat")) == 1
    assert len(deck.findCards("tag:sheep tag:monkey")) == 0
    assert len(deck.findCards("tag:monkey")) == 1
    assert len(deck.findCards("tag:sheep -tag:monkey")) == 1
    assert len(deck.findCards("-tag:sheep")) == 4
    deck.tags.bulkAdd(deck.db.list("select id from notes"), "foo bar")
    assert (len(deck.findCards("tag:foo")) ==
            len(deck.findCards("tag:bar")) ==
            5)
    deck.tags.bulkRem(deck.db.list("select id from notes"), "foo")
    assert len(deck.findCards("tag:foo")) == 0
    assert len(deck.findCards("tag:bar")) == 5
    # text searches
    assert len(deck.findCards("cat")) == 2
    assert len(deck.findCards("cat -dog")) == 1
    assert len(deck.findCards("cat -dog")) == 1
    assert len(deck.findCards("are goats")) == 1
    assert len(deck.findCards('"are goats"')) == 0
    assert len(deck.findCards('"goats are"')) == 1
    # card states
    c = f.cards()[0]
    c.type = 2
    assert deck.findCards("is:review") == []
    c.flush()
    assert deck.findCards("is:review") == [c.id]
    assert deck.findCards("is:due") == []
    c.due = 0; c.queue = 2
    c.flush()
    assert deck.findCards("is:due") == [c.id]
    assert len(deck.findCards("-is:due")) == 4
    c.queue = -1
    # ensure this card gets a later mod time
    import time; time.sleep(1)
    c.flush()
    assert deck.findCards("is:suspended") == [c.id]
    # nids
    assert deck.findCards("nid:54321") == []
    assert len(deck.findCards("nid:%d"%f.id)) == 2
    assert len(deck.findCards("nid:%d,%d" % (f1id, f2id))) == 2
    # templates
    assert len(deck.findCards("card:foo")) == 0
    assert len(deck.findCards("card:forward")) == 4
    assert len(deck.findCards("card:reverse")) == 1
    assert len(deck.findCards("card:1")) == 4
    assert len(deck.findCards("card:2")) == 1
    # fields
    assert len(deck.findCards("front:dog")) == 1
    assert len(deck.findCards("-front:dog")) == 4
    assert len(deck.findCards("front:sheep")) == 0
    assert len(deck.findCards("back:sheep")) == 2
    assert len(deck.findCards("-back:sheep")) == 3
    assert len(deck.findCards("front:")) == 5
    # ordering
    deck.conf['sortType'] = "noteCrt"
    assert deck.findCards("front:")[-1] in latestCardIds
    assert deck.findCards("")[-1] in latestCardIds
    deck.conf['sortType'] = "noteFld"
    assert deck.findCards("")[0] == catCard.id
    assert deck.findCards("")[-1] in latestCardIds
    deck.conf['sortType'] = "cardMod"
    assert deck.findCards("")[-1] in latestCardIds
    assert deck.findCards("")[0] == firstCardId
    deck.conf['sortBackwards'] = True
    assert deck.findCards("")[0] in latestCardIds
    # model
    assert len(deck.findCards("note:basic")) == 5
    assert len(deck.findCards("-note:basic")) == 0
    assert len(deck.findCards("-note:foo")) == 5
    # deck
    assert len(deck.findCards("deck:default")) == 5
    assert len(deck.findCards("-deck:default")) == 0
    assert len(deck.findCards("-deck:foo")) == 5
    # full search
    f = deck.newNote()
    f['Front'] = u'hello<b>world</b>'
    f['Back'] = u'abc'
    deck.addNote(f)
    # as it's the sort field, it matches
    assert len(deck.findCards("helloworld")) == 2
    assert len(deck.findCards("helloworld", full=True)) == 2
    # if we put it on the back, it won't
    (f['Front'], f['Back']) = (f['Back'], f['Front'])
    f.flush()
    assert len(deck.findCards("helloworld")) == 0
    assert len(deck.findCards("helloworld", full=True)) == 2
    assert len(deck.findCards("front:helloworld")) == 0
    assert len(deck.findCards("back:helloworld", full=True)) == 2
    # searching for an invalid special tag should not error
    assert len(deck.findCards("is:invalid")) == 0

def test_findReplace():
    deck = getEmptyDeck()
    f = deck.newNote()
    f['Front'] = u'foo'
    f['Back'] = u'bar'
    deck.addNote(f)
    f2 = deck.newNote()
    f2['Front'] = u'baz'
    f2['Back'] = u'foo'
    deck.addNote(f2)
    nids = [f.id, f2.id]
    # should do nothing
    assert deck.findReplace(nids, "abc", "123") == 0
    # global replace
    assert deck.findReplace(nids, "foo", "qux") == 2
    f.load(); assert f['Front'] == "qux"
    f2.load(); assert f2['Back'] == "qux"
    # single field replace
    assert deck.findReplace(nids, "qux", "foo", field="Front") == 1
    f.load(); assert f['Front'] == "foo"
    f2.load(); assert f2['Back'] == "qux"
    # regex replace
    assert deck.findReplace(nids, "B.r", "reg") == 0
    f.load(); assert f['Back'] != "reg"
    assert deck.findReplace(nids, "B.r", "reg", regex=True) == 1
    f.load(); assert f['Back'] == "reg"
