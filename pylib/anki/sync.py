# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import gzip
import io
import json
import os
import random
from typing import Any, Dict, List, Optional, Sequence, Tuple, Union

import anki
from anki.consts import *
from anki.db import DB
from anki.utils import checksum, ids2str, intTime, platDesc, versionWithBuild

from . import hooks
from .httpclient import HttpClient

# add-on compat
AnkiRequestsClient = HttpClient


class UnexpectedSchemaChange(Exception):
    pass


# Incremental syncing
##########################################################################


class Syncer:
    chunkRows: Optional[List[Sequence]]

    def __init__(self, col: anki.storage._Collection, server=None) -> None:
        self.col = col.weakref()
        self.server = server

        # these are set later; provide dummy values for type checking
        self.lnewer = False
        self.maxUsn = 0
        self.tablesLeft: List[str] = []

    def sync(self) -> str:
        "Returns 'noChanges', 'fullSync', 'success', etc"
        self.syncMsg = ""
        self.uname = ""
        # if the deck has any pending changes, flush them first and bump mod
        # time
        self.col.save()

        # step 1: login & metadata
        hooks.sync_stage_did_change("login")
        meta = self.server.meta()
        self.col.log("rmeta", meta)
        if not meta:
            return "badAuth"
        # server requested abort?
        self.syncMsg = meta["msg"]
        if not meta["cont"]:
            return "serverAbort"
        else:
            # don't abort, but if 'msg' is not blank, gui should show 'msg'
            # after sync finishes and wait for confirmation before hiding
            pass
        rscm = meta["scm"]
        rts = meta["ts"]
        self.rmod = meta["mod"]
        self.maxUsn = meta["usn"]
        self.uname = meta.get("uname", "")
        self.hostNum = meta.get("hostNum")
        meta = self.meta()
        self.col.log("lmeta", meta)
        self.lmod = meta["mod"]
        self.minUsn = meta["usn"]
        lscm = meta["scm"]
        lts = meta["ts"]
        if abs(rts - lts) > 300:
            self.col.log("clock off")
            return "clockOff"
        if self.lmod == self.rmod:
            self.col.log("no changes")
            return "noChanges"
        elif lscm != rscm:
            self.col.log("schema diff")
            return "fullSync"
        self.lnewer = self.lmod > self.rmod
        # step 1.5: check collection is valid
        if not self.col.basicCheck():
            self.col.log("basic check")
            return "basicCheckFailed"
        # step 2: startup and deletions
        hooks.sync_stage_did_change("meta")
        rrem = self.server.start(
            minUsn=self.minUsn, lnewer=self.lnewer, offset=self.col.localOffset()
        )

        # apply deletions to server
        lgraves = self.removed()
        while lgraves:
            gchunk, lgraves = self._gravesChunk(lgraves)
            self.server.applyGraves(chunk=gchunk)

        # then apply server deletions here
        self.remove(rrem)

        # ...and small objects
        lchg = self.changes()
        rchg = self.server.applyChanges(changes=lchg)
        try:
            self.mergeChanges(lchg, rchg)
        except UnexpectedSchemaChange:
            self.server.abort()
            return self._forceFullSync()
        # step 3: stream large tables from server
        hooks.sync_stage_did_change("server")
        while 1:
            hooks.sync_stage_did_change("stream")
            chunk = self.server.chunk()
            self.col.log("server chunk", chunk)
            self.applyChunk(chunk=chunk)
            if chunk["done"]:
                break
        # step 4: stream to server
        hooks.sync_stage_did_change("client")
        while 1:
            hooks.sync_stage_did_change("stream")
            chunk = self.chunk()
            self.col.log("client chunk", chunk)
            self.server.applyChunk(chunk=chunk)
            if chunk["done"]:
                break
        # step 5: sanity check
        hooks.sync_stage_did_change("sanity")
        c = self.sanityCheck()
        ret = self.server.sanityCheck2(client=c)
        if ret["status"] != "ok":
            return self._forceFullSync()
        # finalize
        hooks.sync_stage_did_change("finalize")
        mod = self.server.finish()
        self.finish(mod)
        return "success"

    def _forceFullSync(self) -> str:
        # roll back and force full sync
        self.col.rollback()
        self.col.modSchema(False)
        self.col.save()
        return "sanityCheckFailed"

    def _gravesChunk(self, graves: Dict) -> Tuple[Dict, Optional[Dict]]:
        lim = 250
        chunk: Dict[str, Any] = dict(notes=[], cards=[], decks=[])
        for cat in "notes", "cards", "decks":
            if lim and graves[cat]:
                chunk[cat] = graves[cat][:lim]
                graves[cat] = graves[cat][lim:]
                lim -= len(chunk[cat])

        # anything remaining?
        if graves["notes"] or graves["cards"] or graves["decks"]:
            return chunk, graves
        return chunk, None

    def meta(self) -> dict:
        return dict(
            mod=self.col.mod,
            scm=self.col.scm,
            usn=self.col._usn,
            ts=intTime(),
            musn=0,
            msg="",
            cont=True,
        )

    def changes(self) -> dict:
        "Bundle up small objects."
        d: Dict[str, Any] = dict(
            models=self.getModels(), decks=self.getDecks(), tags=self.getTags()
        )
        if self.lnewer:
            d["conf"] = self.getConf()
            d["crt"] = self.col.crt
        return d

    def mergeChanges(self, lchg, rchg) -> None:
        # then the other objects
        self.mergeModels(rchg["models"])
        self.mergeDecks(rchg["decks"])
        self.mergeTags(rchg["tags"])
        if "conf" in rchg:
            self.mergeConf(rchg["conf"])
        # this was left out of earlier betas
        if "crt" in rchg:
            self.col.crt = rchg["crt"]
        self.prepareToChunk()

    def sanityCheck(self) -> Union[list, str]:
        if not self.col.basicCheck():
            return "failed basic check"
        for t in "cards", "notes", "revlog", "graves":
            if self.col.db.scalar("select count() from %s where usn = -1" % t):
                return "%s had usn = -1" % t
        for g in self.col.decks.all():
            if g["usn"] == -1:
                return "deck had usn = -1"
        for tup in self.col.backend.all_tags():
            if tup.usn == -1:
                return "tag had usn = -1"
        found = False
        for m in self.col.models.all():
            if m["usn"] == -1:
                return "model had usn = -1"
        if found:
            self.col.models.save()
        self.col.sched.reset()
        # check for missing parent decks
        self.col.sched.deckDueList()
        # return summary of deck
        return [
            list(self.col.sched.counts()),
            self.col.db.scalar("select count() from cards"),
            self.col.db.scalar("select count() from notes"),
            self.col.db.scalar("select count() from revlog"),
            self.col.db.scalar("select count() from graves"),
            len(self.col.models.all()),
            len(self.col.decks.all()),
            len(self.col.decks.allConf()),
        ]

    def usnLim(self) -> str:
        return "usn = -1"

    def finish(self, mod: int) -> int:
        self.col.ls = mod
        self.col._usn = self.maxUsn + 1
        # ensure we save the mod time even if no changes made
        self.col.db.mod = True
        self.col.save(mod=mod)
        return mod

    # Chunked syncing
    ##########################################################################

    def prepareToChunk(self) -> None:
        self.tablesLeft = ["revlog", "cards", "notes"]
        self.chunkRows = None

    def getChunkRows(self, table) -> List[Sequence]:
        lim = self.usnLim()
        x = self.col.db.all
        d = (self.maxUsn, lim)
        if table == "revlog":
            return x(
                """
select id, cid, %d, ease, ivl, lastIvl, factor, time, type
from revlog where %s"""
                % d
            )
        elif table == "cards":
            return x(
                """
select id, nid, did, ord, mod, %d, type, queue, due, ivl, factor, reps,
lapses, left, odue, odid, flags, data from cards where %s"""
                % d
            )
        else:
            return x(
                """
select id, guid, mid, mod, %d, tags, flds, '', '', flags, data
from notes where %s"""
                % d
            )

    def chunk(self) -> dict:
        buf: Dict[str, Any] = dict(done=False)
        lim = 250
        while self.tablesLeft and lim:
            curTable = self.tablesLeft[0]
            if self.chunkRows is None:
                self.chunkRows = self.getChunkRows(curTable)
            rows = self.chunkRows[:lim]
            self.chunkRows = self.chunkRows[lim:]
            fetched = len(rows)
            if fetched != lim:
                # table is empty
                self.tablesLeft.pop(0)
                self.chunkRows = None
                # mark the objects as having been sent
                self.col.db.execute(
                    "update %s set usn=? where usn=-1" % curTable, self.maxUsn
                )
            buf[curTable] = rows
            lim -= fetched
        if not self.tablesLeft:
            buf["done"] = True
        return buf

    def applyChunk(self, chunk) -> None:
        if "revlog" in chunk:
            self.mergeRevlog(chunk["revlog"])
        if "cards" in chunk:
            self.mergeCards(chunk["cards"])
        if "notes" in chunk:
            self.mergeNotes(chunk["notes"])

    # Deletions
    ##########################################################################

    def removed(self) -> dict:
        cards = []
        notes = []
        decks = []

        curs = self.col.db.execute("select oid, type from graves where usn = -1")

        for oid, type in curs:
            if type == REM_CARD:
                cards.append(oid)
            elif type == REM_NOTE:
                notes.append(oid)
            else:
                decks.append(oid)

        self.col.db.execute("update graves set usn=? where usn=-1", self.maxUsn)

        return dict(cards=cards, notes=notes, decks=decks)

    def remove(self, graves) -> None:
        # pretend to be the server so we don't set usn = -1
        self.col.server = True  # type: ignore

        # notes first, so we don't end up with duplicate graves
        self.col._remNotes(graves["notes"])
        # then cards
        self.col.remCards(graves["cards"], notes=False)
        # and decks
        for oid in graves["decks"]:
            self.col.decks.rem(oid, childrenToo=False)

        self.col.server = False  # type: ignore

    # Models
    ##########################################################################

    def getModels(self) -> List:
        mods = [m for m in self.col.models.all() if m["usn"] == -1]
        for m in mods:
            m["usn"] = self.maxUsn
        self.col.models.save()
        return mods

    def mergeModels(self, rchg) -> None:
        for r in rchg:
            l = self.col.models.get(r["id"])
            # if missing locally or server is newer, update
            if not l or r["mod"] > l["mod"]:
                # This is a hack to detect when the note type has been altered
                # in an import without a full sync being forced. A future
                # syncing algorithm should handle this in a better way.
                if l:
                    if len(l["flds"]) != len(r["flds"]):
                        raise UnexpectedSchemaChange()
                    if len(l["tmpls"]) != len(r["tmpls"]):
                        raise UnexpectedSchemaChange()
                self.col.models.update(r)

    # Decks
    ##########################################################################

    def getDecks(self) -> List[list]:
        decks = [g for g in self.col.decks.all() if g["usn"] == -1]
        for g in decks:
            g["usn"] = self.maxUsn
        dconf = [g for g in self.col.decks.all_config() if g["usn"] == -1]
        for g in dconf:
            g["usn"] = self.maxUsn
            self.col.decks.update_config(g, preserve_usn=True)
        self.col.decks.save()
        return [decks, dconf]

    def mergeDecks(self, rchg) -> None:
        for r in rchg[0]:
            l = self.col.decks.get(r["id"], False)
            # work around mod time being stored as string
            if l and not isinstance(l["mod"], int):
                l["mod"] = int(l["mod"])

            # if missing locally or server is newer, update
            if not l or r["mod"] > l["mod"]:
                self.col.decks.update(r)
        for r in rchg[1]:
            try:
                l = self.col.decks.get_config(r["id"])
            except KeyError:
                l = None
            # if missing locally or server is newer, update
            if not l or r["mod"] > l["mod"]:
                self.col.decks.update_config(r)

    # Tags
    ##########################################################################

    def getTags(self) -> List:
        return self.col.backend.get_changed_tags(self.maxUsn)

    def mergeTags(self, tags) -> None:
        self.col.tags.register(tags, usn=self.maxUsn)

    # Cards/notes/revlog
    ##########################################################################

    def mergeRevlog(self, logs) -> None:
        self.col.db.executemany(
            "insert or ignore into revlog values (?,?,?,?,?,?,?,?,?)", logs
        )

    def newerRows(self, data, table, modIdx) -> List:
        ids = (r[0] for r in data)
        lmods = {}
        for id, mod in self.col.db.execute(
            "select id, mod from %s where id in %s and %s"
            % (table, ids2str(ids), self.usnLim())
        ):
            lmods[id] = mod
        update = []
        for r in data:
            if r[0] not in lmods or lmods[r[0]] < r[modIdx]:
                update.append(r)
        self.col.log(table, data)
        return update

    def mergeCards(self, cards) -> None:
        self.col.db.executemany(
            "insert or replace into cards values "
            "(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            self.newerRows(cards, "cards", 4),
        )

    def mergeNotes(self, notes) -> None:
        rows = self.newerRows(notes, "notes", 3)
        self.col.db.executemany(
            "insert or replace into notes values (?,?,?,?,?,?,?,?,?,?,?)", rows
        )
        self.col.updateFieldCache([f[0] for f in rows])

    # Col config
    ##########################################################################

    def getConf(self) -> Dict[str, Any]:
        return self.col.backend.get_all_config()

    def mergeConf(self, conf: Dict[str, Any]) -> None:
        self.col.backend.set_all_config(conf)


# HTTP syncing tools
##########################################################################


class HttpSyncer:
    def __init__(self, hkey=None, client=None, hostNum=None) -> None:
        self.hkey = hkey
        self.skey = checksum(str(random.random()))[:8]
        self.client = client or HttpClient()
        self.postVars: Dict[str, str] = {}
        self.hostNum = hostNum
        self.prefix = "sync/"

    def syncURL(self) -> str:
        url = SYNC_BASE % (self.hostNum or "")
        return url + self.prefix

    def assertOk(self, resp) -> None:
        # not using raise_for_status() as aqt expects this error msg
        if resp.status_code != 200:
            raise Exception("Unknown response code: %s" % resp.status_code)

    # Posting data as a file
    ######################################################################
    # We don't want to post the payload as a form var, as the percent-encoding is
    # costly. We could send it as a raw post, but more HTTP clients seem to
    # support file uploading, so this is the more compatible choice.

    def _buildPostData(self, fobj, comp) -> Tuple[Dict[str, str], io.BytesIO]:
        BOUNDARY = b"Anki-sync-boundary"
        bdry = b"--" + BOUNDARY
        buf = io.BytesIO()
        # post vars
        self.postVars["c"] = "1" if comp else "0"
        for (key, value) in list(self.postVars.items()):
            buf.write(bdry + b"\r\n")
            buf.write(
                (
                    'Content-Disposition: form-data; name="%s"\r\n\r\n%s\r\n'
                    % (key, value)
                ).encode("utf8")
            )
        # payload as raw data or json
        rawSize = 0
        if fobj:
            # header
            buf.write(bdry + b"\r\n")
            buf.write(
                b"""\
Content-Disposition: form-data; name="data"; filename="data"\r\n\
Content-Type: application/octet-stream\r\n\r\n"""
            )
            # write file into buffer, optionally compressing
            if comp:
                tgt = gzip.GzipFile(mode="wb", fileobj=buf, compresslevel=comp)
            else:
                tgt = buf  # type: ignore
            while 1:
                data = fobj.read(65536)
                if not data:
                    if comp:
                        tgt.close()
                    break
                rawSize += len(data)
                tgt.write(data)
            buf.write(b"\r\n")
        buf.write(bdry + b"--\r\n")
        size = buf.tell()
        # connection headers
        headers = {
            "Content-Type": "multipart/form-data; boundary=%s"
            % BOUNDARY.decode("utf8"),
            "Content-Length": str(size),
        }
        buf.seek(0)

        if size >= 100 * 1024 * 1024 or rawSize >= 250 * 1024 * 1024:
            raise Exception("Collection too large to upload to AnkiWeb.")

        return headers, buf

    def req(self, method, fobj=None, comp=6, badAuthRaises=True) -> Any:
        headers, body = self._buildPostData(fobj, comp)

        r = self.client.post(self.syncURL() + method, data=body, headers=headers)
        if not badAuthRaises and r.status_code == 403:
            return False
        self.assertOk(r)

        buf = self.client.streamContent(r)
        return buf


# Incremental sync over HTTP
######################################################################


class RemoteServer(HttpSyncer):
    def __init__(self, hkey, hostNum) -> None:
        HttpSyncer.__init__(self, hkey, hostNum=hostNum)

    def hostKey(self, user, pw) -> Any:
        "Returns hkey or none if user/pw incorrect."
        self.postVars = dict()
        ret = self.req(
            "hostKey",
            io.BytesIO(json.dumps(dict(u=user, p=pw)).encode("utf8")),
            badAuthRaises=False,
        )
        if not ret:
            # invalid auth
            return
        self.hkey = json.loads(ret.decode("utf8"))["key"]
        return self.hkey

    def meta(self) -> Any:
        self.postVars = dict(k=self.hkey, s=self.skey,)
        ret = self.req(
            "meta",
            io.BytesIO(
                json.dumps(
                    dict(
                        v=SYNC_VER,
                        cv="ankidesktop,%s,%s" % (versionWithBuild(), platDesc()),
                    )
                ).encode("utf8")
            ),
            badAuthRaises=False,
        )
        if not ret:
            # invalid auth
            return
        return json.loads(ret.decode("utf8"))

    def applyGraves(self, **kw) -> Any:
        return self._run("applyGraves", kw)

    def applyChanges(self, **kw) -> Any:
        return self._run("applyChanges", kw)

    def start(self, **kw) -> Any:
        return self._run("start", kw)

    def chunk(self, **kw) -> Any:
        return self._run("chunk", kw)

    def applyChunk(self, **kw) -> Any:
        return self._run("applyChunk", kw)

    def sanityCheck2(self, **kw) -> Any:
        return self._run("sanityCheck2", kw)

    def finish(self, **kw) -> Any:
        return self._run("finish", kw)

    def abort(self, **kw) -> Any:
        return self._run("abort", kw)

    def _run(self, cmd: str, data: Any) -> Any:
        return json.loads(
            self.req(cmd, io.BytesIO(json.dumps(data).encode("utf8"))).decode("utf8")
        )


# Full syncing
##########################################################################


class FullSyncer(HttpSyncer):
    def __init__(self, col, hkey, client, hostNum) -> None:
        HttpSyncer.__init__(self, hkey, client, hostNum=hostNum)
        self.postVars = dict(
            k=self.hkey, v="ankidesktop,%s,%s" % (anki.version, platDesc()),
        )
        self.col = col.weakref()

    def download(self) -> Optional[str]:
        hooks.sync_stage_did_change("download")
        localNotEmpty = self.col.db.scalar("select 1 from cards")
        self.col.close(downgrade=False)
        cont = self.req("download")
        tpath = self.col.path + ".tmp"
        if cont == "upgradeRequired":
            hooks.sync_stage_did_change("upgradeRequired")
            return None
        open(tpath, "wb").write(cont)
        # check the received file is ok
        d = DB(tpath)
        assert d.scalar("pragma integrity_check") == "ok"
        remoteEmpty = not d.scalar("select 1 from cards")
        d.close()
        # accidental clobber?
        if localNotEmpty and remoteEmpty:
            os.unlink(tpath)
            return "downloadClobber"
        # overwrite existing collection
        os.unlink(self.col.path)
        os.rename(tpath, self.col.path)
        self.col = None
        return None

    def upload(self) -> bool:
        "True if upload successful."
        hooks.sync_stage_did_change("upload")
        # make sure it's ok before we try to upload
        if self.col.db.scalar("pragma integrity_check") != "ok":
            return False
        if not self.col.basicCheck():
            return False
        # apply some adjustments, then upload
        self.col.beforeUpload()
        if self.req("upload", open(self.col.path, "rb")) != b"OK":
            return False
        return True
