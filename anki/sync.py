# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import urllib, simplejson, os, sys, httplib2, gzip
from cStringIO import StringIO
from datetime import date
from anki.db import DB
from anki.errors import *
from anki.utils import ids2str, checksum, intTime
from anki.consts import *
from anki.lang import _
from hooks import runHook

if simplejson.__version__ < "1.7.3":
    raise Exception("SimpleJSON must be 1.7.3 or later.")

# - 64 bit guid will be munged in js; need to escape or rethink

# - make sure /sync/download is compressed
# - status() should be using the hooks instead

# todo:
# - ensure all urllib references are converted to urllib2 for proxies
# - ability to cancel
# - need to make sure syncing doesn't bump the deck modified time if nothing was
#    changed, since by default closing the deck bumps the mod time
# - ensure the user doesn't add foreign chars to passsword

# Incremental syncing
##########################################################################

from anki.consts import *

class Syncer(object):

    def __init__(self, deck, server=None):
        self.deck = deck
        self.server = server

    def status(self, type):
        "Override to trace sync progress."
        #print "sync:", type
        pass

    def sync(self):
        "Returns 'noChanges', 'fullSync', or 'success'."
        # step 1: login & metadata
        self.status("login")
        self.rmod, rscm, self.maxUsn, rts = self.server.meta()
        self.lmod, lscm, self.minUsn, lts = self.meta()
        if abs(rts - lts) > 300:
            return "clockOff"
        if self.lmod == self.rmod:
            return "noChanges"
        elif lscm != rscm:
            return "fullSync"
        self.lnewer = self.lmod > self.rmod
        # step 2: deletions and small objects
        self.status("meta")
        lchg = self.changes()
        rchg = self.server.applyChanges(
            minUsn=self.minUsn, lnewer=self.lnewer, changes=lchg)
        self.mergeChanges(lchg, rchg)
        # step 3: stream large tables from server
        self.status("server")
        while 1:
            self.status("stream")
            chunk = self.server.chunk()
            self.applyChunk(chunk=chunk)
            if chunk['done']:
                break
        # step 4: stream to server
        self.status("client")
        while 1:
            self.status("stream")
            chunk = self.chunk()
            self.server.applyChunk(chunk=chunk)
            if chunk['done']:
                break
        # step 5: sanity check during beta testing
        self.status("sanity")
        c = self.sanityCheck()
        s = self.server.sanityCheck()
        assert c == s
        # finalize
        self.status("finalize")
        mod = self.server.finish()
        self.finish(mod)
        return "success"

    def meta(self):
        return (self.deck.mod, self.deck.scm, self.deck._usn, intTime())

    def changes(self):
        "Bundle up deletions and small objects, and apply if server."
        d = dict(models=self.getModels(),
                 groups=self.getGroups(),
                 tags=self.getTags(),
                 graves=self.getGraves())
        if self.lnewer:
            d['conf'] = self.getConf()
        return d

    def applyChanges(self, minUsn, lnewer, changes):
        # we're the server; save info
        self.maxUsn = self.deck._usn
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
        self.mergeGroups(rchg['groups'])
        self.mergeTags(rchg['tags'])
        if 'conf' in rchg:
            self.mergeConf(rchg['conf'])
        self.prepareToChunk()

    def sanityCheck(self):
        # some basic checks to ensure the sync went ok. this is slow, so will
        # be removed before official release
        assert not self.deck.db.scalar("""
select count() from cards where fid not in (select id from facts)""")
        assert not self.deck.db.scalar("""
select count() from facts where id not in (select distinct fid from cards)""")
        for t in "cards", "facts", "revlog", "graves":
            assert not self.deck.db.scalar(
                "select count() from %s where usn = -1" % t)
        for g in self.deck.groups.all():
            assert g['usn'] != -1
        for t, usn in self.deck.tags.allItems():
            assert usn != -1
        for m in self.deck.models.all():
            assert m['usn'] != -1
        return [
            self.deck.db.scalar("select count() from cards"),
            self.deck.db.scalar("select count() from facts"),
            self.deck.db.scalar("select count() from revlog"),
            self.deck.db.scalar("select count() from fsums"),
            self.deck.db.scalar("select count() from graves"),
            len(self.deck.models.all()),
            len(self.deck.tags.all()),
            len(self.deck.groups.all()),
            len(self.deck.groups.allConf()),
        ]

    def usnLim(self):
        if self.deck.server:
            return "usn >= %d" % self.minUsn
        else:
            return "usn = -1"

    def finish(self, mod=None):
        if not mod:
            # server side; we decide new mod time
            mod = intTime(1000)
        self.deck.ls = mod
        self.deck._usn = self.maxUsn + 1
        self.deck.save(mod=mod)
        return mod

    # Chunked syncing
    ##########################################################################

    def prepareToChunk(self):
        self.tablesLeft = ["revlog", "cards", "facts"]
        self.cursor = None

    def cursorForTable(self, table):
        lim = self.usnLim()
        x = self.deck.db.execute
        d = (self.maxUsn, lim)
        if table == "revlog":
            return x("""
select id, cid, %d, ease, ivl, lastIvl, factor, time, type
from revlog where %s""" % d)
        elif table == "cards":
            return x("""
select id, fid, gid, ord, mod, %d, type, queue, due, ivl, factor, reps,
lapses, left, edue, flags, data from cards where %s""" % d)
        else:
            return x("""
select id, guid, mid, gid, mod, %d, tags, flds, '', flags, data
from facts where %s""" % d)

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
                if not self.deck.server:
                    self.deck.db.execute(
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
        if "facts" in chunk:
            self.mergeFacts(chunk['facts'])

    # Deletions
    ##########################################################################

    def getGraves(self):
        cards = []
        facts = []
        groups = []
        if self.deck.server:
            curs = self.deck.db.execute(
                "select oid, type from graves where usn >= ?", self.minUsn)
        else:
            curs = self.deck.db.execute(
                "select oid, type from graves where usn = -1")
        for oid, type in curs:
            if type == REM_CARD:
                cards.append(oid)
            elif type == REM_FACT:
                facts.append(oid)
            else:
                groups.append(oid)
        if not self.deck.server:
            self.deck.db.execute("update graves set usn=? where usn=-1",
                                 self.maxUsn)
        return dict(cards=cards, facts=facts, groups=groups)

    def mergeGraves(self, graves):
        # facts first, so we don't end up with duplicate graves
        self.deck._remFacts(graves['facts'])
        self.deck.remCards(graves['cards'])
        for oid in graves['groups']:
            self.deck.groups.rem(oid)

    # Models
    ##########################################################################

    def getModels(self):
        if self.deck.server:
            return [m for m in self.deck.models.all() if m['usn'] >= self.minUsn]
        else:
            mods = [m for m in self.deck.models.all() if m['usn'] == -1]
            for m in mods:
                m['usn'] = self.maxUsn
            self.deck.models.save()
            return mods

    def mergeModels(self, rchg):
        for r in rchg:
            l = self.deck.models.get(r['id'])
            # if missing locally or server is newer, update
            if not l or r['mod'] > l['mod']:
                self.deck.models.update(r)

    # Groups
    ##########################################################################

    def getGroups(self):
        if self.deck.server:
            return [
                [g for g in self.deck.groups.all() if g['usn'] >= self.minUsn],
                [g for g in self.deck.groups.allConf() if g['usn'] >= self.minUsn]
            ]
        else:
            groups = [g for g in self.deck.groups.all() if g['usn'] == -1]
            for g in groups:
                g['usn'] = self.maxUsn
            gconf = [g for g in self.deck.groups.allConf() if g['usn'] == -1]
            for g in gconf:
                g['usn'] = self.maxUsn
            self.deck.groups.save()
            return [groups, gconf]

    def mergeGroups(self, rchg):
        for r in rchg[0]:
            l = self.deck.groups.get(r['id'], False)
            # if missing locally or server is newer, update
            if not l or r['mod'] > l['mod']:
                self.deck.groups.update(r)
        for r in rchg[1]:
            l = self.deck.groups.conf(r['id'])
            # if missing locally or server is newer, update
            if not l or r['mod'] > l['mod']:
                self.deck.groups.updateConf(r)

    # Tags
    ##########################################################################

    def getTags(self):
        if self.deck.server:
            return [t for t, usn in self.deck.tags.allItems()
                    if usn >= self.minUsn]
        else:
            tags = []
            for t, usn in self.deck.tags.allItems():
                if usn == -1:
                    self.deck.tags.tags[t] = self.maxUsn
                    tags.append(t)
            self.deck.tags.save()
            return tags

    def mergeTags(self, tags):
        self.deck.tags.register(tags, usn=self.maxUsn)

    # Cards/facts/revlog
    ##########################################################################

    def mergeRevlog(self, logs):
        self.deck.db.executemany(
            "insert or ignore into revlog values (?,?,?,?,?,?,?,?,?)",
            logs)

    def newerRows(self, data, table, modIdx):
        ids = (r[0] for r in data)
        lmods = {}
        for id, mod in self.deck.db.execute(
            "select id, mod from %s where id in %s and %s" % (
                table, ids2str(ids), self.usnLim())):
            lmods[id] = mod
        update = []
        for r in data:
            if r[0] not in lmods or lmods[r[0]] < r[modIdx]:
                update.append(r)
        return update

    def mergeCards(self, cards):
        self.deck.db.executemany(
            "insert or replace into cards values "
            "(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            self.newerRows(cards, "cards", 4))

    def mergeFacts(self, facts):
        rows = self.newerRows(facts, "facts", 4)
        self.deck.db.executemany(
            "insert or replace into facts values (?,?,?,?,?,?,?,?,?,?,?)",
            rows)
        self.deck.updateFieldCache([f[0] for f in rows])

    # Deck config
    ##########################################################################

    def getConf(self):
        return self.deck.conf

    def mergeConf(self, conf):
        self.deck.conf = conf

# Local syncing for unit tests
##########################################################################

class LocalServer(Syncer):

    # serialize/deserialize payload, so we don't end up sharing objects
    # between decks
    def applyChanges(self, minUsn, lnewer, changes):
        l = simplejson.loads; d = simplejson.dumps
        return l(d(Syncer.applyChanges(self, minUsn, lnewer, l(d(changes)))))

# HTTP syncing tools
##########################################################################

class HttpSyncer(object):

    # retrieving a host key for future operations
    def hostKey(self, pw):
        h = httplib2.Http(timeout=60)
        resp, cont = h.request(
            SYNC_URL+"hostKey?" + urllib.urlencode(dict(u=self.user,p=pw)))
        if resp['status'] != '200':
            raise Exception("Invalid response code: %s" % resp['status'])
        self.hkey = cont
        return cont

    def _vars(self):
        return dict(k=self.hkey)

    # Posting data as a file
    ######################################################################
    # We don't want to post the payload as a form var, as the percent-encoding is
    # costly. We could send it as a raw post, but more HTTP clients seem to
    # support file uploading, so this is the more compatible choice.

    def postData(self, http, method, fobj, vars, comp=1):
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
                data = fobj.read(CHUNK_SIZE)
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
        if resp['status'] != '200':
            raise Exception("Invalid response code: %s" % resp['status'])
        return cont

# Incremental sync over HTTP
######################################################################

class RemoteServer(Syncer, HttpSyncer):

    def __init__(self, user, hkey):
        self.user = user
        self.hkey = hkey
        self.con = None

    def meta(self):
        h = httplib2.Http(timeout=60)
        resp, cont = h.request(
            SYNC_URL+"meta?" + urllib.urlencode(dict(u=self.user,v=SYNC_VER)))
        # fixme: convert these into easily-catchable errors
        if resp['status'] in ('503', '504'):
            raise Exception("Server is too busy; please try again later.")
        elif resp['status'] == '501':
            raise Exception("Your client is out of date; please upgrade.")
        elif resp['status'] == '403':
            raise Exception("Invalid key; please authenticate.")
        elif resp['status'] != '200':
            raise Exception("Invalid response code: %s" % resp['status'])
        return simplejson.loads(cont)

    def applyChanges(self, **kw):
        self.con = httplib2.Http(timeout=60)
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

    def __init__(self, deck, hkey):
        self.deck = deck
        self.hkey = hkey

    def _con(self):
        return httplib2.Http(timeout=60)

    def download(self):
        self.deck.close()
        resp, cont = self._con().request(
            SYNC_URL+"download?" + urllib.urlencode(self._vars()))
        if resp['status'] != '200':
            raise Exception("Invalid response code: %s" % resp['status'])
        tpath = self.deck.path + ".tmp"
        open(tpath, "wb").write(cont)
        os.unlink(self.deck.path)
        os.rename(tpath, self.deck.path)
        d = DB(self.deck.path)
        assert d.scalar("pragma integrity_check") == "ok"
        self.deck = None

    def upload(self):
        self.deck.beforeUpload()
        assert self.postData(self._con(), "upload", open(self.deck.path, "rb"),
                             self._vars(), comp=6) == "OK"

# Media syncing
##########################################################################

class MediaSyncer(object):

    def __init__(self, deck, server=None):
        self.deck = deck
        self.server = server
        self.added = None

    def sync(self):
        # step 1: send/recv deletions
        runHook("mediaSync", "remove")
        usn = self.deck.media.usn()
        lrem = self.removed()
        rrem = self.server.remove(fnames=lrem, minUsn=usn)
        self.remove(rrem)
        # step 2: stream files from server
        runHook("mediaSync", "server")
        while 1:
            runHook("mediaSync", "stream")
            zip = self.server.files()
            if self.addFiles(zip=zip) != "continue":
                break
        # step 3: stream files to the server
        runHook("mediaSync", "client")
        while 1:
            runHook("mediaSync", "stream")
            zip = self.files()
            usn = self.server.addFiles(zip=zip)
            if usn != "continue":
                # when server has run out of files, it returns bumped usn
                break
        self.deck.media.setUsn(usn)
        self.deck.media.clearLog()
        # clear cursor so successive calls work
        self.added = None

    def removed(self):
        return self.deck.media.removed()

    def remove(self, fnames, minUsn=None):
        self.deck.media.syncRemove(fnames)
        if minUsn is not None:
            # we're the server
            self.minUsn = minUsn
            return self.deck.media.removed()

    def files(self):
        if not self.added:
            self.added = self.deck.media.added()
        return self.deck.media.zipFromAdded(self.added)

    def addFiles(self, zip):
        "True if zip is the last in set. Server returns new usn instead."
        return self.deck.media.syncAdd(zip)

# Remote media syncing
##########################################################################

class RemoteMediaServer(MediaSyncer, HttpSyncer):

    def __init__(self, hkey):
        self.hkey = hkey
        self.con = httplib2.Http(timeout=60)

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

