# -*- coding: utf-8 -*-
# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import io
import re
import traceback
import urllib.request, urllib.parse, urllib.error
import unicodedata
import sys
import zipfile
import pathlib
import json
import os

from anki.utils import checksum, isWin, isMac
from anki.db import DB, DBError
from anki.consts import *
from anki.latex import mungeQA
from anki.lang import _

class MediaManager:

    soundRegexps = [r"(?i)(\[sound:(?P<fname>[^]]+)\])"]
    imgRegexps = [
        # src element quoted case
        r"(?i)(<img[^>]* src=(?P<str>[\"'])(?P<fname>[^>]+?)(?P=str)[^>]*>)",
        # unquoted case
        r"(?i)(<img[^>]* src=(?!['\"])(?P<fname>[^ >]+)[^>]*?>)",
    ]
    regexps = soundRegexps + imgRegexps

    def __init__(self, col, server):
        self.col = col
        if server:
            self._dir = None
            return
        # media directory
        self._dir = re.sub(r"(?i)\.(anki2)$", ".media", self.col.path)
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
        path = self.dir()+".db2"
        create = not os.path.exists(path)
        os.chdir(self._dir)
        self.db = DB(path)
        if create:
            self._initDB()
        self.maybeUpgrade()

    def _initDB(self):
        self.db.executescript("""
create table media (
 fname text not null primary key,
 csum text,           -- null indicates deleted file
 mtime int not null,  -- zero if deleted
 dirty int not null
);

create index idx_media_dirty on media (dirty);

create table meta (dirMod int, lastUsn int); insert into meta values (0, 0);
""")

    def maybeUpgrade(self):
        oldpath = self.dir()+".db"
        if os.path.exists(oldpath):
            self.db.execute('attach "../collection.media.db" as old')
            try:
                self.db.execute("""
    insert into media
     select m.fname, csum, mod, ifnull((select 1 from log l2 where l2.fname=m.fname), 0) as dirty
     from old.media m
     left outer join old.log l using (fname)
     union
     select fname, null, 0, 1 from old.log where type=1;""")
                self.db.execute("delete from meta")
                self.db.execute("""
    insert into meta select dirMod, usn from old.meta
    """)
                self.db.commit()
            except Exception as e:
                # if we couldn't import the old db for some reason, just start
                # anew
                self.col.log("failed to import old media db:"+traceback.format_exc())
            self.db.execute("detach old")
            npath = "../collection.media.db.old"
            if os.path.exists(npath):
                os.unlink(npath)
            os.rename("../collection.media.db", npath)

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

    def _deleteDB(self):
        path = self.db._path
        self.close()
        os.unlink(path)
        self.connect()

    def dir(self):
        return self._dir

    def _isFAT32(self):
        if not isWin:
            return
        # pylint: disable=import-error
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
        with open(opath, "rb") as f:
            return self.writeData(opath, f.read())

    def writeData(self, opath, data, typeHint=None):
        # if fname is a full path, use only the basename
        fname = os.path.basename(opath)

        # if it's missing an extension and a type hint was provided, use that
        if not os.path.splitext(fname)[1] and typeHint:
            # mimetypes is returning '.jpe' even after calling .init(), so we'll do
            # it manually instead
            typeMap = {
                "image/jpeg": ".jpg",
                "image/png": ".png",
            }
            if typeHint in typeMap:
                fname += typeMap[typeHint]

        # make sure we write it in NFC form (pre-APFS Macs will autoconvert to NFD),
        # and return an NFC-encoded reference
        fname = unicodedata.normalize("NFC", fname)
        # ensure it's a valid filename
        base = self.cleanFilename(fname)
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
                with open(path, "wb") as f:
                    f.write(data)
                return fname
            # if it's identical, reuse
            with open(path, "rb") as f:
                if checksum(f.read()) == csum:
                    return fname
            # otherwise, increment the index in the filename
            reg = r" \((\d+)\)$"
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
        ords = set(re.findall(r"{{c(\d+)::.+?}}", string))
        strings = []
        from anki.template.template import clozeReg
        def qrepl(m):
            if m.group(4):
                return "[%s]" % m.group(4)
            else:
                return "[...]"
        def arepl(m):
            return m.group(2)
        for ord in ords:
            s = re.sub(clozeReg%ord, qrepl, string)
            s = re.sub(clozeReg%".+?", "\\2", s)
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

    def escapeImages(self, string, unescape=False):
        if unescape:
            fn = urllib.parse.unquote
        else:
            fn = urllib.parse.quote
        def repl(match):
            tag = match.group(0)
            fname = match.group("fname")
            if re.match("(https?|ftp)://", fname):
                return tag
            return tag.replace(fname, fn(fname))
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
        if local is None:
            files = os.listdir(mdir)
        else:
            files = local
        renamedFiles = False
        dirFound = False
        warnings = []
        for file in files:
            if not local:
                if not os.path.isfile(file):
                    # ignore directories
                    dirFound = True
                    continue
            if file.startswith("_"):
                # leading _ says to ignore file
                continue

            if self.hasIllegal(file):
                name = file.encode(sys.getfilesystemencoding(), errors="replace")
                name = str(name, sys.getfilesystemencoding())
                warnings.append(
                    _("Invalid file name, please rename: %s") % name)
                continue

            nfcFile = unicodedata.normalize("NFC", file)
            # we enforce NFC fs encoding on non-macs
            if not isMac and not local:
                if file != nfcFile:
                    # delete if we already have the NFC form, otherwise rename
                    if os.path.exists(nfcFile):
                        os.unlink(file)
                        renamedFiles = True
                    else:
                        os.rename(file, nfcFile)
                        renamedFiles = True
                    file = nfcFile
            # compare
            if nfcFile not in allRefs:
                unused.append(file)
            else:
                allRefs.discard(nfcFile)
        # if we renamed any files to nfc format, we must rerun the check
        # to make sure the renamed files are not marked as unused
        if renamedFiles:
            return self.check(local=local)
        nohave = [x for x in allRefs if not x.startswith("_")]
        # make sure the media DB is valid
        try:
            self.findChanges()
        except DBError:
            self._deleteDB()

        if dirFound:
            warnings.append(
                _("Anki does not support files in subfolders of the collection.media folder."))
        return (nohave, unused, warnings)

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

    # Illegal characters and paths
    ##########################################################################

    _illegalCharReg = re.compile(r'[][><:"/?*^\\|\0\r\n]')

    def stripIllegal(self, str):
        return re.sub(self._illegalCharReg, "", str)

    def hasIllegal(self, str):
        if re.search(self._illegalCharReg, str):
            return True
        try:
            str.encode(sys.getfilesystemencoding())
        except UnicodeEncodeError:
            return True
        return False

    def cleanFilename(self, fname):
        fname = self.stripIllegal(fname)
        fname = self._cleanWin32Filename(fname)
        fname = self._cleanLongFilename(fname)
        if not fname:
            fname = "renamed"

        return fname

    def _cleanWin32Filename(self, fname):
        if not isWin:
            return fname

        # deal with things like con/prn/etc
        p = pathlib.WindowsPath(fname)
        if p.is_reserved():
            fname = "renamed" + fname
            assert not pathlib.WindowsPath(fname).is_reserved()

        return fname

    def _cleanLongFilename(self, fname):
        # a fairly safe limit that should work on typical windows
        # paths and on eCryptfs partitions, even with a duplicate
        # suffix appended
        namemax = 136

        if isWin:
            pathmax = 240
        else:
            pathmax = 1024

        # cap namemax based on absolute path
        dirlen = len(os.path.dirname(os.path.abspath(fname)))
        remaining = pathmax - dirlen
        namemax = min(remaining, namemax)
        assert namemax > 0

        if len(fname) > namemax:
            head, ext = os.path.splitext(fname)
            headmax = namemax - len(ext)
            head = head[0:headmax]
            fname = head + ext
            assert(len(fname) <= namemax)

        return fname

    # Tracking changes
    ##########################################################################

    def findChanges(self):
        "Scan the media folder if it's changed, and note any changes."
        if self._changed():
            self._logChanges()

    def haveDirty(self):
        return self.db.scalar("select 1 from media where dirty=1 limit 1")

    def _mtime(self, path):
        return int(os.stat(path).st_mtime)

    def _checksum(self, path):
        with open(path, "rb") as f:
            return checksum(f.read())

    def _changed(self):
        "Return dir mtime if it has changed since the last findChanges()"
        # doesn't track edits, but user can add or remove a file to update
        mod = self.db.scalar("select dirMod from meta")
        mtime = self._mtime(self.dir())
        if not self._isFAT32() and mod and mod == mtime:
            return False
        return mtime

    def _logChanges(self):
        (added, removed) = self._changes()
        media = []
        for f, mtime in added:
            media.append((f, self._checksum(f), mtime, 1))
        for f in removed:
            media.append((f, None, 0, 1))
        # update media db
        self.db.executemany("insert or replace into media values (?,?,?,?)",
                            media)
        self.db.execute("update meta set dirMod = ?", self._mtime(self.dir()))
        self.db.commit()

    def _changes(self):
        self.cache = {}
        for (name, csum, mod) in self.db.execute(
            "select fname, csum, mtime from media where csum is not null"):
            # previous entries may not have been in NFC form
            normname = unicodedata.normalize("NFC", name)
            self.cache[normname] = [csum, mod, False]
        added = []
        removed = []
        # loop through on-disk files
        with os.scandir(self.dir()) as it:
            for f in it:
                # ignore folders and thumbs.db
                if f.is_dir():
                    continue
                if f.name.lower() == "thumbs.db":
                    continue
                # and files with invalid chars
                if self.hasIllegal(f.name):
                    continue
                # empty files are invalid; clean them up and continue
                sz = f.stat().st_size
                if not sz:
                    os.unlink(f.name)
                    continue
                if sz > 100*1024*1024:
                    self.col.log("ignoring file over 100MB", f.name)
                    continue
                # check encoding
                normname = unicodedata.normalize("NFC", f.name)
                if not isMac:
                    if f.name != normname:
                        # wrong filename encoding which will cause sync errors
                        if os.path.exists(normname):
                            os.unlink(f.name)
                        else:
                            os.rename(f.name, normname)
                else:
                    # on Macs we can access the file using any normalization
                    pass

                # newly added?
                mtime = int(f.stat().st_mtime)
                if normname not in self.cache:
                    added.append((normname, mtime))
                else:
                    # modified since last time?
                    if mtime != self.cache[normname][1]:
                        # and has different checksum?
                        if self._checksum(normname) != self.cache[normname][0]:
                            added.append((normname, mtime))
                    # mark as used
                    self.cache[normname][2] = True
        # look for any entries in the cache that no longer exist on disk
        for (k, v) in list(self.cache.items()):
            if not v[2]:
                removed.append(k)
        return added, removed

    # Syncing-related
    ##########################################################################

    def lastUsn(self):
        return self.db.scalar("select lastUsn from meta")

    def setLastUsn(self, usn):
        self.db.execute("update meta set lastUsn = ?", usn)
        self.db.commit()

    def syncInfo(self, fname):
        ret = self.db.first(
            "select csum, dirty from media where fname=?", fname)
        return ret or (None, 0)

    def markClean(self, fnames):
        for fname in fnames:
            self.db.execute(
                "update media set dirty=0 where fname=?", fname)

    def syncDelete(self, fname):
        if os.path.exists(fname):
            os.unlink(fname)
        self.db.execute("delete from media where fname=?", fname)

    def mediaCount(self):
        return self.db.scalar(
            "select count() from media where csum is not null")

    def dirtyCount(self):
        return self.db.scalar(
            "select count() from media where dirty=1")

    def forceResync(self):
        self.db.execute("delete from media")
        self.db.execute("update meta set lastUsn=0,dirMod=0")
        self.db.commit()
        self.db.setAutocommit(True)
        self.db.execute("vacuum")
        self.db.execute("analyze")
        self.db.setAutocommit(False)

    # Media syncing: zips
    ##########################################################################

    def mediaChangesZip(self):
        f = io.BytesIO()
        z = zipfile.ZipFile(f, "w", compression=zipfile.ZIP_DEFLATED)

        fnames = []
        # meta is list of (fname, zipname), where zipname of None
        # is a deleted file
        meta = []
        sz = 0

        for c, (fname, csum) in enumerate(self.db.execute(
                        "select fname, csum from media where dirty=1"
                        " limit %d"%SYNC_ZIP_COUNT)):

            fnames.append(fname)
            normname = unicodedata.normalize("NFC", fname)

            if csum:
                self.col.log("+media zip", fname)
                z.write(fname, str(c))
                meta.append((normname, str(c)))
                sz += os.path.getsize(fname)
            else:
                self.col.log("-media zip", fname)
                meta.append((normname, ""))

            if sz >= SYNC_ZIP_SIZE:
                break

        z.writestr("_meta", json.dumps(meta))
        z.close()
        return f.getvalue(), fnames

    def addFilesFromZip(self, zipData):
        "Extract zip data; true if finished."
        f = io.BytesIO(zipData)
        z = zipfile.ZipFile(f, "r")
        media = []
        # get meta info first
        meta = json.loads(z.read("_meta").decode("utf8"))
        # then loop through all files
        cnt = 0
        for i in z.infolist():
            if i.filename == "_meta":
                # ignore previously-retrieved meta
                continue
            else:
                data = z.read(i)
                csum = checksum(data)
                name = meta[i.filename]
                # normalize name
                name = unicodedata.normalize("NFC", name)
                # save file
                with open(name, "wb") as f:
                    f.write(data)
                # update db
                media.append((name, csum, self._mtime(name), 0))
                cnt += 1
        if media:
            self.db.executemany(
                "insert or replace into media values (?,?,?,?)", media)
        return cnt
