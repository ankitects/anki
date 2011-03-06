# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

import os, shutil, re, urllib2, time, tempfile, unicodedata, urllib
from anki.utils import checksum, genID, intTime
from anki.lang import _

class MediaRegistry(object):

    # other code depends on this order, so don't reorder
    regexps = ("(?i)(\[sound:([^]]+)\])",
               "(?i)(<img[^>]+src=[\"']?([^\"'>]+)[\"']?[^>]*>)")

    def __init__(self, deck):
        self.deck = deck
        self.mediaPrefix = ""
        self._mediaDir = None
        self._updateMediaDir()

    def mediaDir(self, create=False):
        if self._mediaDir:
            return self._mediaDir
        elif create:
            self._updateMediaDir(True)
        return self._mediaDir

    def _updateMediaDir(self, create=False):
        if self.mediaPrefix:
            dir = os.path.join(
                self.mediaPrefix, os.path.basename(self.deck.path))
        else:
            dir = self.deck.path
        dir = re.sub("(?i)\.(anki)$", ".media", dir)
        if create == None:
            # don't create, but return dir
            return dir
        if not os.path.exists(dir):
            if not create:
                return
            # will raise error if we can't create
            os.makedirs(dir)
        # change to the current dir
        os.chdir(dir)
        self._mediaDir = dir

    # Adding and registering media
    ##########################################################################

    def addFile(self, path):
        """Copy PATH to MEDIADIR, and return new filename.
If a file with the same md5sum exists in the DB, return that.
If a file with the same name exists, return a unique name."""
        # see if have duplicate contents
        csum = self.mediaChecksum(path)
        if not csum:
            # file was unreadable or didn't exist
            return None
        file = self.deck.db.scalar(
            "select file from media where csum = :cs",
            cs=csum)
        if not file:
            base = os.path.basename(path)
            mdir = self.mediaDir(create=True)
            file = self.uniquePath(mdir, base)
            shutil.copy2(path, file)
            self.registerFile(base)
        return os.path.basename(file)

    def registerFile(self, file):
        "Add a single file to the media database."
        if self.mediaDir():
            csum = self.mediaChecksum(os.path.join(self.mediaDir(), file))
        else:
            csum = ""
        self.deck.db.execute(
            "insert or replace into media values (?, ?, ?)",
            file, intTime(), csum)

    def registerText(self, string):
        "Add all media in string to the media database."
        for f in self.mediaFiles(string):
            self.registerFile(f)

    def removeUnusedMedia(deck):
        ids = deck.s.list("select id from media where size = 0")
        for id in ids:
            deck.s.statement("insert into mediaDeleted values (:id, :t)",
                             id=id, t=time.time())
        deck.s.statement("delete from media where size = 0")

    # Moving media
    ##########################################################################

    def renameMediaDir(self, oldPath):
        "Copy oldPath to our current media dir. "
        assert os.path.exists(oldPath)
        newPath = self.mediaDir(create=None)
        # copytree doesn't want the dir to exist
        try:
            shutil.copytree(oldPath, newPath)
        except:
            # FIXME: should really remove everything in old dir instead of
            # giving up
            pass

    # Tools
    ##########################################################################

    def mediaChecksum(self, path):
        "Return checksum of PATH, or empty string."
        try:
            return checksum(open(path, "rb").read())
        except:
            return ""

    def uniquePath(self, dir, base):
        # remove any dangerous characters
        base = re.sub(r"[][<>:/\\&]", "", base)
        # find a unique name
        (root, ext) = os.path.splitext(base)
        def repl(match):
            n = int(match.group(1))
            return " (%d)" % (n+1)
        while True:
            path = os.path.join(dir, root + ext)
            if not os.path.exists(path):
                break
            reg = " \((\d+)\)$"
            if not re.search(reg, root):
                root = root + " (1)"
            else:
                root = re.sub(reg, repl, root)
        return path

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

    def stripMedia(self, txt):
        for reg in self.regexps:
            txt = re.sub(reg, "", txt)
        return txt

    def escapeImages(self, string):
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

    def rebuildMediaDir(self, delete=False):
        mdir = self.mediaDir()
        if not mdir:
            return (0, 0)
        self.deck.startProgress()
        # delete all media entries in database
        self.deck.db.execute("delete from media")
        # look through cards for media references
        normrefs = {}
        def norm(s):
            if isinstance(s, unicode):
                return unicodedata.normalize('NFD', s)
            return s
        for (question, answer) in self.deck.db.all(
            "select q, a from cards"):
            for txt in (question, answer):
                for f in self.mediaFiles(txt):
                    normrefs[norm(f)] = True
                    self.registerFile(f)
        # find unused media
        unused = []
        for file in os.listdir(mdir):
            path = os.path.join(mdir, file)
            if not os.path.isfile(path):
                # ignore directories
                continue
            nfile = norm(file)
            if nfile not in normrefs:
                unused.append(file)
        # optionally delete
        if delete:
            for f in unused:
                path = os.path.join(mdir, f)
                os.unlink(path)
        nohave = self.deck.db.list(
            "select file from media where csum = ''")
        self.deck.finishProgress()
        return (nohave, unused)

    # Download missing
    ##########################################################################

    def downloadMissing(self):
        urlbase = self.deck.getVar("mediaURL")
        if not urlbase:
            return None
        mdir = self.deck.mediaDir(create=True)
        self.deck.startProgress()
        missing = 0
        grabbed = 0
        for c, (f, sum) in enumerate(self.deck.db.all(
            "select file, csum from media")):
            path = os.path.join(mdir, f)
            if not os.path.exists(path):
                try:
                    rpath = urlbase + f
                    url = urllib2.urlopen(rpath)
                    open(f, "wb").write(url.read())
                    grabbed += 1
                except:
                    if sum:
                        # the file is supposed to exist
                        self.deck.finishProgress()
                        return (False, rpath)
                    else:
                        # ignore and keep going
                        missing += 1
            self.deck.updateProgress(label=_("File %d...") % (grabbed+missing))
        self.deck.finishProgress()
        return (True, grabbed, missing)

    # Convert remote links to local ones
    ##########################################################################

    def downloadRemote(self):
        mdir = self.deck.mediaDir(create=True)
        refs = {}
        self.deck.startProgress()
        for (question, answer) in self.deck.db.all(
            "select question, answer from cards"):
            for txt in (question, answer):
                for f in mediaFiles(txt, remote=True):
                    refs[f] = True

        tmpdir = tempfile.mkdtemp(prefix="anki")
        failed = []
        passed = []
        for c, link in enumerate(refs.keys()):
            try:
                path = os.path.join(tmpdir, os.path.basename(link))
                url = urllib2.urlopen(link)
                open(path, "wb").write(url.read())
                newpath = copyToMedia(self.deck, path)
                passed.append([link, newpath])
            except:
                failed.append(link)
            self.deck.updateProgress(label=_("Download %d...") % c)
        for (url, name) in passed:
            self.deck.db.execute(
                "update fields set value = replace(value, :url, :name)",
                url=url, name=name)
            self.deck.updateProgress(label=_("Updating references..."))
        self.deck.updateProgress(label=_("Updating cards..."))
        # rebuild entire q/a cache
        for m in self.deck.models:
            self.deck.updateCardsFromModel(m, dirty=True)
        self.deck.finishProgress()
        return (passed, failed)
