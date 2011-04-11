# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import time, os, random, re, stat, simplejson, datetime, copy
from anki.lang import _, ngettext
from anki.utils import parseTags, ids2str, hexifyID, \
     checksum, fieldChecksum, addTags, delTags, stripHTML, intTime, \
     splitFields
from anki.hooks import runHook, runFilter
from anki.sched import Scheduler
from anki.media import MediaRegistry
from anki.consts import *

import anki.latex # sets up hook
import anki.cards, anki.facts, anki.models, anki.template, anki.cram, \
    anki.groups

# Settings related to queue building. These may be loaded without the rest of
# the config to check due counts faster on mobile clients.
defaultQconf = {
    'groups': [],
    'newPerDay': 20,
    'newToday': [0, 0], # currentDay, count
    'newTodayOrder': NEW_TODAY_ORD,
    'newOrder': 1,
    'newSpread': NEW_CARDS_DISTRIBUTE,
    'revOrder': REV_CARDS_RANDOM,
    'collapseTime': 600,
    'repLim': 0,
    'timeLim': 600,
}

# other options
defaultConf = {
    'currentModelId': None,
    'currentGroupId': 1,
    'nextFid': 1,
    'nextCid': 1,
    'nextGid': 2,
    'nextGcid': 2,
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
        self.load()
        if not self.crt:
            d = datetime.datetime.today()
            d -= datetime.timedelta(hours=4)
            d = datetime.datetime(d.year, d.month, d.day)
            d += datetime.timedelta(hours=4)
            self.crt = int(time.mktime(d.timetuple()))
        self.modelCache = {}
        self.undoEnabled = False
        self.sessionStartReps = 0
        self.sessionStartTime = 0
        self.lastSessionStart = 0
        # counter for reps since deck open
        self.reps = 0
        self._stdSched = Scheduler(self)
        self.sched = self._stdSched
        self.media = MediaRegistry(self)
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
         self.syncName,
         self.lastSync,
         self.qconf,
         self.conf,
         self.data) = self.db.first("""
select crt, mod, scm, dty, syncName, lastSync,
qconf, conf, data from deck""")
        self.qconf = simplejson.loads(self.qconf)
        self.conf = simplejson.loads(self.conf)
        self.data = simplejson.loads(self.data)

    def flush(self):
        "Flush state to DB, updating mod time."
        self.mod = intTime()
        self.db.execute(
            """update deck set
crt=?, mod=?, scm=?, dty=?, syncName=?, lastSync=?,
qconf=?, conf=?, data=?""",
            self.crt, self.mod, self.scm, self.dty,
            self.syncName, self.lastSync,
            simplejson.dumps(self.qconf),
            simplejson.dumps(self.conf), simplejson.dumps(self.data))

    def save(self, name=None):
        "Flush, commit DB, and take out another write lock."
        self.flush()
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

    def reopen(self):
        "Reconnect to DB (after changing threads, etc). Doesn't reload."
        import anki.db
        if not self.db:
            self.db = anki.db.DB(self.path)

    def rollback(self):
        self.db.rollback()
        self.load()
        self.lock()

    def modSchema(self):
        if not self.schemaChanged():
            # next sync will be full
            self.emptyTrash()
            runHook("modSchema")
        self.scm = intTime()

    def schemaChanged(self):
        "True if schema changed since last sync, or syncing off."
        return self.scm > self.lastSync

    def setDirty(self):
        self.dty = True

    def cleanup(self):
        if self.dty:
            self.sched.onClose()
            self.dty = False

    # Object creation helpers
    ##########################################################################

    def getCard(self, id):
        return anki.cards.Card(self, id)

    def getFact(self, id):
        return anki.facts.Fact(self, id=id)

    def getModel(self, mid, cache=True):
        "Memoizes; call .reset() to reset cache."
        if cache and mid in self.modelCache:
            return self.modelCache[mid]
        m = anki.models.Model(self, mid)
        if cache:
            self.modelCache[mid] = m
        return m

    # Utils
    ##########################################################################

    def nextID(self, type):
        type = "next"+type.capitalize()
        id = self.conf.get(type, 1)
        self.conf[type] = id+1
        return id

    def reset(self):
        "Rebuild the queue and reload data after DB modified."
        self.modelCache = {}
        self.sched.reset()

    # Facts
    ##########################################################################

    def factCount(self):
        return self.db.scalar("select count() from facts where crt != 0")

    def newFact(self):
        "Return a new fact with the current model."
        return anki.facts.Fact(self, self.currentModel())

    def addFact(self, fact):
        "Add a fact to the deck. Return number of new cards."
        # check we have card models available
        cms = self.findTemplates(fact)
        if not cms:
            return 0
        # flush the fact
        fact.id = self.nextID("fid")
        fact.flush()
        # notice any new tags
        self.registerTags(fact.tags)
        # if random mode, determine insertion point
        if self.qconf['newOrder'] == NEW_CARDS_RANDOM:
            due = random.randrange(0, 1000000)
        else:
            due = fact.id
        # add cards
        ncards = 0
        for template in cms:
            self._newCard(fact, template, due)
            ncards += 1
        return ncards

    def _delFacts(self, ids):
        "Bulk delete facts by ID. Don't call this directly."
        if not ids:
            return
        strids = ids2str(ids)
        self.db.execute("delete from facts where id in %s" % strids)
        self.db.execute("delete from fsums where fid in %s" % strids)

    def _delDanglingFacts(self):
        "Delete any facts without cards. Don't call this directly."
        ids = self.db.list("""
select id from facts where id not in (select distinct fid from cards)""")
        self._delFacts(ids)
        return ids

    # Card creation
    ##########################################################################

    def findTemplates(self, fact, checkActive=True):
        "Return (active), non-empty templates."
        ok = []
        model = fact.model()
        for template in model.templates:
            if template['actv'] or not checkActive:
                # [cid, fid, mid, gid, ord, tags, flds]
                data = [1, 1, model.id, 1, template['ord'],
                        "", fact.joinedFields()]
                now = self._renderQA(model, "", data)
                data[6] = "\x1f".join([""]*len(fact._fields))
                empty = self._renderQA(model, "", data)
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
        if self.qconf['newOrder'] == NEW_CARDS_RANDOM:
            # if this fact has existing new cards, use their due time
            due = self.db.scalar(
                "select due from cards where fid = ? and queue = 0", fact.id)
            due = due or random.randrange(1, 1000000)
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
            cms = fact.model().templates
        if not cms:
            return []
        cards = []
        for template in cms:
            cards.append(self._newCard(fact, template, 1, flush=False))
        return cards

    def _newCard(self, fact, template, due, flush=True):
        "Create a new card."
        card = anki.cards.Card(self)
        card.id = self.nextID("cid")
        card.fid = fact.id
        card.ord = template['ord']
        card.gid = self.defaultGroup(template['gid'] or fact.gid)
        card.due = due
        if flush:
            card.flush()
        return card

    # Cards
    ##########################################################################

    def cardCount(self):
        return self.db.scalar("select count() from cards where crt != 0")

    def delCard(self, id):
        "Delete a card given its id. Delete any unused facts."
        self.delCards([id])

    def delCards(self, ids):
        "Bulk delete cards by ID."
        if not ids:
            return
        sids = ids2str(ids)
        if self.schemaChanged():
            # immediate delete?
            self.db.execute("delete from cards where id in %s" % sids)
            self.db.execute("delete from revlog where cid in %s" % sids)
            # remove any dangling facts
            self._delDanglingFacts()
        else:
            # trash
            sfids = ids2str(
                self.db.list("select fid from cards where id in "+sids))
            # need to handle delete of fsums/revlog remotely after sync
            self.db.execute(
                "update cards set crt = 0, queue = -4, mod = ? where id in "+sids,
                intTime())
            self.db.execute(
                "update facts set crt = 0, mod = ? where id in "+sfids,
                intTime())
            self.db.execute("delete from fsums where fid in "+sfids)
            self.db.execute("delete from revlog where cid in "+sids)

    def emptyTrash(self):
        self.db.executescript("""
delete from facts where id in (select fid from cards where queue = -4);
delete from cards where queue = -4;""")

    # Models
    ##########################################################################

    def currentModel(self):
        return self.getModel(self.conf['currentModelId'])

    def models(self):
        "Return a dict of mid -> model."
        mods = {}
        for m in [self.getModel(id) for id in self.db.list(
            "select id from models")]:
            mods[m.id] = m
        return mods

    def addModel(self, model):
        model.flush()
        self.conf['currentModelId'] = model.id

    def delModel(self, mid):
        "Delete MODEL, and all its cards/facts."
        # do a direct delete
        self.modSchema()
        # delete facts/cards
        self.delCards(self.db.list("""
select id from cards where fid in (select id from facts where mid = ?)""",
                                      mid))
        # then the model
        self.db.execute("delete from models where id = ?", mid)
        # GUI should ensure last model is not deleted
        if self.conf['currentModelId'] == mid:
            self.conf['currentModelId'] = self.db.scalar(
                "select id from models limit 1")

    def allCSS(self):
        return "\n".join(self.db.list("select css from models"))

    # Field checksums and sorting fields
    ##########################################################################

    def _fieldData(self, sfids):
        return self.db.execute(
            "select id, mid, flds from facts where id in "+sfids)

    def updateFieldCache(self, fids, csum=True):
        "Update field checksums and sort cache, after find&replace, etc."
        sfids = ids2str(fids)
        mods = self.models()
        r = []
        r2 = []
        for (fid, mid, flds) in self._fieldData(sfids):
            fields = splitFields(flds)
            model = mods[mid]
            if csum:
                for f in model.fields:
                    if f['uniq'] and fields[f['ord']]:
                        r.append((fid, mid, fieldChecksum(fields[f['ord']])))
            r2.append((stripHTML(fields[model.sortIdx()])[
                :SORT_FIELD_LEN], fid))
        if csum:
            self.db.execute("delete from fsums where fid in "+sfids)
            self.db.executemany("insert into fsums values (?,?,?)", r)
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
        mods = self.models()
        groups = dict(self.db.all("select id, name from groups"))
        return [self._renderQA(mods[row[2]], groups[row[3]], row)
                for row in self._qaData(where)]

    # fixme: don't need gid or data
    def _renderQA(self, model, gname, data):
        "Returns hash of id, question, answer."
        # data is [cid, fid, mid, gid, ord, tags, flds]
        # unpack fields and create dict
        flist = splitFields(data[6])
        fields = {}
        for (name, (idx, conf)) in model.fieldMap().items():
            fields[name] = flist[idx]
            if fields[name]:
                fields[name] = '<span class="fm%s-%s">%s</span>' % (
                    hexifyID(data[2]), hexifyID(idx), fields[name])
            else:
                fields[name] = ""
        fields['Tags'] = data[5]
        fields['Model'] = model.name
        fields['Group'] = gname
        template = model.templates[data[4]]
        fields['Template'] = template['name']
        # render q & a
        d = dict(id=data[0])
        for (type, format) in (("q", template['qfmt']), ("a", template['afmt'])):
            if type == "q":
                format = format.replace("cloze:", "cq:")
            else:
                format = format.replace("cloze:", "ca:")
            fields = runFilter("mungeFields", fields, model, gname, data, self)
            html = anki.template.render(format, fields)
            d[type] = runFilter(
                "mungeQA", html, type, fields, model, gname, data, self)
        return d

    def _qaData(self, where=""):
        "Return [cid, fid, mid, gid, ord, tags, flds] db query"
        return self.db.execute("""
select c.id, f.id, f.mid, c.gid, c.ord, f.tags, f.flds
from cards c, facts f
where c.fid == f.id
%s""" % where)

    # Tags
    ##########################################################################

    def tagList(self):
        return self.db.list("select name from tags order by name")

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
        newTags = parseTags(tags)
        # cache tag names
        self.registerTags(newTags)
        # find facts missing the tags
        if add:
            l = "tags not "
            fn = addTags
        else:
            l = "tags "
            fn = delTags
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
        self.registerTags(parseTags(tags))

    def delTags(self, ids, tags):
        self.addTags(ids, tags, False)

    # Groups
    ##########################################################################

    def groups(self):
        "A list of all group names."
        return self.db.list("select name from groups order by name")

    def groupName(self, id):
        return self.db.scalar("select name from groups where id = ?", id)

    def groupId(self, name):
        "Return the id for NAME, creating if necessary."
        id = self.db.scalar("select id from groups where name = ?", name)
        if not id:
            id = self.db.execute(
                "insert into groups values (?,?,?,?, ?)",
                self.nextID("gid"), intTime(), name, 1,
                simplejson.dumps(anki.groups.defaultData)).lastrowid
        return id

    def defaultGroup(self, id):
        if id == 1:
            return 1
        return self.db.scalar("select id from groups where id = ?", id) or 1

    def delGroup(self, gid):
        self.modSchema()
        self.db.execute("update cards set gid = 1 where gid = ?", gid)
        self.db.execute("update facts set gid = 1 where gid = ?", gid)
        self.db.execute("delete from groups where id = ?", gid)

    def setGroup(self, cids, gid):
        self.db.execute(
            "update cards set gid = ? where id in "+ids2str(cids), gid)

    # Group configuration
    ##########################################################################

    def groupConfs(self):
        "Return [name, id]."
        return self.db.all("select name, id from gconf order by name")

    def groupConf(self, gcid):
        return simplejson.loads(
            self.db.scalar(
                "select conf from gconf where id = ?", gcid))

    # Tag-based selective study
    ##########################################################################

    def selTagFids(self, yes, no):
        l = []
        # find facts that match yes
        lim = ""
        args = []
        query = "select id from facts"
        if not yes and not no:
            pass
        else:
            if yes:
                lim += " or ".join(["tags like ?" for t in yes])
                args += ['%% %s %%' % t for t in yes]
            if no:
                lim2 = " and ".join(["tags not like ?" for t in no])
                if lim:
                    lim = "(%s) and %s" % (lim, lim2)
                else:
                    lim = lim2
                args += ['%% %s %%' % t for t in no]
            query += " where " + lim
        return self.db.list(query, *args)

    def setGroupForTags(self, yes, no, gid):
        fids = self.selTagFids(yes, no)
        self.db.execute(
            "update cards set gid = ? where fid in "+ids2str(fids),
            gid)

    # Finding cards
    ##########################################################################

    def findCards(self, query):
        import anki.find
        return anki.find.Finder(self).findCards(query)

    def findReplace(self, *args, **kwargs):
        import anki.find
        return anki.find.findReplace(self, *args, **kwargs)

    def findDuplicates(self, fmids):
        import anki.find
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

    # Syncing
    ##########################################################################

    def enableSyncing(self):
        self.syncName = self.getSyncName()

    def disableSyncing(self):
        self.syncName = u""

    def syncingEnabled(self):
        return not not self.syncName

    def genSyncName(self):
        return unicode(checksum(self.path.encode("utf-8")))

    def syncHashBad(self):
        if self.syncName and self.syncName != self.genSyncName():
            self.disableSyncing()
            return True

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
            "select time from revlog where cid = ? "
            "order by time desc limit 1", c.id)
        self.db.execute("delete from revlog where time = ?", last)

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
        problems = []
        self.save()
        oldSize = os.stat(self.path)[stat.ST_SIZE]
        self.modSchema()
        # tags
        self.db.execute("delete from tags")
        self.updateFactTags()
        # field cache
        for m in self.models().values():
            self.updateFieldCache(m.fids())
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
