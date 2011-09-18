# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import time, os, random, re, stat, simplejson, datetime, copy, shutil
from anki.lang import _, ngettext
from anki.utils import ids2str, hexifyID, checksum, fieldChecksum, stripHTML, \
    intTime, splitFields
from anki.hooks import runHook, runFilter
from anki.sched import Scheduler
from anki.models import ModelManager
from anki.media import MediaManager
from anki.groups import GroupManager
from anki.tags import TagManager
from anki.consts import *
from anki.errors import AnkiError

import anki.latex # sets up hook
import anki.cards, anki.facts, anki.template, anki.cram, anki.find

defaultConf = {
    # scheduling options
    'activeGroups': [],
    'topGroup': 1,
    'curGroup': None,
    'revOrder': REV_CARDS_RANDOM,
    # other config
    'nextPos': 1,
    'fontFamilies': [
        [u'ＭＳ 明朝',u'ヒラギノ明朝 Pro W3',u'Kochi Mincho', u'東風明朝']
    ],
    'sortType': "factFld",
    'sortBackwards': False,
}

# this is initialized by storage.Deck
class _Deck(object):

    def __init__(self, db):
        self.db = db
        self.path = db._path
        self._lastSave = time.time()
        self.clearUndo()
        self.media = MediaManager(self)
        self.models = ModelManager(self)
        self.groups = GroupManager(self)
        self.tags = TagManager(self)
        self.load()
        if not self.crt:
            d = datetime.datetime.today()
            d -= datetime.timedelta(hours=4)
            d = datetime.datetime(d.year, d.month, d.day)
            d += datetime.timedelta(hours=4)
            self.crt = int(time.mktime(d.timetuple()))
        self.undoEnabled = False
        self.sessionStartReps = 0
        self.sessionStartTime = 0
        self.lastSessionStart = 0
        self._stdSched = Scheduler(self)
        self.sched = self._stdSched
        # check for improper shutdown
        self.cleanup()

    def name(self):
        n = os.path.splitext(os.path.basename(self.path))[0]
        return n

    # DB-related
    ##########################################################################

    def load(self):
        (self.crt,
         self.mod,
         self.scm,
         self.dty,
         self._usn,
         self.ls,
         self.conf,
         models,
         groups,
         gconf,
         tags) = self.db.first("""
select crt, mod, scm, dty, usn, ls,
conf, models, groups, gconf, tags from deck""")
        self.conf = simplejson.loads(self.conf)
        self.models.load(models)
        self.groups.load(groups, gconf)
        self.tags.load(tags)

    def flush(self, mod=None):
        "Flush state to DB, updating mod time."
        self.mod = intTime(1000) if mod is None else mod
        self.db.execute(
            """update deck set
crt=?, mod=?, scm=?, dty=?, usn=?, ls=?, conf=?""",
            self.crt, self.mod, self.scm, self.dty,
            self._usn, self.ls, simplejson.dumps(self.conf))
        self.models.flush()
        self.groups.flush()
        self.tags.flush()

    def save(self, name=None, mod=None):
        "Flush, commit DB, and take out another write lock."
        self.flush(mod=mod)
        self.db.commit()
        self.lock()
        self._markOp(name)
        self._lastSave = time.time()

    def autosave(self):
        "Save if 5 minutes has passed since last save."
        if time.time() - self._lastSave > 300:
            self.save()

    def lock(self):
        self.db.execute("update deck set mod=mod")

    def close(self, save=True):
        "Disconnect from DB."
        self.cleanup()
        if self.db:
            if save:
                self.save()
            else:
                self.rollback()
            self.db.close()
            self.db = None
            self.media.close()

    def reopen(self):
        "Reconnect to DB (after changing threads, etc). Doesn't reload."
        import anki.db
        if not self.db:
            self.db = anki.db.DB(self.path)
            self.media.connect()

    def rollback(self):
        self.db.rollback()
        self.load()
        self.lock()

    def modSchema(self, check=True):
        "Mark schema modified. Call this first so user can abort if necessary."
        if not self.schemaChanged():
            if check and not runFilter("modSchema", True):
                raise AnkiError("abortSchemaMod")
        self.scm = intTime()

    def schemaChanged(self):
        "True if schema changed since last sync."
        return self.scm > self.ls

    def setDirty(self):
        "Signal there are temp. suspended cards that need cleaning up on close."
        self.dty = True

    def cleanup(self):
        "Unsuspend any temporarily suspended cards."
        if self.dty:
            self.sched.onClose()
            self.dty = False

    def rename(self, path):
        raise "nyi"
        # close our DB connection
        self.close()
        # move to new path
        shutil.copy2(self.path, path)
        os.unlink(self.path)
        # record old dir
        olddir = self.media.dir()
        # reconnect & move media
        self.path = path
        self.reopen()
        self.media.move(olddir)

    def usn(self):
        return self._usn

    # Object creation helpers
    ##########################################################################

    def getCard(self, id):
        return anki.cards.Card(self, id)

    def getFact(self, id):
        return anki.facts.Fact(self, id=id)

    # Utils
    ##########################################################################

    def nextID(self, type, inc=True):
        type = "next"+type.capitalize()
        id = self.conf.get(type, 1)
        if inc:
            self.conf[type] = id+1
        return id

    def reset(self):
        "Rebuild the queue and reload data after DB modified."
        self.sched.reset()

    # Deletion logging
    ##########################################################################

    def _logRem(self, ids, type):
        tbl = "cards" if type == REM_CARD else "facts"
        self.db.executemany("insert into graves values (%d, ?, %d)" % (
            intTime(), type), ([x] for x in ids))

    # Facts
    ##########################################################################

    def factCount(self):
        return self.db.scalar("select count() from facts")

    def newFact(self):
        "Return a new fact with the current model."
        return anki.facts.Fact(self, self.models.current())

    def addFact(self, fact):
        "Add a fact to the deck. Return number of new cards."
        # check we have card models available, then save
        cms = self.findTemplates(fact)
        if not cms:
            return 0
        fact.flush()
        # randomize?
        if self.models.randomNew():
            due = self._randPos()
        else:
            due = self.nextID("pos")
        # add cards
        ncards = 0
        for template in cms:
            self._newCard(fact, template, due)
            ncards += 1
        return ncards

    def _randPos(self):
        return random.randrange(1, self.nextID("pos", inc=False))

    def remFacts(self, ids):
        self.remCards(self.db.list("select id from cards where fid in "+
                                   ids2str(ids)))

    def _remFacts(self, ids):
        "Bulk delete facts by ID. Don't call this directly."
        if not ids:
            return
        strids = ids2str(ids)
        # we need to log these independently of cards, as one side may have
        # more card templates
        self._logRem(ids, REM_FACT)
        self.db.execute("delete from facts where id in %s" % strids)
        self.db.execute("delete from fsums where fid in %s" % strids)

    # Card creation
    ##########################################################################

    def findTemplates(self, fact, checkActive=True):
        "Return (active), non-empty templates."
        ok = []
        model = fact.model()
        for template in model['tmpls']:
            if template['actv'] or not checkActive:
                # [cid, fid, mid, gid, ord, tags, flds]
                data = [1, 1, model['id'], 1, template['ord'],
                        "", fact.joinedFields()]
                now = self._renderQA(data)
                data[6] = "\x1f".join([""]*len(fact.fields))
                empty = self._renderQA(data)
                if now['q'] == empty['q']:
                    continue
                if not template['emptyAns']:
                    if now['a'] == empty['a']:
                        continue
                ok.append(template)
        return ok

    def genCards(self, fact, templates):
        "Generate cards for templates if cards not empty. Return cards."
        cards = []
        # if random mode, determine insertion point
        if self.models.randomNew():
            # if this fact has existing new cards, use their due time
            due = self.db.scalar(
                "select due from cards where fid = ? and queue = 0", fact.id)
            due = due or self._randPos()
        else:
            due = fact.id
        for template in self.findTemplates(fact, checkActive=False):
            if template not in templates:
                continue
            # if it doesn't already exist
            if not self.db.scalar(
                "select 1 from cards where fid = ? and ord = ?",
                fact.id, template['ord']):
                # create
                cards.append(self._newCard(fact, template, due))
        return cards

    # type 0 - when previewing in add dialog, only non-empty & active
    # type 1 - when previewing edit, only existing
    # type 2 - when previewing in models dialog, all
    def previewCards(self, fact, type=0):
        "Return uncommited cards for preview."
        if type == 0:
            cms = self.findTemplates(fact, checkActive=True)
        elif type == 1:
            cms = [c.template() for c in fact.cards()]
        else:
            cms = fact.model()['tmpls']
        if not cms:
            return []
        cards = []
        for template in cms:
            cards.append(self._newCard(fact, template, 1, flush=False))
        return cards

    def _newCard(self, fact, template, due, flush=True):
        "Create a new card."
        card = anki.cards.Card(self)
        card.fid = fact.id
        card.ord = template['ord']
        card.gid = template['gid'] or fact.gid
        card.due = due
        if flush:
            card.flush()
        return card

    # Cards
    ##########################################################################

    def cardCount(self):
        return self.db.scalar("select count() from cards")

    def remCards(self, ids):
        "Bulk delete cards by ID."
        if not ids:
            return
        sids = ids2str(ids)
        fids = self.db.list("select fid from cards where id in "+sids)
        # remove cards
        self._logRem(ids, REM_CARD)
        self.db.execute("delete from cards where id in "+sids)
        self.db.execute("delete from revlog where cid in "+sids)
        # then facts
        fids = self.db.list("""
select id from facts where id in %s and id not in (select fid from cards)""" %
                     ids2str(fids))
        self._remFacts(fids)

    # Field checksums and sorting fields
    ##########################################################################

    def _fieldData(self, sfids):
        return self.db.execute(
            "select id, mid, flds from facts where id in "+sfids)

    def updateFieldCache(self, fids, csum=True):
        "Update field checksums and sort cache, after find&replace, etc."
        sfids = ids2str(fids)
        r = []
        r2 = []
        for (fid, mid, flds) in self._fieldData(sfids):
            fields = splitFields(flds)
            model = self.models.get(mid)
            if csum:
                for f in model['flds']:
                    if f['uniq'] and fields[f['ord']]:
                        r.append((fid, mid, fieldChecksum(fields[f['ord']])))
            r2.append((stripHTML(fields[self.models.sortIdx(model)]), fid))
        if csum:
            self.db.execute("delete from fsums where fid in "+sfids)
            self.db.executemany("insert into fsums values (?,?,?)", r)
        # rely on calling code to bump usn+mod
        self.db.executemany("update facts set sfld = ? where id = ?", r2)

    # Q/A generation
    ##########################################################################

    def renderQA(self, ids=None, type="card"):
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
        return [self._renderQA(row)
                for row in self._qaData(where)]

    def _renderQA(self, data):
        "Returns hash of id, question, answer."
        # data is [cid, fid, mid, gid, ord, tags, flds]
        # unpack fields and create dict
        flist = splitFields(data[6])
        fields = {}
        model = self.models.get(data[2])
        for (name, (idx, conf)) in self.models.fieldMap(model).items():
            fields[name] = flist[idx]
            if fields[name]:
                fields[name] = '<span class="fm%s-%s">%s</span>' % (
                    hexifyID(data[2]), hexifyID(idx), fields[name])
            else:
                fields[name] = ""
        fields['Tags'] = data[5]
        fields['Model'] = model['name']
        fields['Group'] = self.groups.name(data[3])
        template = model['tmpls'][data[4]]
        fields['Template'] = template['name']
        # render q & a
        d = dict(id=data[0])
        for (type, format) in (("q", template['qfmt']), ("a", template['afmt'])):
            if type == "q":
                format = format.replace("cloze:", "cq:")
            else:
                if model['clozectx']:
                    name = "cactx:"
                else:
                    name = "ca:"
                format = format.replace("cloze:", name)
            fields = runFilter("mungeFields", fields, model, data, self)
            html = anki.template.render(format, fields)
            d[type] = runFilter(
                "mungeQA", html, type, fields, model, data, self)
        return d

    def _qaData(self, where=""):
        "Return [cid, fid, mid, gid, ord, tags, flds] db query"
        return self.db.execute("""
select c.id, f.id, f.mid, c.gid, c.ord, f.tags, f.flds
from cards c, facts f
where c.fid == f.id
%s""" % where)

    # Finding cards
    ##########################################################################

    def findCards(self, query, full=False):
        return anki.find.Finder(self).findCards(query, full)

    def findReplace(self, fids, src, dst, regex=None, field=None, fold=True):
        return anki.find.findReplace(self, fids, src, dst, regex, field, fold)

    def findDuplicates(self, fmids):
        return anki.find.findDuplicates(self, fmids)

    # Stats
    ##########################################################################

    def cardStats(self, card):
        from anki.stats import CardStats
        return CardStats(self, card).report()

    def stats(self):
        from anki.stats import DeckStats
        return DeckStats(self)

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

    # Schedulers and cramming
    ##########################################################################

    def stdSched(self):
        "True if scheduler changed."
        if self.sched.name != "std":
            self.cleanup()
            self.sched = self._stdSched
            return True

    def cramGroups(self, order="mod desc", min=0, max=None):
        self.stdSched()
        self.sched = anki.cram.CramScheduler(self, order, min, max)

    # Undo
    ##########################################################################

    def clearUndo(self):
        # [type, undoName, data]
        # type 1 = review; type 2 = checkpoint
        self._undo = None

    def undoName(self):
        "Undo menu item name, or None if undo unavailable."
        if not self._undo:
            return None
        return self._undo[1]

    def undo(self):
        if self._undo[0] == 1:
            self._undoReview()
        else:
            self._undoOp()

    def markReview(self, card):
        old = []
        if self._undo:
            if self._undo[0] == 1:
                old = self._undo[2]
            self.clearUndo()
        self._undo = [1, _("Review"), old + [copy.copy(card)]]

    def _undoReview(self):
        data = self._undo[2]
        c = data.pop()
        if not data:
            self.clearUndo()
        # write old data
        c.flush()
        # and delete revlog entry
        last = self.db.scalar(
            "select id from revlog where cid = ? "
            "order by id desc limit 1", c.id)
        self.db.execute("delete from revlog where id = ?", last)

    def _markOp(self, name):
        "Call via .save()"
        if name:
            self._undo = [2, name]
        else:
            # saving disables old checkpoint, but not review undo
            if self._undo and self._undo[0] == 2:
                self.clearUndo()

    def _undoOp(self):
        self.rollback()
        self.clearUndo()

    # DB maintenance
    ##########################################################################

    def fixIntegrity(self):
        "Fix possible problems and rebuild caches."
        self.modSchema(check=False)
        problems = []
        self.save()
        oldSize = os.stat(self.path)[stat.ST_SIZE]
        # delete any facts with missing cards
        ids = self.db.list("""
select id from facts where id not in (select distinct fid from cards)""")
        self._remFacts(ids)
        # tags
        self.tags.registerFacts()
        # field cache
        for m in self.models.all():
            self.updateFieldCache(self.models.fids(m))
        # and finally, optimize
        self.optimize()
        newSize = os.stat(self.path)[stat.ST_SIZE]
        save = (oldSize - newSize)/1024
        txt = _("Database rebuilt and optimized.")
        if save > 0:
            txt += "\n" + _("Saved %dKB.") % save
        problems.append(txt)
        self.save()
        return "\n".join(problems)

    def optimize(self):
        self.db.execute("vacuum")
        self.db.execute("analyze")
        self.lock()
