# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import os
import re
import urllib
import unicodedata
import sys
import zipfile
from cStringIO import StringIO

import send2trash
from anki.utils import checksum, isWin, isMac, json
from anki.db import DB
from anki.consts import *
from anki.latex import mungeQA


class MediaManager(object):

    soundRegexps = ["(?i)(\[sound:(?P<fname>[^]]+)\])"]
    imgRegexps = [
        # src element quoted case
        "(?i)(<img[^>]+src=(?P<str>[\"'])(?P<fname>[^>]+?)(?P=str)[^>]*>)",
        # unquoted case
        "(?i)(<img[^>]+src=(?!['\"])(?P<fname>[^ >]+)[^>]*?>)",
    ]
    regexps = soundRegexps + imgRegexps

    def __init__(self, col, server):
        self.col = col
        if server:
            self._dir = None
            return
        # media directory
        self._dir = re.sub("(?i)\.(anki2)$", ".media", self.col.path)
        # convert dir to unicode if it's not already
        if isinstance(self._dir, str):
            self._dir = unicode(self._dir, sys.getfilesystemencoding())
        if not os.path.exists(self._dir):
            os.makedirs(self._dir)
        try:
            self._oldcwd = os.getcwd()
        except OSError:
            # cwd doesn't exist
            self._oldcwd = None
        try:
            os.chdir(self._dir)
        except OSError:
            raise Exception("invalidTempFolder")
        # change database
        self.connect()

    def connect(self):
        if self.col.server:
            return
        path = self.dir()+".db"
        create = not os.path.exists(path)
        os.chdir(self._dir)
        self.db = DB(path)
        if create:
            self._initDB()

    def close(self):
        if self.col.server:
            return
        self.db.close()
        self.db = None
        # change cwd back to old location
        if self._oldcwd:
            try:
                os.chdir(self._oldcwd)
            except:
                # may have been deleted
                pass

    def dir(self):
        return self._dir

    def _isFAT32(self):
        if not isWin:
            return
        import win32api, win32file
        try:
            name = win32file.GetVolumeNameForVolumeMountPoint(self._dir[:3])
        except:
            # mapped & unmapped network drive; pray that it's not vfat
            return
        if win32api.GetVolumeInformation(name)[4].lower().startswith("fat"):
            return True

    # Adding media
    ##########################################################################
    # opath must be in unicode

    def addFile(self, opath):
        return self.writeData(opath, open(opath, "rb").read())

    def writeData(self, opath, data):
        # if fname is a full path, use only the basename
        fname = os.path.basename(opath)
        # make sure we write it in NFC form (on mac will autoconvert to NFD),
        # and return an NFC-encoded reference
        fname = unicodedata.normalize("NFC", fname)
        # remove any dangerous characters
        base = self.stripIllegal(fname)
        (root, ext) = os.path.splitext(base)
        def repl(match):
            n = int(match.group(1))
            return " (%d)" % (n+1)
        # find the first available name
        csum = checksum(data)
        while True:
            fname = root + ext
            path = os.path.join(self.dir(), fname)
            # if it doesn't exist, copy it directly
            if not os.path.exists(path):
                open(path, "wb").write(data)
                return fname
            # if it's identical, reuse
            if checksum(open(path, "rb").read()) == csum:
                return fname
            # otherwise, increment the index in the filename
            reg = " \((\d+)\)$"
            if not re.search(reg, root):
                root = root + " (1)"
            else:
                root = re.sub(reg, repl, root)

    # String manipulation
    ##########################################################################

    def filesInStr(self, mid, string, includeRemote=False):
        l = []
        model = self.col.models.get(mid)
        strings = []
        if model['type'] == MODEL_CLOZE and "{{c" in string:
            # if the field has clozes in it, we'll need to expand the
            # possibilities so we can render latex
            strings = self._expandClozes(string)
        else:
            strings = [string]
        for string in strings:
            # handle latex
            string = mungeQA(string, None, None, model, None, self.col)
            # extract filenames
            for reg in self.regexps:
                for match in re.finditer(reg, string):
                    fname = match.group("fname")
                    isLocal = not re.match("(https?|ftp)://", fname.lower())
                    if isLocal or includeRemote:
                        l.append(fname)
        return l

    def _expandClozes(self, string):
        ords = set(re.findall("{{c(\d+)::.+?}}", string))
        strings = []
        from anki.template.template import clozeReg
        def qrepl(m):
            if m.group(3):
                return "[%s]" % m.group(3)
            else:
                return "[...]"
        def arepl(m):
            return m.group(1)
        for ord in ords:
            s = re.sub(clozeReg%ord, qrepl, string)
            s = re.sub(clozeReg%".+?", "\\1", s)
            strings.append(s)
        strings.append(re.sub(clozeReg%".+?", arepl, string))
        return strings

    def transformNames(self, txt, func):
        for reg in self.regexps:
            txt = re.sub(reg, func, txt)
        return txt

    def strip(self, txt):
        for reg in self.regexps:
            txt = re.sub(reg, "", txt)
        return txt

    def escapeImages(self, string):
        def repl(match):
            tag = match.group(0)
            fname = match.group("fname")
            if re.match("(https?|ftp)://", fname):
                return tag
            return tag.replace(
                fname, urllib.quote(fname.encode("utf-8")))
        for reg in self.imgRegexps:
            string = re.sub(reg, repl, string)
        return string

    # Rebuilding DB
    ##########################################################################

    def check(self, local=None):
        "Return (missingFiles, unusedFiles)."
        mdir = self.dir()
        # gather all media references in NFC form
        allRefs = set()
        for nid, mid, flds in self.col.db.execute("select id, mid, flds from notes"):
            noteRefs = self.filesInStr(mid, flds)
            # check the refs are in NFC
            for f in noteRefs:
                # if they're not, we'll need to fix them first
                if f != unicodedata.normalize("NFC", f):
                    self._normalizeNoteRefs(nid)
                    noteRefs = self.filesInStr(mid, flds)
                    break
            allRefs.update(noteRefs)
        # loop through media folder
        unused = []
        invalid = []
        if local is None:
            files = os.listdir(mdir)
        else:
            files = local
        for file in files:
            if not local:
                if not os.path.isfile(file):
                    # ignore directories
                    continue
            if file.startswith("_"):
                # leading _ says to ignore file
                continue
            if not isinstance(file, unicode):
                invalid.append(unicode(file, sys.getfilesystemencoding(), "replace"))
                continue
            nfcFile = unicodedata.normalize("NFC", file)
            # we enforce NFC fs encoding on non-macs; on macs we'll have gotten
            # NFD so we use the above variable for comparing references
            if not isMac and not local:
                if file != nfcFile:
                    # delete if we already have the NFC form, otherwise rename
                    if os.path.exists(nfcFile):
                        os.unlink(file)
                    else:
                        os.rename(file, nfcFile)
                    file = nfcFile
            # compare
            if nfcFile not in allRefs:
                unused.append(file)
            else:
                allRefs.discard(nfcFile)
        nohave = [x for x in allRefs if not x.startswith("_")]
        return (nohave, unused, invalid)

    def _normalizeNoteRefs(self, nid):
        note = self.col.getNote(nid)
        for c, fld in enumerate(note.fields):
            nfc = unicodedata.normalize("NFC", fld)
            if nfc != fld:
                note.fields[c] = nfc
        note.flush()

    # Copying on import
    ##########################################################################

    def have(self, fname):
        return os.path.exists(os.path.join(self.dir(), fname))

    # Media syncing - changes and removal
    ##########################################################################

    def hasChanged(self):
        return self.db.scalar("select 1 from log limit 1")

    def removed(self):
        return self.db.list("select * from log where type = ?", MEDIA_REM)

    def syncRemove(self, fnames):
        # remove provided deletions
        for f in fnames:
            if os.path.exists(f):
                send2trash.send2trash(f)
            self.db.execute("delete from log where fname = ?", f)
            self.db.execute("delete from media where fname = ?", f)
        # and all locally-logged deletions, as server has acked them
        self.db.execute("delete from log where type = ?", MEDIA_REM)
        self.db.commit()

    # Media syncing - unbundling zip files from server
    ##########################################################################

    def syncAdd(self, zipData):
        "Extract zip data; true if finished."
        f = StringIO(zipData)
        z = zipfile.ZipFile(f, "r")
        finished = False
        meta = None
        media = []
        # get meta info first
        meta = json.loads(z.read("_meta"))
        nextUsn = int(z.read("_usn"))
        # then loop through all files
        for i in z.infolist():
            if i.filename == "_meta" or i.filename == "_usn":
                # ignore previously-retrieved meta
                continue
            elif i.filename == "_finished":
                # last zip in set
                finished = True
            else:
                data = z.read(i)
                csum = checksum(data)
                name = meta[i.filename]
                if not isinstance(name, unicode):
                    name = unicode(name, "utf8")
                # normalize name for platform
                if isMac:
                    name = unicodedata.normalize("NFD", name)
                else:
                    name = unicodedata.normalize("NFC", name)
                # save file
                open(name, "wb").write(data)
                # update db
                media.append((name, csum, self._mtime(name)))
                # remove entries from local log
                self.db.execute("delete from log where fname = ?", name)
        # update media db and note new starting usn
        if media:
            self.db.executemany(
                "insert or replace into media values (?,?,?)", media)
        self.setUsn(nextUsn) # commits
        # if we have finished adding, we need to record the new folder mtime
        # so that we don't trigger a needless scan
        if finished:
            self.syncMod()
        return finished

    # Illegal characters
    ##########################################################################

    _illegalCharReg = re.compile(r'[][><:"/?*^\\|\0]')

    def stripIllegal(self, str):
        return re.sub(self._illegalCharReg, "", str)

    def hasIllegal(self, str):
        # a file that couldn't be decoded to unicode is considered invalid
        if not isinstance(str, unicode):
            return False
        return not not re.search(self._illegalCharReg, str)

    # Media syncing - bundling zip files to send to server
    ##########################################################################
    # Because there's no standard filename encoding for zips, and because not
    # all zip clients support retrieving mtime, we store the files as ascii
    # and place a json file in the zip with the necessary information.

    def zipAdded(self):
        "Add files to a zip until over SYNC_ZIP_SIZE/COUNT. Return zip data."
        f = StringIO()
        z = zipfile.ZipFile(f, "w", compression=zipfile.ZIP_DEFLATED)
        sz = 0
        cnt = 0
        files = {}
        cur = self.db.execute(
            "select fname from log where type = ?", MEDIA_ADD)
        fnames = []
        while 1:
            fname = cur.fetchone()
            if not fname:
                # add a flag so the server knows it can clean up
                z.writestr("_finished", "")
                break
            fname = fname[0]
            # we add it as a one-element array simply to make
            # the later forgetAdded() call easier
            fnames.append([fname])
            z.write(fname, str(cnt))
            files[str(cnt)] = unicodedata.normalize("NFC", fname)
            sz += os.path.getsize(fname)
            if sz > SYNC_ZIP_SIZE or cnt > SYNC_ZIP_COUNT:
                break
            cnt += 1
        z.writestr("_meta", json.dumps(files))
        z.close()
        return f.getvalue(), fnames

    def forgetAdded(self, fnames):
        if not fnames:
            return
        self.db.executemany("delete from log where fname = ?", fnames)
        self.db.commit()

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
        self.db.commit()

    def syncMod(self):
        self.db.execute("update meta set dirMod = ?", self._mtime(self.dir()))
        self.db.commit()

    def _changed(self):
        "Return dir mtime if it has changed since the last findChanges()"
        # doesn't track edits, but user can add or remove a file to update
        mod = self.db.scalar("select dirMod from meta")
        mtime = self._mtime(self.dir())
        if not self._isFAT32() and mod and mod == mtime:
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
            # ignore folders and thumbs.db
            if os.path.isdir(f):
                continue
            if f.lower() == "thumbs.db":
                continue
            # and files with invalid chars
            if self.hasIllegal(f):
                continue
            # empty files are invalid; clean them up and continue
            if not os.path.getsize(f):
                os.unlink(f)
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

    def sanityCheck(self):
        assert not self.db.scalar("select count() from log")
        cnt = self.db.scalar("select count() from media")
        return cnt

    def forceResync(self):
        self.db.execute("delete from media")
        self.db.execute("delete from log")
        self.db.execute("update meta set usn = 0, dirMod = 0")
        self.db.commit()

    def removeExisting(self, files):
        "Remove files from list of files to sync, and return missing files."
        need = []
        remove = []
        for f in files:
            if self.db.scalar("select 1 from log where fname=?", f):
                remove.append((f,))
            else:
                need.append(f)
        self.db.executemany("delete from log where fname=?", remove)
        self.db.commit()
        # if we need all the server files, it's faster to pass None than
        # the full list
        if need and len(files) == len(need):
            return None
        return need
