# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import time, os, random, re, stat, simplejson, datetime, copy, shutil, sys
from anki.lang import _, ngettext
from anki.utils import ids2str, hexifyID, checksum, fieldChecksum, stripHTML, \
    intTime, splitFields, joinFields, maxID
from anki.hooks import runHook, runFilter
from anki.sched import Scheduler
from anki.models import ModelManager
from anki.media import MediaManager
from anki.decks import DeckManager
from anki.tags import TagManager
from anki.consts import *
from anki.errors import AnkiError

import anki.latex # sets up hook
import anki.cards, anki.notes, anki.template, anki.cram, anki.find

defaultConf = {
    # review options
    'activeDecks': [1],
    'curDeck': 1,
    'newSpread': NEW_CARDS_DISTRIBUTE,
    'collapseTime': 1200,
    'timeLim': 0,
    'estTimes': True,
    'dueCounts': True,
    # other config
    'curModel': None,
    'nextPos': 1,
    'sortType': "noteFld",
    'sortBackwards': False,
}

# this is initialized by storage.Collection
class _Collection(object):

    def __init__(self, db, server=False):
        self.db = db
        self.path = db._path
        self.server = server
        self._lastSave = time.time()
        self.clearUndo()
        self.media = MediaManager(self)
        self.models = ModelManager(self)
        self.decks = DeckManager(self)
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
         decks,
         dconf,
         tags) = self.db.first("""
select crt, mod, scm, dty, usn, ls,
conf, models, decks, dconf, tags from col""")
        self.conf = simplejson.loads(self.conf)
        self.models.load(models)
        self.decks.load(decks, dconf)
        self.tags.load(tags)

    def setMod(self):
        """Mark DB modified.

DB operations and the deck/tag/model managers do this automatically, so this
is only necessary if you modify properties of this object or the conf dict."""
        self.db.mod = True

    def flush(self, mod=None):
        "Flush state to DB, updating mod time."
        self.mod = intTime(1000) if mod is None else mod
        self.db.execute(
            """update col set
crt=?, mod=?, scm=?, dty=?, usn=?, ls=?, conf=?""",
            self.crt, self.mod, self.scm, self.dty,
            self._usn, self.ls, simplejson.dumps(self.conf))

    def save(self, name=None, mod=None):
        "Flush, commit DB, and take out another write lock."
        # let the managers conditionally flush
        self.models.flush()
        self.decks.flush()
        self.tags.flush()
        # and flush deck + bump mod if db has been changed
        if self.db.mod:
            self.flush(mod=mod)
            self.db.commit()
            self.lock()
            self.db.mod = False
        self._markOp(name)
        self._lastSave = time.time()

    def autosave(self):
        "Save if 5 minutes has passed since last save."
        if time.time() - self._lastSave > 300:
            self.save()

    def lock(self):
        # make sure we don't accidentally bump mod time
        mod = self.db.mod
        self.db.execute("update col set mod=mod")
        self.db.mod = mod

    def close(self, save=True):
        "Disconnect from DB."
        if self.db:
            self.cleanup()
            if save:
                self.save()
            else:
                self.rollback()
            if not self.server:
                self.db.execute("pragma journal_mode = delete")
            self.db.close()
            self.db = None
            self.media.close()

    def reopen(self):
        "Reconnect to DB (after changing threads, etc)."
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
        self.scm = intTime(1000)

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

    def usn(self):
        return self._usn if self.server else -1

    def beforeUpload(self):
        "Called before a full upload."
        tbls = "notes", "cards", "revlog", "graves"
        for t in tbls:
            self.db.execute("update %s set usn=0 where usn=-1" % t)
        self._usn += 1
        self.models.beforeUpload()
        self.tags.beforeUpload()
        self.decks.beforeUpload()
        self.modSchema()
        self.ls = self.scm
        self.close()

    # Object creation helpers
    ##########################################################################

    def getCard(self, id):
        return anki.cards.Card(self, id)

    def getNote(self, id):
        return anki.notes.Note(self, id=id)

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
        self.db.executemany("insert into graves values (%d, ?, %d)" % (
            self.usn(), type), ([x] for x in ids))

    # Notes
    ##########################################################################

    def noteCount(self):
        return self.db.scalar("select count() from notes")

    def newNote(self):
        "Return a new note with the current model."
        return anki.notes.Note(self, self.models.current())

    def addNote(self, note):
        "Add a note to the collection. Return number of new cards."
        # check we have card models available, then save
        cms = self.findTemplates(note)
        if not cms:
            return 0
        note.flush()
        # deck conf governs which of these are used
        due = self.nextID("pos")
        # add cards
        ncards = 0
        for template in cms:
            self._newCard(note, template, due)
            ncards += 1
        return ncards

    def remNotes(self, ids):
        self.remCards(self.db.list("select id from cards where nid in "+
                                   ids2str(ids)))

    def _remNotes(self, ids):
        "Bulk delete notes by ID. Don't call this directly."
        if not ids:
            return
        strids = ids2str(ids)
        # we need to log these independently of cards, as one side may have
        # more card templates
        self._logRem(ids, REM_NOTE)
        self.db.execute("delete from notes where id in %s" % strids)

    # Card creation
    ##########################################################################

    def findTemplates(self, note):
        "Return (active), non-empty templates."
        model = note.model()
        avail = self.models.availOrds(model, joinFields(note.fields))
        ok = []
        for t in model['tmpls']:
            if t['ord'] in avail:
                ok.append(t)
        return ok

    def genCards(self, nids):
        "Generate cards for non-empty templates, return ids to remove."
        # build map of (nid,ord) so we don't create dupes
        snids = ids2str(nids)
        have = {}
        for id, nid, ord in self.db.execute(
            "select id, nid, ord from cards where nid in "+snids):
            if nid not in have:
                have[nid] = {}
            have[nid][ord] = id
        # build cards for each note
        data = []
        ts = maxID(self.db)
        now = intTime()
        rem = []
        usn = self.usn()
        for nid, mid, did, flds in self.db.execute(
            "select id, mid, did, flds from notes where id in "+snids):
            model = self.models.get(mid)
            avail = self.models.availOrds(model, flds)
            for t in model['tmpls']:
                doHave = nid in have and t['ord'] in have[nid]
                # if have ord but empty, add cid to remove list
                # (may not have nid if generating before any cards added)
                if doHave and t['ord'] not in avail:
                    rem.append(have[nid][t['ord']])
                # if missing ord and is available, generate
                if not doHave and t['ord'] in avail:
                    data.append((ts, nid, t['did'] or did, t['ord'],
                                 now, usn, nid))
                    ts += 1
        # bulk update
        self.db.executemany("""
insert into cards values (?,?,?,?,?,?,0,0,?,0,0,0,0,0,0,0,"")""",
                            data)
        return rem

    # type 0 - when previewing in add dialog, only non-empty
    # type 1 - when previewing edit, only existing
    # type 2 - when previewing in models dialog, all templates
    def previewCards(self, note, type=0):
        if type == 0:
            cms = self.findTemplates(note)
        elif type == 1:
            cms = [c.template() for c in note.cards()]
        else:
            cms = note.model()['tmpls']
        if not cms:
            return []
        cards = []
        for template in cms:
            cards.append(self._newCard(note, template, 1, flush=False))
        return cards

    def _newCard(self, note, template, due, flush=True):
        "Create a new card."
        card = anki.cards.Card(self)
        card.nid = note.id
        card.ord = template['ord']
        card.did = template['did'] or note.did
        card.due = self._dueForDid(card.did, due)
        if flush:
            card.flush()
        return card

    def _dueForDid(self, did, due):
        conf = self.decks.confForDid(did)
        # in order due?
        if conf['new']['order']:
            return due
        else:
            # random mode; seed with note ts so all cards of this note get the
            # same random number
            r = random.Random()
            r.seed(due)
            return r.randrange(1, 2**32-1)

    # Cards
    ##########################################################################

    def isEmpty(self):
        return not self.db.scalar("select 1 from cards limit 1")

    def cardCount(self):
        return self.db.scalar("select count() from cards")

    def remCards(self, ids):
        "Bulk delete cards by ID."
        if not ids:
            return
        sids = ids2str(ids)
        nids = self.db.list("select nid from cards where id in "+sids)
        # remove cards
        self._logRem(ids, REM_CARD)
        self.db.execute("delete from cards where id in "+sids)
        self.db.execute("delete from revlog where cid in "+sids)
        # then notes
        nids = self.db.list("""
select id from notes where id in %s and id not in (select nid from cards)""" %
                     ids2str(nids))
        self._remNotes(nids)

    def remEmptyCards(self, ids):
        if not ids:
            return
        if runFilter("remEmptyCards", len(ids), True):
            self.remCards(ids)

    # Field checksums and sorting fields
    ##########################################################################

    def _fieldData(self, snids):
        return self.db.execute(
            "select id, mid, flds from notes where id in "+snids)

    def updateFieldCache(self, nids):
        "Update field checksums and sort cache, after find&replace, etc."
        snids = ids2str(nids)
        r = []
        for (nid, mid, flds) in self._fieldData(snids):
            fields = splitFields(flds)
            model = self.models.get(mid)
            r.append((stripHTML(fields[self.models.sortIdx(model)]),
                      fieldChecksum(fields[0]),
                      nid))
        # apply, relying on calling code to bump usn+mod
        self.db.executemany("update notes set sfld=?, csum=? where id=?", r)

    # Q/A generation
    ##########################################################################

    def renderQA(self, ids=None, type="card"):
        # gather metadata
        if type == "card":
            where = "and c.id in " + ids2str(ids)
        elif type == "note":
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
        # data is [cid, nid, mid, did, ord, tags, flds]
        # unpack fields and create dict
        flist = splitFields(data[6])
        fields = {}
        model = self.models.get(data[2])
        for (name, (idx, conf)) in self.models.fieldMap(model).items():
            fields[name] = flist[idx]
        fields['Tags'] = data[5]
        fields['Type'] = model['name']
        fields['Deck'] = self.decks.name(data[3])
        template = model['tmpls'][data[4]]
        fields['Card'] = template['name']
        # render q & a
        d = dict(id=data[0])
        for (type, format) in (("q", template['qfmt']), ("a", template['afmt'])):
            if type == "q":
                format = format.replace("cloze:", "cq:")
            else:
                format = format.replace("cloze:", "ca:")
            fields = runFilter("mungeFields", fields, model, data, self)
            html = anki.template.render(format, fields)
            d[type] = runFilter(
                "mungeQA", html, type, fields, model, data, self)
        return d

    def _qaData(self, where=""):
        "Return [cid, nid, mid, did, ord, tags, flds] db query"
        return self.db.execute("""
select c.id, f.id, f.mid, c.did, c.ord, f.tags, f.flds
from cards c, notes f
where c.nid == f.id
%s""" % where)

    # Finding cards
    ##########################################################################

    def findCards(self, query, full=False):
        return anki.find.Finder(self).findCards(query, full)

    def findReplace(self, nids, src, dst, regex=None, field=None, fold=True):
        return anki.find.findReplace(self, nids, src, dst, regex, field, fold)

    def findDuplicates(self, fmids):
        return anki.find.findDuplicates(self, fmids)

    # Stats
    ##########################################################################

    def cardStats(self, card):
        from anki.stats import CardStats
        return CardStats(self, card).report()

    def stats(self):
        from anki.stats import CollectionStats
        return CollectionStats(self)

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

    def cramDecks(self, order="mod desc", min=0, max=None):
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
            return self._undoReview()
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
        # and finally, update daily counts
        # fixme: what to do in cramming case?
        type = ("new", "lrn", "rev")[c.queue]
        self.sched._updateStats(c, type, -1)
        return c.id

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
        if self.db.scalar("pragma integrity_check") != "ok":
            return _("Collection is corrupt. Please see the manual.")
        # delete any notes with missing cards
        ids = self.db.list("""
select id from notes where id not in (select distinct nid from cards)""")
        self._remNotes(ids)
        # tags
        self.tags.registerNotes()
        # field cache
        for m in self.models.all():
            self.updateFieldCache(self.models.nids(m))
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
