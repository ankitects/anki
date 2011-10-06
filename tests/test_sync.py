# coding: utf-8

import nose, os, tempfile, shutil, time
from tests.shared import assertException

from anki.errors import *
from anki import Deck
from anki.utils import intTime
from anki.sync import Syncer, FullSyncer, LocalServer, RemoteServer, \
    MediaSyncer, RemoteMediaServer
from anki.facts import Fact
from anki.cards import Card
from tests.shared import getEmptyDeck

# Local tests
##########################################################################

deck1=None
deck2=None
client=None
server=None

def setup_basic():
    global deck1, deck2, client, server
    deck1 = getEmptyDeck()
    # add a fact to deck 1
    f = deck1.newFact()
    f['Front'] = u"foo"; f['Back'] = u"bar"; f.tags = [u"foo"]
    deck1.addFact(f)
    # answer it
    deck1.reset(); deck1.sched.answerCard(deck1.sched.getCard(), 4)
    # repeat for deck2
    deck2 = getEmptyDeck(server=True)
    f = deck2.newFact()
    f['Front'] = u"bar"; f['Back'] = u"bar"; f.tags = [u"bar"]
    deck2.addFact(f)
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
            for t in ("revlog", "facts", "cards", "fsums"):
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
def test_facts():
    test_sync()
    # modifications should be synced
    fid = deck1.db.scalar("select id from facts")
    fact = deck1.getFact(fid)
    assert fact['Front'] != "abc"
    fact['Front'] = "abc"
    fact.flush()
    deck1.save()
    assert client.sync() == "success"
    assert deck2.getFact(fid)['Front'] == "abc"
    # deletions too
    assert deck1.db.scalar("select 1 from facts where id = ?", fid)
    deck1.remFacts([fid])
    deck1.save()
    assert client.sync() == "success"
    assert not deck1.db.scalar("select 1 from facts where id = ?", fid)
    assert not deck2.db.scalar("select 1 from facts where id = ?", fid)

@nose.with_setup(setup_modified)
def test_cards():
    test_sync()
    fid = deck1.db.scalar("select id from facts")
    fact = deck1.getFact(fid)
    card = fact.cards()[0]
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
    f = deck1.newFact()
    f['Front'] = u"1";
    deck1.addFact(f)
    deck1.save()
    # at time 2, client 2 syncs to server
    time.sleep(1)
    deck3.save()
    assert client2.sync() == "success"
    # at time 3, client 1 syncs, adding the older fact
    time.sleep(1)
    assert client.sync() == "success"
    assert deck1.factCount() == deck2.factCount()
    # syncing client2 should pick it up
    assert client2.sync() == "success"
    assert deck1.factCount() == deck2.factCount() == deck3.factCount()

def _test_speed():
    t = time.time()
    deck1 = Deck(os.path.expanduser("~/rapid.anki"))
    for tbl in "revlog", "cards", "facts", "graves":
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

# Remote tests
##########################################################################

import anki.sync
anki.sync.SYNC_URL = "http://localhost:8001/sync/"
TEST_USER = "synctest@ichi2.net"
TEST_PASS = "synctest"
TEST_HKEY = "k14LvSaEtXFITCJz"
TEST_REMOTE = True

def setup_remote():
    global server
    setup_basic()
    # mark deck1 as changed
    deck1.save()
    server = RemoteServer(TEST_USER, TEST_HKEY)
    client.server = server

@nose.with_setup(setup_remote)
def test_meta():
    global TEST_REMOTE
    try:
        (mod, scm, usn, ts) = server.meta()
    except Exception, e:
        if e.errno == 61:
            TEST_REMOTE = False
            print "aborting; server offline"
            return
    assert mod
    assert scm
    assert mod != client.deck.mod
    assert abs(ts - time.time()) < 3

@nose.with_setup(setup_remote)
def test_hkey():
    if not TEST_REMOTE:
        return
    assertException(Exception, lambda: server.hostKey("wrongpass"))
    server.hkey = "abc"
    k = server.hostKey(TEST_PASS)
    assert k == server.hkey == TEST_HKEY

@nose.with_setup(setup_remote)
def test_download():
    if not TEST_REMOTE:
        return
    f = FullSyncer(client.deck, "abc")
    assertException(Exception, f.download)
    f.hkey = TEST_HKEY
    f.download()

@nose.with_setup(setup_remote)
def test_remoteSync():
    if not TEST_REMOTE:
        return
    # not yet associated, so will require a full sync
    assert client.sync() == "fullSync"
    # upload
    f = FullSyncer(client.deck, TEST_HKEY)
    f.upload()
    client.deck.reopen()
    # should report no changes
    assert client.sync() == "noChanges"
    # bump local deck
    client.deck.save()
    assert client.sync() == "success"
    # again, no changes
    assert client.sync() == "noChanges"
    # downloading the remote deck should give us the same mod
    lmod = client.deck.mod
    f = FullSyncer(client.deck, TEST_HKEY)
    f.download()
    d = Deck(client.deck.path)
    assert d.mod == lmod

# Remote　media tests
##########################################################################
# We can't run useful tests for local media, because the desktop code assumes
# the current directory is the media folder.

def setup_remoteMedia():
    global client, server
    setup_basic()
    server = RemoteMediaServer(TEST_HKEY)
    client = MediaSyncer(deck1, server)

@nose.with_setup(setup_remoteMedia)
def test_media():
    server.mediatest("reset")
    assert len(os.listdir(deck1.media.dir())) == 0
    assert server.mediatest("count") == 0
    # add a file
    os.chdir(deck1.media.dir())
    p = os.path.join(deck1.media.dir(), "foo.jpg")
    open(p, "wb").write("foo")
    assert len(os.listdir(deck1.media.dir())) == 1
    assert server.mediatest("count") == 0
    client.sync()
    time.sleep(1)
    # should have been synced
    assert len(os.listdir(deck1.media.dir())) == 1
    assert server.mediatest("count") == 1
    # if we remove the file, should be removed
    os.unlink(p)
    client.sync()
    assert len(os.listdir(deck1.media.dir())) == 0
    assert server.mediatest("count") == 0
