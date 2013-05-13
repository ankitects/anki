# coding: utf-8

import nose, os, time
from tests.shared import assertException

from anki.sync import Syncer, FullSyncer, RemoteServer, \
    MediaSyncer, RemoteMediaServer, httpCon
from anki import Collection as aopen

deck1=None
deck2=None
client=None
server=None
server2=None

# Remote tests
##########################################################################

import tests.test_sync as ts
from tests.test_sync import setup_basic
import anki.sync
anki.sync.SYNC_URL = "http://localhost:5000/sync/"
TEST_USER = "synctest@ichi2.net"
TEST_PASS = "abc123"
TEST_HKEY = "WqYF0m7fOHCNPI4a"
TEST_REMOTE = True

def setup_remote():
    setup_basic()
    # mark deck1 as changed
    ts.deck1.save()
    ts.server = RemoteServer(TEST_HKEY)
    ts.client.server = ts.server

@nose.with_setup(setup_remote)
def test_meta():
    global TEST_REMOTE
    try:
        # if the key is wrong, meta returns nothing
        ts.server.hkey = "abc"
        assert not ts.server.meta()
    except Exception, e:
        if e.errno == 61:
            TEST_REMOTE = False
            print "aborting; server offline"
            return
    ts.server.hkey = TEST_HKEY
    (mod, scm, usn, tstamp, mediaUSN) = ts.server.meta()
    assert mod
    assert scm
    assert mod != ts.client.col.mod
    assert abs(tstamp - time.time()) < 3

@nose.with_setup(setup_remote)
def test_hkey():
    if not TEST_REMOTE:
        return
    assert not ts.server.hostKey(TEST_USER, "wrongpass")
    ts.server.hkey = "willchange"
    k = ts.server.hostKey(TEST_USER, TEST_PASS)
    assert k == ts.server.hkey == TEST_HKEY

@nose.with_setup(setup_remote)
def test_download():
    if not TEST_REMOTE:
        return
    f = FullSyncer(ts.client.col, "abc", ts.server.con)
    assertException(Exception, f.download)
    f.hkey = TEST_HKEY
    f.download()

@nose.with_setup(setup_remote)
def test_remoteSync():
    if not TEST_REMOTE:
        return
    # not yet associated, so will require a full sync
    assert ts.client.sync() == "fullSync"
    # upload
    f = FullSyncer(ts.client.col, TEST_HKEY, ts.server.con)
    assert f.upload()
    ts.client.col.reopen()
    # should report no changes
    assert ts.client.sync() == "noChanges"
    # bump local col
    ts.client.col.setMod()
    ts.client.col.save()
    assert ts.client.sync() == "success"
    # again, no changes
    assert ts.client.sync() == "noChanges"

# Remote　media tests
##########################################################################
# We can't run useful tests for local media, because the desktop code assumes
# the current directory is the media folder.

def setup_remoteMedia():
    setup_basic()
    con = httpCon()
    ts.server = RemoteMediaServer(TEST_HKEY, con)
    ts.server2 = RemoteServer(TEST_HKEY)
    ts.client = MediaSyncer(ts.deck1, ts.server)

@nose.with_setup(setup_remoteMedia)
def test_media():
    if not TEST_REMOTE:
        return
    print "media test disabled"
    return
    ts.server.mediatest("reset")
    assert len(os.listdir(ts.deck1.media.dir())) == 0
    assert ts.server.mediatest("count") == 0
    # initially, nothing to do
    assert ts.client.sync(ts.server2.meta()[4]) == "noChanges"
    # add a file
    time.sleep(1)
    os.chdir(ts.deck1.media.dir())
    p = os.path.join(ts.deck1.media.dir(), "foo.jpg")
    open(p, "wb").write("foo")
    assert len(os.listdir(ts.deck1.media.dir())) == 1
    assert ts.server.mediatest("count") == 0
    assert ts.client.sync(ts.server2.meta()[4]) == "success"
    assert ts.client.sync(ts.server2.meta()[4]) == "noChanges"
    time.sleep(1)
    # should have been synced
    assert len(os.listdir(ts.deck1.media.dir())) == 1
    assert ts.server.mediatest("count") == 1
    # if we remove the file, should be removed
    os.unlink(p)
    assert ts.client.sync(ts.server2.meta()[4]) == "success"
    assert ts.client.sync(ts.server2.meta()[4]) == "noChanges"
    assert len(os.listdir(ts.deck1.media.dir())) == 0
    assert ts.server.mediatest("count") == 0
    # we should be able to add it again
    time.sleep(1)
    open(p, "wb").write("foo")
    assert ts.client.sync(ts.server2.meta()[4]) == "success"
    assert ts.client.sync(ts.server2.meta()[4]) == "noChanges"
    assert len(os.listdir(ts.deck1.media.dir())) == 1
    assert ts.server.mediatest("count") == 1
    # if we modify it, it should get sent too. also we set the zip size very
    # low here, so that we can test splitting into multiple zips
    import anki.media; anki.media.SYNC_ZIP_SIZE = 1
    time.sleep(1)
    open(p, "wb").write("bar")
    open(p+"2", "wb").write("baz")
    assert len(os.listdir(ts.deck1.media.dir())) == 2
    assert ts.client.sync(ts.server2.meta()[4]) == "success"
    assert ts.client.sync(ts.server2.meta()[4]) == "noChanges"
    assert len(os.listdir(ts.deck1.media.dir())) == 2
    assert ts.server.mediatest("count") == 2
    # if we lose our media db, we should be able to bring it back in sync
    time.sleep(1)
    ts.deck1.media.close()
    os.unlink(ts.deck1.media.dir()+".db")
    ts.deck1.media.connect()
    assert ts.client.sync(ts.server2.meta()[4]) == "success"
    assert ts.client.sync(ts.server2.meta()[4]) == "noChanges"
    assert len(os.listdir(ts.deck1.media.dir())) == 2
    assert ts.server.mediatest("count") == 2
    # if we send an unchanged file, the server should cope
    time.sleep(1)
    ts.deck1.media.db.execute("insert into log values ('foo.jpg', 0)")
    assert ts.client.sync(ts.server2.meta()[4]) == "success"
    assert ts.client.sync(ts.server2.meta()[4]) == "noChanges"
    assert len(os.listdir(ts.deck1.media.dir())) == 2
    assert ts.server.mediatest("count") == 2
    # if we remove foo.jpg on the ts.server, the removal should be synced
    assert ts.server.mediatest("removefoo") == "OK"
    assert ts.client.sync(ts.server2.meta()[4]) == "success"
    assert len(os.listdir(ts.deck1.media.dir())) == 1
    assert ts.server.mediatest("count") == 1
