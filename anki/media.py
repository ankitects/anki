# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

import os, shutil, re, urllib2, time, tempfile, unicodedata, urllib
from anki.db import *
from anki.utils import checksum, genID, intTime
from anki.lang import _

# other code depends on this order, so don't reorder
regexps = ("(?i)(\[sound:([^]]+)\])",
           "(?i)(<img[^>]+src=[\"']?([^\"'>]+)[\"']?[^>]*>)")

# Tables
##########################################################################

mediaTable = Table(
    'media', metadata,
    Column('id', Integer, primary_key=True, nullable=False),
    Column('filename', UnicodeText, nullable=False, unique=True),
    Column('refcnt', Integer, nullable=False),
    Column('modified', Integer, nullable=False),
    Column('chksum', UnicodeText, nullable=False, default=u""))

# File handling
##########################################################################

def copyToMedia(deck, path):
    """Copy PATH to MEDIADIR, and return new filename.

If a file with the same md5sum exists in the DB, return that.
If a file with the same name exists, return a unique name.
This does not modify the media table."""
    # see if have duplicate contents
    newpath = deck.db.scalar(
        "select filename from media where chksum = :cs",
        cs=checksum(open(path, "rb").read()))
    # check if this filename already exists
    if not newpath:
        base = os.path.basename(path)
        mdir = deck.mediaDir(create=True)
        newpath = uniquePath(mdir, base)
        shutil.copy2(path, newpath)
    return os.path.basename(newpath)

def uniquePath(dir, base):
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

# DB routines
##########################################################################

def updateMediaCount(deck, file, count=1):
    mdir = deck.mediaDir()
    if deck.db.scalar(
        "select 1 from media where filename = :file", file=file):
        deck.db.statement(
            "update media set refcnt = refcnt + :c, modified = :t where filename = :file",
            file=file, c=count, t=intTime())
    elif count > 0:
        try:
            sum = unicode(
                checksum(open(os.path.join(mdir, file), "rb").read()))
        except:
            sum = u""
        deck.db.statement("""
insert into media (id, filename, refcnt, modified, chksum)
values (:id, :file, :c, :mod, :sum)""",
                         id=genID(), file=file, c=count, mod=intTime(),
                         sum=sum)

def removeUnusedMedia(deck):
    ids = deck.db.column0("select id from media where refcnt = 0")
    for id in ids:
        deck.db.statement("insert into mediaDeleted values (:id, :t)",
                         id=id, t=time.time())
    deck.db.statement("delete from media where refcnt = 0")

# String manipulation
##########################################################################

def mediaFiles(string, remote=False):
    l = []
    for reg in regexps:
        for (full, fname) in re.findall(reg, string):
            isLocal = not re.match("(https?|ftp)://", fname.lower())
            if not remote and isLocal:
                l.append(fname)
            elif remote and not isLocal:
                l.append(fname)
    return l

def stripMedia(txt):
    for reg in regexps:
        txt = re.sub(reg, "", txt)
    return txt

def escapeImages(string):
    def repl(match):
        tag = match.group(1)
        fname = match.group(2)
        if re.match("(https?|ftp)://", fname):
            return tag
        return tag.replace(
            fname, urllib.quote(fname.encode("utf-8")))
    return re.sub(regexps[1], repl, string)

# Rebuilding DB
##########################################################################

def rebuildMediaDir(deck, delete=False, dirty=True):
    mdir = deck.mediaDir()
    if not mdir:
        return (0, 0)
    deck.startProgress(title=_("Check Media DB"))
    # set all ref counts to 0
    deck.db.statement("update media set refcnt = 0")
    # look through cards for media references
    refs = {}
    normrefs = {}
    def norm(s):
        if isinstance(s, unicode):
            return unicodedata.normalize('NFD', s)
        return s
    for (question, answer) in deck.db.all(
        "select question, answer from cards"):
        for txt in (question, answer):
            for f in mediaFiles(txt):
                if f in refs:
                    refs[f] += 1
                else:
                    refs[f] = 1
                    normrefs[norm(f)] = True
    # update ref counts
    for (file, count) in refs.items():
        updateMediaCount(deck, file, count)
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
    # remove entries in db for unused media
    removeUnusedMedia(deck)
    # check md5s are up to date
    update = []
    for (file, md5) in deck.db.all(
        "select filename, chksum from media"):
        path = os.path.join(mdir, file)
        if not os.path.exists(path):
            if md5:
                update.append({'f':file, 'sum':u"", 'c':intTime()})
        else:
            sum = unicode(
                checksum(open(os.path.join(mdir, file), "rb").read()))
            if md5 != sum:
                update.append({'f':file, 'sum':sum, 'c':intTime()})
    if update:
        deck.db.statements("""
update media set chksum = :sum, modified = :c where filename = :f""",
                          update)
    # update deck and get return info
    if dirty:
        deck.flushMod()
    nohave = deck.db.column0("select filename from media where chksum = ''")
    deck.finishProgress()
    return (nohave, unused)

# Download missing
##########################################################################

def downloadMissing(deck):
    urlbase = deck.getVar("mediaURL")
    if not urlbase:
        return None
    mdir = deck.mediaDir(create=True)
    deck.startProgress()
    missing = 0
    grabbed = 0
    for c, (f, sum) in enumerate(deck.db.all(
        "select filename, chksum from media")):
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
                    deck.finishProgress()
                    return (False, rpath)
                else:
                    # ignore and keep going
                    missing += 1
        deck.updateProgress(label=_("File %d...") % (grabbed+missing))
    deck.finishProgress()
    return (True, grabbed, missing)

# Convert remote links to local ones
##########################################################################

def downloadRemote(deck):
    mdir = deck.mediaDir(create=True)
    refs = {}
    deck.startProgress()
    for (question, answer) in deck.db.all(
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
            newpath = copyToMedia(deck, path)
            passed.append([link, newpath])
        except:
            failed.append(link)
        deck.updateProgress(label=_("Download %d...") % c)
    for (url, name) in passed:
        deck.db.statement(
            "update fields set value = replace(value, :url, :name)",
            url=url, name=name)
        deck.updateProgress(label=_("Updating references..."))
    deck.updateProgress(label=_("Updating cards..."))
    # rebuild entire q/a cache
    for m in deck.models:
        deck.updateCardsFromModel(m, dirty=True)
    deck.finishProgress()
    deck.flushMod()
    return (passed, failed)
