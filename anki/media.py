# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import os, shutil, re, urllib, urllib2, time, unicodedata, \
    urllib, sys, shutil, simplejson, zipfile
from cStringIO import StringIO
from anki.utils import checksum, intTime, namedtmp, isWin
from anki.lang import _
from anki.db import DB
from anki.consts import *

class MediaManager(object):

    # other code depends on this order, so don't reorder
    regexps = ("(?i)(\[sound:([^]]+)\])",
               "(?i)(<img[^>]+src=[\"']?([^\"'>]+)[\"']?[^>]*>)")

    def __init__(self, deck):
        self.deck = deck
        # media directory
        self._dir = re.sub("(?i)\.(anki)$", ".media", self.deck.path)
        if not os.path.exists(self._dir):
            os.makedirs(self._dir)
        os.chdir(self._dir)
        # change database
        self.connect()

    def connect(self):
        path = self.dir()+".db"
        create = not os.path.exists(path)
        self.db = DB(path)
        if create:
            self._initDB()

    def close(self):
        self.db.close()
        self.db = None

    def dir(self):
        return self._dir

    # Adding media
    ##########################################################################

    def addFile(self, opath):
        """Copy PATH to MEDIADIR, and return new filename.
If the same name exists, compare checksums."""
        mdir = self.dir()
        # remove any dangerous characters
        base = re.sub(r"[][<>:/\\&]", "", os.path.basename(opath))
        dst = os.path.join(mdir, base)
        # if it doesn't exist, copy it directly
        if not os.path.exists(dst):
            shutil.copy2(opath, dst)
            return base
        # if it's identical, reuse
        if self.filesIdentical(opath, dst):
            return base
        # otherwise, find a unique name
        (root, ext) = os.path.splitext(base)
        def repl(match):
            n = int(match.group(1))
            return " (%d)" % (n+1)
        while True:
            path = os.path.join(mdir, root + ext)
            if not os.path.exists(path):
                break
            reg = " \((\d+)\)$"
            if not re.search(reg, root):
                root = root + " (1)"
            else:
                root = re.sub(reg, repl, root)
        # copy and return
        shutil.copy2(opath, path)
        return os.path.basename(os.path.basename(path))

    def filesIdentical(self, path1, path2):
        "True if files are the same."
        return (checksum(open(path1, "rb").read()) ==
                checksum(open(path2, "rb").read()))

    # String manipulation
    ##########################################################################

    def mediaFiles(self, string, includeRemote=False):
        l = []
        for reg in self.regexps:
            for (full, fname) in re.findall(reg, string):
                isLocal = not re.match("(https?|ftp)://", fname.lower())
                if isLocal or includeRemote:
                    l.append(fname)
        return l

    def strip(self, txt):
        for reg in self.regexps:
            txt = re.sub(reg, "", txt)
        return txt

    def escapeImages(self, string):
        # Feeding webkit unicode can result in it not finding images, so on
        # linux/osx we percent escape the image paths as utf8. On Windows the
        # problem is more complicated - if we percent-escape as utf8 it fixes
        # some images but breaks others. When filenames are normalized by
        # dropbox they become unreadable if we escape them.
        if isWin:
            return string
        def repl(match):
            tag = match.group(1)
            fname = match.group(2)
            if re.match("(https?|ftp)://", fname):
                return tag
            return tag.replace(
                fname, urllib.quote(fname.encode("utf-8")))
        return re.sub(self.regexps[1], repl, string)

    # Rebuilding DB
    ##########################################################################

    def check(self, delete=False):
        "Return (missingFiles, unusedFiles)."
        mdir = self.dir()
        if not mdir:
            return (0, 0)
        # generate card q/a and look through all references
        normrefs = {}
        def norm(s):
            if isinstance(s, unicode):
                return unicodedata.normalize('NFD', s)
            return s
        for f in self.allMedia():
            normrefs[norm(f)] = True
        # loop through directory and find unused & missing media
        unused = []
        for file in os.listdir(mdir):
            if file.startswith("latex-"):
                continue
            path = os.path.join(mdir, file)
            if not os.path.isfile(path):
                # ignore directories
                continue
            nfile = norm(file)
            if nfile not in normrefs:
                unused.append(file)
            else:
                del normrefs[nfile]
        # optionally delete
        if delete:
            for f in unused:
                path = os.path.join(mdir, f)
                os.unlink(path)
        nohave = normrefs.keys()
        return (nohave, unused)

    def allMedia(self):
        "Return a set of all referenced filenames."
        files = set()
        for p in self.deck.renderQA(type="all"):
            for type in ("q", "a"):
                for f in self.mediaFiles(p[type]):
                    files.add(f)
        return files

    # Tracking changes (public)
    ##########################################################################

    def added(self):
        self.findChanges()
        return self.db.execute("select * from log where type = ?", MEDIA_ADD)

    def removed(self):
        self.findChanges()
        return self.db.list("select * from log where type = ?", MEDIA_REM)

    def clearLog(self):
        self.db.execute("delete from log")

    def hasChanged(self):
        return self.db.scalar("select 1 from log limit 1")

    # Tracking changes (private)
    ##########################################################################

    def _initDB(self):
        self.db.executescript("""
create table media (fname text primary key, csum text, mod int);
create table meta (dirMod int, usn int); insert into meta values (0, 0);
create table log (fname text primary key, type int);
""")

    def _mtime(self, path):
        return int(os.stat(path).st_mtime)

    def _checksum(self, path):
        return checksum(open(path, "rb").read())

    def usn(self):
        return self.db.scalar("select usn from meta")

    def setUsn(self, usn):
        self.db.execute("update meta set usn = ?", usn)

    def syncMod(self):
        self.db.execute("update meta set dirMod = ?", self._mtime(self.dir()))

    def _changed(self):
        "Return dir mtime if it has changed since the last findChanges()"
        # doesn't track edits, but user can add or remove a file to update
        mod = self.db.scalar("select dirMod from meta")
        mtime = self._mtime(self.dir())
        if mod and mod == mtime:
            return False
        return mtime

    def findChanges(self):
        "Scan the media folder if it's changed, and note any changes."
        if self._changed():
            self._logChanges()

    def _logChanges(self):
        (added, removed) = self._changes()
        log = []
        media = []
        mediaRem = []
        for f in added:
            mt = self._mtime(f)
            media.append((f, self._checksum(f), mt))
            log.append((f, MEDIA_ADD))
        for f in removed:
            mediaRem.append((f,))
            log.append((f, MEDIA_REM))
        # update media db
        self.db.executemany("insert or replace into media values (?,?,?)",
                            media)
        if mediaRem:
            self.db.executemany("delete from media where fname = ?",
                                mediaRem)
        self.db.execute("update meta set dirMod = ?", self._mtime(self.dir()))
        # and logs
        self.db.executemany("insert or replace into log values (?,?)", log)
        self.db.commit()

    def _changes(self):
        self.cache = {}
        for (name, csum, mod) in self.db.execute(
            "select * from media"):
            self.cache[name] = [csum, mod, False]
        added = []
        removed = []
        # loop through on-disk files
        for f in os.listdir(self.dir()):
            # ignore folders
            if os.path.isdir(f):
                continue
            # newly added?
            if f not in self.cache:
                added.append(f)
            else:
                # modified since last time?
                if self._mtime(f) != self.cache[f][1]:
                    # and has different checksum?
                    if self._checksum(f) != self.cache[f][0]:
                        added.append(f)
                # mark as used
                self.cache[f][2] = True
        # look for any entries in the cache that no longer exist on disk
        for (k, v) in self.cache.items():
            if not v[2]:
                removed.append(k)
        return added, removed

    # Adding/removing files in media sync
    ##########################################################################

    def syncRemove(self, fnames):
        for f in fnames:
            if os.path.exists(f):
                os.unlink(f)
                self.db.execute("delete from log where fname = ?", f)
                self.db.execute("delete from media where fname = ?", f)

    def syncAdd(self, zipData):
        "Extract zip data; true if finished."
        f = StringIO(zipData)
        z = zipfile.ZipFile(f, "r")
        finished = False
        meta = None
        media = []
        sizecnt = 0
        # get meta info first
        assert z.getinfo("_meta").file_size < 100000
        meta = simplejson.loads(z.read("_meta"))
        # then loop through all files
        for i in z.infolist():
            # check for zip bombs
            sizecnt += i.file_size
            assert sizecnt < 100*1024*1024
            if i.filename == "_meta":
                # ignore previously-retrieved meta
                continue
            elif i.filename == "_finished":
                # last zip in set
                finished = True
            else:
                data = z.read(i)
                csum = checksum(data)
                name = meta[i.filename]
                # malicious chars?
                for c in '/\\':
                    assert c not in name
                # save file
                open(name, "wb").write(data)
                # update db
                media.append((name, csum, self._mtime(name)))
                # remove entries from local log
                self.db.execute("delete from log where fname = ?", name)
        # update media db
        if media:
            self.db.executemany(
                "insert or replace into media values (?,?,?)", media)
        # if we have finished adding, we need to record the new folder mtime
        # so that we don't trigger a needless scan
        if finished:
            self.syncMod()
        # also need to clear log after sync finished

        return finished

    # Streaming zips
    ##########################################################################
    # Because there's no standard filename encoding for zips, and because not
    # all zip clients support retrieving mtime, we store the files as ascii
    # and place a json file in the zip with the necessary information.

    def zipFromAdded(self, cur):
        "Add files to a zip until over SYNC_ZIP_SIZE. Return zip data."
        f = StringIO()
        z = zipfile.ZipFile(f, "w")
        sz = 0
        cnt = 0
        files = {}
        while 1:
            fname = cur.fetchone()
            if not fname:
                z.writestr("_finished", "")
                break
            fname = fname[0]
            z.write(fname, str(cnt))
            files[str(cnt)] = fname
            sz += os.path.getsize(fname)
            if sz > SYNC_ZIP_SIZE:
                break
            cnt += 1
        z.writestr("_meta", simplejson.dumps(files))
        z.close()
        return f.getvalue()
