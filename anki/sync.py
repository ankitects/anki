# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import zlib, re, urllib, urllib2, socket, simplejson, time, shutil
import os, base64, httplib, sys, httplib, types
from datetime import date
import anki, anki.deck, anki.cards
from anki.errors import *
#from anki.models import Model, Field, Template
#from anki.facts import Fact
#from anki.cards import Card
from anki.utils import ids2str, checksum
from anki.consts import *
#from anki.media import mediaFiles
from anki.lang import _
from hooks import runHook


# - need to add tags table syncing

if simplejson.__version__ < "1.7.3":
    raise Exception("SimpleJSON must be 1.7.3 or later.")

CHUNK_SIZE = 32768
MIME_BOUNDARY = "Anki-sync-boundary"
SYNC_HOST = os.environ.get("SYNC_HOST") or "dev.ankiweb.net"
SYNC_PORT = int(os.environ.get("SYNC_PORT") or 80)
SYNC_URL = "http://%s:%d/sync/" % (SYNC_HOST, SYNC_PORT)
KEYS = ("models", "facts", "cards", "media")

##########################################################################

class Syncer(object):

    def __init__(self, deck=None):
        self.deck = deck
        self.diffs = {}
        self.timediff = 0
        self.fullThreshold = 5000

    # Control
    ##########################################################################

    def setServer(self, server):
        self.server = server

    def sync(self):
        "Sync two decks locally. Reimplement this for finer control."
        if not self.prepareSync(0):
            return
        lsum = self.summary(self.deck.lastSync)
        if lsum:
            rsum = self.server.summary(self.deck.lastSync)
        if not lsum or not rsum:
            raise Exception("full sync required")
        payload = self.genPayload((lsum, rsum))
        res = self.server.applyPayload(payload)
        self.applyPayloadReply(res)
        self.deck.reset()

    def prepareSync(self, timediff):
        "Sync setup. True if sync needed."
        self.localTime = self.modified()
        self.remoteTime = self.server.modified()
        if self.localTime == self.remoteTime:
            return False
        l = self._lastSync(); r = self.server._lastSync()
        # set lastSync to the lower of the two sides, account for slow clocks,
        # and assume it took up to 10 seconds for the reply to arrive
        self.deck.lastSync = max(0, min(l, r) - timediff - 10)
        return True

    def genPayload(self, summaries):
        (lsum, rsum) = summaries
        payload = {}
        # first, handle models, facts and cards
        for key in KEYS:
            diff = self.diffSummary(lsum, rsum, key)
            payload["added-" + key] = self.getObjsFromKey(diff[0], key)
            payload["deleted-" + key] = diff[1]
            payload["missing-" + key] = diff[2]
            self.deleteObjsFromKey(diff[3], key)
        # handle the remainder
        if self.localTime > self.remoteTime:
            payload['history'] = self.bundleHistory()
            payload['sources'] = self.bundleSources()
            # finally, set new lastSync and bundle the deck info
            payload['deck'] = self.bundleDeck()
        return payload

    def applyPayload(self, payload):
        reply = {}
        # model, facts and cards
        for key in KEYS:
            k = 'added-' + key
            # send back any requested
            if k in payload:
                reply[k] = self.getObjsFromKey(
                    payload['missing-' + key], key)
                self.updateObjsFromKey(payload['added-' + key], key)
                self.deleteObjsFromKey(payload['deleted-' + key], key)
        # send back deck-related stuff if it wasn't sent to us
        if not 'deck' in payload:
            reply['history'] = self.bundleHistory()
            reply['sources'] = self.bundleSources()
            # finally, set new lastSync and bundle the deck info
            reply['deck'] = self.bundleDeck()
        else:
            self.updateDeck(payload['deck'])
            self.updateHistory(payload['history'])
            if 'sources' in payload:
                self.updateSources(payload['sources'])
        self.postSyncRefresh()
        cardIds = [x[0] for x in payload['added-cards']]
        self.deck.updateCardTags(cardIds)
        return reply

    def applyPayloadReply(self, reply):
        # model, facts and cards
        for key in KEYS:
            k = 'added-' + key
            # old version may not send media
            if k in reply:
                self.updateObjsFromKey(reply['added-' + key], key)
        # deck
        if 'deck' in reply:
            self.updateDeck(reply['deck'])
            self.updateHistory(reply['history'])
            if 'sources' in reply:
                self.updateSources(reply['sources'])
        self.postSyncRefresh()
        cardIds = [x[0] for x in reply['added-cards']]
        self.deck.updateCardTags(cardIds)
        if self.missingFacts() != 0:
            raise Exception(
                "Facts missing after sync. Please run Tools>Advanced>Check DB.")

    def missingFacts(self):
        return self.deck.db.scalar(
            "select count() from cards where factId "+
                "not in (select id from facts)");

    def postSyncRefresh(self):
        "Flush changes to DB, and reload object associations."
        self.deck.db.flush()
        self.deck.db.refresh(self.deck)
        self.deck.currentModel

    # Changes
    ##########################################################################

    def deletions(self, lastSync):
        sql = "select oid from graves where time > ? and type = ?"
        return [self.deck.db.list(sql, lastSync, REM_FACT),
                self.deck.db.list(sql, lastSync, REM_CARD)]

    def delete(self, deletions):
        self.deck.delFacts(deletions[0])
        self.deck.delCards(deletions[1])

    def changes(self, lastSync, deletions=None):
        # set deck sync time
        self.deck.lastSync = lastSync
        # return early if there's been a schema change
        if self.deck.schemaChanged():
            return None
        # things to delete?
        if deletions:
            self.delete(deletions)
        d = {}
        cats = [
            # cards
            ("cards",
             "select * from cards where mod > ?"),
            # facts
            ("facts",
             "select * from facts where mod > ?"),
            # the rest
            ("models", "select * from models where mod > ?"),
            ("revlog", "select * from revlog where time > ?*1000"),
            ("tags", "select * from tags where mod > ?"),
            ("gconf", "select * from gconf where mod > ?"),
            ("groups", "select * from groups where mod > ?"),
        ]
        for (key, sql) in cats:
            if self.fullThreshold:
                sql += " limit %d" % self.fullThreshold
            ret = self.deck.db.all(sql, lastSync)
            if self.fullThreshold and self.fullThreshold == len(ret):
                # theshold exceeded, abort early
                return None
            d[key] = ret
        d['deck'] = self.deck.db.first("select * from deck")
        if deletions:
            # add our deletions
            d['deletions'] = self.deletions(lastSync)
        return d

    # ID rewriting
    ##########################################################################

    def rewriteIds(self, remote):
        "Rewrite local IDs so they don't conflict with server version."
        conf = simplejson.loads(remote['deck'][9])
        # cards
        id = self.deck.db.scalar(
            "select min(id) from cards where crt > ?", self.deck.lastSync)
        if id < conf['nextCid']:
            diff = conf['nextCid'] - id
            ids = self.deck.db.list(
                "select id from cards where crt > ?", self.deck.lastSync)
            sids = ids2str(ids)
            self.deck.db.execute(
                "update revlog set cid = cid + ? where cid in "+sids,
                diff)
            self.deck.db.execute(
                "update cards set id = id + ? where id in "+sids,
                diff)
        # facts
        id = self.deck.db.scalar(
            "select min(id) from facts where crt > ?", self.deck.lastSync)
        if id < conf['nextFid']:
            diff = conf['nextFid'] - id
            ids = self.deck.db.list(
                "select id from facts where crt > ?", self.deck.lastSync)
            sids = ids2str(ids)
            self.deck.db.execute(
                "update fsums set fid = fid + ? where fid in "+sids,
                diff)
            self.deck.db.execute(
                "update facts set id = id + ? where id in "+sids,
                diff)

    # Diffing
    ##########################################################################

    def diff(self, local, remote, key, modidx):
        ids = {}
        for type, data, key in [[0, local, key],
                                [1, remote, key]]:
            for rec in data[key]:
                id = rec[0]
                mod = rec[modidx]
                if id not in ids or ids[id][1] < mod:
                    ids[id] = [type, mod, rec]
        lchanges = []
        rchanges = []
        for type, mod, key in ids.values():
            if type == 0:
                lchanges.append(key)
            else:
                rchanges.append(key)
        return lchanges, rchanges

    # Facts
    ##########################################################################

    def diffFacts(self, local, remote):
        return self.diff(local, remote, 'facts', 4)

    def updateFacts(self, facts):
        if not facts:
            return
        self.deck.db.executemany(
            "insert or replace into facts values (?,?,?,?,?,?,?,?,?)", facts)
        # FIXME: this could be made faster
        self.deck.updateFieldCache(f[0] for f in facts)

    # Cards
    ##########################################################################

    def diffCards(self, local, remote):
        return self.diff(local, remote, 'cards', 5)

    def updateCards(self, cards):
        if not cards:
            return
        self.deck.db.executemany(
            "insert or replace into cards values "
            "(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            cards)

    # Models
    ##########################################################################

    def diffModels(self, local, remote):
        return self.diff(local, remote, 'models', 2)

    def updateModels(self, models):
        if not models:
            return
        self.deck.db.executemany(
            "insert or replace into models values (?,?,?,?,?,?,?,?)",
            models)

    # Groups
    ##########################################################################

    def diffGroups(self, local, remote):
        return self.diff(local, remote, 'groups', 1)

    def updateGroups(self, groups):
        if not groups:
            return
        self.deck.db.executemany(
            "insert or replace into groups values (?,?,?,?,?)",
            groups)

    # Gconf
    ##########################################################################

    def diffGconf(self, local, remote):
        return self.diff(local, remote, 'gconf', 1)

    def updateGconf(self, gconf):
        if not gconf:
            return
        self.deck.db.executemany(
            "insert or replace into gconf values (?,?,?,?,?)",
            gconf)

    # Revlog
    ##########################################################################

    def updateRevlog(self, revlog):
        if not revlog:
            return
        self.deck.db.executemany(
            "insert or replace into revlog values (?,?,?,?,?,?,?,?)",
            revlog)

    # Tags
    ##########################################################################

    def updateTags(self, tags):
        if not tags:
            return
        self.deck.db.executemany(
            "insert or replace into tags values (?,?,?)",
            tags)

    # Deck
    ##########################################################################

    def bundleDeck(self):
        # ensure modified is not greater than server time
        if getattr(self, "server", None) and getattr(
            self.server, "timestamp", None):
            self.deck.modified = min(self.deck.modified,self.server.timestamp)
        # and ensure lastSync is greater than modified
        self.deck.lastSync = max(time.time(), self.deck.modified+1)
        d = self.dictFromObj(self.deck)
        for bad in ("Session", "engine", "s", "db", "path", "syncName",
                    "version", "newQueue", "failedQueue", "revQueue",
                    "css", "models", "currentModel"):
            if bad in d:
                del d[bad]
        keys = d.keys()
        for k in keys:
            if isinstance(d[k], types.MethodType):
                del d[k]
        d['meta'] = self.deck.db.all("select * from deckVars")
        return d

    def updateDeck(self, deck):
        if 'meta' in deck:
            meta = deck['meta']
            for (k,v) in meta:
                self.deck.db.execute("""
insert or replace into deckVars
(key, value) values (:k, :v)""", k=k, v=v)
            del deck['meta']
        self.applyDict(self.deck, deck)

    # Tools
    ##########################################################################

    def modified(self):
        return self.deck.modified

    def _lastSync(self):
        return self.deck.lastSync

    def unstuff(self, data):
        return simplejson.loads(unicode(zlib.decompress(data), "utf8"))

    def stuff(self, data):
        return zlib.compress(simplejson.dumps(data))

    # Full sync
    ##########################################################################

    def needFullSync(self, sums):
        if self.deck.lastSync <= 0:
            return True
        for sum in sums:
            for l in sum.values():
                if len(l) > 1000:
                    return True
        if self.deck.db.scalar(
            "select count() from revlog where time > :ls",
            ls=self.deck.lastSync) > 1000:
            return True
        lastDay = date.fromtimestamp(max(0, self.deck.lastSync - 60*60*24))
        return False

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

#     # One-way syncing (sharing)
#     ##########################################################################

#     def syncOneWay(self, lastSync):
#         "Sync two decks one way."
#         payload = self.server.genOneWayPayload(lastSync)
#         self.applyOneWayPayload(payload)
#         self.deck.reset()

#     def syncOneWayDeckName(self):
#         return (self.deck.s.scalar("select name from sources where id = :id",
#                                    id=self.server.deckName) or
#                 hexifyID(int(self.server.deckName)))

#     def prepareOneWaySync(self):
#         "Sync setup. True if sync needed. Not used for local sync."
#         srcID = self.server.deckName
#         (lastSync, syncPeriod) = self.deck.s.first(
#             "select lastSync, syncPeriod from sources where id = :id", id=srcID)
#         if self.server.modified() <= lastSync:
#             return
#         self.deck.lastSync = lastSync
#         return True

#     def genOneWayPayload(self, lastSync):
#         "Bundle all added or changed objects since the last sync."
#         p = {}
#         # facts
#         factIds = self.deck.s.column0(
#             "select id from facts where modified > :l", l=lastSync)
#         p['facts'] = self.getFacts(factIds, updateModified=True)
#         # models
#         modelIds = self.deck.s.column0(
#             "select id from models where modified > :l", l=lastSync)
#         p['models'] = self.getModels(modelIds, updateModified=True)
#         # media
#         mediaIds = self.deck.s.column0(
#             "select id from media where created > :l", l=lastSync)
#         p['media'] = self.getMedia(mediaIds)
#         # cards
#         cardIds = self.deck.s.column0(
#             "select id from cards where modified > :l", l=lastSync)
#         p['cards'] = self.getOneWayCards(cardIds)
#         return p

#     def applyOneWayPayload(self, payload):
#         keys = [k for k in KEYS if k != "cards"]
#         # model, facts, media
#         for key in keys:
#             self.updateObjsFromKey(payload[key], key)
#         # models need their source tagged
#         for m in payload["models"]:
#             self.deck.s.statement("update models set source = :s "
#                                   "where id = :id",
#                                   s=self.server.deckName,
#                                   id=m['id'])
#         # cards last, handled differently
#         t = time.time()
#         try:
#             self.updateOneWayCards(payload['cards'])
#         except KeyError:
#             sys.stderr.write("Subscribed to a broken deck. "
#                              "Try removing your deck subscriptions.")
#             t = 0
#         # update sync time
#         self.deck.s.statement(
#             "update sources set lastSync = :t where id = :id",
#             id=self.server.deckName, t=t)
#         self.deck.modified = time.time()

#     def getOneWayCards(self, ids):
#         "The minimum information necessary to generate one way cards."
#         return self.deck.s.all(
#             "select id, factId, cardModelId, ordinal, created from cards "
#             "where id in %s" % ids2str(ids))

#     def updateOneWayCards(self, cards):
#         if not cards:
#             return
#         t = time.time()
#         dlist = [{'id': c[0], 'factId': c[1], 'cardModelId': c[2],
#                   'ordinal': c[3], 'created': c[4], 't': t} for c in cards]
#         # add any missing cards
#         self.deck.s.statements("""
# insert or ignore into cards
# (id, factId, cardModelId, created, modified, tags, ordinal,
# priority, interval, lastInterval, due, lastDue, factor,
# firstAnswered, reps, successive, averageTime, reviewTime, youngEase0,
# youngEase1, youngEase2, youngEase3, youngEase4, matureEase0,
# matureEase1, matureEase2, matureEase3, matureEase4, yesCount, noCount,
# question, answer, lastFactor, spaceUntil, isDue, type, combinedDue,
# relativeDelay)
# values
# (:id, :factId, :cardModelId, :created, :t, "", :ordinal,
# 1, 0, 0, :created, 0, 2.5,
# 0, 0, 0, 0, 0, 0,
# 0, 0, 0, 0, 0,
# 0, 0, 0, 0, 0,
# 0, "", "", 2.5, 0, 0, 2, :t, 2)""", dlist)
#         # update q/as
#         models = dict(self.deck.s.all("""
# select cards.id, models.id
# from cards, facts, models
# where cards.factId = facts.id
# and facts.modelId = models.id
# and cards.id in %s""" % ids2str([c[0] for c in cards])))
#         self.deck.s.flush()
#         self.deck.updateCardQACache(
#             [(c[0], c[2], c[1], models[c[0]]) for c in cards])
#         # rebuild priorities on client
#         cardIds = [c[0] for c in cards]
#         self.deck.updateCardTags(cardIds)
#         self.rebuildPriorities(cardIds)

# Local syncing
##########################################################################

class SyncServer(Syncer):
    pass

class SyncClient(Syncer):
    pass

# HTTP proxy: act as a server and direct requests to the real server
##########################################################################

class HttpSyncServerProxy(SyncServer):

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

# HTTP server: respond to proxy requests and return data
##########################################################################

class HttpSyncServer(SyncServer):
    def __init__(self):
        SyncServer.__init__(self)
        self.decks = {}
        self.deck = None

    def summary(self, lastSync):
        return self.stuff(SyncServer.summary(
            self, float(zlib.decompress(lastSync))))

    def applyPayload(self, payload):
        return self.stuff(SyncServer.applyPayload(self,
            self.unstuff(payload)))

    def genOneWayPayload(self, lastSync):
        return self.stuff(SyncServer.genOneWayPayload(
            self, float(zlib.decompress(lastSync))))

    def getDecks(self, libanki, client, sources, pversion):
        return self.stuff({
            "status": "OK",
            "decks": self.decks,
            "timestamp": time.time(),
            })

    def createDeck(self, name):
        "Create a deck on the server. Not implemented."
        return self.stuff("OK")

# Local media copying
##########################################################################

def copyLocalMedia(src, dst):
    srcDir = src.mediaDir()
    if not srcDir:
        return
    dstDir = dst.mediaDir(create=True)
    files = os.listdir(srcDir)
    # find media references
    used = {}
    for col in ("question", "answer"):
        txt = dst.s.column0("""
select %(c)s from cards where
%(c)s like '%%<img %%'
or %(c)s like '%%[sound:%%'""" % {'c': col})
        for entry in txt:
            for fname in mediaFiles(entry):
                used[fname] = True
    # copy only used media
    for file in files:
        if file not in used:
            continue
        srcfile = os.path.join(srcDir, file)
        dstfile = os.path.join(dstDir, file)
        if not os.path.exists(dstfile):
            try:
                shutil.copy2(srcfile, dstfile)
            except IOError, OSError:
                pass

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
