# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import os, shutil, re, urllib, urllib2, time, unicodedata, \
    urllib, sys, shutil
from anki.utils import checksum, intTime, namedtmp, isWin
from anki.lang import _
from anki.db import DB

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
        path = self.dir()+"db"
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

    # Tracking changes
    ##########################################################################

    def _initDB(self):
        # in the log, a mod time of zero indicates a delete
        self.db.executescript("""
create table media (fname text primary key, csum text, mod int);
create table meta (dirMod int, inSync int);
insert into meta values (0, 0);
create table log (id int, fname text, mod int);
create index ix_log_id on log (id);
""")

    def _mtime(self, path):
        return int(os.stat(path).st_mtime)

    def _checksum(self, path):
        return checksum(open(path, "rb").read())

    def changed(self):
        "Return dir mtime if it has changed since the last findChanges()"
        # doesn't track edits, but user can add or remove a file to update
        mod = self.db.scalar("select dirMod from meta")
        mtime = self._mtime(self.dir())
        if mod and mod == mtime:
            return False
        return mtime

    def findChanges(self):
        "Scan the media folder if it's changed, and note any changes."
        if self.changed():
            self._logChanges()

    def changesSince(self, mod):
        "Return a list of added and removed files since MOD time."
        self.findChanges()
        added = {}
        removed = {}
        # loop through and collect changes, removing duplicates
        for fname, mod in self.db.all(
            "select fname, mod from log where id > ?", mod):
            if mod:
                added[fname] = mod
            else:
                removed[fname] = mod
        return added.items(), removed.keys()

    def _changes(self):
        self.cache = {}
        for (name, csum, mod) in self.db.execute(
            "select * from media"):
            self.cache[name] = [csum, mod, False]
        added = []
        removed = []
        changed = []
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
                        changed.append(f)
                # mark as used
                self.cache[f][2] = True
        # look for any entries in the cache that no longer exist on disk
        for (k, v) in self.cache.items():
            if not v[2]:
                removed.append(k)
        return added, changed, removed

    def _logChanges(self):
        (added, changed, removed) = self._changes()
        log = []
        media = []
        mediaRem = []
        t = intTime()
        for f in added:
            mt = self._mtime(f)
            media.append((f, self._checksum(f), mt))
            log.append((t, f, mt))
        for f in changed:
            mt = self._mtime(f)
            media.append((f, self._checksum(f), mt))
            log.append((t, f, 0))
            log.append((t, f, mt))
        for f in removed:
            mediaRem.append((f,))
            log.append((t, f, 0))
        # update db
        self.db.executemany("insert or replace into media values (?,?,?)",
                            media)
        self.db.executemany("insert into log values (?,?,?)", log)
        if mediaRem:
            self.db.executemany("delete from media where fname = ?",
                                mediaRem)
        self.db.execute("update meta set dirMod = ?", self._mtime(self.dir()))
        self.db.commit()

