# coding: utf-8

import nose, os, tempfile, shutil, time
from tests.shared import assertException

from anki.errors import *
from anki import Deck
from anki.utils import intTime
from anki.sync import Syncer, FullSyncer, LocalServer, RemoteServer, \
    MediaSyncer, RemoteMediaServer
from anki.notes import Note
from anki.cards import Card
from tests.shared import getEmptyDeck

# Local tests
##########################################################################

deck1=None
deck2=None
client=None
server=None
server2=None

def setup_basic():
    global deck1, deck2, client, server
    deck1 = getEmptyDeck()
    # add a note to deck 1
    f = deck1.newNote()
    f['Front'] = u"foo"; f['Back'] = u"bar"; f.tags = [u"foo"]
    deck1.addNote(f)
    # answer it
    deck1.reset(); deck1.sched.answerCard(deck1.sched.getCard(), 4)
    # repeat for deck2
    deck2 = getEmptyDeck(server=True)
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
    deck1.save()

@nose.with_setup(setup_basic)
def test_nochange():
    assert client.sync() == "noChanges"

@nose.with_setup(setup_modified)
def test_changedSchema():
    deck1.scm += 1
    assert client.sync() == "fullSync"

@nose.with_setup(setup_modified)
def test_sync():
    def check(num):
        for d in deck1, deck2:
            for t in ("revlog", "notes", "cards", "nsums"):
                assert d.db.scalar("select count() from %s" % t) == num
            assert len(d.models.all()) == num*2
            # the default group and config have an id of 1, so always 1
            assert len(d.groups.all()) == 1
            assert len(d.groups.gconf) == 1
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
    # actual use we use a full sync to ensure initial a common starting point.
    check(2)
    # repeating it does nothing
    assert client.sync() == "noChanges"
    # if we bump mod time, everything is copied across again because of the
    # 600 second sync leeway. but the decks should remain the same.
    deck1.save()
    assert client.sync() == "success"
    check(2)

@nose.with_setup(setup_modified)
def test_models():
    test_sync()
    # update model one
    cm = deck1.models.current()
    cm['name'] = "new"
    time.sleep(1)
    deck1.models.save(cm)
    deck1.save()
    assert deck2.models.get(cm['id'])['name'] == "Basic"
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
    deck2.save()
    assert client.sync() == "success"
    assert deck1.tags.all() == deck2.tags.all()

@nose.with_setup(setup_modified)
def test_groups():
    test_sync()
    assert len(deck1.groups.all()) == 1
    assert len(deck1.groups.all()) == len(deck2.groups.all())
    deck1.groups.id("new")
    assert len(deck1.groups.all()) != len(deck2.groups.all())
    time.sleep(0.1)
    deck2.groups.id("new2")
    deck1.save()
    deck2.save()
    assert client.sync() == "success"
    assert deck1.tags.all() == deck2.tags.all()
    assert len(deck1.groups.all()) == len(deck2.groups.all())
    assert len(deck1.groups.all()) == 3
    assert deck1.groups.conf(1)['maxTaken'] == 60
    deck2.groups.conf(1)['maxTaken'] = 30
    deck2.groups.save(deck2.groups.conf(1))
    deck2.save()
    assert client.sync() == "success"
    assert deck1.groups.conf(1)['maxTaken'] == 30

@nose.with_setup(setup_modified)
def test_conf():
    test_sync()
    assert deck2.conf['topGroup'] == 1
    deck1.conf['topGroup'] = 2
    deck1.save()
    assert client.sync() == "success"
    assert deck2.conf['topGroup'] == 2

@nose.with_setup(setup_modified)
def test_threeway():
    test_sync()
    deck1.close(save=False)
    d3path = deck1.path.replace(".anki", "2.anki")
    shutil.copy2(deck1.path, d3path)
    deck1.reopen()
    deck3 = Deck(d3path)
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
    deck3.save()
    assert client2.sync() == "success"
    # at time 3, client 1 syncs, adding the older note
    time.sleep(1)
    assert client.sync() == "success"
    assert deck1.noteCount() == deck2.noteCount()
    # syncing client2 should pick it up
    assert client2.sync() == "success"
    assert deck1.noteCount() == deck2.noteCount() == deck3.noteCount()

def _test_speed():
    t = time.time()
    deck1 = Deck(os.path.expanduser("~/rapid.anki"))
    for tbl in "revlog", "cards", "notes", "graves":
        deck1.db.execute("update %s set usn = -1 where usn != -1"%tbl)
    for m in deck1.models.all():
        m['usn'] = -1
    for tx in deck1.tags.all():
        deck1.tags.tags[tx] = -1
    deck1._usn = -1
    deck1.save()
    deck2 = getEmptyDeck(server=True)
    deck1.scm = deck2.scm = 0
    server = LocalServer(deck2)
    client = Syncer(deck1, server)
    print "load %d" % ((time.time() - t)*1000); t = time.time()
    assert client.sync() == "success"
    print "sync %d" % ((time.time() - t)*1000); t = time.time()
