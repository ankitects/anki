# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import zlib, re, urllib, urllib2, socket, simplejson, time, shutil
import os, base64, sys, httplib, types
from datetime import date
import anki, anki.deck, anki.cards
from anki.errors import *
from anki.utils import ids2str, checksum, intTime
from anki.consts import *
from anki.lang import _
from hooks import runHook

if simplejson.__version__ < "1.7.3":
    raise Exception("SimpleJSON must be 1.7.3 or later.")

CHUNK_SIZE = 32768
MIME_BOUNDARY = "Anki-sync-boundary"
SYNC_HOST = os.environ.get("SYNC_HOST") or "dev.ankiweb.net"
SYNC_PORT = int(os.environ.get("SYNC_PORT") or 80)
SYNC_URL = "http://%s:%d/sync/" % (SYNC_HOST, SYNC_PORT)
KEYS = ("models", "facts", "cards", "media")

# todo:
# - ensure all urllib references are converted to urllib2 for proxies
# - ability to cancel
# - need to make sure syncing doesn't bump the deck modified time if nothing was
#    changed, since by default closing the deck bumps the mod time
# - syncing with #/&/etc in password
# - timeout on all requests (issue 2625)
# - ditch user/pass in favour of session key?

# full sync:
# - compress and divide into pieces
# - zlib? zip? content-encoding? if latter, need to account for bad proxies
#   that decompress.

##########################################################################

from anki.consts import *

class Syncer(object):

    MAX_REVLOG = 5000
    MAX_CARDS = 5000
    MAX_FACTS = 2500

    def __init__(self, deck, server=None):
        self.deck = deck
        self.server = server

    def status(self, type):
        "Override to trace sync progress."
        #print "sync:", type
        pass

    def sync(self):
        "Returns 'noChanges', 'fullSync', or 'success'."
        # get local and remote modified, schema and sync times
        self.lmod, lscm, lsyn, self.minUsn = self.times()
        self.rmod, rscm, rsyn, self.maxUsn = self.server.times()
        if self.lmod == self.rmod:
            return "noChanges"
        elif lscm != rscm:
            return "fullSync"
        self.lnewer = self.lmod > self.rmod
        # get local changes and switch to full sync if there were too many
        self.status("getLocal")
        lchg = self.changes()
        if lchg == "fullSync":
            return "fullSync"
        # send them to the server, and get the server's changes
        self.status("getServer")
        rchg = self.server.changes(minUsn=self.minUsn, lnewer=self.lnewer,
                                   changes=lchg)
        if rchg == "fullSync":
            return "fullSync"
        # otherwise, merge
        self.status("merge")
        self.merge(lchg, rchg)
        # then tell server to save, and save local
        self.status("finish")
        mod = self.server.finish()
        self.finish(mod)
        return "success"

    def times(self):
        return (self.deck.mod, self.deck.scm, self.deck.ls, self.deck._usn)

    def changes(self, minUsn=None, lnewer=None, changes=None):
        if minUsn is not None:
            # we're the server; save info
            self.maxUsn = self.deck._usn
            self.minUsn = minUsn
            self.lnewer = not lnewer
            self.rchg = changes
        try:
            d = dict(revlog=self.getRevlog(),
                     facts=self.getFacts(),
                     cards=self.getCards(),
                     models=self.getModels(),
                     groups=self.getGroups(),
                     tags=self.getTags())
        except SyncTooLarge:
            return "fullSync"
        # collection-level configuration from last modified side
        if self.lnewer:
            d['conf'] = self.getConf()
        if minUsn is not None:
            # we're the server, we can merge our side before returning
            self.merge(d, self.rchg)
        return d

    def merge(self, lchg, rchg):
        # order is important here
        self.mergeModels(rchg['models'])
        self.mergeGroups(rchg['groups'])
        self.mergeRevlog(rchg['revlog'])
        self.mergeFacts(lchg['facts'], rchg['facts'])
        self.mergeCards(lchg['cards'], rchg['cards'])
        self.mergeTags(rchg['tags'])
        if 'conf' in rchg:
            self.mergeConf(rchg['conf'])

    def finish(self, mod=None):
        if not mod:
            # server side; we decide new mod time
            mod = intTime()
        self.deck.ls = mod
        self.deck._usn = self.maxUsn + 1
        self.deck.save(mod=mod)
        return mod

    # Models
    ##########################################################################

    def getModels(self):
        return [m for m in self.deck.models.all() if m['usn'] >= self.minUsn]

    def mergeModels(self, rchg):
        # deletes result in schema mod, so we only have to worry about
        # added or changed
        for r in rchg:
            l = self.deck.models.get(r['id'])
            # if missing locally or server is newer, update
            if not l or r['mod'] > l['mod']:
                self.deck.models.update(r)

    # Groups
    ##########################################################################

    def getGroups(self):
        return [
            [g for g in self.deck.groups.all() if g['usn'] >= self.minUsn],
            [g for g in self.deck.groups.allConf() if g['usn'] >= self.minUsn]
            ]

    def mergeGroups(self, rchg):
        # like models we rely on schema mod for deletes
        for r in rchg[0]:
            l = self.deck.groups.get(r['id'], False)
            # if missing locally or server is newer, update
            if not l or r['mod'] > l['mod']:
                self.deck.groups.update(r)
        for r in rchg[1]:
            l = self.deck.groups.conf(r['id'])
            # if missing locally or server is newer, update
            if not l or r['mod'] > l['mod']:
                self.deck.groups.updateConf(r)

    # Tags
    ##########################################################################

    def getTags(self):
        return self.deck.tags.allSinceUSN(self.minUsn)

    def mergeTags(self, tags):
        self.deck.tags.register(tags)

    # Revlog
    ##########################################################################

    def getRevlog(self):
        r = self.deck.db.all("select * from revlog where usn >= ? limit ?",
                             self.minUsn, self.MAX_REVLOG)
        if len(r) == self.MAX_REVLOG:
            raise SyncTooLarge
        return r

    def mergeRevlog(self, logs):
        for l in logs:
            l[2] = self.maxUsn
        self.deck.db.executemany(
            "insert or ignore into revlog values (?,?,?,?,?,?,?,?,?)",
            logs)

    # Facts
    ##########################################################################

    def getFacts(self):
        f = self.deck.db.all("select * from facts where usn >= ? limit ?",
                             self.minUsn, self.MAX_FACTS)
        if len(f) == self.MAX_FACTS:
            raise SyncTooLarge
        return [
            f,
            self.deck.db.list(
                "select oid from graves where usn >= ? and type = ?",
                self.minUsn, REM_FACT)
            ]

    def mergeFacts(self, lchg, rchg):
        (toAdd, toRem) = self.findChanges(
            lchg[0], lchg[1], rchg[0], rchg[1], 4, 5)
        # add missing
        self.deck.db.executemany(
            "insert or replace into facts values (?,?,?,?,?,?,?,?,?,?,?)",
            toAdd)
        # update fsums table - fixme: in future could skip sort cache
        self.deck.updateFieldCache([f[0] for f in toAdd])
        # remove remotely deleted
        self.deck._remFacts(toRem)

    # Cards
    ##########################################################################

    def getCards(self):
        c = self.deck.db.all("select * from cards where usn >= ? limit ?",
                             self.minUsn, self.MAX_CARDS)
        if len(c) == self.MAX_CARDS:
            raise SyncTooLarge
        return [
            c,
            self.deck.db.list(
                "select oid from graves where usn >= ? and type = ?",
                self.minUsn, REM_CARD)
            ]

    def mergeCards(self, lchg, rchg):
        # cards with higher reps preserved, so that gid changes don't clobber
        # older reviews
        (toAdd, toRem) = self.findChanges(
            lchg[0], lchg[1], rchg[0], rchg[1], 11, 5)
        # add missing
        self.deck.db.executemany(
            "insert or replace into cards values "
            "(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            toAdd)
        # remove remotely deleted
        self.deck.remCards(toRem)

    # Deck config
    ##########################################################################

    def getConf(self):
        return self.deck.conf

    def mergeConf(self, conf):
        self.deck.conf = conf

    # Merging
    ##########################################################################

    def findChanges(self, localAdds, localRems, remoteAdds, remoteRems, key, usn):
        local = {}
        toAdd = []
        toRem = []
        # cache local side
        for l in localAdds:
            local[l[0]] = (True, l)
        for l in localRems:
            local[l] = (False, l)
        # check remote adds
        for r in remoteAdds:
            if r[0] in local:
                # added remotely; changed locally
                (lAdded, l) = local[r[0]]
                if lAdded:
                    # added on both sides
                    if r[key] > l[key]:
                        # remote newer; update
                        r[usn] = self.maxUsn
                        toAdd.append(r)
                    else:
                        # local newer; remote will update
                        pass
                else:
                    # local deleted; remote will delete
                    pass
            else:
                # changed on server only
                r[usn] = self.maxUsn
                toAdd.append(r)
        return toAdd, remoteRems

class LocalServer(Syncer):
    # serialize/deserialize payload, so we don't end up sharing objects
    # between decks in testing
    def changes(self, minUsn, lnewer, changes):
        l = simplejson.loads; d = simplejson.dumps
        return l(d(Syncer.changes(self, minUsn, lnewer, l(d(changes)))))

# not yet ported
class RemoteServer(Syncer):
    pass
    # def unstuff(self, data):
    #     return simplejson.loads(unicode(zlib.decompress(data), "utf8"))
    # def stuff(self, data):
    #     return zlib.compress(simplejson.dumps(data))

# HTTP proxy: act as a server and direct requests to the real server
##########################################################################
# not yet ported

class HttpSyncServerProxy(object):

    def __init__(self, user, passwd):
        SyncServer.__init__(self)
        self.decks = None
        self.deckName = None
        self.username = user
        self.password = passwd
        self.protocolVersion = 5
        self.sourcesToCheck = []

    def connect(self, clientVersion=""):
        "Check auth, protocol & grab deck list."
        if not self.decks:
            import socket
            socket.setdefaulttimeout(30)
            d = self.runCmd("getDecks",
                            libanki=anki.version,
                            client=clientVersion,
                            sources=simplejson.dumps(self.sourcesToCheck),
                            pversion=self.protocolVersion)
            socket.setdefaulttimeout(None)
            if d['status'] != "OK":
                raise SyncError(type="authFailed", status=d['status'])
            self.decks = d['decks']
            self.timestamp = d['timestamp']
            self.timediff = abs(self.timestamp - time.time())

    def hasDeck(self, deckName):
        self.connect()
        return deckName in self.decks.keys()

    def availableDecks(self):
        self.connect()
        return self.decks.keys()

    def createDeck(self, deckName):
        ret = self.runCmd("createDeck", name=deckName.encode("utf-8"))
        if not ret or ret['status'] != "OK":
            raise SyncError(type="createFailed")
        self.decks[deckName] = [0, 0]

    def summary(self, lastSync):
        return self.runCmd("summary",
                           lastSync=self.stuff(lastSync))

    def genOneWayPayload(self, lastSync):
        return self.runCmd("genOneWayPayload",
                           lastSync=self.stuff(lastSync))

    def modified(self):
        self.connect()
        return self.decks[self.deckName][0]

    def _lastSync(self):
        self.connect()
        return self.decks[self.deckName][1]

    def applyPayload(self, payload):
        return self.runCmd("applyPayload",
                           payload=self.stuff(payload))

    def finish(self):
        assert self.runCmd("finish") == "OK"

    def runCmd(self, action, **args):
        data = {"p": self.password,
                "u": self.username,
                "v": 2}
        if self.deckName:
            data['d'] = self.deckName.encode("utf-8")
        else:
            data['d'] = None
        data.update(args)
        data = urllib.urlencode(data)
        try:
            f = urllib2.urlopen(SYNC_URL + action, data)
        except (urllib2.URLError, socket.error, socket.timeout,
                httplib.BadStatusLine), e:
            raise SyncError(type="connectionError",
                            exc=`e`)
        ret = f.read()
        if not ret:
            raise SyncError(type="noResponse")
        try:
            return self.unstuff(ret)
        except Exception, e:
            raise SyncError(type="connectionError",
                            exc=`e`)

# Full syncing
##########################################################################
# not yet ported

class FullSyncer(object):

    def __init__(self, deck):
        self.deck = deck

    def prepareFullSync(self):
        t = time.time()
        # ensure modified is not greater than server time
        self.deck.modified = min(self.deck.modified, self.server.timestamp)
        self.deck.db.commit()
        self.deck.close()
        fields = {
            "p": self.server.password,
            "u": self.server.username,
            "d": self.server.deckName.encode("utf-8"),
            }
        if self.localTime > self.remoteTime:
            return ("fromLocal", fields, self.deck.path)
        else:
            return ("fromServer", fields, self.deck.path)

    def fullSync(self):
        ret = self.prepareFullSync()
        if ret[0] == "fromLocal":
            self.fullSyncFromLocal(ret[1], ret[2])
        else:
            self.fullSyncFromServer(ret[1], ret[2])

    def fullSyncFromLocal(self, fields, path):
        global sendProgressHook
        try:
            # write into a temporary file, since POST needs content-length
            src = open(path, "rb")
            name = namedtmp("fullsync.anki")
            tmp = open(name, "wb")
            # post vars
            for (key, value) in fields.items():
                tmp.write('--' + MIME_BOUNDARY + "\r\n")
                tmp.write('Content-Disposition: form-data; name="%s"\r\n' % key)
                tmp.write('\r\n')
                tmp.write(value)
                tmp.write('\r\n')
            # file header
            tmp.write('--' + MIME_BOUNDARY + "\r\n")
            tmp.write(
                'Content-Disposition: form-data; name="deck"; filename="deck"\r\n')
            tmp.write('Content-Type: application/octet-stream\r\n')
            tmp.write('\r\n')
            # data
            comp = zlib.compressobj()
            while 1:
                data = src.read(CHUNK_SIZE)
                if not data:
                    tmp.write(comp.flush())
                    break
                tmp.write(comp.compress(data))
            src.close()
            tmp.write('\r\n--' + MIME_BOUNDARY + '--\r\n\r\n')
            size = tmp.tell()
            tmp.seek(0)
            # open http connection
            runHook("fullSyncStarted", size)
            headers = {
                'Content-type': 'multipart/form-data; boundary=%s' %
                MIME_BOUNDARY,
                'Content-length': str(size),
                'Host': SYNC_HOST,
                }
            req = urllib2.Request(SYNC_URL + "fullup?v=2", tmp, headers)
            try:
                sendProgressHook = fullSyncProgressHook
                res = urllib2.urlopen(req).read()
                assert res.startswith("OK")
                # update lastSync
                c = sqlite.connect(path)
                c.execute("update decks set lastSync = ?",
                          (res[3:],))
                c.commit()
                c.close()
            finally:
                sendProgressHook = None
                tmp.close()
        finally:
            runHook("fullSyncFinished")

    def fullSyncFromServer(self, fields, path):
        try:
            runHook("fullSyncStarted", 0)
            fields = urllib.urlencode(fields)
            src = urllib.urlopen(SYNC_URL + "fulldown", fields)
            tmpname = namedtmp("fullsync.anki")
            tmp = open(tmpname, "wb")
            decomp = zlib.decompressobj()
            cnt = 0
            while 1:
                data = src.read(CHUNK_SIZE)
                if not data:
                    tmp.write(decomp.flush())
                    break
                tmp.write(decomp.decompress(data))
                cnt += CHUNK_SIZE
                runHook("fullSyncProgress", "fromServer", cnt)
            src.close()
            tmp.close()
            os.close(fd)
            # if we were successful, overwrite old deck
            os.unlink(path)
            os.rename(tmpname, path)
            # reset the deck name
            c = sqlite.connect(path)
            c.execute("update decks set syncName = ?",
                      [checksum(path.encode("utf-8"))])
            c.commit()
            c.close()
        finally:
            runHook("fullSyncFinished")

##########################################################################
# Monkey-patch httplib to incrementally send instead of chewing up large
# amounts of memory, and track progress.

sendProgressHook = None

def incrementalSend(self, strOrFile):
    if self.sock is None:
        if self.auto_open:
            self.connect()
        else:
            raise NotConnected()
    if self.debuglevel > 0:
        print "send:", repr(str)
    try:
        if (isinstance(strOrFile, str) or
            isinstance(strOrFile, unicode)):
            self.sock.sendall(strOrFile)
        else:
            cnt = 0
            t = time.time()
            while 1:
                if sendProgressHook and time.time() - t > 1:
                    sendProgressHook(cnt)
                    t = time.time()
                data = strOrFile.read(CHUNK_SIZE)
                cnt += len(data)
                if not data:
                    break
                self.sock.sendall(data)
    except socket.error, v:
        if v[0] == 32:      # Broken pipe
            self.close()
        raise

httplib.HTTPConnection.send = incrementalSend

def fullSyncProgressHook(cnt):
    runHook("fullSyncProgress", "fromLocal", cnt)
