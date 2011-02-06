# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

"""\
Synchronisation
==============================

Support for keeping two decks synchronized. Both local syncing and syncing
over HTTP are supported.

Server implements the following calls:

getDecks(): return a list of deck names & modtimes
summary(lastSync): a list of all objects changed after lastSync
applyPayload(payload): apply any sent changes and return any changed remote
                       objects
finish(): save deck on server after payload applied and response received
createDeck(name): create a deck on the server

Full sync support is not documented yet.
"""
__docformat__ = 'restructuredtext'

import zlib, re, urllib, urllib2, socket, simplejson, time, shutil
import os, base64, httplib, sys, tempfile, httplib, types
from datetime import date
import anki, anki.deck, anki.cards
from anki.db import sqlite
from anki.errors import *
from anki.models import Model, FieldModel, CardModel
from anki.facts import Fact, Field
from anki.cards import Card
from anki.stats import Stats, globalStats
from anki.history import CardHistoryEntry
from anki.stats import globalStats
from anki.utils import ids2str, hexifyID, checksum
from anki.media import mediaFiles
from anki.lang import _
from hooks import runHook

if simplejson.__version__ < "1.7.3":
    raise Exception("SimpleJSON must be 1.7.3 or later.")

CHUNK_SIZE = 32768
MIME_BOUNDARY = "Anki-sync-boundary"
# live
SYNC_URL = "http://ankiweb.net/sync/"
SYNC_HOST = "ankiweb.net"; SYNC_PORT = 80
# testing
#SYNC_URL = "http://localhost:8001/sync/"
#SYNC_HOST = "localhost"; SYNC_PORT = 8001


KEYS = ("models", "facts", "cards", "media")

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

##########################################################################

class SyncTools(object):

    def __init__(self, deck=None):
        self.deck = deck
        self.diffs = {}
        self.serverExcludedTags = []
        self.timediff = 0

    # Control
    ##########################################################################

    def setServer(self, server):
        self.server = server

    def sync(self):
        "Sync two decks locally. Reimplement this for finer control."
        if not self.prepareSync(0):
            return
        sums = self.summaries()
        payload = self.genPayload(sums)
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
        # set lastSync to the lower of the two sides, and account for slow
        # clocks & assume it took up to 10 seconds for the reply to arrive
        self.deck.lastSync = min(l, r) - timediff - 10
        return True

    def summaries(self):
        return (self.summary(self.deck.lastSync),
                self.server.summary(self.deck.lastSync))

    def genPayload(self, summaries):
        (lsum, rsum) = summaries
        self.preSyncRefresh()
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
            payload['stats'] = self.bundleStats()
            payload['history'] = self.bundleHistory()
            payload['sources'] = self.bundleSources()
            # finally, set new lastSync and bundle the deck info
            payload['deck'] = self.bundleDeck()
        return payload

    def applyPayload(self, payload):
        reply = {}
        self.preSyncRefresh()
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
            reply['stats'] = self.bundleStats()
            reply['history'] = self.bundleHistory()
            reply['sources'] = self.bundleSources()
            # finally, set new lastSync and bundle the deck info
            reply['deck'] = self.bundleDeck()
        else:
            self.updateDeck(payload['deck'])
            self.updateStats(payload['stats'])
            self.updateHistory(payload['history'])
            if 'sources' in payload:
                self.updateSources(payload['sources'])
        self.postSyncRefresh()
        cardIds = [x[0] for x in payload['added-cards']]
        self.deck.updateCardTags(cardIds)
        # rebuild priorities on server
        self.rebuildPriorities(cardIds, self.serverExcludedTags)
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
            self.updateStats(reply['stats'])
            self.updateHistory(reply['history'])
            if 'sources' in reply:
                self.updateSources(reply['sources'])
        self.postSyncRefresh()
        # rebuild priorities on client
        cardIds = [x[0] for x in reply['added-cards']]
        self.deck.updateCardTags(cardIds)
        self.rebuildPriorities(cardIds)
        if self.missingFacts() != 0:
            raise Exception(
                "Facts missing after sync. Please run Tools>Advanced>Check DB.")

    def missingFacts(self):
        return self.deck.s.scalar(
            "select count() from cards where factId "+
                "not in (select id from facts)");

    def rebuildPriorities(self, cardIds, suspend=[]):
        self.deck.updateAllPriorities(partial=True, dirty=False)
        self.deck.updatePriorities(cardIds, suspend=suspend, dirty=False)

    def postSyncRefresh(self):
        "Flush changes to DB, and reload object associations."
        self.deck.s.flush()
        self.deck.s.refresh(self.deck)
        self.deck.currentModel

    def preSyncRefresh(self):
        # ensure global stats are available (queue may not be built)
        self.deck._globalStats = globalStats(self.deck)

    def payloadChanges(self, payload):
        h = {
            'lf': len(payload['added-facts']['facts']),
            'rf': len(payload['missing-facts']),
            'lc': len(payload['added-cards']),
            'rc': len(payload['missing-cards']),
            'lm': len(payload['added-models']),
            'rm': len(payload['missing-models']),
            }
        if self.localTime > self.remoteTime:
            h['ls'] = _('all')
            h['rs'] = 0
        else:
            h['ls'] = 0
            h['rs'] = _('all')
        return h

    def payloadChangeReport(self, payload):
        p = self.payloadChanges(payload)
        return _("""\
<table>
<tr><td><b>Added/Changed&nbsp;&nbsp;&nbsp;</b></td>
<td><b>Here&nbsp;&nbsp;&nbsp;</b></td><td><b>Server</b></td></tr>
<tr><td>Cards</td><td>%(lc)d</td><td>%(rc)d</td></tr>
<tr><td>Facts</td><td>%(lf)d</td><td>%(rf)d</td></tr>
<tr><td>Models</td><td>%(lm)d</td><td>%(rm)d</td></tr>
<tr><td>Stats</td><td>%(ls)s</td><td>%(rs)s</td></tr>
</table>""") % p

    # Summaries
    ##########################################################################

    def summary(self, lastSync):
        "Generate a full summary of modtimes for two-way syncing."
        # client may have selected an earlier sync time
        self.deck.lastSync = lastSync
        # ensure we're flushed first
        self.deck.s.flush()
        return {
            # cards
            "cards": self.realLists(self.deck.s.all(
            "select id, modified from cards where modified > :mod",
            mod=lastSync)),
            "delcards": self.realLists(self.deck.s.all(
            "select cardId, deletedTime from cardsDeleted "
            "where deletedTime > :mod", mod=lastSync)),
            # facts
            "facts": self.realLists(self.deck.s.all(
            "select id, modified from facts where modified > :mod",
            mod=lastSync)),
            "delfacts": self.realLists(self.deck.s.all(
            "select factId, deletedTime from factsDeleted "
            "where deletedTime > :mod", mod=lastSync)),
            # models
            "models": self.realLists(self.deck.s.all(
            "select id, modified from models where modified > :mod",
            mod=lastSync)),
            "delmodels": self.realLists(self.deck.s.all(
            "select modelId, deletedTime from modelsDeleted "
            "where deletedTime > :mod", mod=lastSync)),
            # media
            "media": self.realLists(self.deck.s.all(
            "select id, created from media where created > :mod",
            mod=lastSync)),
            "delmedia":  self.realLists(self.deck.s.all(
            "select mediaId, deletedTime from mediaDeleted "
            "where deletedTime > :mod", mod=lastSync)),
            }

    # Diffing
    ##########################################################################

    def diffSummary(self, localSummary, remoteSummary, key):
        # list of ids on both ends
        lexists = localSummary[key]
        ldeleted = localSummary["del"+key]
        rexists = remoteSummary[key]
        rdeleted = remoteSummary["del"+key]
        ldeletedIds = dict(ldeleted)
        rdeletedIds = dict(rdeleted)
        # to store the results
        locallyEdited = []
        locallyDeleted = []
        remotelyEdited = []
        remotelyDeleted = []
        # build a hash of all ids, with value (localMod, remoteMod).
        # deleted/nonexisting cards are marked with a modtime of None.
        ids = {}
        for (id, mod) in rexists:
            ids[id] = [None, mod]
        for (id, mod) in rdeleted:
            ids[id] = [None, None]
        for (id, mod) in lexists:
            if id in ids:
                ids[id][0] = mod
            else:
                ids[id] = [mod, None]
        for (id, mod) in ldeleted:
            if id in ids:
                ids[id][0] = None
            else:
                ids[id] = [None, None]
        # loop through the hash, determining differences
        for (id, (localMod, remoteMod)) in ids.items():
            if localMod and remoteMod:
                # changed/existing on both sides
                if localMod < remoteMod:
                    remotelyEdited.append(id)
                elif localMod > remoteMod:
                    locallyEdited.append(id)
            elif localMod and not remoteMod:
                # if it's missing on server or newer here, sync
                if (id not in rdeletedIds or
                    rdeletedIds[id] < localMod):
                    locallyEdited.append(id)
                else:
                    remotelyDeleted.append(id)
            elif remoteMod and not localMod:
                # if it's missing locally or newer there, sync
                if (id not in ldeletedIds or
                    ldeletedIds[id] < remoteMod):
                    remotelyEdited.append(id)
                else:
                    locallyDeleted.append(id)
            else:
                if id in ldeletedIds and id not in rdeletedIds:
                   locallyDeleted.append(id)
                elif id in rdeletedIds and id not in ldeletedIds:
                   remotelyDeleted.append(id)
        return (locallyEdited, locallyDeleted,
                remotelyEdited, remotelyDeleted)

    # Models
    ##########################################################################

    def getModels(self, ids, updateModified=False):
        return [self.bundleModel(id, updateModified) for id in ids]

    def bundleModel(self, id, updateModified):
        "Return a model representation suitable for transport."
        mod = self.deck.s.query(Model).get(id)
        # force load of lazy attributes
        mod.fieldModels; mod.cardModels
        m = self.dictFromObj(mod)
        m['fieldModels'] = [self.bundleFieldModel(fm) for fm in m['fieldModels']]
        m['cardModels'] = [self.bundleCardModel(fm) for fm in m['cardModels']]
        if updateModified:
            m['modified'] = time.time()
        return m

    def bundleFieldModel(self, fm):
        d = self.dictFromObj(fm)
        if 'model' in d: del d['model']
        return d

    def bundleCardModel(self, cm):
        d = self.dictFromObj(cm)
        if 'model' in d: del d['model']
        return d

    def updateModels(self, models):
        for model in models:
            local = self.getModel(model['id'])
            # avoid overwriting any existing card/field models
            fms = model['fieldModels']; del model['fieldModels']
            cms = model['cardModels']; del model['cardModels']
            self.applyDict(local, model)
            self.mergeFieldModels(local, fms)
            self.mergeCardModels(local, cms)
        self.deck.s.statement(
            "delete from modelsDeleted where modelId in %s" %
            ids2str([m['id'] for m in models]))

    def getModel(self, id, create=True):
        "Return a local model with same ID, or create."
        id = int(id)
        for l in self.deck.models:
            if l.id == id:
                return l
        if not create:
            return
        m = Model()
        self.deck.models.append(m)
        return m

    def mergeFieldModels(self, model, fms):
        ids = []
        for fm in fms:
            local = self.getFieldModel(model, fm)
            self.applyDict(local, fm)
            ids.append(fm['id'])
        for fm in model.fieldModels:
            if fm.id not in ids:
                self.deck.deleteFieldModel(model, fm)

    def getFieldModel(self, model, remote):
        id = int(remote['id'])
        for fm in model.fieldModels:
            if fm.id == id:
                return fm
        fm = FieldModel()
        model.addFieldModel(fm)
        return fm

    def mergeCardModels(self, model, cms):
        ids = []
        for cm in cms:
            local = self.getCardModel(model, cm)
            if not 'allowEmptyAnswer' in cm or cm['allowEmptyAnswer'] is None:
                cm['allowEmptyAnswer'] = True
            self.applyDict(local, cm)
            ids.append(cm['id'])
        for cm in model.cardModels:
            if cm.id not in ids:
                self.deck.deleteCardModel(model, cm)

    def getCardModel(self, model, remote):
        id = int(remote['id'])
        for cm in model.cardModels:
            if cm.id == id:
                return cm
        cm = CardModel()
        model.addCardModel(cm)
        return cm

    def deleteModels(self, ids):
        for id in ids:
            model = self.getModel(id, create=False)
            if model:
                self.deck.deleteModel(model)

    # Facts
    ##########################################################################

    def getFacts(self, ids, updateModified=False):
        if updateModified:
            modified = time.time()
        else:
            modified = "modified"
        factIds = ids2str(ids)
        return {
            'facts': self.realLists(self.deck.s.all("""
select id, modelId, created, %s, tags, spaceUntil, lastCardId from facts
where id in %s""" % (modified, factIds))),
            'fields': self.realLists(self.deck.s.all("""
select id, factId, fieldModelId, ordinal, value from fields
where factId in %s""" % factIds))
            }

    def updateFacts(self, factsdict):
        facts = factsdict['facts']
        fields = factsdict['fields']
        if not facts:
            return
        # update facts first
        dlist = [{
            'id': f[0],
            'modelId': f[1],
            'created': f[2],
            'modified': f[3],
            'tags': f[4],
            'spaceUntil': f[5] or "",
            'lastCardId': f[6]
            } for f in facts]
        self.deck.s.execute("""
insert or replace into facts
(id, modelId, created, modified, tags, spaceUntil, lastCardId)
values
(:id, :modelId, :created, :modified, :tags, :spaceUntil, :lastCardId)""", dlist)
        # now fields
        dlist = [{
            'id': f[0],
            'factId': f[1],
            'fieldModelId': f[2],
            'ordinal': f[3],
            'value': f[4]
            } for f in fields]
        # delete local fields since ids may have changed
        self.deck.s.execute(
            "delete from fields where factId in %s" %
            ids2str([f[0] for f in facts]))
        # then update
        self.deck.s.execute("""
insert into fields
(id, factId, fieldModelId, ordinal, value)
values
(:id, :factId, :fieldModelId, :ordinal, :value)""", dlist)
        self.deck.s.statement(
            "delete from factsDeleted where factId in %s" %
            ids2str([f[0] for f in facts]))

    def deleteFacts(self, ids):
        self.deck.deleteFacts(ids)

    # Cards
    ##########################################################################

    def getCards(self, ids):
        return self.realLists(self.deck.s.all("""
select id, factId, cardModelId, created, modified, tags, ordinal,
priority, interval, lastInterval, due, lastDue, factor,
firstAnswered, reps, successive, averageTime, reviewTime, youngEase0,
youngEase1, youngEase2, youngEase3, youngEase4, matureEase0,
matureEase1, matureEase2, matureEase3, matureEase4, yesCount, noCount,
question, answer, lastFactor, spaceUntil, type, combinedDue, relativeDelay
from cards where id in %s""" % ids2str(ids)))

    def updateCards(self, cards):
        if not cards:
            return
        # FIXME: older clients won't send this, so this is temp compat code
        def getType(row):
            if len(row) > 36:
                return row[36]
            if row[15]:
                return 1
            elif row[14]:
                return 0
            return 2
        dlist = [{'id': c[0],
                  'factId': c[1],
                  'cardModelId': c[2],
                  'created': c[3],
                  'modified': c[4],
                  'tags': c[5],
                  'ordinal': c[6],
                  'priority': c[7],
                  'interval': c[8],
                  'lastInterval': c[9],
                  'due': c[10],
                  'lastDue': c[11],
                  'factor': c[12],
                  'firstAnswered': c[13],
                  'reps': c[14],
                  'successive': c[15],
                  'averageTime': c[16],
                  'reviewTime': c[17],
                  'youngEase0': c[18],
                  'youngEase1': c[19],
                  'youngEase2': c[20],
                  'youngEase3': c[21],
                  'youngEase4': c[22],
                  'matureEase0': c[23],
                  'matureEase1': c[24],
                  'matureEase2': c[25],
                  'matureEase3': c[26],
                  'matureEase4': c[27],
                  'yesCount': c[28],
                  'noCount': c[29],
                  'question': c[30],
                  'answer': c[31],
                  'lastFactor': c[32],
                  'spaceUntil': c[33],
                  'type': c[34],
                  'combinedDue': c[35],
                  'rd': getType(c)
                  } for c in cards]
        self.deck.s.execute("""
insert or replace into cards
(id, factId, cardModelId, created, modified, tags, ordinal,
priority, interval, lastInterval, due, lastDue, factor,
firstAnswered, reps, successive, averageTime, reviewTime, youngEase0,
youngEase1, youngEase2, youngEase3, youngEase4, matureEase0,
matureEase1, matureEase2, matureEase3, matureEase4, yesCount, noCount,
question, answer, lastFactor, spaceUntil, type, combinedDue,
relativeDelay, isDue)
values
(:id, :factId, :cardModelId, :created, :modified, :tags, :ordinal,
:priority, :interval, :lastInterval, :due, :lastDue, :factor,
:firstAnswered, :reps, :successive, :averageTime, :reviewTime, :youngEase0,
:youngEase1, :youngEase2, :youngEase3, :youngEase4, :matureEase0,
:matureEase1, :matureEase2, :matureEase3, :matureEase4, :yesCount,
:noCount, :question, :answer, :lastFactor, :spaceUntil,
:type, :combinedDue, :rd, 0)""", dlist)
        self.deck.s.statement(
            "delete from cardsDeleted where cardId in %s" %
            ids2str([c[0] for c in cards]))

    def deleteCards(self, ids):
        self.deck.deleteCards(ids)

    # Deck/stats/history
    ##########################################################################

    def bundleDeck(self):
        # ensure modified is not greater than server time
        if getattr(self, "server", None) and getattr(
            self.server, "timestamp", None):
            self.deck.modified = min(self.deck.modified,self.server.timestamp)
        # and ensure lastSync is greater than modified
        self.deck.lastSync = max(time.time(), self.deck.modified+1)
        d = self.dictFromObj(self.deck)
        del d['Session']
        del d['engine']
        del d['s']
        del d['path']
        del d['syncName']
        del d['version']
        if 'newQueue' in d:
            del d['newQueue']
            del d['failedQueue']
            del d['revQueue']
        # these may be deleted before bundling
        if 'css' in d: del d['css']
        if 'models' in d: del d['models']
        if 'currentModel' in d: del d['currentModel']
        keys = d.keys()
        for k in keys:
            if isinstance(d[k], types.MethodType):
                del d[k]
        d['meta'] = self.realLists(self.deck.s.all("select * from deckVars"))
        return d

    def updateDeck(self, deck):
        if 'meta' in deck:
            meta = deck['meta']
            for (k,v) in meta:
                self.deck.s.statement("""
insert or replace into deckVars
(key, value) values (:k, :v)""", k=k, v=v)
            del deck['meta']
        self.applyDict(self.deck, deck)

    def bundleStats(self):
        def bundleStat(stat):
            s = self.dictFromObj(stat)
            s['day'] = s['day'].toordinal()
            del s['id']
            return s
        lastDay = date.fromtimestamp(max(0, self.deck.lastSync - 60*60*24))
        ids = self.deck.s.column0(
            "select id from stats where type = 1 and day >= :day", day=lastDay)
        stat = Stats()
        def statFromId(id):
            stat.fromDB(self.deck.s, id)
            return stat
        stats = {
            'global': bundleStat(self.deck._globalStats),
            'daily': [bundleStat(statFromId(id)) for id in ids],
            }
        return stats

    def updateStats(self, stats):
        stats['global']['day'] = date.fromordinal(stats['global']['day'])
        self.applyDict(self.deck._globalStats, stats['global'])
        self.deck._globalStats.toDB(self.deck.s)
        for record in stats['daily']:
            record['day'] = date.fromordinal(record['day'])
            stat = Stats()
            id = self.deck.s.scalar("select id from stats where "
                                    "type = :type and day = :day",
                                    type=1, day=record['day'])
            if id:
                stat.fromDB(self.deck.s, id)
            else:
                stat.create(self.deck.s, 1, record['day'])
            self.applyDict(stat, record)
            stat.toDB(self.deck.s)

    def bundleHistory(self):
        return self.realLists(self.deck.s.all("""
select cardId, time, lastInterval, nextInterval, ease, delay,
lastFactor, nextFactor, reps, thinkingTime, yesCount, noCount
from reviewHistory where time > :ls""",
            ls=self.deck.lastSync))

    def updateHistory(self, history):
        dlist = [{'cardId': h[0],
                  'time': h[1],
                  'lastInterval': h[2],
                  'nextInterval': h[3],
                  'ease': h[4],
                  'delay': h[5],
                  'lastFactor': h[6],
                  'nextFactor': h[7],
                  'reps': h[8],
                  'thinkingTime': h[9],
                  'yesCount': h[10],
                  'noCount': h[11]} for h in history]
        if not dlist:
            return
        self.deck.s.statements("""
insert or ignore into reviewHistory
(cardId, time, lastInterval, nextInterval, ease, delay,
lastFactor, nextFactor, reps, thinkingTime, yesCount, noCount)
values
(:cardId, :time, :lastInterval, :nextInterval, :ease, :delay,
:lastFactor, :nextFactor, :reps, :thinkingTime, :yesCount, :noCount)""",
                         dlist)

    def bundleSources(self):
        return self.realLists(self.deck.s.all("select * from sources"))

    def updateSources(self, sources):
        for s in sources:
            self.deck.s.statement("""
insert or replace into sources values
(:id, :name, :created, :lastSync, :syncPeriod)""",
                                  id=s[0],
                                  name=s[1],
                                  created=s[2],
                                  lastSync=s[3],
                                  syncPeriod=s[4])

    # Media metadata
    ##########################################################################

    def getMedia(self, ids):
        return [tuple(row) for row in self.deck.s.all("""
select id, filename, size, created, originalPath, description
from media where id in %s""" % ids2str(ids))]

    def updateMedia(self, media):
        meta = []
        for m in media:
            # build meta
            meta.append({
                'id': m[0],
                'filename': m[1],
                'size': m[2],
                'created': m[3],
                'originalPath': m[4],
                'description': m[5]})
        # apply metadata
        if meta:
            self.deck.s.statements("""
insert or replace into media (id, filename, size, created,
originalPath, description)
values (:id, :filename, :size, :created, :originalPath,
:description)""", meta)
        self.deck.s.statement(
            "delete from mediaDeleted where mediaId in %s" %
            ids2str([m[0] for m in media]))

    def deleteMedia(self, ids):
        sids = ids2str(ids)
        files = self.deck.s.column0(
            "select filename from media where id in %s" % sids)
        self.deck.s.statement("""
insert into mediaDeleted
select id, :now from media
where media.id in %s""" % sids, now=time.time())
        self.deck.s.execute(
            "delete from media where id in %s" % sids)

    # One-way syncing (sharing)
    ##########################################################################

    def syncOneWay(self, lastSync):
        "Sync two decks one way."
        payload = self.server.genOneWayPayload(lastSync)
        self.applyOneWayPayload(payload)
        self.deck.reset()

    def syncOneWayDeckName(self):
        return (self.deck.s.scalar("select name from sources where id = :id",
                                   id=self.server.deckName) or
                hexifyID(int(self.server.deckName)))

    def prepareOneWaySync(self):
        "Sync setup. True if sync needed. Not used for local sync."
        srcID = self.server.deckName
        (lastSync, syncPeriod) = self.deck.s.first(
            "select lastSync, syncPeriod from sources where id = :id", id=srcID)
        if self.server.modified() <= lastSync:
            return
        self.deck.lastSync = lastSync
        return True

    def genOneWayPayload(self, lastSync):
        "Bundle all added or changed objects since the last sync."
        p = {}
        # facts
        factIds = self.deck.s.column0(
            "select id from facts where modified > :l", l=lastSync)
        p['facts'] = self.getFacts(factIds, updateModified=True)
        # models
        modelIds = self.deck.s.column0(
            "select id from models where modified > :l", l=lastSync)
        p['models'] = self.getModels(modelIds, updateModified=True)
        # media
        mediaIds = self.deck.s.column0(
            "select id from media where created > :l", l=lastSync)
        p['media'] = self.getMedia(mediaIds)
        # cards
        cardIds = self.deck.s.column0(
            "select id from cards where modified > :l", l=lastSync)
        p['cards'] = self.realLists(self.getOneWayCards(cardIds))
        return p

    def applyOneWayPayload(self, payload):
        keys = [k for k in KEYS if k != "cards"]
        # model, facts, media
        for key in keys:
            self.updateObjsFromKey(payload[key], key)
        # models need their source tagged
        for m in payload["models"]:
            self.deck.s.statement("update models set source = :s "
                                  "where id = :id",
                                  s=self.server.deckName,
                                  id=m['id'])
        # cards last, handled differently
        t = time.time()
        try:
            self.updateOneWayCards(payload['cards'])
        except KeyError:
            sys.stderr.write("Subscribed to a broken deck. "
                             "Try removing your deck subscriptions.")
            t = 0
        # update sync time
        self.deck.s.statement(
            "update sources set lastSync = :t where id = :id",
            id=self.server.deckName, t=t)
        self.deck.modified = time.time()

    def getOneWayCards(self, ids):
        "The minimum information necessary to generate one way cards."
        return self.deck.s.all(
            "select id, factId, cardModelId, ordinal, created from cards "
            "where id in %s" % ids2str(ids))

    def updateOneWayCards(self, cards):
        if not cards:
            return
        t = time.time()
        dlist = [{'id': c[0], 'factId': c[1], 'cardModelId': c[2],
                  'ordinal': c[3], 'created': c[4], 't': t} for c in cards]
        # add any missing cards
        self.deck.s.statements("""
insert or ignore into cards
(id, factId, cardModelId, created, modified, tags, ordinal,
priority, interval, lastInterval, due, lastDue, factor,
firstAnswered, reps, successive, averageTime, reviewTime, youngEase0,
youngEase1, youngEase2, youngEase3, youngEase4, matureEase0,
matureEase1, matureEase2, matureEase3, matureEase4, yesCount, noCount,
question, answer, lastFactor, spaceUntil, isDue, type, combinedDue,
relativeDelay)
values
(:id, :factId, :cardModelId, :created, :t, "", :ordinal,
1, 0, 0, :created, 0, 2.5,
0, 0, 0, 0, 0, 0,
0, 0, 0, 0, 0,
0, 0, 0, 0, 0,
0, "", "", 2.5, 0, 0, 2, :t, 0)""", dlist)
        # update q/as
        models = dict(self.deck.s.all("""
select cards.id, models.id
from cards, facts, models
where cards.factId = facts.id
and facts.modelId = models.id
and cards.id in %s""" % ids2str([c[0] for c in cards])))
        self.deck.s.flush()
        self.deck.updateCardQACache(
            [(c[0], c[2], c[1], models[c[0]]) for c in cards])
        # rebuild priorities on client
        cardIds = [c[0] for c in cards]
        self.deck.updateCardTags(cardIds)
        self.rebuildPriorities(cardIds)

    # Tools
    ##########################################################################

    def modified(self):
        return self.deck.modified

    def _lastSync(self):
        return self.deck.lastSync

    def unstuff(self, data):
        "Uncompress and convert to unicode."
        return simplejson.loads(unicode(zlib.decompress(data), "utf8"))

    def stuff(self, data):
        "Convert into UTF-8 and compress."
        return zlib.compress(simplejson.dumps(data))

    def dictFromObj(self, obj):
        "Return a dict representing OBJ without any hidden db fields."
        return dict([(k,v) for (k,v) in obj.__dict__.items()
                     if not k.startswith("_")])

    def applyDict(self, obj, dict):
        "Apply each element in DICT to OBJ in a way the ORM notices."
        for (k,v) in dict.items():
            setattr(obj, k, v)

    def realLists(self, result):
        "Convert an SQLAlchemy response into a list of real lists."
        return [list(x) for x in result]

    def getObjsFromKey(self, ids, key):
        return getattr(self, "get" + key.capitalize())(ids)

    def deleteObjsFromKey(self, ids, key):
        return getattr(self, "delete" + key.capitalize())(ids)

    def updateObjsFromKey(self, ids, key):
        return getattr(self, "update" + key.capitalize())(ids)

    # Full sync
    ##########################################################################

    def needFullSync(self, sums):
        if self.deck.lastSync <= 0:
            return True
        for sum in sums:
            for l in sum.values():
                if len(l) > 1000:
                    return True
        if self.deck.s.scalar(
            "select count() from reviewHistory where time > :ls",
            ls=self.deck.lastSync) > 1000:
            return True
        lastDay = date.fromtimestamp(max(0, self.deck.lastSync - 60*60*24))
        if self.deck.s.scalar(
            "select count() from stats where day >= :day",
            day=lastDay) > 100:
            return True
        return False

    def prepareFullSync(self):
        t = time.time()
        # ensure modified is not greater than server time
        self.deck.modified = min(self.deck.modified, self.server.timestamp)
        self.deck.s.commit()
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
            (fd, name) = tempfile.mkstemp(prefix="anki")
            tmp = open(name, "w+b")
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
                os.close(fd)
                os.unlink(name)
        finally:
            runHook("fullSyncFinished")

    def fullSyncFromServer(self, fields, path):
        try:
            runHook("fullSyncStarted", 0)
            fields = urllib.urlencode(fields)
            src = urllib.urlopen(SYNC_URL + "fulldown", fields)
            (fd, tmpname) = tempfile.mkstemp(dir=os.path.dirname(path),
                                             prefix="fullsync")
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

# Local syncing
##########################################################################


class SyncServer(SyncTools):

    def __init__(self, deck=None):
        SyncTools.__init__(self, deck)

class SyncClient(SyncTools):

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
