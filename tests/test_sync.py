# coding: utf-8

import nose, os, shutil, time

from anki import Collection as aopen, Collection
from anki.utils import intTime
from anki.sync import Syncer, LocalServer
from tests.shared import getEmptyCol, getEmptyDeckWith

# Local tests
##########################################################################

deck1=None
deck2=None
client=None
server=None
server2=None

def setup_basic():
    global deck1, deck2, client, server
    deck1 = getEmptyCol()
    # add a note to deck 1
    f = deck1.newNote()
    f['Front'] = u"foo"; f['Back'] = u"bar"; f.tags = [u"foo"]
    deck1.addNote(f)
    # answer it
    deck1.reset(); deck1.sched.answerCard(deck1.sched.getCard(), 4)
    # repeat for deck2
    deck2 = getEmptyDeckWith(server=True)
    f = deck2.newNote()
    f['Front'] = u"bar"; f['Back'] = u"bar"; f.tags = [u"bar"]
    deck2.addNote(f)
    deck2.reset(); deck2.sched.answerCard(deck2.sched.getCard(), 4)
    # start with same schema and sync time
    deck1.scm = deck2.scm = 0
    # and same mod time, so sync does nothing
    t = intTime(1000)
    deck1.save(mod=t); deck2.save(mod=t)
    server = LocalServer(deck2)
    client = Syncer(deck1, server)

def setup_modified():
    setup_basic()
    # mark deck1 as changed
    time.sleep(0.1)
    deck1.setMod()
    deck1.save()

@nose.with_setup(setup_basic)
def test_nochange():
    assert client.sync() == "noChanges"

@nose.with_setup(setup_modified)
def test_changedSchema():
    deck1.scm += 1
    deck1.setMod()
    assert client.sync() == "fullSync"

@nose.with_setup(setup_modified)
def test_sync():
    def check(num):
        for d in deck1, deck2:
            for t in ("revlog", "notes", "cards"):
                assert d.db.scalar("select count() from %s" % t) == num
            assert len(d.models.all()) == num*4
            # the default deck and config have an id of 1, so always 1
            assert len(d.decks.all()) == 1
            assert len(d.decks.dconf) == 1
            assert len(d.tags.all()) == num
    check(1)
    origUsn = deck1.usn()
    assert client.sync() == "success"
    # last sync times and mod times should agree
    assert deck1.mod == deck2.mod
    assert deck1._usn == deck2._usn
    assert deck1.mod == deck1.ls
    assert deck1._usn != origUsn
    # because everything was created separately it will be merged in. in
    # actual use, we use a full sync to ensure a common starting point.
    check(2)
    # repeating it does nothing
    assert client.sync() == "noChanges"
    # if we bump mod time, the decks will sync but should remain the same.
    deck1.setMod()
    deck1.save()
    assert client.sync() == "success"
    check(2)
    # crt should be synced
    deck1.crt = 123
    deck1.setMod()
    assert client.sync() == "success"
    assert deck1.crt == deck2.crt

@nose.with_setup(setup_modified)
def test_models():
    test_sync()
    # update model one
    cm = deck1.models.current()
    cm['name'] = "new"
    time.sleep(1)
    deck1.models.save(cm)
    deck1.save()
    assert deck2.models.get(cm['id'])['name'].startswith("Basic")
    assert client.sync() == "success"
    assert deck2.models.get(cm['id'])['name'] == "new"
    # deleting triggers a full sync
    deck1.scm = deck2.scm = 0
    deck1.models.rem(cm)
    deck1.save()
    assert client.sync() == "fullSync"

@nose.with_setup(setup_modified)
def test_notes():
    test_sync()
    # modifications should be synced
    nid = deck1.db.scalar("select id from notes")
    note = deck1.getNote(nid)
    assert note['Front'] != "abc"
    note['Front'] = "abc"
    note.flush()
    deck1.save()
    assert client.sync() == "success"
    assert deck2.getNote(nid)['Front'] == "abc"
    # deletions too
    assert deck1.db.scalar("select 1 from notes where id = ?", nid)
    deck1.remNotes([nid])
    deck1.save()
    assert client.sync() == "success"
    assert not deck1.db.scalar("select 1 from notes where id = ?", nid)
    assert not deck2.db.scalar("select 1 from notes where id = ?", nid)

@nose.with_setup(setup_modified)
def test_cards():
    test_sync()
    nid = deck1.db.scalar("select id from notes")
    note = deck1.getNote(nid)
    card = note.cards()[0]
    # answer the card locally
    card.startTimer()
    deck1.sched.answerCard(card, 4)
    assert card.reps == 2
    deck1.save()
    assert deck2.getCard(card.id).reps == 1
    assert client.sync() == "success"
    assert deck2.getCard(card.id).reps == 2
    # if it's modified on both sides , later mod time should win
    for test in ((deck1, deck2), (deck2, deck1)):
        time.sleep(1)
        c = test[0].getCard(card.id)
        c.reps = 5; c.flush()
        test[0].save()
        time.sleep(1)
        c = test[1].getCard(card.id)
        c.reps = 3; c.flush()
        test[1].save()
        assert client.sync() == "success"
        assert test[1].getCard(card.id).reps == 3
        assert test[0].getCard(card.id).reps == 3
    # removals should work too
    deck1.remCards([card.id])
    deck1.save()
    assert deck2.db.scalar("select 1 from cards where id = ?", card.id)
    assert client.sync() == "success"
    assert not deck2.db.scalar("select 1 from cards where id = ?", card.id)

@nose.with_setup(setup_modified)
def test_tags():
    test_sync()
    assert deck1.tags.all() == deck2.tags.all()
    deck1.tags.register(["abc"])
    deck2.tags.register(["xyz"])
    assert deck1.tags.all() != deck2.tags.all()
    deck1.save()
    time.sleep(0.1)
    deck2.save()
    assert client.sync() == "success"
    assert deck1.tags.all() == deck2.tags.all()

@nose.with_setup(setup_modified)
def test_decks():
    test_sync()
    assert len(deck1.decks.all()) == 1
    assert len(deck1.decks.all()) == len(deck2.decks.all())
    deck1.decks.id("new")
    assert len(deck1.decks.all()) != len(deck2.decks.all())
    time.sleep(0.1)
    deck2.decks.id("new2")
    deck1.save()
    time.sleep(0.1)
    deck2.save()
    assert client.sync() == "success"
    assert deck1.tags.all() == deck2.tags.all()
    assert len(deck1.decks.all()) == len(deck2.decks.all())
    assert len(deck1.decks.all()) == 3
    assert deck1.decks.confForDid(1)['maxTaken'] == 60
    deck2.decks.confForDid(1)['maxTaken'] = 30
    deck2.decks.save(deck2.decks.confForDid(1))
    deck2.save()
    assert client.sync() == "success"
    assert deck1.decks.confForDid(1)['maxTaken'] == 30

@nose.with_setup(setup_modified)
def test_conf():
    test_sync()
    assert deck2.conf['curDeck'] == 1
    deck1.conf['curDeck'] = 2
    time.sleep(0.1)
    deck1.setMod()
    deck1.save()
    assert client.sync() == "success"
    assert deck2.conf['curDeck'] == 2

@nose.with_setup(setup_modified)
def test_threeway():
    test_sync()
    deck1.close(save=False)
    d3path = deck1.path.replace(".anki", "2.anki")
    shutil.copy2(deck1.path, d3path)
    deck1.reopen()
    deck3 = aopen(d3path)
    client2 = Syncer(deck3, server)
    assert client2.sync() == "noChanges"
    # client 1 adds a card at time 1
    time.sleep(1)
    f = deck1.newNote()
    f['Front'] = u"1";
    deck1.addNote(f)
    deck1.save()
    # at time 2, client 2 syncs to server
    time.sleep(1)
    deck3.setMod()
    deck3.save()
    assert client2.sync() == "success"
    # at time 3, client 1 syncs, adding the older note
    time.sleep(1)
    assert client.sync() == "success"
    assert deck1.noteCount() == deck2.noteCount()
    # syncing client2 should pick it up
    assert client2.sync() == "success"
    assert deck1.noteCount() == deck2.noteCount() == deck3.noteCount()

def test_threeway2():
    # for this test we want ms precision of notes so we don't have to
    # sleep a lot
    import anki.notes
    intTime = anki.notes.intTime
    anki.notes.intTime = lambda x=1: intTime(1000)
    def setup():
        # create collection 1 with a single note
        c1 = getEmptyCol()
        f = c1.newNote()
        f['Front'] = u"startingpoint"
        nid = f.id
        c1.addNote(f)
        cid = f.cards()[0].id
        c1.beforeUpload()
        # start both clients and server off in this state
        s1path = c1.path.replace(".anki2", "-s1.anki2")
        c2path = c1.path.replace(".anki2", "-c2.anki2")
        shutil.copy2(c1.path, s1path)
        shutil.copy2(c1.path, c2path)
        # open them
        c1 = Collection(c1.path)
        c2 = Collection(c2path)
        s1 = Collection(s1path, server=True)
        return c1, c2, s1, nid, cid
    c1, c2, s1, nid, cid = setup()
    # modify c1 then sync c1->s1
    n = c1.getNote(nid)
    t = "firstmod"
    n['Front'] = t
    n.flush()
    c1.db.execute("update cards set mod=1, usn=-1")
    srv = LocalServer(s1)
    clnt1 = Syncer(c1, srv)
    clnt1.sync()
    n.load()
    assert n['Front'] == t
    assert s1.getNote(nid)['Front'] == t
    assert s1.db.scalar("select mod from cards") == 1
    # sync s1->c2
    clnt2 = Syncer(c2, srv)
    clnt2.sync()
    assert c2.getNote(nid)['Front'] == t
    assert c2.db.scalar("select mod from cards") == 1
    # modify c1 and sync
    time.sleep(0.001)
    t = "secondmod"
    n = c1.getNote(nid)
    n['Front'] = t
    n.flush()
    c1.db.execute("update cards set mod=2, usn=-1")
    clnt1.sync()
    # modify c2 and sync - both c2 and server should be the same
    time.sleep(0.001)
    t2 = "thirdmod"
    n = c2.getNote(nid)
    n['Front'] = t2
    n.flush()
    c2.db.execute("update cards set mod=3, usn=-1")
    clnt2.sync()
    n.load()
    assert n['Front'] == t2
    assert c2.db.scalar("select mod from cards") == 3
    n = s1.getNote(nid)
    assert n['Front'] == t2
    assert s1.db.scalar("select mod from cards") == 3
    # and syncing c1 again should yield the updated note as well
    clnt1.sync()
    n = s1.getNote(nid)
    assert n['Front'] == t2
    assert s1.db.scalar("select mod from cards") == 3
    n = c1.getNote(nid)
    assert n['Front'] == t2
    assert c1.db.scalar("select mod from cards") == 3

def _test_speed():
    t = time.time()
    deck1 = aopen(os.path.expanduser("~/rapid.anki"))
    for tbl in "revlog", "cards", "notes", "graves":
        deck1.db.execute("update %s set usn = -1 where usn != -1"%tbl)
    for m in deck1.models.all():
        m['usn'] = -1
    for tx in deck1.tags.all():
        deck1.tags.tags[tx] = -1
    deck1._usn = -1
    deck1.save()
    deck2 = getEmptyDeckWith(server=True)
    deck1.scm = deck2.scm = 0
    server = LocalServer(deck2)
    client = Syncer(deck1, server)
    print "load %d" % ((time.time() - t)*1000); t = time.time()
    assert client.sync() == "success"
    print "sync %d" % ((time.time() - t)*1000); t = time.time()

@nose.with_setup(setup_modified)
def test_filtered_delete():
    test_sync()
    nid = deck1.db.scalar("select id from notes")
    note = deck1.getNote(nid)
    card = note.cards()[0]
    card.type = 2
    card.ivl = 10
    card.factor = 2500
    card.due = deck1.sched.today
    card.flush()
    # put cards into a filtered deck
    did = deck1.decks.newDyn("dyn")
    deck1.sched.rebuildDyn(did)
    # sync the filtered deck
    assert client.sync() == "success"
    # answer the card locally
    time.sleep(1)
    card.load()
    card.startTimer()
    deck1.sched.answerCard(card, 4)
    assert card.ivl > 10
    # delete the filtered deck
    deck1.decks.rem(did)
    # sync again
    assert client.sync() == "success"
    card.load()
    assert card.ivl > 10
    return
