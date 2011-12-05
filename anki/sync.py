# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import urllib, simplejson, os, sys, httplib2, gzip
from cStringIO import StringIO
from datetime import date
from anki.db import DB
from anki.errors import *
from anki.utils import ids2str, checksum, intTime, httpCon
from anki.consts import *
from anki.lang import _
from hooks import runHook

if simplejson.__version__ < "1.7.3":
    raise Exception("SimpleJSON must be 1.7.3 or later.")

# - make sure /sync/download is compressed
# - status() should be using the hooks instead

# todo:
# - ability to cancel
# - need to make sure syncing doesn't bump the col modified time if nothing was
#    changed, since by default closing the col bumps the mod time
# - ensure the user doesn't add foreign chars to passsword

# Incremental syncing
##########################################################################

from anki.consts import *

class Syncer(object):

    def __init__(self, col, server=None):
        self.col = col
        self.server = server

    def sync(self):
        "Returns 'noChanges', 'fullSync', or 'success'."
        # if the deck has any pending changes, flush them first and bump mod
        # time
        self.col.save()
        # step 1: login & metadata
        runHook("sync", "login")
        ret = self.server.meta()
        if not ret:
            return "badAuth"
        self.rmod, rscm, self.maxUsn, rts, self.mediaUsn = ret
        self.lmod, lscm, self.minUsn, lts, dummy = self.meta()
        if abs(rts - lts) > 300:
            return "clockOff"
        if self.lmod == self.rmod:
            return "noChanges"
        elif lscm != rscm:
            return "fullSync"
        self.lnewer = self.lmod > self.rmod
        # step 2: deletions and small objects
        runHook("sync", "meta")
        lchg = self.changes()
        rchg = self.server.applyChanges(
            minUsn=self.minUsn, lnewer=self.lnewer, changes=lchg)
        self.mergeChanges(lchg, rchg)
        # step 3: stream large tables from server
        runHook("sync", "server")
        while 1:
            runHook("sync", "stream")
            chunk = self.server.chunk()
            self.applyChunk(chunk=chunk)
            if chunk['done']:
                break
        # step 4: stream to server
        runHook("sync", "client")
        while 1:
            runHook("sync", "stream")
            chunk = self.chunk()
            self.server.applyChunk(chunk=chunk)
            if chunk['done']:
                break
        # step 5: sanity check during beta testing
        runHook("sync", "sanity")
        c = self.sanityCheck()
        s = self.server.sanityCheck()
        assert c == s
        # finalize
        runHook("sync", "finalize")
        mod = self.server.finish()
        self.finish(mod)
        return "success"

    def meta(self):
        return (self.col.mod, self.col.scm, self.col._usn, intTime(), None)

    def changes(self):
        "Bundle up deletions and small objects, and apply if server."
        d = dict(models=self.getModels(),
                 decks=self.getDecks(),
                 tags=self.getTags(),
                 graves=self.getGraves())
        if self.lnewer:
            d['conf'] = self.getConf()
        return d

    def applyChanges(self, minUsn, lnewer, changes):
        # we're the server; save info
        self.maxUsn = self.col._usn
        self.minUsn = minUsn
        self.lnewer = not lnewer
        self.rchg = changes
        lchg = self.changes()
        # merge our side before returning
        self.mergeChanges(lchg, self.rchg)
        return lchg

    def mergeChanges(self, lchg, rchg):
        # first, handle the deletions
        self.mergeGraves(rchg['graves'])
        # then the other objects
        self.mergeModels(rchg['models'])
        self.mergeDecks(rchg['decks'])
        self.mergeTags(rchg['tags'])
        if 'conf' in rchg:
            self.mergeConf(rchg['conf'])
        self.prepareToChunk()

    def sanityCheck(self):
        # some basic checks to ensure the sync went ok. this is slow, so will
        # be removed before official release
        assert not self.col.db.scalar("""
select count() from cards where nid not in (select id from notes)""")
        assert not self.col.db.scalar("""
select count() from notes where id not in (select distinct nid from cards)""")
        for t in "cards", "notes", "revlog", "graves":
            assert not self.col.db.scalar(
                "select count() from %s where usn = -1" % t)
        for g in self.col.decks.all():
            assert g['usn'] != -1
        for t, usn in self.col.tags.allItems():
            assert usn != -1
        for m in self.col.models.all():
            assert m['usn'] != -1
        self.col.sched.reset()
        return [
            list(self.col.sched.counts()),
            self.col.db.scalar("select count() from cards"),
            self.col.db.scalar("select count() from notes"),
            self.col.db.scalar("select count() from revlog"),
            self.col.db.scalar("select count() from graves"),
            len(self.col.models.all()),
            len(self.col.tags.all()),
            len(self.col.decks.all()),
            len(self.col.decks.allConf()),
        ]

    def usnLim(self):
        if self.col.server:
            return "usn >= %d" % self.minUsn
        else:
            return "usn = -1"

    def finish(self, mod=None):
        if not mod:
            # server side; we decide new mod time
            mod = intTime(1000)
        self.col.ls = mod
        self.col._usn = self.maxUsn + 1
        self.col.save(mod=mod)
        return mod

    # Chunked syncing
    ##########################################################################

    def prepareToChunk(self):
        self.tablesLeft = ["revlog", "cards", "notes"]
        self.cursor = None

    def cursorForTable(self, table):
        lim = self.usnLim()
        x = self.col.db.execute
        d = (self.maxUsn, lim)
        if table == "revlog":
            return x("""
select id, cid, %d, ease, ivl, lastIvl, factor, time, type
from revlog where %s""" % d)
        elif table == "cards":
            return x("""
select id, nid, did, ord, mod, %d, type, queue, due, ivl, factor, reps,
lapses, left, edue, flags, data from cards where %s""" % d)
        else:
            return x("""
select id, guid, mid, did, mod, %d, tags, flds, '', '', flags, data
from notes where %s""" % d)

    def chunk(self):
        buf = dict(done=False)
        # gather up to 5000 records
        lim = 5000
        while self.tablesLeft and lim:
            curTable = self.tablesLeft[0]
            if not self.cursor:
                self.cursor = self.cursorForTable(curTable)
            rows = self.cursor.fetchmany(lim)
            fetched = len(rows)
            if fetched != lim:
                # table is empty
                self.tablesLeft.pop(0)
                self.cursor = None
                # if we're the client, mark the objects as having been sent
                if not self.col.server:
                    self.col.db.execute(
                        "update %s set usn=? where usn=-1"%curTable,
                        self.maxUsn)
            buf[curTable] = rows
            lim -= fetched
        if not self.tablesLeft:
            buf['done'] = True
        return buf

    def applyChunk(self, chunk):
        if "revlog" in chunk:
            self.mergeRevlog(chunk['revlog'])
        if "cards" in chunk:
            self.mergeCards(chunk['cards'])
        if "notes" in chunk:
            self.mergeNotes(chunk['notes'])

    # Deletions
    ##########################################################################

    def getGraves(self):
        cards = []
        notes = []
        decks = []
        if self.col.server:
            curs = self.col.db.execute(
                "select oid, type from graves where usn >= ?", self.minUsn)
        else:
            curs = self.col.db.execute(
                "select oid, type from graves where usn = -1")
        for oid, type in curs:
            if type == REM_CARD:
                cards.append(oid)
            elif type == REM_NOTE:
                notes.append(oid)
            else:
                decks.append(oid)
        if not self.col.server:
            self.col.db.execute("update graves set usn=? where usn=-1",
                                 self.maxUsn)
        return dict(cards=cards, notes=notes, decks=decks)

    def mergeGraves(self, graves):
        # notes first, so we don't end up with duplicate graves
        self.col._remNotes(graves['notes'])
        self.col.remCards(graves['cards'])
        for oid in graves['decks']:
            self.col.decks.rem(oid)

    # Models
    ##########################################################################

    def getModels(self):
        if self.col.server:
            return [m for m in self.col.models.all() if m['usn'] >= self.minUsn]
        else:
            mods = [m for m in self.col.models.all() if m['usn'] == -1]
            for m in mods:
                m['usn'] = self.maxUsn
            self.col.models.save()
            return mods

    def mergeModels(self, rchg):
        for r in rchg:
            l = self.col.models.get(r['id'])
            # if missing locally or server is newer, update
            if not l or r['mod'] > l['mod']:
                self.col.models.update(r)

    # Decks
    ##########################################################################

    def getDecks(self):
        if self.col.server:
            return [
                [g for g in self.col.decks.all() if g['usn'] >= self.minUsn],
                [g for g in self.col.decks.allConf() if g['usn'] >= self.minUsn]
            ]
        else:
            decks = [g for g in self.col.decks.all() if g['usn'] == -1]
            for g in decks:
                g['usn'] = self.maxUsn
            dconf = [g for g in self.col.decks.allConf() if g['usn'] == -1]
            for g in dconf:
                g['usn'] = self.maxUsn
            self.col.decks.save()
            return [decks, dconf]

    def mergeDecks(self, rchg):
        for r in rchg[0]:
            l = self.col.decks.get(r['id'], False)
            # if missing locally or server is newer, update
            if not l or r['mod'] > l['mod']:
                self.col.decks.update(r)
        for r in rchg[1]:
            l = self.col.decks.conf(r['id'])
            # if missing locally or server is newer, update
            if not l or r['mod'] > l['mod']:
                self.col.decks.updateConf(r)

    # Tags
    ##########################################################################

    def getTags(self):
        if self.col.server:
            return [t for t, usn in self.col.tags.allItems()
                    if usn >= self.minUsn]
        else:
            tags = []
            for t, usn in self.col.tags.allItems():
                if usn == -1:
                    self.col.tags.tags[t] = self.maxUsn
                    tags.append(t)
            self.col.tags.save()
            return tags

    def mergeTags(self, tags):
        self.col.tags.register(tags, usn=self.maxUsn)

    # Cards/notes/revlog
    ##########################################################################

    def mergeRevlog(self, logs):
        self.col.db.executemany(
            "insert or ignore into revlog values (?,?,?,?,?,?,?,?,?)",
            logs)

    def newerRows(self, data, table, modIdx):
        ids = (r[0] for r in data)
        lmods = {}
        for id, mod in self.col.db.execute(
            "select id, mod from %s where id in %s and %s" % (
                table, ids2str(ids), self.usnLim())):
            lmods[id] = mod
        update = []
        for r in data:
            if r[0] not in lmods or lmods[r[0]] < r[modIdx]:
                update.append(r)
        return update

    def mergeCards(self, cards):
        self.col.db.executemany(
            "insert or replace into cards values "
            "(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            self.newerRows(cards, "cards", 4))

    def mergeNotes(self, notes):
        rows = self.newerRows(notes, "notes", 4)
        self.col.db.executemany(
            "insert or replace into notes values (?,?,?,?,?,?,?,?,?,?,?,?)",
            rows)
        self.col.updateFieldCache([f[0] for f in rows])

    # Col config
    ##########################################################################

    def getConf(self):
        return self.col.conf

    def mergeConf(self, conf):
        self.col.conf = conf

# Local syncing for unit tests
##########################################################################

class LocalServer(Syncer):

    # serialize/deserialize payload, so we don't end up sharing objects
    # between cols
    def applyChanges(self, minUsn, lnewer, changes):
        l = simplejson.loads; d = simplejson.dumps
        return l(d(Syncer.applyChanges(self, minUsn, lnewer, l(d(changes)))))

# HTTP syncing tools
##########################################################################

# Calling code should catch the following codes:
# - 501: client needs upgrade
# - 502: ankiweb down
# - 503/504: server too busy

class HttpSyncer(object):

    def _vars(self):
        return dict(k=self.hkey)

    def assertOk(self, resp):
        if resp['status'] != '200':
            raise Exception("Unknown response code: %s" % resp['status'])

    # Posting data as a file
    ######################################################################
    # We don't want to post the payload as a form var, as the percent-encoding is
    # costly. We could send it as a raw post, but more HTTP clients seem to
    # support file uploading, so this is the more compatible choice.

    def postData(self, http, method, fobj, vars, comp=6):
        bdry = "--"+MIME_BOUNDARY
        # write out post vars, including session key and compression flag
        buf = StringIO()
        vars = vars or {}
        vars['c'] = 1 if comp else 0
        for (key, value) in vars.items():
            buf.write(bdry + "\r\n")
            buf.write(
                'Content-Disposition: form-data; name="%s"\r\n\r\n%s\r\n' %
                (key, value))
        # file header
        if fobj:
            buf.write(bdry + "\r\n")
            buf.write("""\
Content-Disposition: form-data; name="data"; filename="data"\r\n\
Content-Type: application/octet-stream\r\n\r\n""")
            # write file into buffer, optionally compressing
            if comp:
                tgt = gzip.GzipFile(mode="wb", fileobj=buf, compresslevel=comp)
            else:
                tgt = buf
            while 1:
                data = fobj.read(65536)
                if not data:
                    if comp:
                        tgt.close()
                    break
                tgt.write(data)
            buf.write('\r\n' + bdry + '--\r\n')
        size = buf.tell()
        # connection headers
        headers = {
            'Content-Type': 'multipart/form-data; boundary=%s' % MIME_BOUNDARY,
            'Content-Length': str(size),
        }
        body = buf.getvalue()
        buf.close()
        resp, cont = http.request(
            SYNC_URL+method, "POST", headers=headers, body=body)
        self.assertOk(resp)
        return cont

# Incremental sync over HTTP
######################################################################

class RemoteServer(Syncer, HttpSyncer):

    def __init__(self, hkey):
        self.hkey = hkey
        self.con = httpCon()

    def hostKey(self, user, pw):
        "Returns hkey or none if user/pw incorrect."
        user = user.encode("utf-8")
        pw = pw.encode("utf-8")
        resp, cont = self.con.request(
            SYNC_URL+"hostKey?" + urllib.urlencode(dict(u=user,p=pw)))
        if resp['status'] == '403':
            # invalid auth
            return
        self.assertOk(resp)
        self.hkey = simplejson.loads(cont)['key']
        return self.hkey

    def meta(self):
        resp, cont = self.con.request(
            SYNC_URL+"meta?" + urllib.urlencode(dict(k=self.hkey,v=SYNC_VER)))
        if resp['status'] == '403':
            # auth failure
            return
        self.assertOk(resp)
        return simplejson.loads(cont)

    def applyChanges(self, **kw):
        return self._run("applyChanges", kw)

    def chunk(self, **kw):
        return self._run("chunk", kw)

    def applyChunk(self, **kw):
        return self._run("applyChunk", kw)

    def sanityCheck(self, **kw):
        return self._run("sanityCheck", kw)

    def finish(self, **kw):
        return self._run("finish", kw)

    def _run(self, cmd, data):
        return simplejson.loads(
            self.postData(self.con, cmd, StringIO(simplejson.dumps(data)),
                          self._vars()))

# Full syncing
##########################################################################

class FullSyncer(HttpSyncer):

    def __init__(self, col, hkey, con):
        self.col = col
        self.hkey = hkey
        self.con = con

    def download(self):
        runHook("sync", "download")
        self.col.close()
        resp, cont = self.con.request(
            SYNC_URL+"download?" + urllib.urlencode(self._vars()))
        self.assertOk(resp)
        tpath = self.col.path + ".tmp"
        open(tpath, "wb").write(cont)
        # check the received file is ok
        d = DB(tpath)
        assert d.scalar("pragma integrity_check") == "ok"
        d.close()
        # overwrite existing collection
        os.unlink(self.col.path)
        os.rename(tpath, self.col.path)
        self.col = None

    def upload(self):
        runHook("sync", "upload")
        self.col.beforeUpload()
        assert self.postData(self.con, "upload", open(self.col.path, "rb"),
                             self._vars()) == "OK"

# Media syncing
##########################################################################

class MediaSyncer(object):

    def __init__(self, col, server=None):
        self.col = col
        self.server = server
        self.added = None

    def sync(self, mediaUsn):
        # step 1: check if there have been any changes
        runHook("sync", "findMedia")
        self.col.media.findChanges()
        lusn = self.col.media.usn()
        if lusn == mediaUsn and not self.col.media.hasChanged():
            return "noChanges"
        # step 2: send/recv deletions
        runHook("sync", "removeMedia")
        lrem = self.removed()
        rrem = self.server.remove(fnames=lrem, minUsn=lusn)
        self.remove(rrem)
        # step 3: stream files from server
        runHook("sync", "server")
        while 1:
            runHook("sync", "streamMedia")
            zip = self.server.files()
            if self.addFiles(zip=zip):
                break
        # step 4: stream files to the server
        runHook("sync", "client")
        while 1:
            runHook("sync", "streamMedia")
            zip = self.files()
            usn = self.server.addFiles(zip=zip)
            if usn is not False:
                # when server has run out of files, it returns bumped usn
                break
        # step 5: finalize
        self.col.media.setUsn(usn)
        self.col.media.clearLog()
        # clear cursor so successive calls work
        self.added = None
        return "success"

    def removed(self):
        return self.col.media.removed()

    def remove(self, fnames, minUsn=None):
        self.col.media.syncRemove(fnames)
        if minUsn is not None:
            # we're the server
            self.minUsn = minUsn
            return self.col.media.removed()

    def files(self):
        if not self.added:
            self.added = self.col.media.added()
        return self.col.media.zipFromAdded(self.added)

    def addFiles(self, zip):
        "True if zip is the last in set. Server returns new usn instead."
        return self.col.media.syncAdd(zip)

# Remote media syncing
##########################################################################

class RemoteMediaServer(MediaSyncer, HttpSyncer):

    def __init__(self, hkey, con):
        self.hkey = hkey
        self.con = con

    def remove(self, **kw):
        return simplejson.loads(
            self.postData(
                self.con, "remove", StringIO(simplejson.dumps(kw)),
                          self._vars()))

    def files(self):
        return self.postData(self.con, "files", None, self._vars())

    def addFiles(self, zip):
        return simplejson.loads(
            self.postData(self.con, "addFiles", StringIO(zip),
                          self._vars(), comp=0))

    # only for unit tests
    def mediatest(self, n):
        return simplejson.loads(
            self.postData(self.con, "mediatest", StringIO(
                simplejson.dumps(dict(n=n))), self._vars()))
