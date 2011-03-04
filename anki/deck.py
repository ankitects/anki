# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

import tempfile, time, os, random, sys, re, stat, shutil
import types, traceback, simplejson, datetime

from anki.db import *
from anki.lang import _, ngettext
from anki.errors import DeckAccessError
from anki.stdmodels import BasicModel
from anki.utils import parseTags, tidyHTML, genID, ids2str, hexifyID, \
     canonifyTags, joinTags, addTags, deleteTags, checksum, fieldChecksum, intTime
from anki.revlog import logReview
from anki.models import Model, CardModel, formatQA
from anki.fonts import toPlatformFont
from operator import itemgetter
from itertools import groupby
from anki.hooks import runHook, hookEmpty
from anki.template import render
from anki.media import updateMediaCount, mediaFiles, \
     rebuildMediaDir
from anki.upgrade import upgradeSchema, updateIndices, upgradeDeck, DECK_VERSION
from anki.sched import Scheduler
from anki.consts import *
import anki.latex # sets up hook

# ensure all the DB metadata in other files is loaded before proceeding
import anki.models, anki.facts, anki.cards, anki.media, anki.groups, anki.graves

# Settings related to queue building. These may be loaded without the rest of
# the config to check due counts faster on mobile clients.
defaultQconf = {
    'revGroups': [],
    'newGroups': [],
    'newPerDay': 20,
    'newToday': [0, 0], # currentDay, count
    'newTodayOrder': NEW_TODAY_ORDINAL,
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

# syncName: md5sum of current deck location, to detect if deck was moved or
#           renamed. mobile clients can treat this as a simple boolean
deckTable = Table(
    'deck', metadata,
    Column('id', Integer, nullable=False, primary_key=True),
    Column('created', Integer, nullable=False, default=intTime),
    Column('modified', Integer, nullable=False, default=intTime),
    Column('schemaMod', Integer, nullable=False, default=intTime),
    Column('version', Integer, nullable=False, default=DECK_VERSION),
    Column('syncName', UnicodeText, nullable=False, default=u""),
    Column('lastSync', Integer, nullable=False, default=0),
    Column('utcOffset', Integer, nullable=False, default=-2),
    Column('qconf', UnicodeText, nullable=False, default=unicode(
        simplejson.dumps(defaultQconf))),
    Column('config', UnicodeText, nullable=False, default=unicode(
        simplejson.dumps(defaultConf))),
    Column('data', UnicodeText, nullable=False, default=u"{}")
)

class Deck(object):
    "Top-level object. Manages facts, cards and scheduling information."

    factorFour = 1.3

    def _initVars(self):
        if self.utcOffset == -2:
            # shared deck; reset timezone and creation date
            self.utcOffset = time.timezone + 60*60*4
            self.created = time.time()
        self.mediaPrefix = ""
        self.lastLoaded = time.time()
        self.undoEnabled = False
        self.sessionStartReps = 0
        self.sessionStartTime = 0
        self.lastSessionStart = 0
        # counter for reps since deck open
        self.reps = 0
        self.sched = Scheduler(self)

    def modifiedSinceSave(self):
        return self.modified > self.lastLoaded

    def reset(self):
        self.sched.reset()
        # recache css
        self.rebuildCSS()

    def getCard(self):
        return self.sched.getCard()

    def answerCard(self, card, ease):
        self.sched.answerCard(card, ease)
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
update cards set modified=:now, position=0, type=2, queue=2, lastInterval=0,
interval=0, due=created, factor=2.5, reps=0, successive=0, lapses=0, flags=0"""
        sql2 = "delete from revlog"
        if ids is None:
            lim = ""
        else:
            sids = ids2str(ids)
            sql += " where id in "+sids
            sql2 += "  where cardId in "+sids
        self.db.statement(sql, now=time.time())
        self.db.statement(sql2)
        if self.qconf['newCardOrder'] == NEW_CARDS_RANDOM:
            # we need to re-randomize now
            self.randomizeNewCards(ids)
        self.flushMod()
        self.refreshSession()

    def randomizeNewCards(self, cardIds=None):
        "Randomize 'due' on all new cards."
        now = time.time()
        query = "select distinct factId from cards where reps = 0"
        if cardIds:
            query += " and id in %s" % ids2str(cardIds)
        fids = self.db.column0(query)
        data = [{'fid': fid,
                 'rand': random.uniform(0, now),
                 'now': now} for fid in fids]
        self.db.statements("""
update cards
set due = :rand + ordinal,
modified = :now
where factId = :fid
and type = 2""", data)

    def orderNewCards(self):
        "Set 'due' to card creation time."
        self.db.statement("""
update cards set
due = created,
modified = :now
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
        self.db.statements("""
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
        self.flushMod()

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
        "Suspend cards. Caller must .reset()"
        self.startProgress()
        self.db.statement("""
update cards
set queue = -1, modified = :t
where id in %s""" % ids2str(ids), t=time.time())
        self.flushMod()
        self.finishProgress()

    def unsuspendCards(self, ids):
        "Unsuspend cards. Caller must .reset()"
        self.startProgress()
        self.db.statement("""
update cards set queue = type, modified=:t
where queue = -1 and id in %s""" %
            ids2str(ids), t=time.time())
        self.flushMod()
        self.finishProgress()

    def buryFact(self, fact):
        "Bury all cards for fact until next session. Caller must .reset()"
        for card in fact.cards:
            if card.queue in (0,1,2):
                card.queue = -2
        self.flushMod()

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

    def newFact(self, model=None):
        "Return a new fact with the current model."
        if model is None:
            model = self.currentModel
        return anki.facts.Fact(model, self.getFactPos())

    def addFact(self, fact, reset=True):
        "Add a fact to the deck. Return list of new cards."
        if not fact.model:
            fact.model = self.currentModel
        # validate
        fact.assertValid()
        fact.assertUnique(self.db)
        # check we have card models available
        cms = self.availableCardModels(fact)
        if not cms:
            return None
        # proceed
        cards = []
        self.db.save(fact)
        # update field cache
        self.flushMod()
        isRandom = self.qconf['newCardOrder'] == NEW_CARDS_RANDOM
        if isRandom:
            due = random.randrange(0, 10000)
        for cardModel in cms:
            group = self.groupForTemplate(cardModel)
            card = anki.cards.Card(fact, cardModel, group)
            if isRandom:
                card.due = due
            self.flushMod()
            cards.append(card)
        # update card q/a
        fact.setModified(True, self)
        self.registerTags(fact.tags())
        self.flushMod()
        if reset:
            self.reset()
        return fact

    def groupForTemplate(self, template):
        print "add default group to template"
        id = self.config['currentGroupId']
        return self.db.query(anki.groups.GroupConfig).get(id).load()

    def availableCardModels(self, fact, checkActive=True):
        "List of active card models that aren't empty for FACT."
        models = []
        for cardModel in fact.model.cardModels:
           if cardModel.active or not checkActive:
               ok = True
               for (type, format) in [("q", cardModel.qformat),
                                      ("a", cardModel.aformat)]:
                   # compat
                   format = re.sub("%\((.+?)\)s", "{{\\1}}", format)
                   empty = {}
                   local = {}; local.update(fact)
                   local['tags'] = u""
                   local['Tags'] = u""
                   local['cardModel'] = u""
                   local['modelName'] = u""
                   for k in local.keys():
                       empty[k] = u""
                       empty["text:"+k] = u""
                       local["text:"+k] = local[k]
                   empty['tags'] = ""
                   local['tags'] = fact._tags
                   try:
                       if (render(format, local) ==
                           render(format, empty)):
                           ok = False
                           break
                   except (KeyError, TypeError, ValueError):
                       ok = False
                       break
               if ok or type == "a" and cardModel.allowEmptyAnswer:
                   models.append(cardModel)
        return models

    def addCards(self, fact, cardModelIds):
        "Caller must flush first and flushMod after."
        ids = []
        for cardModel in self.availableCardModels(fact, False):
            if cardModel.id not in cardModelIds:
                continue
            if self.db.scalar("""
select count(id) from cards
where factId = :fid and cardModelId = :cmid""",
                                 fid=fact.id, cmid=cardModel.id) == 0:
                    # enough for 10 card models assuming 0.00001 timer precision
                    card = anki.cards.Card(
                        fact, cardModel,
                        fact.created+0.0001*cardModel.ordinal)
                    raise Exception("incorrect; not checking selective study")
                    self.newAvail += 1
                    ids.append(card.id)

        if ids:
            fact.setModified(textChanged=True, deck=self)
            self.setModified()
        return ids

    def factIsInvalid(self, fact):
        "True if existing fact is invalid. Returns the error."
        try:
            fact.assertValid()
            fact.assertUnique(self.db)
        except FactInvalidError, e:
            return e

    def factUseCount(self, factId):
        "Return number of cards referencing a given fact id."
        return self.db.scalar("select count(id) from cards where factId = :id",
                             id=factId)

    def deleteFact(self, factId):
        "Delete a fact. Removes any associated cards. Don't flush."
        self.db.flush()
        # remove any remaining cards
        self.db.statement("insert into cardsDeleted select id, :time "
                         "from cards where factId = :factId",
                         time=time.time(), factId=factId)
        self.db.statement(
            "delete from cards where factId = :id", id=factId)
        # and then the fact
        self.deleteFacts([factId])
        self.setModified()

    def deleteFacts(self, ids):
        "Bulk delete facts by ID; don't touch cards. Caller must .reset()."
        if not ids:
            return
        self.db.flush()
        now = time.time()
        strids = ids2str(ids)
        self.db.statement("delete from facts where id in %s" % strids)
        self.db.statement("delete from fields where factId in %s" % strids)
        anki.graves.registerMany(self.db, anki.graves.FACT, ids)
        self.setModified()

    def deleteDanglingFacts(self):
        "Delete any facts without cards. Return deleted ids."
        ids = self.db.column0("""
select facts.id from facts
where facts.id not in (select distinct factId from cards)""")
        self.deleteFacts(ids)
        return ids

    def previewFact(self, oldFact, cms=None):
        "Duplicate fact and generate cards for preview. Don't add to deck."
        # check we have card models available
        if cms is None:
            cms = self.availableCardModels(oldFact, checkActive=True)
        if not cms:
            return []
        fact = self.cloneFact(oldFact)
        # proceed
        cards = []
        for cardModel in cms:
            card = anki.cards.Card(fact, cardModel)
            cards.append(card)
        fact.setModified(textChanged=True, deck=self, media=False)
        return cards

    def cloneFact(self, oldFact):
        "Copy fact into new session."
        model = self.db.query(Model).get(oldFact.model.id)
        fact = self.newFact(model)
        for field in fact.fields:
            fact[field.name] = oldFact[field.name]
        fact._tags = oldFact._tags
        return fact

    # Cards
    ##########################################################################

    def cardCount(self):
        return self.db.scalar("select count() from cards")

    def deleteCard(self, id):
        "Delete a card given its id. Delete any unused facts. Don't flush."
        self.deleteCards([id])

    def deleteCards(self, ids):
        "Bulk delete cards by ID. Caller must .reset()"
        if not ids:
            return
        self.db.flush()
        now = time.time()
        strids = ids2str(ids)
        self.startProgress()
        # grab fact ids
        factIds = self.db.column0("select factId from cards where id in %s"
                                 % strids)
        # drop from cards
        self.db.statement("delete from cards where id in %s" % strids)
        # note deleted
        anki.graves.registerMany(self.db, anki.graves.CARD, ids)
        # remove any dangling facts
        self.deleteDanglingFacts()
        self.refreshSession()
        self.flushMod()
        self.finishProgress()

    # Models
    ##########################################################################

    def getCurrentModel(self):
        return self.db.query(anki.models.Model).get(self.currentModelId)
    def setCurrentModel(self, model):
        self.currentModelId = model.id
    currentModel = property(getCurrentModel, setCurrentModel)

    def getModels(self):
        return self.db.query(anki.models.Model).all()
    models = property(getModels)

    def addModel(self, model):
        self.db.add(model)
        self.setSchemaModified()
        self.currentModel = model
        self.flushMod()

    def deleteModel(self, model):
        "Delete MODEL, and all its cards/facts. Caller must .reset()."
        if self.db.scalar("select count(id) from models where id=:id",
                         id=model.id):
            self.setSchemaModified()
            # delete facts/cards
            self.currentModel
            self.deleteCards(self.db.column0("""
select cards.id from cards, facts where
facts.modelId = :id and
facts.id = cards.factId""", id=model.id))
            # then the model
            self.models.remove(model)
            self.db.delete(model)
            self.db.flush()
            if self.currentModel == model:
                self.currentModel = self.models[0]
            anki.graves.registerOne(self.db, anki.graves.MODEL, model.id)
            self.flushMod()
            self.refreshSession()
            self.setModified()

    def modelUseCount(self, model):
        "Return number of facts using model."
        return self.db.scalar("select count(facts.modelId) from facts "
                             "where facts.modelId = :id",
                             id=model.id)

    def deleteEmptyModels(self):
        for model in self.models:
            if not self.modelUseCount(model):
                self.deleteModel(model)

    def rebuildCSS(self):
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
  features, editFontFamily from fieldModels""")])
        cardRows = self.db.all("""
select id, null, null, null, questionAlign, 0, 0 from cardModels""")
        css += "".join([_genCSS("#cmq", row) for row in cardRows])
        css += "".join([_genCSS("#cma", row) for row in cardRows])
        css += "".join([".cmb%s {background:%s;}\n" %
        (hexifyID(row[0]), row[1]) for row in self.db.all("""
select id, lastFontColour from cardModels""")])
        self.css = css
        self.data['cssCache'] = css
        self.addHexCache()
        return css

    def addHexCache(self):
        ids = self.db.column0("""
select id from fieldModels union
select id from cardModels union
select id from models""")
        cache = {}
        for id in ids:
            cache[id] = hexifyID(id)
        self.data['hexCache'] = cache

    def copyModel(self, oldModel):
        "Add a new model to DB based on MODEL."
        m = Model(_("%s copy") % oldModel.name)
        for f in oldModel.fieldModels:
            f = f.copy()
            m.addFieldModel(f)
        for c in oldModel.cardModels:
            c = c.copy()
            m.addCardModel(c)
        self.addModel(m)
        return m

    def changeModel(self, factIds, newModel, fieldMap, cardMap):
        "Caller must .reset()"
        self.setSchemaModified()
        self.db.flush()
        fids = ids2str(factIds)
        changed = False
        # field remapping
        if fieldMap:
            changed = True
            self.startProgress(len(fieldMap)+2)
            seen = {}
            for (old, new) in fieldMap.items():
                self.updateProgress(_("Changing fields..."))
                seen[new] = 1
                if new:
                    # can rename
                    self.db.statement("""
update fields set
fieldModelId = :new,
ordinal = :ord
where fieldModelId = :old
and factId in %s""" % fids, new=new.id, ord=new.ordinal, old=old.id)
                else:
                    # no longer used
                    self.db.statement("""
delete from fields where factId in %s
and fieldModelId = :id""" % fids, id=old.id)
            # new
            for field in newModel.fieldModels:
                self.updateProgress()
                if field not in seen:
                    d = [{'id': genID(),
                          'fid': f,
                          'fmid': field.id,
                          'ord': field.ordinal}
                         for f in factIds]
                    self.db.statements('''
insert into fields
(id, factId, fieldModelId, ordinal, value)
values
(:id, :fid, :fmid, :ord, "")''', d)
            # fact modtime
            self.updateProgress()
            self.db.statement("""
update facts set
modified = :t,
modelId = :id
where id in %s""" % fids, t=time.time(), id=newModel.id)
            self.finishProgress()
        # template remapping
        self.startProgress(len(cardMap)+3)
        toChange = []
        self.updateProgress(_("Changing cards..."))
        for (old, new) in cardMap.items():
            if not new:
                # delete
                self.db.statement("""
delete from cards
where cardModelId = :cid and
factId in %s""" % fids, cid=old.id)
            elif old != new:
                # gather ids so we can rename x->y and y->x
                ids = self.db.column0("""
select id from cards where
cardModelId = :id and factId in %s""" % fids, id=old.id)
                toChange.append((new, ids))
        for (new, ids) in toChange:
            self.updateProgress()
            self.db.statement("""
update cards set
cardModelId = :new,
ordinal = :ord
where id in %s""" % ids2str(ids), new=new.id, ord=new.ordinal)
        self.updateProgress()
        self.updateCardQACacheFromIds(factIds, type="facts")
        self.flushMod()
        self.updateProgress()
        cardIds = self.db.column0(
            "select id from cards where factId in %s" %
            ids2str(factIds))
        self.refreshSession()
        self.finishProgress()

    # Fields
    ##########################################################################

    def allFields(self):
        "Return a list of all possible fields across all models."
        return self.db.column0("select distinct name from fieldmodels")

    def deleteFieldModel(self, model, field):
        self.startProgress()
        self.setSchemaModified()
        self.db.statement("delete from fields where fieldModelId = :id",
                         id=field.id)
        self.db.statement("update facts set modified = :t where modelId = :id",
                         id=model.id, t=time.time())
        model.fieldModels.remove(field)
        # update q/a formats
        for cm in model.cardModels:
            types = ("%%(%s)s" % field.name,
                     "%%(text:%s)s" % field.name,
                     # new style
                     "<<%s>>" % field.name,
                     "<<text:%s>>" % field.name)
            for t in types:
                for fmt in ('qformat', 'aformat'):
                    setattr(cm, fmt, getattr(cm, fmt).replace(t, ""))
        self.updateCardsFromModel(model)
        model.setModified()
        self.flushMod()
        self.finishProgress()

    def addFieldModel(self, model, field):
        "Add FIELD to MODEL and update cards."
        self.setSchemaModified()
        model.addFieldModel(field)
        # commit field to disk
        self.db.flush()
        self.db.statement("""
insert into fields (factId, fieldModelId, ordinal, value)
select facts.id, :fmid, :ordinal, "" from facts
where facts.modelId = :mid""", fmid=field.id, mid=model.id, ordinal=field.ordinal)
        # ensure facts are marked updated
        self.db.statement("""
update facts set modified = :t where modelId = :mid"""
                         , t=time.time(), mid=model.id)
        model.setModified()
        self.flushMod()

    def renameFieldModel(self, model, field, newName):
        "Change FIELD's name in MODEL and update FIELD in all facts."
        for cm in model.cardModels:
            types = ("%%(%s)s",
                     "%%(text:%s)s",
                     # new styles
                     "{{%s}}",
                     "{{text:%s}}",
                     "{{#%s}}",
                     "{{^%s}}",
                     "{{/%s}}")
            for t in types:
                for fmt in ('qformat', 'aformat'):
                    setattr(cm, fmt, getattr(cm, fmt).replace(t%field.name,
                                                              t%newName))
        field.name = newName
        model.setModified()
        self.flushMod()

    def fieldModelUseCount(self, fieldModel):
        "Return the number of cards using fieldModel."
        return self.db.scalar("""
select count(id) from fields where
fieldModelId = :id and value != ""
""", id=fieldModel.id)

    def rebuildFieldOrdinals(self, modelId, ids):
        """Update field ordinal for all fields given field model IDS.
Caller must update model modtime."""
        self.setSchemaModified()
        self.db.flush()
        strids = ids2str(ids)
        self.db.statement("""
update fields
set ordinal = (select ordinal from fieldModels where id = fieldModelId)
where fields.fieldModelId in %s""" % strids)
        # dirty associated facts
        self.db.statement("""
update facts
set modified = strftime("%s", "now")
where modelId = :id""", id=modelId)
        self.flushMod()

    def updateAllFieldChecksums(self):
        # zero out
        self.db.statement("update fields set chksum = ''")
        # add back for unique fields
        for m in self.models:
            for fm in m.fieldModels:
                self.updateFieldChecksums(fm.id)

    def updateFieldChecksums(self, fmid):
        self.db.flush()
        self.setSchemaModified()
        unique = self.db.scalar(
            "select \"unique\" from fieldModels where id = :id", id=fmid)
        if unique:
            l = []
            for (id, value) in self.db.all(
                "select id, value from fields where fieldModelId = :id",
                id=fmid):
                l.append({'id':id, 'chk':fieldChecksum(value)})
            self.db.statements(
                "update fields set chksum = :chk where id = :id", l)
        else:
            self.db.statement(
                "update fields set chksum = '' where fieldModelId=:id",
                id=fmid)

    # Card models
    ##########################################################################

    def cardModelUseCount(self, cardModel):
        "Return the number of cards using cardModel."
        return self.db.scalar("""
select count(id) from cards where
cardModelId = :id""", id=cardModel.id)

    def addCardModel(self, model, cardModel):
        self.setSchemaModified()
        model.addCardModel(cardModel)

    def deleteCardModel(self, model, cardModel):
        "Delete all cards that use CARDMODEL from the deck."
        self.setSchemaModified()
        cards = self.db.column0("select id from cards where cardModelId = :id",
                               id=cardModel.id)
        self.deleteCards(cards)
        model.cardModels.remove(cardModel)
        model.setModified()
        self.flushMod()

    def updateCardsFromModel(self, model, dirty=True):
        "Update all card question/answer when model changes."
        ids = self.db.all("""
select cards.id, cards.cardModelId, cards.factId, facts.modelId from
cards, facts where
cards.factId = facts.id and
facts.modelId = :id""", id=model.id)
        if not ids:
            return
        self.updateCardQACache(ids, dirty)

    def updateCardsFromFactIds(self, ids, dirty=True):
        "Update all card question/answer when model changes."
        ids = self.db.all("""
select cards.id, cards.cardModelId, cards.factId, facts.modelId from
cards, facts where
cards.factId = facts.id and
facts.id in %s""" % ids2str(ids))
        if not ids:
            return
        self.updateCardQACache(ids, dirty)

    def updateCardQACacheFromIds(self, ids, type="cards"):
        "Given a list of card or fact ids, update q/a cache."
        if type == "facts":
            # convert to card ids
            ids = self.db.column0(
                "select id from cards where factId in %s" % ids2str(ids))
        rows = self.db.all("""
select c.id, c.cardModelId, f.id, f.modelId
from cards as c, facts as f
where c.factId = f.id
and c.id in %s""" % ids2str(ids))
        self.updateCardQACache(rows)

    def updateCardQACache(self, ids, dirty=True):
        "Given a list of (cardId, cardModelId, factId, modId), update q/a cache."
        if dirty:
            mod = ", modified = %f" % time.time()
        else:
            mod = ""
        # tags
        cids = ids2str([x[0] for x in ids])
        tags = dict([(x[0], x[1:]) for x in
                     self.splitTagsList(
            where="and cards.id in %s" % cids)])
        facts = {}
        # fields
        for k, g in groupby(self.db.all("""
select fields.factId, fieldModels.name, fieldModels.id, fields.value
from fields, fieldModels where fields.factId in %s and
fields.fieldModelId = fieldModels.id
order by fields.factId""" % ids2str([x[2] for x in ids])),
                            itemgetter(0)):
            facts[k] = dict([(r[1], (r[2], r[3])) for r in g])
        # card models
        cms = {}
        for c in self.db.query(CardModel).all():
            cms[c.id] = c
        pend = [formatQA(cid, mid, facts[fid], tags[cid], cms[cmid], self)
                for (cid, cmid, fid, mid) in ids]
        if pend:
            # find existing media references
            files = {}
            for txt in self.db.column0(
                "select question || answer from cards where id in %s" %
                cids):
                for f in mediaFiles(txt):
                    if f in files:
                        files[f] -= 1
                    else:
                        files[f] = -1
            # determine ref count delta
            for p in pend:
                for type in ("question", "answer"):
                    txt = p[type]
                    for f in mediaFiles(txt):
                        if f in files:
                            files[f] += 1
                        else:
                            files[f] = 1
            # update references - this could be more efficient
            for (f, cnt) in files.items():
                if not cnt:
                    continue
                updateMediaCount(self, f, cnt)
            # update q/a
            self.db.execute("""
    update cards set
    question = :question, answer = :answer
    %s
    where id = :id""" % mod, pend)
            # update fields cache
            self.updateFieldCache(facts.keys())
        if dirty:
            self.flushMod()

    def updateFieldCache(self, fids):
        "Add stripped HTML cache for sorting/searching."
        try:
            all = self.db.all(
                ("select factId, group_concat(value, ' ') from fields "
                 "where factId in %s group by factId") % ids2str(fids))
        except:
            # older sqlite doesn't support group_concat. this code taken from
            # the wm port
            all=[]
            for factId in fids:
                values=self.db.all("select value from fields where value is not NULL and factId=%(factId)i" % {"factId": factId})
                value_list=[]
                for row in values:
                        value_list.append(row[0])
                concatenated_values=' '.join(value_list)
                all.append([factId, concatenated_values])
        r = []
        from anki.utils import stripHTMLMedia
        for a in all:
            r.append({'id':a[0], 'v':stripHTMLMedia(a[1])})
        self.db.statements(
            "update facts set cache=:v where id=:id", r)

    def rebuildCardOrdinals(self, ids):
        "Update all card models in IDS. Caller must update model modtime."
        self.setSchemaModified()
        self.db.flush()
        strids = ids2str(ids)
        self.db.statement("""
update cards set
ordinal = (select ordinal from cardModels where id = cardModelId),
modified = :now
where cardModelId in %s""" % strids, now=time.time())
        self.flushMod()

    # Tags
    ##########################################################################

    def tagList(self):
        return self.db.column0("select name from tags order by name")

    def splitTagsList(self, where=""):
        return self.db.all("""
select cards.id, facts.tags, models.name, cardModels.name
from cards, facts, models, cardModels where
cards.factId == facts.id and facts.modelId == models.id
and cards.cardModelId = cardModels.id
%s""" % where)

    def cardsWithNoTags(self):
        return self.db.column0("""
select cards.id from cards, facts where
facts.tags = ""
and cards.factId = facts.id""")

    def cardHasTag(self, card, tag):
        tags = self.db.scalar("select tags from fact where id = :fid",
                              fid=card.factId)
        return tag.lower() in parseTags(tags.lower())

    def updateFactTags(self, factIds=None):
        "Add any missing tags to the tags list."
        if factIds:
            lim = " where id in " + ids2str(factIds)
        else:
            lim = ""
        self.registerTags(set(parseTags(
            " ".join(self.db.column0("select distinct tags from facts"+lim)))))

    def registerTags(self, tags):
        r = []
        for t in tags:
            r.append({'t': t})
        self.db.statements("""
insert or ignore into tags (modified, name) values (%d, :t)""" % intTime(),
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
            return {'id': row[0], 't': fn(tags, row[1])}
        self.db.statements("""
update facts set tags = :t, modified = %d
where id = :id""" % intTime(), [fix(row) for row in res])
        # update q/a cache
        self.updateCardQACacheFromIds(fids, type="facts")
        self.flushMod()
        self.finishProgress()
        self.refreshSession()

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
        self.db.flush()

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

    # Meta vars
    ##########################################################################

    def getInt(self, key, type=int):
        ret = self.db.scalar("select value from deckVars where key = :k",
                            k=key)
        if ret is not None:
            ret = type(ret)
        return ret

    def getFloat(self, key):
        return self.getInt(key, float)

    def getBool(self, key):
        ret = self.db.scalar("select value from deckVars where key = :k",
                            k=key)
        if ret is not None:
            # hack to work around ankidroid bug
            if ret.lower() == "true":
                return True
            elif ret.lower() == "false":
                return False
            else:
                ret = not not int(ret)
        return ret

    def getVar(self, key):
        "Return value for key as string, or None."
        return self.db.scalar("select value from deckVars where key = :k",
                             k=key)

    def setVar(self, key, value, mod=True):
        if self.db.scalar("""
select value = :value from deckVars
where key = :key""", key=key, value=value):
            return
        # can't use insert or replace as it confuses the undo code
        if self.db.scalar("select 1 from deckVars where key = :key", key=key):
            self.db.statement("update deckVars set value=:value where key = :key",
                             key=key, value=value)
        else:
            self.db.statement("insert into deckVars (key, value) "
                             "values (:key, :value)", key=key, value=value)
        if mod:
            self.setModified()

    def setVarDefault(self, key, value):
        if not self.db.scalar(
            "select 1 from deckVars where key = :key", key=key):
            self.db.statement("insert into deckVars (key, value) "
                             "values (:key, :value)", key=key, value=value)

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

    # Media
    ##########################################################################

    def mediaDir(self, create=False):
        "Return the media directory if exists. None if couldn't create."
        if self.mediaPrefix:
            dir = os.path.join(
                self.mediaPrefix, os.path.basename(self.path))
        else:
            dir = self.path
        dir = re.sub("(?i)\.(anki)$", ".media", dir)
        if create == None:
            # don't create, but return dir
            return dir
        if not os.path.exists(dir) and create:
            try:
                os.makedirs(dir)
            except OSError:
                # permission denied
                return None
        if not dir or not os.path.exists(dir):
            return None
        # change to the current dir
        os.chdir(dir)
        return dir

    def addMedia(self, path):
        """Add PATH to the media directory.
Return new path, relative to media dir."""
        return anki.media.copyToMedia(self, path)

    def renameMediaDir(self, oldPath):
        "Copy oldPath to our current media dir. "
        assert os.path.exists(oldPath)
        newPath = self.mediaDir(create=None)
        # copytree doesn't want the dir to exist
        try:
            shutil.copytree(oldPath, newPath)
        except:
            # FIXME: should really remove everything in old dir instead of
            # giving up
            pass

    # DB helpers
    ##########################################################################

    def save(self, config=True):
        "Commit any pending changes to disk."
        if self.lastLoaded == self.modified:
            return
        self.lastLoaded = self.modified
        if config:
            self.flushConfig()
        self.db.commit()

    def flushConfig(self):
        print "make flushConfig() more intelligent"
        self._config = unicode(simplejson.dumps(self.config))
        self._qconf = unicode(simplejson.dumps(self.qconf))
        self._data = unicode(simplejson.dumps(self.data))

    def close(self):
        if self.db:
            self.db.rollback()
            self.db.close()
            self.db = None
            self.s = None
        self.engine.dispose()
        runHook("deckClosed")

    def rollback(self):
        "Roll back the current transaction and reset session state."
        self.db.rollback()
        self.db.expunge_all()
        self.db.update(self)
        self.db.refresh(self)

    def refreshSession(self):
        "Flush and expire all items from the session."
        self.db.flush()
        self.db.expire_all()

    def openSession(self, first=False):
        "Open a new session. Assumes old session is already closed."
        self.db = SessionHelper(self.Session())
        self.s = self.db
        self.db.update(self)
        self.refreshSession()

    def closeSession(self):
        "Close the current session, saving any changes. Do nothing if no session."
        if self.db:
            self.save()
            try:
                self.db.expunge(self)
            except:
                import sys
                sys.stderr.write("ERROR expunging deck..\n")
            self.db.close()
            self.db = None
            self.s = None

    def setModified(self):
        #import traceback; traceback.print_stack()
        self.modified = intTime()

    def setSchemaModified(self):
        self.schemaMod = intTime()
        anki.graves.forgetAll(self.db)

    def getFactPos(self):
        "Return next fact position, incrementing it."
        # note this is incremented even if facts are not added; gaps are not a bug
        p = self.config['nextFactPos']
        self.config['nextFactPos'] += 1
        self.setModified()
        return p

    def flushMod(self):
        "Mark modified and flush to DB."
        self.setModified()
        self.db.flush()

    def saveAs(self, newPath):
        "Returns new deck. Old connection is closed without saving."
        oldMediaDir = self.mediaDir()
        self.flushConfig()
        self.db.flush()
        # remove new deck if it exists
        try:
            os.unlink(newPath)
        except OSError:
            pass
        self.startProgress()
        # copy tables, avoiding implicit commit on current db
        DeckStorage.Deck(newPath, backup=False).close()
        new = sqlite.connect(newPath)
        for table in self.db.column0(
            "select name from sqlite_master where type = 'table'"):
            if table.startswith("sqlite_"):
                continue
            new.execute("delete from %s" % table)
            cols = [str(x[1]) for x in new.execute(
                "pragma table_info('%s')" % table).fetchall()]
            q = "select 'insert into %(table)s values("
            q += ",".join(["'||quote(\"" + col + "\")||'" for col in cols])
            q += ")' from %(table)s"
            q = q % {'table': table}
            c = 0
            for row in self.db.execute(q):
                new.execute(row[0])
                if c % 1000:
                    self.updateProgress()
                c += 1
        # save new, close both
        new.commit()
        new.close()
        self.close()
        # open again in orm
        newDeck = DeckStorage.Deck(newPath, backup=False)
        # move media
        if oldMediaDir:
            newDeck.renameMediaDir(oldMediaDir)
        # forget sync name
        newDeck.syncName = u""
        newDeck.db.commit()
        # and return the new deck
        self.finishProgress()
        return newDeck

    # Syncing
    ##########################################################################
    # toggling does not bump deck mod time, since it may happen on upgrade,
    # and the variable is not synced

    def enableSyncing(self):
        self.syncName = unicode(checksum(self.path.encode("utf-8")))
        self.db.commit()

    def disableSyncing(self):
        self.syncName = u""
        self.db.commit()

    def syncingEnabled(self):
        return self.syncName

    def checkSyncHash(self):
        if self.syncName and self.syncName != checksum(self.path.encode("utf-8")):
            self.notify(_("""\
Because '%s' has been moved or copied, automatic synchronisation \
has been disabled (ERR-0100).

You can disable this check in Settings>Preferences>Network.""") % self.name())
            self.disableSyncing()
            self.syncName = u""

    # DB maintenance
    ##########################################################################

    def recoverCards(self, ids):
        "Put cards with damaged facts into new facts."
        # create a new model in case the user has modified a previous one
        from anki.stdmodels import RecoveryModel
        m = RecoveryModel()
        last = self.currentModel
        self.addModel(m)
        def repl(s):
            # strip field model text
            return re.sub("<span class=\"fm.*?>(.*?)</span>", "\\1", s)
        # add new facts, pointing old card at new fact
        for (id, q, a) in self.db.all("""
select id, question, answer from cards
where id in %s""" % ids2str(ids)):
            f = self.newFact()
            f['Question'] = repl(q)
            f['Answer'] = repl(a)
            try:
                f.tags = self.db.scalar("""
select group_concat(name, " ") from tags t, cardTags ct
where cardId = :cid and ct.tagId = t.id""", cid=id) or u""
                if f.tags:
                    f.tags = " " + f.tags + " "
            except:
                raise Exception("Your sqlite is too old.")
            cards = self.addFact(f)
            # delete the freshly created card and point old card to this fact
            self.db.statement("delete from cards where id = :id",
                             id=f.cards[0].id)
            self.db.statement("""
update cards set factId = :fid, cardModelId = :cmid, ordinal = 0
where id = :id""", fid=f.id, cmid=m.cardModels[0].id, id=id)
        # restore old model
        self.currentModel = last

    def fixIntegrity(self, quick=False):
        "Fix some problems and rebuild caches. Caller must .reset()"
        self.db.commit()
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
        # fields missing a field model
        ids = self.db.column0("""
select id from fields where fieldModelId not in (
select distinct id from fieldModels)""")
        if ids:
            self.db.statement("delete from fields where id in %s" %
                             ids2str(ids))
            problems.append(ngettext("Deleted %d field with missing field model",
                            "Deleted %d fields with missing field model", len(ids)) %
                            len(ids))
        # facts missing a field?
        ids = self.db.column0("""
select distinct facts.id from facts, fieldModels where
facts.modelId = fieldModels.modelId and fieldModels.id not in
(select fieldModelId from fields where factId = facts.id)""")
        if ids:
            self.deleteFacts(ids)
            problems.append(ngettext("Deleted %d fact with missing fields",
                            "Deleted %d facts with missing fields", len(ids)) %
                            len(ids))
        # cards missing a fact?
        ids = self.db.column0("""
select id from cards where factId not in (select id from facts)""")
        if ids:
            recover = True
            self.recoverCards(ids)
            problems.append(ngettext("Recovered %d card with missing fact",
                            "Recovered %d cards with missing fact", len(ids)) %
                            len(ids))
        # cards missing a card model?
        ids = self.db.column0("""
select id from cards where cardModelId not in
(select id from cardModels)""")
        if ids:
            recover = True
            self.recoverCards(ids)
            problems.append(ngettext("Recovered %d card with no card template",
                            "Recovered %d cards with no card template", len(ids)) %
                            len(ids))
        # cards with a card model from the wrong model
        ids = self.db.column0("""
select id from cards where cardModelId not in (select cm.id from
cardModels cm, facts f where cm.modelId = f.modelId and
f.id = cards.factId)""")
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
        ids = self.db.column0("""
select id from fields where factId not in (select id from facts)""")
        if ids:
            self.db.statement(
                "delete from fields where id in %s" % ids2str(ids))
            problems.append(ngettext("Deleted %d dangling field",
                            "Deleted %d dangling fields", len(ids)) %
                            len(ids))
        self.db.flush()
        if not quick:
            self.updateProgress()
            # these sometimes end up null on upgrade
            self.db.statement("update models set source = 0 where source is null")
            self.db.statement(
                "update cardModels set allowEmptyAnswer = 1, typeAnswer = '' "
                "where allowEmptyAnswer is null or typeAnswer is null")
            # fix tags
            self.updateProgress()
            self.db.statement("delete from tags")
            self.updateFactTags()
            print "should ensure tags having leading/trailing space"
            # make sure ordinals are correct
            self.updateProgress()
            self.db.statement("""
update fields set ordinal = (select ordinal from fieldModels
where id = fieldModelId)""")
            self.db.statement("""
update cards set ordinal = (select ordinal from cardModels
where cards.cardModelId = cardModels.id)""")
            # fix problems with stripping html
            self.updateProgress()
            fields = self.db.all("select id, value from fields")
            newFields = []
            for (id, value) in fields:
                newFields.append({'id': id, 'value': tidyHTML(value)})
            self.db.statements(
                "update fields set value=:value where id=:id",
                newFields)
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
            self.setSchemaModified()
            # and finally, optimize
            self.updateProgress()
            self.optimize()
            newSize = os.stat(self.path)[stat.ST_SIZE]
            save = (oldSize - newSize)/1024
            txt = _("Database rebuilt and optimized.")
            if save > 0:
                txt += "\n" + _("Saved %dKB.") % save
            problems.append(txt)
        # update deck and save
        if not quick:
            self.flushMod()
            self.save()
        self.refreshSession()
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
        self.db.commit()
        self.db.statement("vacuum")
        self.db.statement("analyze")

    # Undo/redo
    ##########################################################################

    def initUndo(self):
        # note this code ignores 'unique', as it's an sqlite reserved word
        self.undoStack = []
        self.redoStack = []
        self.undoEnabled = True
        self.db.statement(
            "create temporary table undoLog (seq integer primary key not null, sql text)")
        tables = self.db.column0(
            "select name from sqlite_master where type = 'table'")
        for table in tables:
            if table in ("undoLog", "sqlite_stat1"):
                continue
            columns = [r[1] for r in
                       self.db.all("pragma table_info(%s)" % table)]
            # insert
            self.db.statement("""
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
            self.db.statement(sql)
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
            self.db.statement(sql)

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
            self.db.statement("delete from undoLog")
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
        self.db.flush()
        if merge and self.undoStack:
            if self.undoStack[-1] and self.undoStack[-1][0] == name:
                # merge with last entry?
                return
        start = self._latestUndoRow()
        self.undoStack.append([name, start, None])

    def setUndoEnd(self, name):
        if not self.undoEnabled:
            return
        self.db.flush()
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
        self.db.flush()
        while 1:
            u = src.pop()
            if u:
                break
        (start, end) = (u[1], u[2])
        if end is None:
            end = self._latestUndoRow()
        sql = self.db.column0("""
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
        "Undo the last action(s). Caller must .reset()"
        self._undoredo(self.undoStack, self.redoStack)
        self.refreshSession()
        runHook("postUndoRedo")

    def redo(self):
        "Redo the last action(s). Caller must .reset()"
        self._undoredo(self.redoStack, self.undoStack)
        self.refreshSession()
        runHook("postUndoRedo")

    # Dynamic indices
    ##########################################################################

    def updateDynamicIndices(self):
        # determine required columns
        required = []
        if self.qconf['newTodayOrder'] == NEW_TODAY_ORDINAL:
            required.append("ordinal")
        if self.qconf['revCardOrder'] in (REV_CARDS_OLD_FIRST, REV_CARDS_NEW_FIRST):
            required.append("interval")
        cols = ["queue", "due", "groupId"] + required
        # update if changed
        if self.db.scalar(
            "select 1 from sqlite_master where name = 'ix_cards_multi'"):
            rows = self.db.all("pragma index_info('ix_cards_multi')")
        else:
            rows = None
        if not (rows and cols == [r[2] for r in rows]):
            self.db.statement("drop index if exists ix_cards_multi")
            self.db.statement("create index ix_cards_multi on cards (%s)" %
                              ", ".join(cols))
            self.db.statement("analyze")

mapper(Deck, deckTable, properties={
    '_qconf': deckTable.c.qconf,
    '_config': deckTable.c.config,
    '_data': deckTable.c.data,
})

# Shared decks
##########################################################################

sourcesTable = Table(
    'sources', metadata,
    Column('id', Integer, nullable=False, primary_key=True),
    Column('name', UnicodeText, nullable=False, default=""),
    Column('created', Integer, nullable=False, default=intTime),
    Column('lastSync', Integer, nullable=False, default=0),
    # -1 = never check, 0 = always check, 1+ = number of seconds passed.
    # not currently exposed in the GUI
    Column('syncPeriod', Integer, nullable=False, default=0))

# Labels
##########################################################################

def newCardOrderLabels():
    return {
        0: _("Add new cards in random order"),
        1: _("Add new cards to end of queue"),
        }

def newCardSchedulingLabels():
    return {
        0: _("Spread new cards out through reviews"),
        1: _("Show new cards after all other cards"),
        2: _("Show new cards before reviews"),
        }

# FIXME: order due is not very useful anymore
def revCardOrderLabels():
    return {
        0: _("Review cards from largest interval"),
        1: _("Review cards from smallest interval"),
        2: _("Review cards in order due"),
        3: _("Review cards in random order"),
        }

def failedCardOptionLabels():
    return {
        0: _("Show failed cards soon"),
        1: _("Show failed cards at end"),
        2: _("Show failed cards in 10 minutes"),
        3: _("Show failed cards in 8 hours"),
        4: _("Show failed cards in 3 days"),
        5: _("Custom failed cards handling"),
        }

# Deck storage
##########################################################################

class DeckStorage(object):

    def _getDeck(path, create, pool):
        engine = None
        try:
            (engine, session) = DeckStorage._attach(path, create, pool)
            s = session()
            if create:
                DeckStorage._addTables(engine)
                metadata.create_all(engine)
                DeckStorage._addConfig(engine)
                deck = DeckStorage._init(s)
                updateIndices(engine)
                engine.execute("analyze")
            else:
                ver = upgradeSchema(engine, s)
                # add any possibly new tables if we're upgrading
                if ver < DECK_VERSION:
                    DeckStorage._addTables(engine)
                    metadata.create_all(engine)
                deck = s.query(Deck).get(1)
                if not deck:
                    raise DeckAccessError(_("Deck missing core table"),
                                          type="nocore")
            # attach db vars
            deck.path = path
            deck.Session = session
            deck.engine = engine
            # db is new style; s old style
            deck.db = SessionHelper(s)
            deck.s = deck.db
            deck._initVars()
            if not create:
                upgradeDeck(deck)
            return deck
        except OperationalError, e:
            if engine:
                engine.dispose()
            if (str(e.orig).startswith("database table is locked") or
                str(e.orig).startswith("database is locked")):
                raise DeckAccessError(_("File is in use by another process"),
                                      type="inuse")
            else:
                raise e
    _getDeck = staticmethod(_getDeck)

    def _attach(path, create, pool=True):
        "Attach to a file, maybe initializing DB"
        path = "sqlite:///" + path.encode("utf-8")
        if pool:
            # open and lock connection for single use
            engine = create_engine(path, connect_args={'timeout': 0})
        else:
            # no pool & concurrent access w/ timeout
            engine = create_engine(
                path, poolclass=NullPool, connect_args={'timeout': 60})
        session = sessionmaker(bind=engine, autoflush=False, autocommit=True)
        if create:
            engine.execute("pragma page_size = 4096")
            engine.execute("pragma legacy_file_format = 0")
            engine.execute("vacuum")
        engine.execute("pragma cache_size = 20000")
        return (engine, session)
    _attach = staticmethod(_attach)

    def _init(s):
        "Add a new deck to the database. Return saved deck."
        deck = Deck()
        if sqlalchemy.__version__.startswith("0.4."):
            s.save(deck)
        else:
            s.add(deck)
        s.flush()
        return deck
    _init = staticmethod(_init)

    def _addConfig(s):
        "Add a default group & config."
        s.execute("""
insert into groupConfig values (1, :t, :name, :conf)""",
                  t=intTime(), name=_("Default Config"),
                  conf=simplejson.dumps(anki.groups.defaultConf))
        s.execute("""
insert into groups values (1, :t, "Default", 1)""",
                  t=intTime())
    _addConfig = staticmethod(_addConfig)

    def _addTables(s):
        "Add tables with syntax that older sqlalchemy versions don't support."
        sql = [
            """
create table tags (
id integer not null,
modified integer not null,
name text not null collate nocase unique,
primary key(id))""",
            """
create table groups (
id integer primary key autoincrement,
modified integer not null,
name text not null collate nocase unique,
confId integer not null)"""
        ]
        for table in sql:
            try:
                s.execute(table)
            except:
                pass

    _addTables = staticmethod(_addTables)

    def Deck(path, backup=True, pool=True, minimal=False):
        "Create a new deck or attach to an existing one. Path should be unicode."
        path = os.path.abspath(path)
        create = not os.path.exists(path)
        deck = DeckStorage._getDeck(path, create, pool)
        oldMod = deck.modified
        deck.qconf = simplejson.loads(deck._qconf)
        deck.config = simplejson.loads(deck._config)
        deck.data = simplejson.loads(deck._data)
        if minimal:
            return deck
        # check if deck has been moved, and disable syncing
        deck.checkSyncHash()
        # rebuild queue
        deck.reset()
        # make sure we haven't accidentally bumped the modification time
        assert deck.modified == oldMod
        return deck
    Deck = staticmethod(Deck)
