# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import os, shutil, re, urllib, urllib2, time, tempfile, unicodedata, \
    urllib, sys
from anki.utils import checksum, intTime
from anki.lang import _

class MediaRegistry(object):

    # other code depends on this order, so don't reorder
    regexps = ("(?i)(\[sound:([^]]+)\])",
               "(?i)(<img[^>]+src=[\"']?([^\"'>]+)[\"']?[^>]*>)")

    def __init__(self, deck):
        self.deck = deck
        self.mediaPrefix = ""
        self._dir = None
        self._updateDir()

    def dir(self, create=False):
        "Call with create=None to retrieve dir without creating."
        if self._dir:
            return self._dir
        elif create == None:
            return self._updateDir(create)
        elif create:
            self._updateDir(True)
        return self._dir

    def _updateDir(self, create=False):
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
        self._dir = dir

    # Adding media
    ##########################################################################

    def addFile(self, opath):
        """Copy PATH to MEDIADIR, and return new filename.
If the same name exists, compare checksums."""
        mdir = self.dir(create=True)
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
        if sys.platform.startswith("win32"):
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

    # Download missing
    ##########################################################################

    def downloadMissing(self):
        raise Exception()
        urlbase = self.deck.getVar("mediaURL")
        if not urlbase:
            return None
        mdir = self.deck.dir(create=True)
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
                        return (False, rpath)
                    else:
                        # ignore and keep going
                        missing += 1
            #self.deck.updateProgress(label=_("File %d...") % (grabbed+missing))
        return (True, grabbed, missing)

    # Convert remote links to local ones
    ##########################################################################

    def downloadRemote(self):
        raise Exception()
        mdir = self.deck.dir(create=True)
        refs = {}
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
            #self.deck.updateProgress(label=_("Download %d...") % c)
        for (url, name) in passed:
            self.deck.db.execute(
                "update fields set value = replace(value, :url, :name)",
                url=url, name=name)
            #self.deck.updateProgress(label=_("Updating references..."))
        #self.deck.updateProgress(label=_("Updating cards..."))
        # rebuild entire q/a cache
        for m in self.deck.models:
            self.deck.updateCardsFromModel(m, dirty=True)
        return (passed, failed)
