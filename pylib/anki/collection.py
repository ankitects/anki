# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import copy
import datetime
import os
import pprint
import random
import re
import stat
import time
import traceback
import unicodedata
import weakref
from typing import Any, Dict, Iterable, List, Optional, Sequence, Tuple, Union

import anki.find
import anki.latex  # sets up hook
import anki.template
from anki import hooks
from anki.cards import Card
from anki.config import ConfigManager
from anki.consts import *
from anki.dbproxy import DBProxy
from anki.decks import DeckManager
from anki.errors import AnkiError
from anki.lang import _, ngettext
from anki.media import MediaManager
from anki.models import ModelManager, NoteType, Template
from anki.notes import Note
from anki.rsbackend import TR, RustBackend
from anki.sched import Scheduler as V1Scheduler
from anki.schedv2 import Scheduler as V2Scheduler
from anki.tags import TagManager
from anki.utils import (
    devMode,
    fieldChecksum,
    ids2str,
    intTime,
    joinFields,
    maxID,
    splitFields,
    stripHTMLMedia,
)


# this is initialized by storage.Collection
class _Collection:
    db: Optional[DBProxy]
    sched: Union[V1Scheduler, V2Scheduler]
    crt: int
    mod: int
    scm: int
    dty: bool  # no longer used
    _usn: int
    ls: int
    _undo: List[Any]

    def __init__(
        self,
        db: DBProxy,
        backend: RustBackend,
        server: Optional["anki.storage.ServerData"] = None,
        log: bool = False,
    ) -> None:
        self.backend = backend
        self._debugLog = log
        self.db = db
        self.path = db._path
        self._openLog()
        self.log(self.path, anki.version)
        self.server = server
        self._lastSave = time.time()
        self.clearUndo()
        self.media = MediaManager(self, server is not None)
        self.models = ModelManager(self)
        self.decks = DeckManager(self)
        self.tags = TagManager(self)
        self.conf = ConfigManager(self)
        self.load()
        if not self.crt:
            d = datetime.datetime.today()
            d -= datetime.timedelta(hours=4)
            d = datetime.datetime(d.year, d.month, d.day)
            d += datetime.timedelta(hours=4)
            self.crt = int(time.mktime(d.timetuple()))
        self._loadScheduler()
        if not self.conf.get("newBury", False):
            self.conf["newBury"] = True
            self.setMod()

    def name(self) -> Any:
        n = os.path.splitext(os.path.basename(self.path))[0]
        return n

    def tr(self, key: TR, **kwargs: Union[str, int, float]) -> str:
        return self.backend.translate(key, **kwargs)

    def weakref(self) -> anki.storage._Collection:
        "Shortcut to create a weak reference that doesn't break code completion."
        return weakref.proxy(self)

    # Scheduler
    ##########################################################################

    supportedSchedulerVersions = (1, 2)

    def schedVer(self) -> Any:
        ver = self.conf.get("schedVer", 1)
        if ver in self.supportedSchedulerVersions:
            return ver
        else:
            raise Exception("Unsupported scheduler version")

    def _loadScheduler(self) -> None:
        ver = self.schedVer()
        if ver == 1:
            self.sched = V1Scheduler(self)
        elif ver == 2:
            self.sched = V2Scheduler(self)

    def changeSchedulerVer(self, ver: int) -> None:
        if ver == self.schedVer():
            return
        if ver not in self.supportedSchedulerVersions:
            raise Exception("Unsupported scheduler version")

        self.modSchema(check=True)
        self.clearUndo()

        v2Sched = V2Scheduler(self)

        if ver == 1:
            v2Sched.moveToV1()
        else:
            v2Sched.moveToV2()

        self.conf["schedVer"] = ver
        self.setMod()

        self._loadScheduler()

    # the sync code uses this to send the local timezone to AnkiWeb
    def localOffset(self) -> Optional[int]:
        "Minutes west of UTC. Only applies to V2 scheduler."
        if isinstance(self.sched, V1Scheduler):
            return None
        else:
            return self.backend.local_minutes_west(intTime())

    # DB-related
    ##########################################################################

    def load(self) -> None:
        (
            self.crt,
            self.mod,
            self.scm,
            self.dty,  # no longer used
            self._usn,
            self.ls,
            decks,
        ) = self.db.first(
            """
select crt, mod, scm, dty, usn, ls,
decks from col"""
        )
        self.decks.decks = self.backend.get_all_decks()
        self.decks.changed = False
        self.models.models = self.backend.get_all_notetypes()
        self.models.changed = False

    def setMod(self) -> None:
        """Mark DB modified.

DB operations and the deck/model managers do this automatically, so this
is only necessary if you modify properties of this object."""
        self.db.mod = True

    def flush(self, mod: Optional[int] = None) -> None:
        "Flush state to DB, updating mod time."
        self.mod = intTime(1000) if mod is None else mod
        self.db.execute(
            """update col set
crt=?, mod=?, scm=?, dty=?, usn=?, ls=?""",
            self.crt,
            self.mod,
            self.scm,
            self.dty,
            self._usn,
            self.ls,
        )

    def flush_all_changes(self, mod: Optional[int] = None):
        self.models.flush()
        self.decks.flush()
        # set mod flag if mtime changed by backend
        if self.db.scalar("select mod from col") != self.mod:
            self.db.mod = True
        if self.db.mod:
            self.flush(mod)

    def save(
        self, name: Optional[str] = None, mod: Optional[int] = None, trx: bool = True
    ) -> None:
        "Flush, commit DB, and take out another write lock if trx=True."
        self.flush_all_changes(mod)
        # and flush deck + bump mod if db has been changed
        if self.db.mod:
            self.db.commit()
            self.db.mod = False
            if trx:
                self.db.begin()
        elif not trx:
            # if no changes were pending but calling code expects to be
            # outside of a transaction, we need to roll back
            self.db.rollback()

        self._markOp(name)
        self._lastSave = time.time()

    def autosave(self) -> Optional[bool]:
        "Save if 5 minutes has passed since last save. True if saved."
        if time.time() - self._lastSave > 300:
            self.save()
            return True
        return None

    def close(self, save: bool = True, downgrade: bool = False) -> None:
        "Disconnect from DB."
        if self.db:
            if save:
                self.save(trx=False)
            else:
                self.db.rollback()
            self.backend.close_collection(downgrade=downgrade)
            self.db = None
            self.media.close()
            self._closeLog()

    def rollback(self) -> None:
        self.db.rollback()
        self.db.begin()
        self.load()

    def modSchema(self, check: bool) -> None:
        "Mark schema modified. Call this first so user can abort if necessary."
        if not self.schemaChanged():
            if check and not hooks.schema_will_change(proceed=True):
                raise AnkiError("abortSchemaMod")
        self.scm = intTime(1000)
        self.setMod()
        self.save()

    def schemaChanged(self) -> Any:
        "True if schema changed since last sync."
        return self.scm > self.ls

    def usn(self) -> Any:
        return self._usn if self.server else -1

    def beforeUpload(self) -> None:
        "Called before a full upload."
        tbls = "notes", "cards", "revlog"
        for t in tbls:
            self.db.execute("update %s set usn=0 where usn=-1" % t)
        # we can save space by removing the log of deletions
        self.db.execute("delete from graves")
        self._usn += 1
        self.models.beforeUpload()
        self.decks.beforeUpload()
        self.backend.before_upload()
        self.modSchema(check=False)
        self.ls = self.scm
        # ensure db is compacted before upload
        self.save(trx=False)
        self.db.execute("vacuum")
        self.db.execute("analyze")
        self.close(save=False, downgrade=True)

    # Object creation helpers
    ##########################################################################

    def getCard(self, id: int) -> Card:
        return Card(self, id)

    def getNote(self, id: int) -> Note:
        return Note(self, id=id)

    # Utils
    ##########################################################################

    def nextID(self, type: str, inc: bool = True) -> Any:
        type = "next" + type.capitalize()
        id = self.conf.get(type, 1)
        if inc:
            self.conf[type] = id + 1
        return id

    def reset(self) -> None:
        "Rebuild the queue and reload data after DB modified."
        self.sched.reset()

    # Deletion logging
    ##########################################################################

    def _logRem(self, ids: List[int], type: int) -> None:
        self.db.executemany(
            "insert into graves values (%d, ?, %d)" % (self.usn(), type),
            ([x] for x in ids),
        )

    # Notes
    ##########################################################################

    def noteCount(self) -> Any:
        return self.db.scalar("select count() from notes")

    def newNote(self, forDeck: bool = True) -> Note:
        "Return a new note with the current model."
        return Note(self, self.models.current(forDeck))

    def addNote(self, note: Note) -> int:
        """Add a note to the collection. Return number of new cards."""
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

    def remNotes(self, ids: Iterable[int]) -> None:
        """Deletes notes with the given IDs."""
        self.remCards(self.db.list("select id from cards where nid in " + ids2str(ids)))

    def _remNotes(self, ids: List[int]) -> None:
        """Bulk delete notes by ID. Don't call this directly."""
        if not ids:
            return
        strids = ids2str(ids)
        # we need to log these independently of cards, as one side may have
        # more card templates
        hooks.notes_will_be_deleted(self, ids)
        self._logRem(ids, REM_NOTE)
        self.db.execute("delete from notes where id in %s" % strids)

    # Card creation
    ##########################################################################

    def findTemplates(self, note: Note) -> List:
        "Return (active), non-empty templates."
        model = note.model()
        avail = self.models.availOrds(model, joinFields(note.fields))
        return self._tmplsFromOrds(model, avail)

    def _tmplsFromOrds(self, model: NoteType, avail: List[int]) -> List:
        ok = []
        if model["type"] == MODEL_STD:
            for t in model["tmpls"]:
                if t["ord"] in avail:
                    ok.append(t)
        else:
            # cloze - generate temporary templates from first
            for ord in avail:
                t = copy.copy(model["tmpls"][0])
                t["ord"] = ord
                ok.append(t)
        return ok

    def genCards(self, nids: List[int]) -> List[int]:
        "Generate cards for non-empty templates, return ids to remove."
        # build map of (nid,ord) so we don't create dupes
        snids = ids2str(nids)
        have: Dict[int, Dict[int, int]] = {}
        dids: Dict[int, Optional[int]] = {}
        dues: Dict[int, int] = {}
        for id, nid, ord, did, due, odue, odid, type in self.db.execute(
            "select id, nid, ord, did, due, odue, odid, type from cards where nid in "
            + snids
        ):
            # existing cards
            if nid not in have:
                have[nid] = {}
            have[nid][ord] = id
            # if in a filtered deck, add new cards to original deck
            if odid != 0:
                did = odid
            # and their dids
            if nid in dids:
                if dids[nid] and dids[nid] != did:
                    # cards are in two or more different decks; revert to
                    # model default
                    dids[nid] = None
            else:
                # first card or multiple cards in same deck
                dids[nid] = did
            # save due
            if odid != 0:
                due = odue
            if nid not in dues and type == 0:
                # Add due to new card only if it's the due of a new sibling
                dues[nid] = due
        # build cards for each note
        data = []
        ts = maxID(self.db)
        now = intTime()
        rem = []
        usn = self.usn()
        for nid, mid, flds in self.db.execute(
            "select id, mid, flds from notes where id in " + snids
        ):
            model = self.models.get(mid)
            assert model
            avail = self.models.availOrds(model, flds)
            did = dids.get(nid) or model["did"]
            due = dues.get(nid)
            # add any missing cards
            for t in self._tmplsFromOrds(model, avail):
                doHave = nid in have and t["ord"] in have[nid]
                if not doHave:
                    # check deck is not a cram deck
                    did = t["did"] or did
                    if self.decks.isDyn(did):
                        did = 1
                    # if the deck doesn't exist, use default instead
                    did = self.decks.get(did)["id"]
                    # use sibling due# if there is one, else use a new id
                    if due is None:
                        due = self.nextID("pos")
                    data.append((ts, nid, did, t["ord"], now, usn, due))
                    ts += 1
            # note any cards that need removing
            if nid in have:
                for ord, id in list(have[nid].items()):
                    if ord not in avail:
                        rem.append(id)
        # bulk update
        self.db.executemany(
            """
insert into cards values (?,?,?,?,?,?,0,0,?,0,0,0,0,0,0,0,0,"")""",
            data,
        )
        return rem

    # type is no longer used
    def previewCards(
        self, note: Note, type: int = 0, did: Optional[int] = None
    ) -> List:
        existing_cards = {}
        for card in note.cards():
            existing_cards[card.ord] = card

        all_cards = []
        for idx, template in enumerate(note.model()["tmpls"]):
            if idx in existing_cards:
                all_cards.append(existing_cards[idx])
            else:
                # card not currently in database, generate an ephemeral one
                all_cards.append(self._newCard(note, template, 1, flush=False, did=did))

        return all_cards

    def _newCard(
        self,
        note: Note,
        template: Template,
        due: int,
        flush: bool = True,
        did: Optional[int] = None,
    ) -> Card:
        "Create a new card."
        card = Card(self)
        card.nid = note.id
        card.ord = template["ord"]  # type: ignore
        card.did = self.db.scalar(
            "select did from cards where nid = ? and ord = ?", card.nid, card.ord
        )
        # Use template did (deck override) if valid, otherwise did in argument, otherwise model did
        if not card.did:
            if template["did"] and str(template["did"]) in self.decks.decks:
                card.did = int(template["did"])
            elif did:
                card.did = did
            else:
                card.did = note.model()["did"]
        # if invalid did, use default instead
        deck = self.decks.get(card.did)
        assert deck
        if deck["dyn"]:
            # must not be a filtered deck
            card.did = 1
        else:
            card.did = deck["id"]
        card.due = self._dueForDid(card.did, due)
        if flush:
            card.flush()
        return card

    def _dueForDid(self, did: int, due: int) -> int:
        conf = self.decks.confForDid(did)
        # in order due?
        if conf["new"]["order"] == NEW_CARDS_DUE:
            return due
        else:
            # random mode; seed with note ts so all cards of this note get the
            # same random number
            r = random.Random()
            r.seed(due)
            return r.randrange(1, max(due, 1000))

    # Cards
    ##########################################################################

    def isEmpty(self) -> bool:
        return not self.db.scalar("select 1 from cards limit 1")

    def cardCount(self) -> Any:
        return self.db.scalar("select count() from cards")

    def remCards(self, ids: List[int], notes: bool = True) -> None:
        "Bulk delete cards by ID."
        if not ids:
            return
        sids = ids2str(ids)
        nids = self.db.list("select nid from cards where id in " + sids)
        # remove cards
        self._logRem(ids, REM_CARD)
        self.db.execute("delete from cards where id in " + sids)
        # then notes
        if not notes:
            return
        nids = self.db.list(
            """
select id from notes where id in %s and id not in (select nid from cards)"""
            % ids2str(nids)
        )
        self._remNotes(nids)

    def emptyCids(self) -> List[int]:
        """Returns IDs of empty cards."""
        rem: List[int] = []
        for m in self.models.all():
            rem += self.genCards(self.models.nids(m))
        return rem

    def emptyCardReport(self, cids) -> str:
        rep = ""
        for ords, cnt, flds in self.db.all(
            """
select group_concat(ord+1), count(), flds from cards c, notes n
where c.nid = n.id and c.id in %s group by nid"""
            % ids2str(cids)
        ):
            rep += self.tr(
                TR.EMPTY_CARDS_CARD_LINE,
                **{"card-numbers": ords, "fields": flds.replace("\x1f", " / ")},
            )
            rep += "\n\n"
        return rep

    # Field checksums and sorting fields
    ##########################################################################

    def _fieldData(self, snids: str) -> Any:
        return self.db.execute("select id, mid, flds from notes where id in " + snids)

    def updateFieldCache(self, nids: List[int]) -> None:
        "Update field checksums and sort cache, after find&replace, etc."
        snids = ids2str(nids)
        r = []
        for (nid, mid, flds) in self._fieldData(snids):
            fields = splitFields(flds)
            model = self.models.get(mid)
            if not model:
                # note points to invalid model
                continue
            r.append(
                (
                    stripHTMLMedia(fields[self.models.sortIdx(model)]),
                    fieldChecksum(fields[0]),
                    nid,
                )
            )
        # apply, relying on calling code to bump usn+mod
        self.db.executemany("update notes set sfld=?, csum=? where id=?", r)

    # Finding cards
    ##########################################################################

    # if order=True, use the sort order stored in the collection config
    # if order=False, do no ordering
    #
    # if order is a string, that text is added after 'order by' in the sql statement.
    # you must add ' asc' or ' desc' to the order, as Anki will replace asc with
    # desc and vice versa when reverse is set in the collection config, eg
    # order="c.ivl asc, c.due desc"
    #
    # if order is an int enum, sort using that builtin sort, eg
    # col.find_cards("", order=BuiltinSortKind.CARD_DUE)
    # the reverse argument only applies when a BuiltinSortKind is provided;
    # otherwise the collection config defines whether reverse is set or not
    def find_cards(
        self, query: str, order: Union[bool, str, int] = False, reverse: bool = False,
    ) -> Sequence[int]:
        self.flush_all_changes()
        return self.backend.search_cards(query, order, reverse)

    def find_notes(self, query: str) -> Sequence[int]:
        self.flush_all_changes()
        return self.backend.search_notes(query)

    def findReplace(
        self,
        nids: List[int],
        src: str,
        dst: str,
        regex: Optional[bool] = None,
        field: Optional[str] = None,
        fold: bool = True,
    ) -> int:
        return anki.find.findReplace(self, nids, src, dst, regex, field, fold)

    def findDupes(self, fieldName: str, search: str = "") -> List[Tuple[Any, list]]:
        return anki.find.findDupes(self, fieldName, search)

    findCards = find_cards
    findNotes = find_notes

    # Config
    ##########################################################################

    def get_config(self, key: str, default: Any = None) -> Any:
        try:
            return self.conf.get_immutable(key)
        except KeyError:
            return default

    def set_config(self, key: str, val: Any):
        self.setMod()
        self.conf.set(key, val)

    def remove_config(self, key):
        self.setMod()
        self.conf.remove(key)

    # Stats
    ##########################################################################

    def cardStats(self, card: Card) -> str:
        from anki.stats import CardStats

        return CardStats(self, card).report()

    def stats(self) -> "anki.stats.CollectionStats":
        from anki.stats import CollectionStats

        return CollectionStats(self)

    # Timeboxing
    ##########################################################################

    def startTimebox(self) -> None:
        self._startTime = time.time()
        self._startReps = self.sched.reps

    # FIXME: Use Literal[False] when on Python 3.8
    def timeboxReached(self) -> Union[bool, Tuple[Any, int]]:
        "Return (elapsedTime, reps) if timebox reached, or False."
        if not self.conf["timeLim"]:
            # timeboxing disabled
            return False
        elapsed = time.time() - self._startTime
        if elapsed > self.conf["timeLim"]:
            return (self.conf["timeLim"], self.sched.reps - self._startReps)
        return False

    # Undo
    ##########################################################################

    def clearUndo(self) -> None:
        # [type, undoName, data]
        # type 1 = review; type 2 = checkpoint
        self._undo = None

    def undoName(self) -> Any:
        "Undo menu item name, or None if undo unavailable."
        if not self._undo:
            return None
        return self._undo[1]

    def undo(self) -> Any:
        if self._undo[0] == 1:
            return self._undoReview()
        else:
            self._undoOp()

    def markReview(self, card: Card) -> None:
        old: List[Any] = []
        if self._undo:
            if self._undo[0] == 1:
                old = self._undo[2]
            self.clearUndo()
        wasLeech = card.note().hasTag("leech") or False
        self._undo = [1, _("Review"), old + [copy.copy(card)], wasLeech]

    def _undoReview(self) -> Any:
        data = self._undo[2]
        wasLeech = self._undo[3]
        c = data.pop()  # pytype: disable=attribute-error
        if not data:
            self.clearUndo()
        # remove leech tag if it didn't have it before
        if not wasLeech and c.note().hasTag("leech"):
            c.note().delTag("leech")
            c.note().flush()
        # write old data
        c.flush()
        # and delete revlog entry
        last = self.db.scalar(
            "select id from revlog where cid = ? " "order by id desc limit 1", c.id
        )
        self.db.execute("delete from revlog where id = ?", last)
        # restore any siblings
        self.db.execute(
            "update cards set queue=type,mod=?,usn=? where queue=-2 and nid=?",
            intTime(),
            self.usn(),
            c.nid,
        )
        # and finally, update daily counts
        n = 1 if c.queue in (3, 4) else c.queue
        type = ("new", "lrn", "rev")[n]
        self.sched._updateStats(c, type, -1)
        self.sched.reps -= 1
        return c.id

    def _markOp(self, name: Optional[str]) -> None:
        "Call via .save()"
        if name:
            self._undo = [2, name]
        else:
            # saving disables old checkpoint, but not review undo
            if self._undo and self._undo[0] == 2:
                self.clearUndo()

    def _undoOp(self) -> None:
        self.rollback()
        self.clearUndo()

    # DB maintenance
    ##########################################################################

    def basicCheck(self) -> bool:
        "Basic integrity check for syncing. True if ok."
        # cards without notes
        if self.db.scalar(
            """
select 1 from cards where nid not in (select id from notes) limit 1"""
        ):
            return False
        # notes without cards or models
        if self.db.scalar(
            """
select 1 from notes where id not in (select distinct nid from cards)
or mid not in %s limit 1"""
            % ids2str(self.models.ids())
        ):
            return False
        # invalid ords
        for m in self.models.all():
            # ignore clozes
            if m["type"] != MODEL_STD:
                continue
            if self.db.scalar(
                """
select 1 from cards where ord not in %s and nid in (
select id from notes where mid = ?) limit 1"""
                % ids2str([t["ord"] for t in m["tmpls"]]),
                m["id"],
            ):
                return False
        return True

    def fixIntegrity(self) -> Tuple[str, bool]:
        """Fix possible problems and rebuild caches.

        Returns tuple of (error: str, ok: bool). 'ok' will be true if no
        problems were found.
        """
        problems = []
        # problems that don't require a full sync
        syncable_problems = []
        self.save()
        oldSize = os.stat(self.path)[stat.ST_SIZE]
        if self.db.scalar("pragma integrity_check") != "ok":
            return (_("Collection is corrupt. Please see the manual."), False)
        # note types with a missing model
        ids = self.db.list(
            """
select id from notes where mid not in """
            + ids2str(self.models.ids())
        )
        if ids:
            problems.append(
                ngettext(
                    "Deleted %d note with missing note type.",
                    "Deleted %d notes with missing note type.",
                    len(ids),
                )
                % len(ids)
            )
            self.remNotes(ids)
        # for each model
        for m in self.models.all():
            for t in m["tmpls"]:
                if t["did"] == "None":
                    t["did"] = None
                    problems.append(_("Fixed AnkiDroid deck override bug."))
                    self.models.save(m, updateReqs=False)
            if m["type"] == MODEL_STD:
                # model with missing req specification
                if "req" not in m:
                    self.models._updateRequired(m)
                    problems.append(_("Fixed note type: %s") % m["name"])
                # cards with invalid ordinal
                ids = self.db.list(
                    """
select id from cards where ord not in %s and nid in (
select id from notes where mid = ?)"""
                    % ids2str([t["ord"] for t in m["tmpls"]]),
                    m["id"],
                )
                if ids:
                    problems.append(
                        ngettext(
                            "Deleted %d card with missing template.",
                            "Deleted %d cards with missing template.",
                            len(ids),
                        )
                        % len(ids)
                    )
                    self.remCards(ids)
            # notes with invalid field count
            ids = []
            for id, flds in self.db.execute(
                "select id, flds from notes where mid = ?", m["id"]
            ):
                if (flds.count("\x1f") + 1) != len(m["flds"]):
                    ids.append(id)
            if ids:
                problems.append(
                    ngettext(
                        "Deleted %d note with wrong field count.",
                        "Deleted %d notes with wrong field count.",
                        len(ids),
                    )
                    % len(ids)
                )
                self.remNotes(ids)
        # delete any notes with missing cards
        ids = self.db.list(
            """
select id from notes where id not in (select distinct nid from cards)"""
        )
        if ids:
            cnt = len(ids)
            problems.append(
                ngettext(
                    "Deleted %d note with no cards.",
                    "Deleted %d notes with no cards.",
                    cnt,
                )
                % cnt
            )
            self._remNotes(ids)
        # cards with missing notes
        ids = self.db.list(
            """
select id from cards where nid not in (select id from notes)"""
        )
        if ids:
            cnt = len(ids)
            problems.append(
                ngettext(
                    "Deleted %d card with missing note.",
                    "Deleted %d cards with missing note.",
                    cnt,
                )
                % cnt
            )
            self.remCards(ids)
        # cards with odue set when it shouldn't be
        ids = self.db.list(
            """
select id from cards where odue > 0 and (type=1 or queue=2) and not odid"""
        )
        if ids:
            cnt = len(ids)
            problems.append(
                ngettext(
                    "Fixed %d card with invalid properties.",
                    "Fixed %d cards with invalid properties.",
                    cnt,
                )
                % cnt
            )
            self.db.execute("update cards set odue=0 where id in " + ids2str(ids))
        # cards with odid set when not in a dyn deck
        dids = [id for id in self.decks.allIds() if not self.decks.isDyn(id)]
        ids = self.db.list(
            """
select id from cards where odid > 0 and did in %s"""
            % ids2str(dids)
        )
        if ids:
            cnt = len(ids)
            problems.append(
                ngettext(
                    "Fixed %d card with invalid properties.",
                    "Fixed %d cards with invalid properties.",
                    cnt,
                )
                % cnt
            )
            self.db.execute(
                "update cards set odid=0, odue=0 where id in " + ids2str(ids)
            )
        # notes with non-normalized tags
        cnt = self._normalize_tags()
        if cnt > 0:
            syncable_problems.append(
                self.tr(TR.DATABASE_CHECK_FIXED_NON_NORMALIZED_TAGS, count=cnt)
            )
        # tags
        self.tags.registerNotes()
        # field cache
        for m in self.models.all():
            self.updateFieldCache(self.models.nids(m))
        # new cards can't have a due position > 32 bits, so wrap items over
        # 2 million back to 1 million
        self.db.execute(
            """
update cards set due=1000000+due%1000000,mod=?,usn=? where due>=1000000
and type=0 and queue!=4""",
            intTime(),
            self.usn(),
        )
        rowcount = self.db.scalar("select changes()")
        if rowcount:
            syncable_problems.append(
                "Found %d new cards with a due number >= 1,000,000 - consider repositioning them in the Browse screen."
                % rowcount
            )
        # new card position
        self.conf["nextPos"] = (
            self.db.scalar("select max(due)+1 from cards where type = 0") or 0
        )
        # reviews should have a reasonable due #
        ids = self.db.list("select id from cards where queue = 2 and due > 100000")
        if ids:
            problems.append("Reviews had incorrect due date.")
            self.db.execute(
                "update cards set due = ?, ivl = 1, mod = ?, usn = ? where id in %s"
                % ids2str(ids),
                self.sched.today,
                intTime(),
                self.usn(),
            )
        # v2 sched had a bug that could create decimal intervals
        self.db.execute(
            "update cards set ivl=round(ivl),due=round(due) where ivl!=round(ivl) or due!=round(due)"
        )
        rowcount = self.db.scalar("select changes()")
        if rowcount:
            problems.append("Fixed %d cards with v2 scheduler bug." % rowcount)

        self.db.execute(
            "update revlog set ivl=round(ivl),lastIvl=round(lastIvl) where ivl!=round(ivl) or lastIvl!=round(lastIvl)"
        )
        rowcount = self.db.scalar("select changes()")
        if rowcount:
            problems.append(
                "Fixed %d review history entries with v2 scheduler bug." % rowcount
            )
        # models
        if self.models.ensureNotEmpty():
            problems.append("Added missing note type.")
        # and finally, optimize
        self.optimize()
        newSize = os.stat(self.path)[stat.ST_SIZE]
        txt = _("Database rebuilt and optimized.")
        ok = not problems
        problems.append(txt)
        # if any problems were found, force a full sync
        if not ok:
            self.modSchema(check=False)
        self.save()
        problems.extend(syncable_problems)
        return ("\n".join(problems), ok)

    def _normalize_tags(self) -> int:
        to_fix = []
        for id, tags in self.db.execute("select id, tags from notes"):
            norm = unicodedata.normalize("NFC", tags)
            if not norm.strip():
                norm = ""
            elif not norm.startswith(" ") or not norm.endswith(" "):
                norm = " " + norm + " "
            if norm != tags:
                to_fix.append((norm, self.usn(), intTime(), id))
        if to_fix:
            self.db.executemany(
                "update notes set tags=?, usn=?, mod=? where id=?", to_fix
            )
        return len(to_fix)

    def optimize(self) -> None:
        self.save(trx=False)
        self.db.execute("vacuum")
        self.db.execute("analyze")
        self.db.begin()

    # Logging
    ##########################################################################

    def log(self, *args, **kwargs) -> None:
        if not self._debugLog:
            return

        def customRepr(x):
            if isinstance(x, str):
                return x
            return pprint.pformat(x)

        path, num, fn, y = traceback.extract_stack(limit=2 + kwargs.get("stack", 0))[0]
        buf = "[%s] %s:%s(): %s" % (
            intTime(),
            os.path.basename(path),
            fn,
            ", ".join([customRepr(x) for x in args]),
        )
        self._logHnd.write(buf + "\n")
        if devMode:
            print(buf)

    def _openLog(self) -> None:
        if not self._debugLog:
            return
        lpath = re.sub(r"\.anki2$", ".log", self.path)
        if os.path.exists(lpath) and os.path.getsize(lpath) > 10 * 1024 * 1024:
            lpath2 = lpath + ".old"
            if os.path.exists(lpath2):
                os.unlink(lpath2)
            os.rename(lpath, lpath2)
        self._logHnd = open(lpath, "a", encoding="utf8")

    def _closeLog(self) -> None:
        if not self._debugLog:
            return
        self._logHnd.close()
        self._logHnd = None

    # Card Flags
    ##########################################################################

    def setUserFlag(self, flag: int, cids: List[int]) -> None:
        assert 0 <= flag <= 7
        self.db.execute(
            "update cards set flags = (flags & ~?) | ?, usn=?, mod=? where id in %s"
            % ids2str(cids),
            0b111,
            flag,
            self.usn(),
            intTime(),
        )
