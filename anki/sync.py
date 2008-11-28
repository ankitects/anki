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
createDeck(name): create a deck on the server

"""
__docformat__ = 'restructuredtext'

import zlib, re, urllib, urllib2, socket, simplejson, time
import os, base64, httplib, sys
from datetime import date
import anki, anki.deck, anki.cards
from anki.errors import *
from anki.models import Model, FieldModel, CardModel
from anki.facts import Fact, Field
from anki.cards import Card
from anki.stats import Stats, globalStats
from anki.history import CardHistoryEntry
from anki.stats import globalStats
from anki.media import checksum
from anki.utils import ids2str, hexifyID
from anki.lang import _

if simplejson.__version__ < "1.7.3":
    raise "SimpleJSON must be 1.7.3 or later."

##########################################################################

class SyncTools(object):

    def __init__(self, deck=None):
        self.deck = deck
        self.diffs = {}
        self.serverExcludedTags = []
        self.mediaSyncPending = False

    # Control
    ##########################################################################

    def setServer(self, server):
        self.server = server

    def sync(self):
        "Sync two decks locally. Reimplement this for finer control."
        if not self.prepareSync():
            return
        sums = self.summaries()
        payload = self.genPayload(sums)
        res = self.server.applyPayload(payload)
        self.applyPayloadReply(res)
        if self.mediaSyncPending:
            bulkClient = BulkMediaSyncer(self.deck)
            bulkServer = BulkMediaSyncer(self.server.deck)
            bulkClient.server = bulkServer
            bulkClient.sync()

    def prepareSync(self):
        "Sync setup. True if sync needed."
        self.localTime = self.modified()
        self.remoteTime = self.server.modified()
        if self.localTime == self.remoteTime:
            return False
        l = self._lastSync(); r = self.server._lastSync()
        if l != r:
            self.lastSync = min(l, r) - 600
        else:
            self.lastSync = l
        return True

    def summaries(self):
        return (self.summary(self.lastSync),
                self.server.summary(self.lastSync))

    def genPayload(self, summaries):
        (lsum, rsum) = summaries
        self.preSyncRefresh()
        payload = {}
        # first, handle models, facts and cards
        for key in self.keys():
            diff = self.diffSummary(lsum, rsum, key)
            payload["added-" + key] = self.getObjsFromKey(diff[0], key)
            payload["deleted-" + key] = diff[1]
            payload["missing-" + key] = diff[2]
            self.deleteObjsFromKey(diff[3], key)
        # handle the remainder
        if self.localTime > self.remoteTime:
            payload['deck'] = self.bundleDeck()
            payload['stats'] = self.bundleStats()
            payload['history'] = self.bundleHistory()
            payload['sources'] = self.bundleSources()
            self.deck.lastSync = self.deck.modified
        return payload

    def applyPayload(self, payload):
        reply = {}
        self.preSyncRefresh()
        # model, facts and cards
        for key in self.keys():
            # send back any requested
            reply['added-' + key] = self.getObjsFromKey(
                payload['missing-' + key], key)
            self.updateObjsFromKey(payload['added-' + key], key)
            self.deleteObjsFromKey(payload['deleted-' + key], key)
        # send back deck-related stuff if it wasn't sent to us
        if not 'deck' in payload:
            reply['deck'] = self.bundleDeck()
            reply['stats'] = self.bundleStats()
            reply['history'] = self.bundleHistory()
            reply['sources'] = self.bundleSources()
            self.deck.lastSync = self.deck.modified
        else:
            self.updateDeck(payload['deck'])
            self.updateStats(payload['stats'])
            self.updateHistory(payload['history'])
            if 'sources' in payload:
                self.updateSources(payload['sources'])
        self.postSyncRefresh()
        # rebuild priorities on server
        cardIds = [x[0] for x in payload['added-cards']]
        self.rebuildPriorities(cardIds, self.serverExcludedTags)
        # rebuild due counts
        self.deck.rebuildCounts(full=False)
        return reply

    def applyPayloadReply(self, reply):
        # model, facts and cards
        for key in self.keys():
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
        self.rebuildPriorities(cardIds)
        # rebuild due counts
        self.deck.rebuildCounts(full=False)

    def rebuildPriorities(self, cardIds, extraExcludes=[]):
        where = "and cards.id in %s" % ids2str(cardIds)
        self.deck.updateAllPriorities(extraExcludes=extraExcludes,
                                      where=where)

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
        if self.mediaSupported():
            h['lM'] = len(payload['added-media'])
            h['rM'] = len(payload['missing-media'])
        return h

    def payloadChangeReport(self, payload):
        p = self.payloadChanges(payload)
        if self.mediaSupported():
            p['media'] = (
                "<tr><td>Media</td><td>%(lM)d</td><td>%(rM)d</td></tr>" % p)
        else:
            p['media'] = (
                "<tr><td>Media</td><td>off</td><td>off</td></tr>" % p)
        return _("""\
<table width=500>
<tr><td><b>Added/Changed</b></td><td><b>Here</b></td><td><b>Server</b></td></tr>
<tr><td>Cards</td><td>%(lc)d</td><td>%(rc)d</td></tr>
<tr><td>Facts</td><td>%(lf)d</td><td>%(rf)d</td></tr>
<tr><td>Models</td><td>%(lm)d</td><td>%(rm)d</td></tr>
%(media)s
</table>""") % p

    # Summaries
    ##########################################################################

    def summary(self, lastSync):
        "Generate a full summary of modtimes for two-way syncing."
        # ensure we're flushed first
        self.deck.s.flush()
        return {
            # cards
            "cards": self.realTuples(self.deck.s.all(
            "select id, modified from cards where modified > :mod",
            mod=lastSync)),
            "delcards": self.realTuples(self.deck.s.all(
            "select cardId, deletedTime from cardsDeleted "
            "where deletedTime > :mod", mod=lastSync)),
            # facts
            "facts": self.realTuples(self.deck.s.all(
            "select id, modified from facts where modified > :mod",
            mod=lastSync)),
            "delfacts": self.realTuples(self.deck.s.all(
            "select factId, deletedTime from factsDeleted "
            "where deletedTime > :mod", mod=lastSync)),
            # models
            "models": self.realTuples(self.deck.s.all(
            "select id, modified from models where modified > :mod",
            mod=lastSync)),
            "delmodels": self.realTuples(self.deck.s.all(
            "select modelId, deletedTime from modelsDeleted "
            "where deletedTime > :mod", mod=lastSync)),
            # media
            "media": self.realTuples(self.deck.s.all(
            "select id, created from media where created > :mod",
            mod=lastSync)),
            "delmedia":  self.realTuples(self.deck.s.all(
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
        # force load of lazy attributes
        mod = self.deck.s.query(Model).get(id)
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
        for fm in model.fieldModels:
            if fm.id == remote['id']:
                return fm
        fm = FieldModel()
        model.addFieldModel(fm)
        return fm

    def mergeCardModels(self, model, cms):
        ids = []
        for cm in cms:
            local = self.getCardModel(model, cm)
            self.applyDict(local, cm)
            ids.append(cm['id'])
        for cm in model.cardModels:
            if cm.id not in ids:
                self.deck.deleteCardModel(model, cm)

    def getCardModel(self, model, remote):
        for cm in model.cardModels:
            if cm.id == remote['id']:
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
            'facts': self.realTuples(self.deck.s.all("""
select id, modelId, created, %s, tags, spaceUntil, lastCardId from facts
where id in %s""" % (modified, factIds))),
            'fields': self.realTuples(self.deck.s.all("""
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
            'spaceUntil': f[5],
            'lastCardId': f[6]
            } for f in facts]
        self.deck.factCount += (len(facts) - self.deck.s.scalar(
            "select count(*) from facts where id in %s" %
            ids2str([f[0] for f in facts])))
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
insert or replace into fields
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
        return self.realTuples(self.deck.s.all("""
select id, factId, cardModelId, created, modified, tags, ordinal,
priority, interval, lastInterval, due, lastDue, factor,
firstAnswered, reps, successive, averageTime, reviewTime, youngEase0,
youngEase1, youngEase2, youngEase3, youngEase4, matureEase0,
matureEase1, matureEase2, matureEase3, matureEase4, yesCount, noCount,
question, answer, lastFactor, spaceUntil, type, combinedDue
from cards where id in %s""" % ids2str(ids)))

    def updateCards(self, cards):
        if not cards:
            return
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
                  } for c in cards]
        self.deck.cardCount += (len(cards) - self.deck.s.scalar(
            "select count(*) from cards where id in %s" %
            ids2str([c[0] for c in cards])))
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
:type, :combinedDue, 0, 0)""", dlist)
        self.deck.s.statement(
            "delete from cardsDeleted where cardId in %s" %
            ids2str([c[0] for c in cards]))


    def deleteCards(self, ids):
        self.deck.deleteCards(ids)

    # Deck/stats/history
    ##########################################################################

    def bundleDeck(self):
        d = self.dictFromObj(self.deck)
        del d['Session']
        del d['engine']
        del d['s']
        del d['path']
        del d['syncName']
        del d['version']
        # these may be deleted before bundling
        if 'models' in d: del d['models']
        if 'currentModel' in d: del d['currentModel']
        return d

    def updateDeck(self, deck):
        self.applyDict(self.deck, deck)
        self.deck.lastSync = self.deck.modified

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
        return self.realTuples(self.deck.s.all("""
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
insert into reviewHistory
(cardId, time, lastInterval, nextInterval, ease, delay,
lastFactor, nextFactor, reps, thinkingTime, yesCount, noCount)
values
(:cardId, :time, :lastInterval, :nextInterval, :ease, :delay,
:lastFactor, :nextFactor, :reps, :thinkingTime, :yesCount, :noCount)""",
                         dlist)

    def bundleSources(self):
        return self.realTuples(self.deck.s.all("select * from sources"))

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

    # Media
    ##########################################################################

    def getMedia(self, ids, updateCreated=False):
        size = self.deck.s.scalar(
            "select sum(size) from media where id in %s" %
            ids2str(ids))
        if ids:
            self.mediaSyncPending = True
        if updateCreated:
            created = time.time()
        else:
            created = "created"
        return [tuple(row) for row in self.deck.s.all("""
select id, filename, size, %s, originalPath, description
from media where id in %s""" % (created, ids2str(ids)))]

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
            self.mediaSyncPending = True
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
        for file in files:
            self.deleteMediaFile(file)

    # the following routines are reimplemented by the anki server so that
    # media can be shared and accounted

    def deleteMediaFile(self, file):
        try:
            os.unlink(self.mediaPath(file))
        except OSError:
            pass

    def mediaPath(self, path):
        "Return the path to store media in. Defaults to the deck media dir."
        return os.path.join(self.deck.mediaDir(create=True), path)

    # One-way syncing (sharing)
    ##########################################################################

    def syncOneWay(self, lastSync):
        "Sync two decks one way."
        payload = self.server.genOneWayPayload(lastSync)
        self.applyOneWayPayload(payload)

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
        self.lastSync = lastSync
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
        if self.mediaSupported():
            mediaIds = self.deck.s.column0(
                "select id from media where created > :l", l=lastSync)
            p['media'] = self.getMedia(mediaIds, updateCreated=True)
            if p['media']:
                self.mediaSyncPending = True
        # cards
        cardIds = self.deck.s.column0(
            "select id from cards where modified > :l", l=lastSync)
        p['cards'] = self.realTuples(self.getOneWayCards(cardIds))
        return p

    def applyOneWayPayload(self, payload):
        keys = [k for k in self.keys() if k != "cards"]
        # model, facts, media
        for key in keys:
            self.updateObjsFromKey(payload[key], key)
        # models need their source tagged
        for m in payload["models"]:
            self.deck.s.statement("update models set source = :s "
                                  "where id = :id",
                                  s=self.server.deckName,
                                  id=m['id'])
        # if media arrived, we'll need to download the data
        self.mediaSyncPending = (self.mediaSyncPending or
                                 self.mediaSupported() and payload['media'])
        # cards last, handled differently
        t = time.time()
        try:
            self.updateOneWayCards(payload['cards'])
        except KeyError:
            sys.stderr.write("Error in one way sync\n")
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
        self.deck.cardCount += (len(cards) - self.deck.s.scalar(
            "select count(*) from cards where id in %s" %
            ids2str([c[0] for c in cards])))
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

    # Tools
    ##########################################################################

    def modified(self):
        return self.deck.modified

    def _lastSync(self):
        return self.deck.lastSync

    def unstuff(self, data):
        "Uncompress and convert to unicode."
        return simplejson.loads(zlib.decompress(data))

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

    def realTuples(self, result):
        "Convert an SQLAlchemy response into a list of real tuples."
        return [tuple(x) for x in result]

    def getObjsFromKey(self, ids, key):
        return getattr(self, "get" + key.capitalize())(ids)

    def deleteObjsFromKey(self, ids, key):
        return getattr(self, "delete" + key.capitalize())(ids)

    def updateObjsFromKey(self, ids, key):
        return getattr(self, "update" + key.capitalize())(ids)

    def keys(self):
        if self.mediaSupported():
            return standardKeys + ("media",)
        return standardKeys

# Local syncing
##########################################################################

standardKeys = ("models", "facts", "cards")

class SyncServer(SyncTools):

    def __init__(self, deck=None):
        SyncTools.__init__(self, deck)
        self._mediaSupported = True

    def mediaSupported(self):
        return self._mediaSupported

class SyncClient(SyncTools):

    def mediaSupported(self):
        return self.server._mediaSupported

# HTTP proxy: act as a server and direct requests to the real server
##########################################################################

class HttpSyncServerProxy(SyncServer):

    def __init__(self, user, passwd):
        SyncServer.__init__(self)
        self.decks = None
        self.deckName = None
        self.username = user
        self.password = passwd
        self.syncURL="http://anki.ichi2.net/sync/"
        #self.syncURL="http://localhost:8001/sync/"
        self.protocolVersion = 4
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
            self._mediaSupported = d['mediaSupported']
            self.decks = d['decks']
            self.timestamp = d['timestamp']

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

    def runCmd(self, action, **args):
        data = {"p": self.password,
                "u": self.username}
        if self.deckName:
            data['d'] = self.deckName.encode("utf-8")
        else:
            data['d'] = None
        data.update(args)
        data = urllib.urlencode(data)
        try:
            f = urllib2.urlopen(self.syncURL + action, data)
        except (urllib2.URLError, socket.error, socket.timeout,
                httplib.BadStatusLine):
            raise SyncError(type="noResponse")
        ret = f.read()
        if not ret:
            raise SyncError(type="noResponse")
        return self.unstuff(ret)

# HTTP server: respond to proxy requests and return data
##########################################################################

class HttpSyncServer(SyncServer):
    def __init__(self):
        SyncServer.__init__(self)
        self.decks = {}
        self.deck = None

    def summary(self, lastSync):
        return self.stuff(SyncServer.summary(
            self, self.unstuff(lastSync)))

    def applyPayload(self, payload):
        return self.stuff(SyncServer.applyPayload(self,
            self.unstuff(payload)))

    def genOneWayPayload(self, payload):
        return self.stuff(SyncServer.genOneWayPayload(self,
            self.unstuff(payload)))

    def getDecks(self, libanki, client, sources, pversion):
        return self.stuff({
            "status": "OK",
            "decks": self.decks,
            "mediaSupported": self.mediaSupported(),
            "timestamp": time.time(),
            })

    def createDeck(self, name):
        "Create a deck on the server. Not implemented."
        return self.stuff("OK")

# Bulk uploader/downloader
##########################################################################

class BulkMediaSyncer(SyncTools):

    def __init__(self, deck):
        self.deck = deck
        self.server = None
        self.oneWay = False

    def missingMedia(self):
        fnames = self.deck.s.column0(
            "select filename from media")
        return [f for f in fnames if
                not os.path.exists(self.mediaPath(f))]

    def progressCallback(self, type, count, total, fname):
        return

    def sync(self):
        # upload to server
        if not self.oneWay:
            missing = self.server.missingMedia()
            total = len(missing)
            for n in range(total):
                fname = missing[n]
                data = self.getFile(fname)
                self.progressCallback('up', n, total, fname)
                if data:
                    self.server.addFile(fname, data)
                n += 1
        # download from server
        missing = self.missingMedia()
        total = len(missing)
        for n in range(total):
            fname = missing[n]
            data = self.server.getFile(fname)
            self.progressCallback('down', n, total, fname)
            if data:
                self.addFile(fname, data)
            n += 1

    def getFile(self, fname):
        try:
            return open(self.mediaPath(fname), "rb").read()
        except (IOError, OSError):
            return None

    def addFile(self, fname, data):
        path = self.mediaPath(fname)
        assert not os.path.exists(path)
        size = self.deck.s.scalar(
            "select size from media where filename = :f",
            f=fname)
        assert size
        assert size == len(data)
        assert checksum(data) == os.path.splitext(fname)[0]
        open(path, "wb").write(data)

class BulkMediaSyncerProxy(HttpSyncServerProxy):

    def missingMedia(self):
        return self.unstuff(self.runCmd("missingMedia"))

    def _splitMedia(self, fname):
        return os.path.join(fname[0:1], fname[0:2], fname)

    def _relativeMediaPath(self, fname):
        return "http://ankimedia.ichi2.net/%s" % (
            self._splitMedia(fname))

    def getFile(self, fname):
        try:
            f = urllib2.urlopen(self._relativeMediaPath(fname))
        except (urllib2.URLError, socket.error, socket.timeout):
            return ""
        ret = f.read()
        if not ret:
            return ""
        return ret

    def addFile(self, fname, data):
        return self.runCmd("addFile", fname=fname, data=data)

    def runCmd(self, action, **args):
        data = {"p": self.password,
                "u": self.username,
                "d": self.deckName.encode("utf-8")}
        data.update(args)
        data = urllib.urlencode(data)
        try:
            f = urllib2.urlopen(self.syncURL + action, data)
        except (urllib2.URLError, socket.error, socket.timeout,
                httplib.BadStatusLine):
            raise SyncError(type="noResponse")
        ret = f.read()
        if not ret:
            raise SyncError(type="noResponse")
        return ret
