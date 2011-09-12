# coding: utf-8

import nose, os, tempfile, shutil, time
from tests.shared import assertException

from anki.errors import *
from anki import Deck
from anki.utils import intTime
from anki.sync import Syncer, LocalServer
from anki.facts import Fact
from anki.cards import Card
from tests.shared import getEmptyDeck

# Local tests
##########################################################################

deck1=None
deck2=None
client=None
server=None

def setup_basic(loadDecks=None):
    global deck1, deck2, client, server
    if loadDecks:
        deck1 = Deck(loadDecks[0])
        deck2 = Deck(loadDecks[1])
    else:
        deck1 = getEmptyDeck()
        # add a fact to deck 1
        f = deck1.newFact()
        f['Front'] = u"foo"; f['Back'] = u"bar"; f.tags = [u"foo"]
        deck1.addFact(f)
        # answer it
        deck1.reset(); deck1.sched.answerCard(deck1.sched.getCard(), 4)
        # repeat for deck2
        deck2 = getEmptyDeck()
        f = deck2.newFact()
        f['Front'] = u"bar"; f['Back'] = u"bar"; f.tags = [u"bar"]
        deck2.addFact(f)
        deck2.reset(); deck2.sched.answerCard(deck2.sched.getCard(), 4)
        # start with same schema and sync time
        deck1.lastSync = deck2.lastSync = intTime() - 1
        deck1.scm = deck2.scm = 0
        # and same mod time, so sync does nothing
        deck1.save(); deck2.save()
    server = LocalServer(deck2)
    client = Syncer(deck1, server)
    # for testing, don't add the 10 minute padding
    def _lastSync(lsyn, rsyn):
        return min(lsyn, rsyn) - 1
    client._lastSync = _lastSync

def setup_modified():
    setup_basic()
    # mark deck1 as changed
    deck1.save(mod=intTime()+1)

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
    origLs = deck1.lastSync
    assert client.sync() == "success"
    # last sync times and mod times should agree
    assert deck1.mod == deck2.mod
    assert deck1.lastSync == deck2.lastSync
    assert deck1.mod == deck1.lastSync
    assert deck1.lastSync != origLs
    # because everything was created separately it will be merged in. in
    # actual use we use a full sync to ensure initial a common starting point.
    check(2)
    # repeating it does nothing
    assert client.sync() == "noChanges"
    # if we bump mod time, everything is copied across again because of the
    # 600 second sync leeway. but the decks should remain the same.
    deck1.save(mod=intTime()+2)
    assert client.sync() == "success"
    check(2)

@nose.with_setup(setup_modified)
def test_models():
    test_sync()
    # update model one
    cm = deck1.models.current()
    cm['name'] = "new"
    cm['mod'] = intTime() + 1
    deck1.save(mod=intTime()+1)
    assert deck2.models.get(cm['id'])['name'] == "Basic"
    assert client.sync() == "success"
    assert deck2.models.get(cm['id'])['name'] == "new"
    # deleting triggers a full sync
    deck1.scm = deck2.scm = 0
    deck1.models.rem(cm)
    deck1.save(mod=intTime()+1)
    assert client.sync() == "fullSync"

@nose.with_setup(setup_modified)
def test_facts():
    test_sync()
    # modifications should be synced
    fid = deck1.db.scalar("select id from facts")
    fact = deck1.getFact(fid)
    assert fact['Front'] != "abc"
    fact['Front'] = "abc"
    fact.flush(mod=intTime()+1)
    deck1.save(mod=intTime()+1)
    assert client.sync() == "success"
    assert deck2.getFact(fid)['Front'] == "abc"
    # deletions too
    assert deck1.db.scalar("select 1 from facts where id = ?", fid)
    deck1.remFacts([fid])
    deck1.save(mod=intTime()+1)
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
    deck1.save(mod=intTime()+1)
    assert deck2.getCard(card.id).reps == 1
    assert client.sync() == "success"
    assert deck2.getCard(card.id).reps == 2
    deck1.remCards([card.id])
    deck1.save(mod=intTime()+1)
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
    deck1.save(mod=intTime()+1)
    deck2.save(mod=intTime()+2)
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
    deck1.save(mod=intTime()+1)
    deck2.save(mod=intTime()+2)
    assert client.sync() == "success"
    assert deck1.tags.all() == deck2.tags.all()
    assert len(deck1.groups.all()) == len(deck2.groups.all())
    assert len(deck1.groups.all()) == 3
    assert deck1.groups.conf(1)['maxTaken'] == 60
    deck2.groups.conf(1)['maxTaken'] = 30
    deck2.groups.save(deck2.groups.conf(1))
    deck2.save(mod=intTime()+2)
    assert client.sync() == "success"
    assert deck1.groups.conf(1)['maxTaken'] == 30

@nose.with_setup(setup_modified)
def test_conf():
    test_sync()
    assert deck2.conf['topGroup'] == 1
    deck1.conf['topGroup'] = 2
    deck1.save(mod=intTime()+2)
    assert client.sync() == "success"
    assert deck2.conf['topGroup'] == 2

@nose.with_setup(setup_modified)
def test_threeway():
    test_sync()
    deck1.close()
    d3path = deck1.path.replace(".anki", "2.anki")
    shutil.copy2(deck1.path, d3path)
    deck1.reopen()
    deck3 = Deck(d3path)
    client2 = Syncer(deck3, server)
    # for testing, don't add the 10 minute padding
    def _lastSync(lsyn, rsyn):
        return min(lsyn, rsyn) - 1
    client2._lastSync = _lastSync
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
    # it now has a last sync time greater than when the card was added at time
    # 1
    assert deck3.lastSync > f.mod
    # at time 3, client 1 syncs, adding the older fact
    time.sleep(1)
    assert client.sync() == "success"
    assert deck1.factCount() == deck2.factCount()
    # syncing client2 should pick it up
    assert client2.sync() == "success"
    print deck1.factCount(), deck2.factCount(), deck3.factCount()
    assert deck1.factCount() == deck2.factCount() == deck3.factCount()

def _test_speed():
    t = time.time()
    setup_basic([os.path.expanduser("~/rapid.anki"),
                 os.path.expanduser("~/rapid2.anki")])
    print "load %d" % ((time.time() - t)*1000); t = time.time()
    deck2.save(mod=intTime()+1)
    # 3000 revlog entries: ~128ms
    # 3000 cards: ~200ms
    # 3000 facts: ~500ms
    assert client.sync() != "fullSync"
    print "sync %d" % ((time.time() - t)*1000); t = time.time()



