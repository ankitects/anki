# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

import time, os, random, re, stat, simplejson

from anki.lang import _, ngettext
from anki.utils import parseTags, tidyHTML, ids2str, hexifyID, \
     checksum, fieldChecksum, addTags, delTags, stripHTML, intTime, \
     splitFields
from anki.hooks import runHook, runFilter
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
    'nextFid': 1,
    'nextCid': 1,
    'nextGid': 2,
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
    ]
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

    def name(self):
        n = os.path.splitext(os.path.basename(self.path))[0]
        return n

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
        "Flush, commit DB, and take out another write lock."
        self.flush()
        self.db.commit()
        self.lock()

    def lock(self):
        self.db.execute("update deck set mod=mod")

    def close(self, save=True):
        "Disconnect from DB."
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
        self.lock()

    def modSchema(self):
        if not self.schemaDirty():
            # next sync will be full
            self.emptyTrash()
        self.schema = intTime()

    def schemaDirty(self):
        "True if schema changed since last sync, or syncing off."
        return self.schema > self.lastSync

    # Object creation helpers
    ##########################################################################

    def getCard(self, id):
        return anki.cards.Card(self, id)

    def getFact(self, id):
        return anki.facts.Fact(self, id=id)

    def getTemplate(self, mid, ord):
        return self.getModel(mid).templates[ord]

    def getModel(self, mid):
        return anki.models.Model(self, mid)

    # Utils
    ##########################################################################

    def nextID(self, type):
        type = "next"+type.capitalize()
        id = self.conf.get(type, 1)
        self.conf[type] = id+1
        return id

    def reset(self):
        "Rebuild the queue and reload data after DB modified."
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
            return None
        # flush the fact
        fact.id = self.nextID("fid")
        fact.flush()
        # notice any new tags
        self.registerTags(fact.tags)
        # if random mode, determine insertion point
        if self.qconf['newCardOrder'] == NEW_CARDS_RANDOM:
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
                # [cid, fid, mid, gid, ord, tags, flds, data]
                data = [1, 1, model.id, 1, template['ord'],
                        "", fact.joinedFields(), ""]
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
        if self.qconf['newCardOrder'] == NEW_CARDS_RANDOM:
            # if this fact has existing new cards, use their due time
            due = self.db.scalar(
                "select due from cards where fid = ? and queue = 2", fact.id)
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
        card.gid = template['gid'] or fact.gid
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
        if self.schemaDirty():
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

    def _renderQA(self, model, gname, data, filters=True):
        "Returns hash of id, question, answer."
        # data is [cid, fid, mid, gid, ord, tags, flds, data]
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
        fields['Group'] = gname
        template = model.templates[data[4]]
        fields['Template'] = template['name']
        # render q & a
        d = dict(id=data[0])
        for (type, format) in (("q", template['qfmt']), ("a", template['afmt'])):
            # if filters:
            #     fields = runFilter("renderQA.pre", fields, , self)
            html = anki.template.render(format, fields)
            # if filters:
            #     d[type] = runFilter("renderQA.post", html, fields, meta, self)
            self.media.registerText(html)
            d[type] = html
        return d

    def _qaData(self, where=""):
        "Return [cid, fid, mid, gid, ord, tags, flds, data] db query"
        return self.db.execute("""
select c.id, f.id, m.id, g.id, c.ord, f.tags, f.flds, f.data
from cards c, facts f, models m, groups g
where c.fid == f.id and f.mid == m.id and c.gid = g.id
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
        return self.db.list("select name from groups")

    def groupId(self, name):
        "Return the id for NAME, creating if necessary."
        id = self.db.scalar("select id from groups where name = ?", name)
        if not id:
            id = self.db.execute("insert into groups values (?,?,?,?)",
                                 self.nextID("gid"), intTime(), name,
                                 1).lastrowid
        return id

    def delGroup(self, gid):
        self.db.scalar("delete from groups where id = ?", gid)

    def groupConf(self, gid):
        return simplejson.loads(
            self.db.scalar("""
select conf from gconf where id = (select gcid from groups where id = ?)""",
                           gid))

    def activeGroups(self, type):
        return self.qconf[type+"Groups"]

    def setActiveGroups(self, type, list):
        self.qconf[type+"Groups"] = list

    def setGroup(self, cids, gid):
        self.db.execute(
            "update cards set gid = ? where id in "+ids2str(cids), gid)

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
        return anki.find.findCards(self, query)

    def findReplace(self, *args, **kwargs):
        import anki.find
        return anki.find.findReplace(self, *args, **kwargs)

    def findDuplicates(self, fmids):
        import anki.find
        return anki.find.findDuplicates(self, fmids)

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
        return self.syncName

    def genSyncName(self):
        return unicode(checksum(self.path.encode("utf-8")))

    def syncHashBad(self):
        if self.syncName and self.syncName != self.genSyncName():
            self.disableSyncing()
            return True

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
        self.lock()

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
        newstart = self._latestUndoRow()
        for c, s in enumerate(sql):
            self.engine.execute(s)
        newend = self._latestUndoRow()
        dst.append([u[0], newstart, newend])

    def undo(self):
        "Undo the last action(s)."
        self._undoredo(self.undoStack, self.redoStack)

    def redo(self):
        "Redo the last action(s)."
        self._undoredo(self.redoStack, self.undoStack)

    # DB maintenance
    ##########################################################################

    def fixIntegrity(self):
        "Fix possible problems and rebuild caches."
        problems = []
        self.save()
        self.resetUndo()
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
