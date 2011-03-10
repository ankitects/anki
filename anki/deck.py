# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

import tempfile, time, os, random, sys, re, stat, shutil
import types, traceback, simplejson, datetime
from operator import itemgetter
from itertools import groupby

from anki.lang import _, ngettext
from anki.utils import parseTags, tidyHTML, ids2str, hexifyID, \
     canonifyTags, joinTags, addTags, deleteTags, checksum, fieldChecksum, \
     stripHTML, intTime

from anki.fonts import toPlatformFont
from anki.hooks import runHook, hookEmpty, runFilter

from anki.sched import Scheduler
from anki.media import MediaRegistry

from anki.consts import *
import anki.latex # sets up hook

import anki.cards, anki.facts, anki.models, anki.template

# Settings related to queue building. These may be loaded without the rest of
# the config to check due counts faster on mobile clients.
defaultQconf = {
    'revGroups': [],
    'newGroups': [],
    'newPerDay': 20,
    'newToday': [0, 0], # currentDay, count
    'newTodayOrder': NEW_TODAY_ORD,
    'newCardOrder': 1,
    'newCardSpacing': NEW_CARDS_DISTRIBUTE,
    'revCardOrder': REV_CARDS_RANDOM,
}

# scheduling and other options
defaultConf = {
    'collapseTime': 600,
    'sessionRepLimit': 0,
    'sessionTimeLimit': 600,
    'currentModelId': None,
    'currentGroupId': 1,
    'nextFactPos': 1,
    'mediaURL': "",
    'latexPre': """\
\\documentclass[12pt]{article}
\\special{papersize=3in,5in}
\\usepackage[utf8]{inputenc}
\\usepackage{amssymb,amsmath}
\\pagestyle{empty}
\\setlength{\\parindent}{0in}
\\begin{document}
""",
    'latexPost': "\\end{document}",
}

# this is initialized by storage.Deck
class _Deck(object):

    # fixme: make configurable?
    factorFour = 1.3

    def __init__(self, db):
        self.db = db
        self.path = db._path
        self.load()
        if self.utcOffset == -2:
            # shared deck; reset timezone and creation date
            self.utcOffset = time.timezone + 60*60*4
            self.crt = intTime()
        self.undoEnabled = False
        self.sessionStartReps = 0
        self.sessionStartTime = 0
        self.lastSessionStart = 0
        # counter for reps since deck open
        self.reps = 0
        self.sched = Scheduler(self)
        self.media = MediaRegistry(self)

    # DB-related
    ##########################################################################

    def load(self):
        (self.crt,
         self.mod,
         self.schema,
         self.syncName,
         self.lastSync,
         self.utcOffset,
         self.qconf,
         self.conf,
         self.data) = self.db.first("""
select crt, mod, schema, syncName, lastSync,
utcOffset, qconf, conf, data from deck""")
        self.qconf = simplejson.loads(self.qconf)
        self.conf = simplejson.loads(self.conf)
        self.data = simplejson.loads(self.data)

    def flush(self):
        "Flush state to DB, updating mod time."
        self.mod = intTime()
        self.db.execute(
            """update deck set
mod=?, schema=?, syncName=?, lastSync=?, utcOffset=?,
qconf=?, conf=?, data=?""",
            self.mod, self.schema, self.syncName, self.lastSync,
            self.utcOffset, simplejson.dumps(self.qconf),
            simplejson.dumps(self.conf), simplejson.dumps(self.data))

    def save(self):
        "Flush, then commit DB."
        self.flush()
        self.db.commit()

    def close(self, save=True):
        "Disconnect from DB."
        if self.db:
            if save:
                self.save()
            else:
                self.rollback()
            self.db.close()
            self.db = None
        runHook("deckClosed", self)

    def reopen(self):
        "Reconnect to DB (after changing threads, etc). Doesn't reload."
        import anki.db
        if not self.db:
            self.db = anki.db.DB(self.path)

    def rollback(self):
        self.db.rollback()

    def modSchema(self):
        if not self.schemaDirty():
            # next sync will be full
            self.emptyTrash()
        self.schema = intTime()

    def schemaDirty(self):
        "True if schema changed since last sync, or syncing off."
        return self.schema > self.lastSync

    # unsorted
    ##########################################################################

    def nextID(self, type):
        id = self.conf.get(type, 1)
        self.conf[type] = id+1
        return id

    def reset(self):
        self.sched.reset()
        # recache css
        self.rebuildCSS()

    def getCard(self, id):
        return anki.cards.Card(self, id)

    def getFact(self, id):
        return anki.facts.Fact(self, id=id)

    def getTemplate(self, id):
        return anki.models.Template(self, self.deck.db.first(
            "select * from templates where id = ?", id))

        # if card:
        #     return card
        # if sched.name == "main":
        #     self.stopSession()
        # else:
        #     # in a custom scheduler; return to normal
        #     print "fixme: this should be done in gui code"
        #     self.sched.cleanup()
        #     self.sched = AnkiScheduler(self)
        #     return self.getCard()

    def resetCards(self, ids=None):
        "Reset progress on cards in IDS."
        print "position in resetCards()"
        sql = """
update cards set mod=:now, position=0, type=2, queue=2, lastInterval=0,
interval=0, due=created, factor=2.5, reps=0, successive=0, lapses=0, flags=0"""
        sql2 = "delete from revlog"
        if ids is None:
            lim = ""
        else:
            sids = ids2str(ids)
            sql += " where id in "+sids
            sql2 += "  where cardId in "+sids
        self.db.execute(sql, now=time.time())
        self.db.execute(sql2)
        if self.qconf['newCardOrder'] == NEW_CARDS_RANDOM:
            # we need to re-randomize now
            self.randomizeNewCards(ids)

    def randomizeNewCards(self, cardIds=None):
        "Randomize 'due' on all new cards."
        now = time.time()
        query = "select distinct fid from cards where reps = 0"
        if cardIds:
            query += " and id in %s" % ids2str(cardIds)
        fids = self.db.list(query)
        data = [{'fid': fid,
                 'rand': random.uniform(0, now),
                 'now': now} for fid in fids]
        self.db.executemany("""
update cards
set due = :rand + ord,
mod = :now
where fid = :fid
and type = 2""", data)

    def orderNewCards(self):
        "Set 'due' to card creation time."
        self.db.execute("""
update cards set
due = created,
mod = :now
where type = 2""", now=time.time())

    def rescheduleCards(self, ids, min, max):
        "Reset cards and schedule with new interval in days (min, max)."
        self.resetCards(ids)
        vals = []
        for id in ids:
            r = random.uniform(min*86400, max*86400)
            vals.append({
                'id': id,
                'due': r + time.time(),
                'int': r / 86400.0,
                't': time.time(),
                })
        self.db.executemany("""
update cards set
interval = :int,
due = :due,
reps = 1,
successive = 1,
yesCount = 1,
firstAnswered = :t,
queue = 1,
type = 1,
where id = :id""", vals)

    # Times
    ##########################################################################

    def nextDueMsg(self):
        next = self.earliestTime()
        if next:
            # all new cards except suspended
            newCount = self.newCardsDueBy(self.dayCutoff + 86400)
            newCardsTomorrow = min(newCount, self.newCardsPerDay)
            cards = self.cardsDueBy(self.dayCutoff + 86400)
            msg = _('''\
<style>b { color: #00f; }</style>
At this time tomorrow:<br>
%(wait)s<br>
%(new)s''') % {
                'new': ngettext("There will be <b>%d new</b> card.",
                          "There will be <b>%d new</b> cards.",
                          newCardsTomorrow) % newCardsTomorrow,
                'wait': ngettext("There will be <b>%s review</b>.",
                          "There will be <b>%s reviews</b>.", cards) % cards,
                }
            if next > (self.dayCutoff+86400) and not newCardsTomorrow:
                msg = (_("The next review is in <b>%s</b>.") %
                       self.earliestTimeStr())
        else:
            msg = _("No cards are due.")
        return msg

    def earliestTime(self):
        """Return the time of the earliest card.
This may be in the past if the deck is not finished.
If the deck has no (enabled) cards, return None.
Ignore new cards."""
        earliestRev = self.db.scalar(self.cardLimit("revActive", "revInactive", """
select due from cards c where queue = 1
order by due
limit 1"""))
        earliestFail = self.db.scalar(self.cardLimit("revActive", "revInactive", """
select due+%d from cards c where queue = 0
order by due
limit 1""" % self.delay0))
        if earliestRev and earliestFail:
            return min(earliestRev, earliestFail)
        elif earliestRev:
            return earliestRev
        else:
            return earliestFail

    def earliestTimeStr(self, next=None):
        """Return the relative time to the earliest card as a string."""
        if next == None:
            next = self.earliestTime()
        if not next:
            return _("unknown")
        diff = next - time.time()
        return anki.utils.fmtTimeSpan(diff)

    def cardsDueBy(self, time):
        "Number of cards due at TIME. Ignore new cards"
        return self.db.scalar(
            self.cardLimit(
            "revActive", "revInactive",
            "select count(*) from cards c where queue between 0 and 1 "
            "and due < :lim"), lim=time)

    def newCardsDueBy(self, time):
        "Number of new cards due at TIME."
        return self.db.scalar(
            self.cardLimit(
            "newActive", "newInactive",
            "select count(*) from cards c where queue = 2 "
            "and due < :lim"), lim=time)

    def deckFinishedMsg(self):
        spaceSusp = ""
        c= self.spacedCardCount()
        if c:
            spaceSusp += ngettext(
                'There is <b>%d delayed</b> card.',
                'There are <b>%d delayed</b> cards.', c) % c
        c2 = self.hiddenCards()
        if c2:
            if spaceSusp:
                spaceSusp += "<br>"
            spaceSusp += _(
                "Some cards are inactive or suspended.")
        if spaceSusp:
            spaceSusp = "<br><br>" + spaceSusp
        return _('''\
<div style="white-space: normal;">
<h1>Congratulations!</h1>You have finished for now.<br><br>
%(next)s
%(spaceSusp)s
</div>''') % {
    "next": self.nextDueMsg(),
    "spaceSusp": spaceSusp,
    }

    # Suspending
    ##########################################################################

    def suspendCards(self, ids):
        "Suspend cards."
        self.startProgress()
        self.db.execute("""
update cards
set queue = -1, mod = :t
where id in %s""" % ids2str(ids), t=time.time())
        self.finishProgress()

    def unsuspendCards(self, ids):
        "Unsuspend cards."
        self.startProgress()
        self.db.execute("""
update cards set queue = type, mod=:t
where queue = -1 and id in %s""" %
            ids2str(ids), t=time.time())
        self.finishProgress()

    def buryFact(self, fact):
        "Bury all cards for fact until next session."
        for card in fact.cards:
            if card.queue in (0,1,2):
                card.queue = -2

    # Counts
    ##########################################################################

    def hiddenCards(self):
        "Assumes queue finished. True if some due cards have not been shown."
        return self.db.scalar("""
select 1 from cards where due < :now
and queue between 0 and 1 limit 1""", now=self.dayCutoff)

    def spacedCardCount(self):
        "Number of spaced cards."
        print "spacedCardCount"
        return 0
        return self.db.scalar("""
select count(cards.id) from cards where
due > :now and due < :now""", now=time.time())

    def isEmpty(self):
        return not self.cardCount

    def matureCardCount(self):
        return self.db.scalar(
            "select count(id) from cards where interval >= :t ",
            t=MATURE_THRESHOLD)

    def youngCardCount(self):
        return self.db.scalar(
            "select count(id) from cards where interval < :t "
            "and reps != 0", t=MATURE_THRESHOLD)

    def newCountAll(self):
        "All new cards, including spaced."
        return self.db.scalar(
            "select count(id) from cards where type = 2")

    def seenCardCount(self):
        return self.db.scalar(
            "select count(id) from cards where type between 0 and 1")

    # Card predicates
    ##########################################################################

    def cardState(self, card):
        if self.cardIsNew(card):
            return "new"
        elif card.interval > MATURE_THRESHOLD:
            return "mature"
        return "young"

    def cardIsNew(self, card):
        "True if a card has never been seen before."
        return card.reps == 0

    def cardIsYoung(self, card):
        "True if card is not new and not mature."
        return (not self.cardIsNew(card) and
                not self.cardIsMature(card))

    def cardIsMature(self, card):
        return card.interval >= MATURE_THRESHOLD

    # Stats
    ##########################################################################

    def getETA(self, stats):
        # rev + new cards first, account for failures
        import traceback; traceback.print_stack()
        count = stats['rev'] + stats['new']
        count *= 1 + stats['gYoungNo%'] / 100.0
        left = count * stats['dAverageTime']
        # failed - higher time per card for higher amount of cards
        failedBaseMulti = 1.5
        failedMod = 0.07
        failedBaseCount = 20
        factor = (failedBaseMulti +
                  (failedMod * (stats['failed'] - failedBaseCount)))
        left += stats['failed'] * stats['dAverageTime'] * factor
        return left

    # Facts
    ##########################################################################

    def factCount(self):
        return self.db.scalar("select count() from facts")

    def newFact(self):
        "Return a new fact with the current model."
        return anki.facts.Fact(self, self.currentModel())

    def addFact(self, fact):
        "Add a fact to the deck. Return number of new cards."
        # check we have card models available
        cms = self.findTemplates(fact)
        if not cms:
            return None
        # set pos
        fact.pos = self.conf['nextFactPos']
        self.conf['nextFactPos'] += 1
        ncards = 0
        isRandom = self.qconf['newCardOrder'] == NEW_CARDS_RANDOM
        if isRandom:
            due = random.randrange(0, 10000)
        # flush the fact so we get its id
        fact.flush()
        for template in cms:
            print "fixme:specify group on fact add"
            group = self.groupForTemplate(template)
            card = anki.cards.Card(self)
            card.fid = fact.id
            card.tid = template.id
            card.ord = template.ord
            card.gid = 1 #group.id
            if isRandom:
                card.due = due
            else:
                card.due = fact.pos
            card.flush()
            ncards += 1
        # save fact last, which will update caches too
        fact.flush()
        self.registerTags(fact.tags)
        return ncards

    def groupForTemplate(self, template):
        return 1
        id = self.conf['currentGroupId']
        return self.db.query(anki.groups.GroupConf).get(id).load()

    def findTemplates(self, fact, checkActive=True):
        "Return active, non-empty templates."
        ok = []
        for template in fact.model.templates:
            if template.active or not checkActive:
                # [cid, fid, mid, tid, gid, tags, flds, data]
                data = [1, 1, fact.model.id, template.id, 1,
                        "", fact.joinedFields(), ""]
                now = self.formatQA(fact.model, template, "", data)
                data[6] = "\x1f".join([""]*len(fact._fields))
                empty = self.formatQA(fact.model, template, "", data)
                if now['q'] == empty['q']:
                    continue
                if not template.conf['allowEmptyAns']:
                    if now['a'] == empty['a']:
                        continue
                ok.append(template)
        return ok

    def addCards(self, fact, tids):
        ids = []
        for template in self.findTemplates(fact, False):
            if template.id not in tids:
                continue
            if self.db.scalar("""
select count(id) from cards
where fid = :fid and tid = :cmid""",
                                 fid=fact.id, cmid=template.id) == 0:
                    # enough for 10 card models assuming 0.00001 timer precision
                    card = anki.cards.Card(
                        fact, template,
                        fact.created+0.0001*template.ord)
                    raise Exception("incorrect; not checking selective study")
                    self.newAvail += 1
                    ids.append(card.id)

        if ids:
            fact.setMod(textChanged=True, deck=self)
            self.setMod()
        return ids

    def factIsInvalid(self, fact):
        "True if existing fact is invalid. Returns the error."
        try:
            fact.assertValid()
            fact.assertUnique(self.db)
        except FactInvalidError, e:
            return e

    def factUseCount(self, fid):
        "Return number of cards referencing a given fact id."
        return self.db.scalar("select count(id) from cards where fid = :id",
                             id=fid)

    def _deleteFacts(self, ids):
        "Bulk delete facts by ID. Don't call this directly."
        if not ids:
            return
        strids = ids2str(ids)
        self.db.execute("delete from facts where id in %s" % strids)
        self.db.execute("delete from fsums where fid in %s" % strids)

    def _deleteDanglingFacts(self):
        "Delete any facts without cards. Don't call this directly."
        ids = self.db.list("""
select id from facts where id not in (select distinct fid from cards)""")
        self._deleteFacts(ids)
        return ids

    def previewFact(self, oldFact, cms=None):
        "Duplicate fact and generate cards for preview. Don't add to deck."
        # check we have card models available
        if cms is None:
            cms = self.findTemplates(oldFact, checkActive=True)
        if not cms:
            return []
        fact = self.cloneFact(oldFact)
        # proceed
        cards = []
        for template in cms:
            card = anki.cards.Card(fact, template)
            cards.append(card)
        fact.setMod(textChanged=True, deck=self, media=False)
        return cards

    def cloneFact(self, oldFact):
        "Copy fact into new session."
        model = self.db.query(Model).get(oldFact.model.id)
        fact = self.newFact(model)
        for field in fact.fdata:
            fact[field.name] = oldFact[field.name]
        fact._tags = oldFact._tags
        return fact

    # Cards
    ##########################################################################

    def cardCount(self):
        all = self.db.scalar("select count() from cards")
        trash = self.db.scalar("select count() from cards where queue = -4")
        return all - trash

    def deleteCard(self, id):
        "Delete a card given its id. Delete any unused facts."
        self.deleteCards([id])

    def deleteCards(self, ids):
        "Bulk delete cards by ID."
        if not ids:
            return
        sids = ids2str(ids)
        self.startProgress()
        if self.schemaDirty():
            # immediate delete?
            self.db.execute("delete from cards where id in %s" % sids)
            # remove any dangling facts
            self._deleteDanglingFacts()
        else:
            # trash
            sfids = ids2str(
                self.db.list("select fid from cards where id in "+sids))
            # need to handle delete of fsums/revlog remotely after sync
            self.db.execute(
                "update cards set crt = 0, mod = ? where id in "+sids,
                intTime())
            self.db.execute(
                "update facts set crt = 0, mod = ? where id in "+sfids,
                intTime())
            self.db.execute("delete from fsums where fid in "+sfids)
            self.db.execute("delete from revlog where cid in "+sids)
        self.finishProgress()

    def emptyTrash(self):
        self.db.executescript("""
delete from facts where id in (select fid from cards where queue = -4);
delete from cards where queue = -4;""")

    # Models
    ##########################################################################

    def currentModel(self):
        return self.getModel(self.conf['currentModelId'])

    def allModels(self):
        return [self.getModel(id) for id in self.db.list(
            "select id from models")]

    def getModel(self, mid):
        return anki.models.Model(self, mid)

    def addModel(self, model):
        model.flush()
        self.conf['currentModelId'] = model.id

    def deleteModel(self, mid):
        "Delete MODEL, and all its cards/facts."
        # do a direct delete
        self.modSchema()
        # delete facts/cards
        self.deleteCards(self.db.list("""
select id from cards where fid in (select id from facts where mid = ?)""",
                                      mid))
        # then the model
        self.db.execute("delete from models where id = ?", mid)
        self.db.execute("delete from templates where mid = ?", mid)
        # GUI should ensure last model is not deleted
        if self.conf['currentModelId'] == mid:
            self.conf['currentModelId'] = self.db.scalar(
                "select id from models limit 1")

    def modelUseCount(self, model):
        "Return number of facts using model."
        return self.db.scalar("select count() from facts "
                             "where facts.mid = :id",
                             id=model.id)

    def rebuildCSS(self):
        print "fix rebuildCSS()"
        return
        # css for all fields
        def _genCSS(prefix, row):
            (id, fam, siz, col, align, rtl, pre) = row
            t = ""
            if fam: t += 'font-family:"%s";' % toPlatformFont(fam)
            if siz: t += 'font-size:%dpx;' % siz
            if col: t += 'color:%s;' % col
            if rtl == "rtl":
                t += "direction:rtl;unicode-bidi:embed;"
            if pre:
                t += "white-space:pre-wrap;"
            if align != -1:
                if align == 0: align = "center"
                elif align == 1: align = "left"
                else: align = "right"
                t += 'text-align:%s;' % align
            if t:
                t = "%s%s {%s}\n" % (prefix, hexifyID(id), t)
            return t
        css = "".join([_genCSS(".fm", row) for row in self.db.all("""
select id, quizFontFamily, quizFontSize, quizFontColour, -1,
  features, editFontFamily from fields""")])
        cardRows = self.db.all("""
select id, null, null, null, questionAlign, 0, 0 from templates""")
        css += "".join([_genCSS("#cmq", row) for row in cardRows])
        css += "".join([_genCSS("#cma", row) for row in cardRows])
        css += "".join([".cmb%s {background:%s;}\n" %
        (hexifyID(row[0]), row[1]) for row in self.db.all("""
select id, lastFontColour from templates""")])
        self.css = css
        self.data['cssCache'] = css
        self.addHexCache()
        return css

    def addHexCache(self):
        ids = self.db.list("""
select id from fields union
select id from templates union
select id from models""")
        cache = {}
        for id in ids:
            cache[id] = hexifyID(id)
        self.data['hexCache'] = cache

    def changeModel(self, fids, newModel, fieldMap, cardMap):
        self.modSchema()
        sfids = ids2str(fids)
        self.startProgress()
        # field remapping
        if fieldMap:
            seen = {}
            for (old, new) in fieldMap.items():
                seen[new] = 1
                if new:
                    # can rename
                    self.db.execute("""
update fdata set
fmid = :new,
ord = :ord
where fmid = :old
and fid in %s""" % sfids, new=new.id, ord=new.ord, old=old.id)
                else:
                    # no longer used
                    self.db.execute("""
delete from fdata where fid in %s
and fmid = :id""" % sfids, id=old.id)
            # new
            for field in newModel.fields:
                if field not in seen:
                    d = [{'fid': f,
                          'fmid': field.id,
                          'ord': field.ord}
                         for f in fids]
                    self.db.executemany('''
insert into fdata
(fid, fmid, ord, value)
values
(:fid, :fmid, :ord, "")''', d)
            # fact modtime
            self.db.execute("""
update facts set
mod = :t,
mid = :id
where id in %s""" % sfids, t=time.time(), id=newModel.id)
            self.finishProgress()
        # template remapping
        self.startProgress(len(cardMap)+3)
        toChange = []
        for (old, new) in cardMap.items():
            if not new:
                # delete
                self.db.execute("""
delete from cards
where tid = :cid and
fid in %s""" % sfids, cid=old.id)
            elif old != new:
                # gather ids so we can rename x->y and y->x
                ids = self.db.list("""
select id from cards where
tid = :id and fid in %s""" % sfids, id=old.id)
                toChange.append((new, ids))
        for (new, ids) in toChange:
            self.db.execute("""
update cards set
tid = :new,
ord = :ord
where id in %s""" % ids2str(ids), new=new.id, ord=new.ord)
        self.updateCache(fids, type="fact")
        cardIds = self.db.list(
            "select id from cards where fid in %s" %
            ids2str(fids))
        self.finishProgress()

    # Fields
    ##########################################################################

    def allFields(self):
        "Return a list of all possible fields across all models."
        return self.db.list("select distinct name from fieldmodels")

    def deleteFieldModel(self, model, field):
        self.startProgress()
        self.modSchema()
        self.db.execute("delete from fdata where fmid = :id",
                         id=field.id)
        self.db.execute("update facts set mod = :t where mid = :id",
                         id=model.id, t=time.time())
        model.fields.remove(field)
        # update q/a formats
        for cm in model.templates:
            types = ("%%(%s)s" % field.name,
                     "%%(text:%s)s" % field.name,
                     # new style
                     "<<%s>>" % field.name,
                     "<<text:%s>>" % field.name)
            for t in types:
                for fmt in ('qfmt', 'afmt'):
                    setattr(cm, fmt, getattr(cm, fmt).replace(t, ""))
        self.updateCardsFromModel(model)
        model.flush()
        self.finishProgress()

    def addFieldModel(self, model, field):
        "Add FIELD to MODEL and update cards."
        self.modSchema()
        model.addFieldModel(field)
        # flush field to disk
        self.db.execute("""
insert into fdata (fid, fmid, ord, value)
select facts.id, :fmid, :ord, "" from facts
where facts.mid = :mid""", fmid=field.id, mid=model.id, ord=field.ord)
        # ensure facts are marked updated
        self.db.execute("""
update facts set mod = :t where mid = :mid"""
                         , t=time.time(), mid=model.id)
        model.flush()

    def renameFieldModel(self, model, field, newName):
        "Change FIELD's name in MODEL and update FIELD in all facts."
        for cm in model.templates:
            types = ("%%(%s)s",
                     "%%(text:%s)s",
                     # new styles
                     "{{%s}}",
                     "{{text:%s}}",
                     "{{#%s}}",
                     "{{^%s}}",
                     "{{/%s}}")
            for t in types:
                for fmt in ('qfmt', 'afmt'):
                    setattr(cm, fmt, getattr(cm, fmt).replace(t%field.name,
                                                              t%newName))
        field.name = newName
        model.flush()

    def fieldUseCount(self, field):
        "Return the number of cards using field."
        return self.db.scalar("""
select count(id) from fdata where
fmid = :id and val != ""
""", id=field.id)

    def rebuildFieldOrds(self, mid, ids):
        self.modSchema()
        strids = ids2str(ids)
        self.db.execute("""
update fdata
set ord = (select ord from fields where id = fmid)
where fdata.fmid in %s""" % strids)
        # dirty associated facts
        self.db.execute("""
update facts
set mod = strftime("%s", "now")
where mid = :id""", id=mid)

    # Card models
    ##########################################################################

    def templateUseCount(self, template):
        "Return the number of cards using template."
        return self.db.scalar("""
select count(id) from cards where
tid = :id""", id=template.id)

    def addCardModel(self, model, template):
        self.modSchema()
        model.addCardModel(template)

    def deleteCardModel(self, model, template):
        "Delete all cards that use CARDMODEL from the deck."
        self.modSchema()
        cards = self.db.list("select id from cards where tid = :id",
                               id=template.id)
        self.deleteCards(cards)
        model.templates.remove(template)
        model.flush()

    def rebuildCardOrds(self, ids):
        "Update all card models in IDS. Caller must update model modtime."
        self.modSchema()
        strids = ids2str(ids)
        self.db.execute("""
update cards set
ord = (select ord from templates where id = tid),
mod = :now
where tid in %s""" % strids, now=time.time())

    # Caches: q/a, facts.cache and fdata.csum
    ##########################################################################

    def updateCache(self, ids=None, type="card"):
        "Update cache after facts or models changed."
        # gather metadata
        if type == "card":
            where = "and c.id in " + ids2str(ids)
        elif type == "fact":
            where = "and f.id in " + ids2str(ids)
        elif type == "model":
            where = "and m.id in " + ids2str(ids)
        elif type == "all":
            where = ""
        else:
            raise Exception()
        mods = {}
        templs = {}
        for m in self.allModels():
            mods[m.id] = m
            for t in m.templates:
                templs[t.id] = t
        groups = dict(self.db.all("select id, name from groups"))
        return [self.formatQA(mods[row[2]], templs[row[3]], groups[row[4]], row)
                for row in self._qaData(where)]
        # # and checksum
        # self._updateFieldChecksums(facts)

    def formatQA(self, model, template, gname, data, filters=True):
        "Returns hash of id, question, answer."
        # data is [cid, fid, mid, tid, gid, tags, flds, data]
        # unpack fields and create dict
        flist = data[6].split("\x1f")
        fields = {}
        for (name, (idx, conf)) in model.fieldMap().items():
            fields[name] = flist[idx]
            fields["text:"+name] = stripHTML(fields[name])
            if fields[name]:
                fields["text:"+name] = stripHTML(fields[name])
                fields[name] = '<span class="fm%s-%s">%s</span>' % (
                    hexifyID(data[2]), hexifyID(idx), fields[name])
            else:
                fields["text:"+name] = ""
                fields[name] = ""
        fields['Tags'] = data[5]
        fields['Model'] = model.name
        fields['Template'] = template.name
        fields['Group'] = gname
        # render q & a
        d = dict(id=data[0])
        for (type, format) in (("q", template.qfmt), ("a", template.afmt)):
            # if filters:
            #     fields = runFilter("formatQA.pre", fields, , self)
            html = anki.template.render(format, fields)
            # if filters:
            #     d[type] = runFilter("formatQA.post", html, fields, meta, self)
            self.media.registerText(html)
            d[type] = html
        return d

    def _qaData(self, where=""):
        "Return [cid, fid, mid, tid, gid, tags, flds, data] db query"
        return self.db.execute("""
select c.id, f.id, m.id, t.id, g.id, f.tags, f.flds, f.data
from cards c, facts f, models m, templates t, groups g
where c.fid == f.id and f.mid == m.id and
c.tid = t.id and c.gid = g.id
%s""" % where)

    def _updateFieldChecksums(self, facts):
        print "benchmark updatefieldchecksums"
        confs = {}
        r = []
        for (fid, map) in facts.items():
            for (fmid, val) in map.values():
                if fmid and fmid not in confs:
                    confs[fmid] = simplejson.loads(self.db.scalar(
                        "select conf from fields where id = ?",
                        fmid))
                    # if unique checking has been turned off, don't bother to
                    # zero out old values
                    if confs[fmid]['unique']:
                        csum = fieldChecksum(val)
                        r.append((csum, fid, fmid))
        self.db.executemany(
            "update fdata set csum=? where fid=? and fmid=?", r)

    # Tags
    ##########################################################################

    def tagList(self):
        return self.db.list("select name from tags order by name")

    def cardsWithNoTags(self):
        return self.db.list("""
select cards.id from cards, facts where
facts.tags = ""
and cards.fid = facts.id""")

    def cardHasTag(self, card, tag):
        tags = self.db.scalar("select tags from fact where id = :fid",
                              fid=card.fid)
        return tag.lower() in parseTags(tags.lower())

    def updateFactTags(self, fids=None):
        "Add any missing tags to the tags list."
        if fids:
            lim = " where id in " + ids2str(fids)
        else:
            lim = ""
        self.registerTags(set(parseTags(
            " ".join(self.db.list("select distinct tags from facts"+lim)))))

    def registerTags(self, tags):
        r = []
        for t in tags:
            r.append({'t': t})
        self.db.executemany("""
insert or ignore into tags (mod, name) values (%d, :t)""" % intTime(),
                            r)

    def addTags(self, ids, tags, add=True):
        "Add tags in bulk. TAGS is space-separated."
        self.startProgress()
        newTags = parseTags(tags)
        # cache tag names
        self.registerTags(newTags)
        # find facts missing the tags
        if add:
            l = "tags not "
            fn = addTags
        else:
            l = "tags "
            fn = deleteTags
        lim = " or ".join(
            [l+"like :_%d" % c for c, t in enumerate(newTags)])
        res = self.db.all(
            "select id, tags from facts where " + lim,
            **dict([("_%d" % x, '%% %s %%' % y) for x, y in enumerate(newTags)]))
        # update tags
        fids = []
        def fix(row):
            fids.append(row[0])
            return {'id': row[0], 't': fn(tags, row[1]), 'n':intTime()}
        self.db.executemany("""
update facts set tags = :t, mod = :n where id = :id""", [fix(row) for row in res])
        # update q/a cache
        self.updateCache(fids, type="fact")
        self.finishProgress()

    def deleteTags(self, ids, tags):
        self.addTags(ids, tags, False)

    # Finding cards
    ##########################################################################

    def findCards(self, query):
        import anki.find
        return anki.find.findCards(self, query)

    def findReplace(self, *args, **kwargs):
        import anki.find
        return anki.find.findReplace(self, *args, **kwargs)

    def findDuplicates(self, fmids):
        import anki.find
        return anki.find.findDuplicates(self, fmids)

    # Progress info
    ##########################################################################

    def startProgress(self, max=0, min=0, title=None):
        self.enableProgressHandler()
        runHook("startProgress", max, min, title)

    def updateProgress(self, label=None, value=None):
        runHook("updateProgress", label, value)

    def finishProgress(self):
        runHook("updateProgress")
        runHook("finishProgress")
        self.disableProgressHandler()

    def progressHandler(self):
        if (time.time() - self.progressHandlerCalled) < 0.2:
            return
        self.progressHandlerCalled = time.time()
        if self.progressHandlerEnabled:
            runHook("dbProgress")

    def setupProgressHandler(self):
        self.progressHandlerCalled = 0
        self.progressHandlerEnabled = False
        try:
            self.engine.raw_connection().set_progress_handler(
                deck.progressHandler, 100)
        except:
            pass

    def enableProgressHandler(self):
        self.progressHandlerEnabled = True

    def disableProgressHandler(self):
        self.progressHandlerEnabled = False

    # Notifications
    ##########################################################################

    def notify(self, msg):
        "Send a notice to all listeners, or display on stdout."
        if hookEmpty("notify"):
            pass
        else:
            runHook("notify", msg)

    # File-related
    ##########################################################################

    def name(self):
        if not self.path:
            return u"untitled"
        n = os.path.splitext(os.path.basename(self.path))[0]
        assert '/' not in n
        assert '\\' not in n
        return n

    # Timeboxing
    ##########################################################################

    def startTimebox(self):
        self.lastSessionStart = self.sessionStartTime
        self.sessionStartTime = time.time()
        self.sessionStartReps = self.repsToday

    def stopTimebox(self):
        self.sessionStartTime = 0

    def timeboxStarted(self):
        return self.sessionStartTime

    def timeboxReached(self):
        if not self.sessionStartTime:
            # not started
            return False
        if (self.sessionTimeLimit and time.time() >
            (self.sessionStartTime + self.sessionTimeLimit)):
            return True
        if (self.sessionRepLimit and self.sessionRepLimit <=
            self.repsToday - self.sessionStartReps):
            return True
        return False

    # Failed card handling
    ##########################################################################

    def setFailedCardPolicy(self, idx):
        if idx == 5:
            # custom
            return
        self.collapseTime = 0
        self.failedCardMax = 0
        if idx == 0:
            d = 600
            self.collapseTime = 1
            self.failedCardMax = 20
        elif idx == 1:
            d = 0
        elif idx == 2:
            d = 600
        elif idx == 3:
            d = 28800
        elif idx == 4:
            d = 259200
        self.delay0 = d
        self.delay1 = 0

    def getFailedCardPolicy(self):
        if self.delay1:
            return 5
        d = self.delay0
        if self.collapseTime == 1:
            if d == 600 and self.failedCardMax == 20:
                return 0
            return 5
        if d == 0 and self.failedCardMax == 0:
            return 1
        elif d == 600:
            return 2
        elif d == 28800:
            return 3
        elif d == 259200:
            return 4
        return 5

    # Syncing
    ##########################################################################

    def enableSyncing(self):
        self.syncName = self.getSyncName()

    def disableSyncing(self):
        self.syncName = u""

    def syncingEnabled(self):
        return self.syncName

    def genSyncName(self):
        return unicode(checksum(self.path.encode("utf-8")))

    def syncHashBad(self):
        if self.syncName and self.syncName != self.genSyncName():
            self.disableSyncing()
            return True

    # DB maintenance
    ##########################################################################

    def fixIntegrity(self, quick=False):
        "Fix possible problems and rebuild caches."
        self.save()
        self.resetUndo()
        problems = []
        recover = False
        if quick:
            num = 4
        else:
            num = 10
            oldSize = os.stat(self.path)[stat.ST_SIZE]
        self.startProgress(num)
        self.updateProgress(_("Checking database..."))
        if self.db.scalar("pragma integrity_check") != "ok":
            self.finishProgress()
            return _("Database file is damaged.\n"
                     "Please restore from automatic backup (see FAQ).")
        # ensure correct views and indexes are available
        self.updateProgress()
        updateIndices(self)
        # does the user have a model?
        self.updateProgress()
        if not self.db.scalar("select count(id) from models"):
            self.addModel(BasicModel())
            problems.append(_("Deck was missing a model"))
        # is currentModel pointing to a valid model?
        if not self.db.all("""
select decks.id from decks, models where
decks.currentModelId = models.id"""):
            self.currentModelId = self.models[0].id
            problems.append(_("The current model didn't exist"))
        # fdata missing a field model
        ids = self.db.list("""
select id from fdata where fmid not in (
select distinct id from fields)""")
        if ids:
            self.db.execute("delete from fdata where id in %s" %
                             ids2str(ids))
            problems.append(ngettext("Deleted %d field with missing field model",
                            "Deleted %d fdata with missing field model", len(ids)) %
                            len(ids))
        # facts missing a field?
        ids = self.db.list("""
select distinct facts.id from facts, fields where
facts.mid = fields.mid and fields.id not in
(select fmid from fdata where fid = facts.id)""")
        if ids:
            self.deleteFacts(ids)
            problems.append(ngettext("Deleted %d fact with missing fields",
                            "Deleted %d facts with missing fields", len(ids)) %
                            len(ids))
        # cards missing a fact?
        ids = self.db.list("""
select id from cards where fid not in (select id from facts)""")
        if ids:
            recover = True
            self.recoverCards(ids)
            problems.append(ngettext("Recovered %d card with missing fact",
                            "Recovered %d cards with missing fact", len(ids)) %
                            len(ids))
        # cards missing a card model?
        ids = self.db.list("""
select id from cards where tid not in
(select id from templates)""")
        if ids:
            recover = True
            self.recoverCards(ids)
            problems.append(ngettext("Recovered %d card with no card template",
                            "Recovered %d cards with no card template", len(ids)) %
                            len(ids))
        # cards with a card model from the wrong model
        ids = self.db.list("""
select id from cards where tid not in (select cm.id from
templates cm, facts f where cm.mid = f.mid and
f.id = cards.fid)""")
        if ids:
            recover = True
            self.recoverCards(ids)
            problems.append(ngettext("Recovered %d card with wrong card template",
                            "Recovered %d cards with wrong card template", len(ids)) %
                            len(ids))
        # facts missing a card?
        ids = self.deleteDanglingFacts()
        if ids:
            problems.append(ngettext("Deleted %d fact with no cards",
                            "Deleted %d facts with no cards", len(ids)) %
                            len(ids))
        # dangling fields?
        ids = self.db.list("""
select id from fdata where fid not in (select id from facts)""")
        if ids:
            self.db.execute(
                "delete from fdata where id in %s" % ids2str(ids))
            problems.append(ngettext("Deleted %d dangling field",
                            "Deleted %d dangling fields", len(ids)) %
                            len(ids))
        if not quick:
            self.updateProgress()
            # these sometimes end up null on upgrade
            self.db.execute("update models set source = 0 where source is null")
            self.db.execute(
                "update templates set allowEmptyAnswer = 1, typeAnswer = '' "
                "where allowEmptyAnswer is null or typeAnswer is null")
            # fix tags
            self.updateProgress()
            self.db.execute("delete from tags")
            self.updateFactTags()
            print "should ensure tags having leading/trailing space"
            # make sure ords are correct
            self.updateProgress()
            self.db.execute("""
update fdata set ord = (select ord from fields
where id = fmid)""")
            self.db.execute("""
update cards set ord = (select ord from templates
where cards.tid = templates.id)""")
            # fix problems with stripping html
            self.updateProgress()
            fdata = self.db.all("select id, val from fdata")
            newFdata = []
            for (id, val) in fdata:
                newFdata.append({'id': id, 'val': tidyHTML(val)})
            self.db.executemany(
                "update fdata set val=:val where id=:id",
                newFdata)
            # and field checksums
            self.updateProgress()
            self.updateAllFieldChecksums()
            # regenerate question/answer cache
            for m in self.models:
                self.updateCardsFromModel(m, dirty=False)
            # rebuild
            self.updateProgress()
            self.rebuildTypes()
            # force a full sync
            self.modSchema()
            # and finally, optimize
            self.updateProgress()
            self.optimize()
            newSize = os.stat(self.path)[stat.ST_SIZE]
            save = (oldSize - newSize)/1024
            txt = _("Database rebuilt and optimized.")
            if save > 0:
                txt += "\n" + _("Saved %dKB.") % save
            problems.append(txt)
        self.save()
        self.finishProgress()
        if problems:
            if recover:
                problems.append("\n" + _("""\
Cards with corrupt or missing facts have been placed into new facts. \
Your scheduling info and card content has been preserved, but the \
original layout of the facts has been lost."""))
            return "\n".join(problems)
        return "ok"

    def optimize(self):
        self.db.execute("vacuum")
        self.db.execute("analyze")

    # Undo/redo
    ##########################################################################

    def initUndo(self):
        # note this code ignores 'unique', as it's an sqlite reserved word
        self.undoStack = []
        self.redoStack = []
        self.undoEnabled = True
        self.db.execute(
            "create temporary table undoLog (seq integer primary key not null, sql text)")
        tables = self.db.list(
            "select name from sqlite_master where type = 'table'")
        for table in tables:
            if table in ("undoLog", "sqlite_stat1"):
                continue
            columns = [r[1] for r in
                       self.db.all("pragma table_info(%s)" % table)]
            # insert
            self.db.execute("""
create temp trigger _undo_%(t)s_it
after insert on %(t)s begin
insert into undoLog values
(null, 'delete from %(t)s where rowid = ' || new.rowid); end""" % {'t': table})
            # update
            sql = """
create temp trigger _undo_%(t)s_ut
after update on %(t)s begin
insert into undoLog values (null, 'update %(t)s """ % {'t': table}
            sep = "set "
            for c in columns:
                if c == "unique":
                    continue
                sql += "%(s)s%(c)s=' || quote(old.%(c)s) || '" % {
                    's': sep, 'c': c}
                sep = ","
            sql += " where rowid = ' || old.rowid); end"
            self.db.execute(sql)
            # delete
            sql = """
create temp trigger _undo_%(t)s_dt
before delete on %(t)s begin
insert into undoLog values (null, 'insert into %(t)s (rowid""" % {'t': table}
            for c in columns:
                sql += ",\"%s\"" % c
            sql += ") values (' || old.rowid ||'"
            for c in columns:
                if c == "unique":
                    sql += ",1"
                    continue
                sql += ",' || quote(old.%s) ||'" % c
            sql += ")'); end"
            self.db.execute(sql)

    def undoName(self):
        for n in reversed(self.undoStack):
            if n:
                return n[0]

    def redoName(self):
        return self.redoStack[-1][0]

    def undoAvailable(self):
        if not self.undoEnabled:
            return
        for r in reversed(self.undoStack):
            if r:
                return True

    def redoAvailable(self):
        return self.undoEnabled and self.redoStack

    def resetUndo(self):
        try:
            self.db.execute("delete from undoLog")
        except:
            pass
        self.undoStack = []
        self.redoStack = []

    def setUndoBarrier(self):
        if not self.undoStack or self.undoStack[-1] is not None:
            self.undoStack.append(None)

    def setUndoStart(self, name, merge=False):
        if not self.undoEnabled:
            return
        if merge and self.undoStack:
            if self.undoStack[-1] and self.undoStack[-1][0] == name:
                # merge with last entry?
                return
        start = self._latestUndoRow()
        self.undoStack.append([name, start, None])

    def setUndoEnd(self, name):
        if not self.undoEnabled:
            return
        end = self._latestUndoRow()
        while self.undoStack[-1] is None:
            # strip off barrier
            self.undoStack.pop()
        self.undoStack[-1][2] = end
        if self.undoStack[-1][1] == self.undoStack[-1][2]:
            self.undoStack.pop()
        else:
            self.redoStack = []
        runHook("undoEnd")

    def _latestUndoRow(self):
        return self.db.scalar("select max(rowid) from undoLog") or 0

    def _undoredo(self, src, dst):
        while 1:
            u = src.pop()
            if u:
                break
        (start, end) = (u[1], u[2])
        if end is None:
            end = self._latestUndoRow()
        sql = self.db.list("""
select sql from undoLog where
seq > :s and seq <= :e order by seq desc""", s=start, e=end)
        mod = len(sql) / 35
        if mod:
            self.startProgress(36)
            self.updateProgress(_("Processing..."))
        newstart = self._latestUndoRow()
        for c, s in enumerate(sql):
            if mod and not c % mod:
                self.updateProgress()
            self.engine.execute(s)
        newend = self._latestUndoRow()
        dst.append([u[0], newstart, newend])
        if mod:
            self.finishProgress()

    def undo(self):
        "Undo the last action(s)."
        self._undoredo(self.undoStack, self.redoStack)
        runHook("postUndoRedo")

    def redo(self):
        "Redo the last action(s)."
        self._undoredo(self.redoStack, self.undoStack)
        runHook("postUndoRedo")

    # Dynamic indices
    ##########################################################################

    def updateDynamicIndices(self):
        # determine required columns
        required = []
        if self.qconf['newTodayOrder'] == NEW_TODAY_ORD:
            required.append("ord")
        if self.qconf['revCardOrder'] in (REV_CARDS_OLD_FIRST, REV_CARDS_NEW_FIRST):
            required.append("interval")
        cols = ["queue", "due", "gid"] + required
        # update if changed
        if self.db.scalar(
            "select 1 from sqlite_master where name = 'ix_cards_multi'"):
            rows = self.db.all("pragma index_info('ix_cards_multi')")
        else:
            rows = None
        if not (rows and cols == [r[2] for r in rows]):
            self.db.execute("drop index if exists ix_cards_multi")
            self.db.execute("create index ix_cards_multi on cards (%s)" %
                              ", ".join(cols))
            self.db.execute("analyze")

# Shared decks
##########################################################################

# sourcesTable = Table(
#     'sources', metadata,
#     Column('id', Integer, nullable=False, primary_key=True),
#     Column('name', UnicodeText, nullable=False, default=""),
#     Column('created', Integer, nullable=False, default=intTime),
#     Column('lastSync', Integer, nullable=False, default=0),
#     # -1 = never check, 0 = always check, 1+ = number of seconds passed.
#     # not currently exposed in the GUI
#     Column('syncPeriod', Integer, nullable=False, default=0))
