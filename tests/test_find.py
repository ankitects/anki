# coding: utf-8

from anki.find import Finder
from tests.shared import getEmptyCol

def test_parse():
    f = Finder(None)
    assert f._tokenize("hello world") == ["hello", "world"]
    assert f._tokenize("hello  world") == ["hello", "world"]
    assert f._tokenize("one -two") == ["one", "-", "two"]
    assert f._tokenize("one --two") == ["one", "-", "two"]
    assert f._tokenize("one - two") == ["one", "-", "two"]
    assert f._tokenize("one or -two") == ["one", "or", "-", "two"]
    assert f._tokenize("'hello \"world\"'") == ["hello \"world\""]
    assert f._tokenize('"hello world"') == ["hello world"]
    assert f._tokenize("one (two or ( three or four))") == [
        "one", "(", "two", "or", "(", "three", "or", "four",
        ")", ")"]
    assert f._tokenize("embedded'string") == ["embedded'string"]
    assert f._tokenize("deck:'two words'") == ["deck:two words"]

def test_findCards():
    deck = getEmptyCol()
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
    f['Front'] = u'test'
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
    c.queue = c.type = 2
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
    c.flush()
    deck.db.execute("update cards set mod = mod + 1 where id = ?", c.id)
    assert deck.findCards("is:suspended") == [c.id]
    # nids
    assert deck.findCards("nid:54321") == []
    assert len(deck.findCards("nid:%d"%f.id)) == 2
    assert len(deck.findCards("nid:%d,%d" % (f1id, f2id))) == 2
    # templates
    assert len(deck.findCards("card:foo")) == 0
    assert len(deck.findCards("'card:card 1'")) == 4
    assert len(deck.findCards("card:reverse")) == 1
    assert len(deck.findCards("card:1")) == 4
    assert len(deck.findCards("card:2")) == 1
    # fields
    assert len(deck.findCards("front:dog")) == 1
    assert len(deck.findCards("-front:dog")) == 4
    assert len(deck.findCards("front:sheep")) == 0
    assert len(deck.findCards("back:sheep")) == 2
    assert len(deck.findCards("-back:sheep")) == 3
    assert len(deck.findCards("front:do")) == 0
    assert len(deck.findCards("front:*")) == 5
    # ordering
    deck.conf['sortType'] = "noteCrt"
    assert deck.findCards("front:*", order=True)[-1] in latestCardIds
    assert deck.findCards("", order=True)[-1] in latestCardIds
    deck.conf['sortType'] = "noteFld"
    assert deck.findCards("", order=True)[0] == catCard.id
    assert deck.findCards("", order=True)[-1] in latestCardIds
    deck.conf['sortType'] = "cardMod"
    assert deck.findCards("", order=True)[-1] in latestCardIds
    assert deck.findCards("", order=True)[0] == firstCardId
    deck.conf['sortBackwards'] = True
    assert deck.findCards("", order=True)[0] in latestCardIds
    # model
    assert len(deck.findCards("note:basic")) == 5
    assert len(deck.findCards("-note:basic")) == 0
    assert len(deck.findCards("-note:foo")) == 5
    # deck
    assert len(deck.findCards("deck:default")) == 5
    assert len(deck.findCards("-deck:default")) == 0
    assert len(deck.findCards("-deck:foo")) == 5
    assert len(deck.findCards("deck:def*")) == 5
    assert len(deck.findCards("deck:*EFAULT")) == 5
    assert len(deck.findCards("deck:*cefault")) == 0
    # full search
    f = deck.newNote()
    f['Front'] = u'hello<b>world</b>'
    f['Back'] = u'abc'
    deck.addNote(f)
    # as it's the sort field, it matches
    assert len(deck.findCards("helloworld")) == 2
    #assert len(deck.findCards("helloworld", full=True)) == 2
    # if we put it on the back, it won't
    (f['Front'], f['Back']) = (f['Back'], f['Front'])
    f.flush()
    assert len(deck.findCards("helloworld")) == 0
    #assert len(deck.findCards("helloworld", full=True)) == 2
    #assert len(deck.findCards("back:helloworld", full=True)) == 2
    # searching for an invalid special tag should not error
    assert len(deck.findCards("is:invalid")) == 0
    # should be able to limit to parent deck, no children
    id = deck.db.scalar("select id from cards limit 1")
    deck.db.execute("update cards set did = ? where id = ?",
                    deck.decks.id("Default::Child"), id)
    assert len(deck.findCards("deck:default")) == 7
    assert len(deck.findCards("deck:default::child")) == 1
    assert len(deck.findCards("deck:default -deck:default::*")) == 6
    # properties
    id = deck.db.scalar("select id from cards limit 1")
    deck.db.execute(
        "update cards set queue=2, ivl=10, reps=20, due=30, factor=2200 "
        "where id = ?", id)
    assert len(deck.findCards("prop:ivl>5")) == 1
    assert len(deck.findCards("prop:ivl<5")) > 1
    assert len(deck.findCards("prop:ivl>=5")) == 1
    assert len(deck.findCards("prop:ivl=9")) == 0
    assert len(deck.findCards("prop:ivl=10")) == 1
    assert len(deck.findCards("prop:ivl!=10")) > 1
    assert len(deck.findCards("prop:due>0")) == 1
    # due dates should work
    deck.sched.today = 15
    assert len(deck.findCards("prop:due=14")) == 0
    assert len(deck.findCards("prop:due=15")) == 1
    assert len(deck.findCards("prop:due=16")) == 0
    # including negatives
    deck.sched.today = 32
    assert len(deck.findCards("prop:due=-1")) == 0
    assert len(deck.findCards("prop:due=-2")) == 1
    # ease factors
    assert len(deck.findCards("prop:ease=2.3")) == 0
    assert len(deck.findCards("prop:ease=2.2")) == 1
    assert len(deck.findCards("prop:ease>2")) == 1
    assert len(deck.findCards("-prop:ease>2")) > 1
    # recently failed
    assert len(deck.findCards("rated:1:1")) == 0
    assert len(deck.findCards("rated:1:2")) == 0
    c = deck.sched.getCard()
    deck.sched.answerCard(c, 2)
    assert len(deck.findCards("rated:1:1")) == 0
    assert len(deck.findCards("rated:1:2")) == 1
    c = deck.sched.getCard()
    deck.sched.answerCard(c, 1)
    assert len(deck.findCards("rated:1:1")) == 1
    assert len(deck.findCards("rated:1:2")) == 1
    assert len(deck.findCards("rated:1")) == 2
    assert len(deck.findCards("rated:0:2")) == 0
    assert len(deck.findCards("rated:2:2")) == 1
    # empty field
    assert len(deck.findCards("front:")) == 0
    f = deck.newNote()
    f['Front'] = u''
    f['Back'] = u'abc2'
    assert deck.addNote(f) == 1
    assert len(deck.findCards("front:")) == 1
    # OR searches and nesting
    assert len(deck.findCards("tag:monkey or tag:sheep")) == 2
    assert len(deck.findCards("(tag:monkey OR tag:sheep)")) == 2
    assert len(deck.findCards("-(tag:monkey OR tag:sheep)")) == 6
    assert len(deck.findCards("tag:monkey or (tag:sheep sheep)")) == 2
    assert len(deck.findCards("tag:monkey or (tag:sheep octopus)")) == 1
    # invalid grouping shouldn't error
    assert len(deck.findCards(")")) == 0
    assert len(deck.findCards("(()")) == 0
    # added
    assert len(deck.findCards("added:0")) == 0
    deck.db.execute("update cards set id = id - 86400*1000 where id = ?",
                    id)
    assert len(deck.findCards("added:1")) == deck.cardCount() - 1
    assert len(deck.findCards("added:2")) == deck.cardCount()

def test_findReplace():
    deck = getEmptyCol()
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

def test_findDupes():
    deck = getEmptyCol()
    f = deck.newNote()
    f['Front'] = u'foo'
    f['Back'] = u'bar'
    deck.addNote(f)
    f2 = deck.newNote()
    f2['Front'] = u'baz'
    f2['Back'] = u'bar'
    deck.addNote(f2)
    f3 = deck.newNote()
    f3['Front'] = u'quux'
    f3['Back'] = u'bar'
    deck.addNote(f3)
    f4 = deck.newNote()
    f4['Front'] = u'quuux'
    f4['Back'] = u'nope'
    deck.addNote(f4)
    r = deck.findDupes("Back")
    assert r[0][0] == "bar"
    assert len(r[0][1]) == 3
    # valid search
    r = deck.findDupes("Back", "bar")
    assert r[0][0] == "bar"
    assert len(r[0][1]) == 3
    # excludes everything
    r = deck.findDupes("Back", "invalid")
    assert not r
    # front isn't dupe
    assert deck.findDupes("Front") == []
