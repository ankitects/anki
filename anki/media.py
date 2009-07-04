# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

"""\
Media support
====================
"""
__docformat__ = 'restructuredtext'

import os, stat, time, shutil, re, sys, urllib2
from anki.db import *
from anki.facts import Fact
from anki.utils import addTags, genID, ids2str, checksum
from anki.lang import _

regexps = (("(\[sound:([^]]+)\])",
            "[sound:%s]"),
           ("(<img src=[\"']?([^\"'>]+)[\"']? ?/?>)",
            "<img src=\"%s\">"))

# Tables
##########################################################################

mediaTable = Table(
    'media', metadata,
    Column('id', Integer, primary_key=True, nullable=False),
    Column('filename', UnicodeText, nullable=False),
    Column('size', Integer, nullable=False),
    Column('created', Float, nullable=False),
    Column('originalPath', UnicodeText, nullable=False, default=u""),
    Column('description', UnicodeText, nullable=False, default=u""))

class Media(object):
    pass

mapper(Media, mediaTable)

mediaDeletedTable = Table(
    'mediaDeleted', metadata,
    Column('mediaId', Integer, ForeignKey("cards.id"),
           nullable=False),
    Column('deletedTime', Float, nullable=False))

# Helper functions
##########################################################################

def mediaFilename(path):
    "Return checksum.ext for path"
    new = checksum(open(path, "rb").read())
    ext = os.path.splitext(path)[1].lower()
    return "%s%s" % (new, ext)

def copyToMedia(deck, path, latex=None):
    """Copy PATH to MEDIADIR, and return new filename.
Update media table. If file already exists, don't copy."""
    if latex:
        origPath = latex
        description = "latex"
    else:
        origPath = path
        description = os.path.splitext(os.path.basename(path))[0]
    newBase = mediaFilename(path)
    new = os.path.join(deck.mediaDir(create=True), newBase)
    # copy if not existing
    if not os.path.exists(new):
        if new.lower() == path.lower():
            # case insensitive filesystems suck
            os.rename(path, new)
        else:
            shutil.copy2(path, new)
    newSize = os.stat(new)[stat.ST_SIZE]
    if not deck.s.scalar(
        "select 1 from media where filename = :f",
        f=newBase):
        try:
            path = unicode(path, sys.getfilesystemencoding())
        except TypeError:
            pass
        deck.s.statement("""
insert into media (id, filename, size, created, originalPath,
description)
values (:id, :filename, :size, :created, :originalPath,
:description)""",
                         id=genID(),
                         filename=newBase,
                         size=newSize,
                         created=time.time(),
                         originalPath=origPath,
                         description=description)
    deck.flushMod()
    return newBase

def _modifyFields(deck, fieldsToUpdate, modifiedFacts, dirty):
    factIds = ids2str(modifiedFacts.keys())
    if fieldsToUpdate:
        deck.s.execute("update fields set value = :val where id = :id",
                       fieldsToUpdate)
    deck.s.statement(
        "update facts set modified = :time where id in %s" %
        factIds, time=time.time())
    ids = deck.s.all("""select cards.id, cards.cardModelId, facts.id,
facts.modelId from cards, facts where
cards.factId = facts.id and facts.id in %s"""
                     % factIds)
    deck.updateCardQACache(ids, dirty)
    deck.flushMod()


def mediaRefs(string):
    "Return list of (fullMatch, filename, replacementString)."
    l = []
    for (reg, repl) in regexps:
        for (full, fname) in re.findall(reg, string):
            l.append((full, fname, repl))
    return l

# Rebuilding DB
##########################################################################

def rebuildMediaDir(deck, deleteRefs=False, dirty=True):
    "Delete references to missing files, delete unused files."
    localFiles = {}
    modifiedFacts = {}
    unmodifiedFacts = {}
    renamedFiles = {}
    existingFiles = {}
    factsMissingMedia = {}
    updateFields = []
    usedFiles = {}
    unusedFileCount = 0
    missingFileCount = 0
    deck.mediaDir(create=True)
    deck.startProgress(16, 0, _("Check Media DB"))
    # rename all files to checksum versions, note non-renamed ones
    deck.updateProgress(_("Checksum files..."))
    files = os.listdir(unicode(deck.mediaDir()))
    mod = len(files) / 10
    for c, oldBase in enumerate(files):
        if mod and not c % mod:
            deck.updateProgress()
        oldPath = os.path.join(deck.mediaDir(), oldBase)
        if oldBase.startswith("."):
            continue
        if os.path.isdir(oldPath):
            continue
        newBase = copyToMedia(deck, oldPath)
        if oldBase.lower() == newBase.lower():
            existingFiles[oldBase] = 1
        else:
            renamedFiles[oldBase] = newBase
    deck.updateProgress(value=10)
    # now look through all fields, and update references to files
    deck.updateProgress(_("Scan fields..."))
    for (id, fid, val) in deck.s.all(
        "select id, factId, value from fields"):
        oldval = val
        for (full, fname, repl) in mediaRefs(val):
            if fname in renamedFiles:
                # renamed
                newBase = renamedFiles[fname]
                val = re.sub(re.escape(full), repl % newBase, val)
                usedFiles[newBase] = 1
            elif fname in existingFiles:
                # used & current
                usedFiles[fname] = 1
            else:
                # missing
                missingFileCount += 1
                if deleteRefs:
                    val = re.sub(re.escape(full), "", val)
                else:
                    factsMissingMedia[fid] = 1
        if val != oldval:
            updateFields.append({'id': id, 'val': val})
            modifiedFacts[fid] = 1
        else:
            if fid not in factsMissingMedia:
                unmodifiedFacts[fid] = 1
    # update modified fields
    deck.updateProgress(_("Modify fields..."))
    if modifiedFacts:
        _modifyFields(deck, updateFields, modifiedFacts, dirty)
    # fix tags
    deck.updateProgress(_("Update tags..."))
    if dirty:
        if deleteRefs:
            deck.deleteTags(modifiedFacts.keys(), _("MediaMissing"))
        else:
            deck.addTags(factsMissingMedia.keys(), _("MediaMissing"))
        deck.deleteTags(unmodifiedFacts.keys(), _("MediaMissing"))
    # build cache of db records
    deck.updateProgress(_("Delete unused files..."))
    mediaIds = dict(deck.s.all("select filename, id from media"))
    # assume latex files exist
    for f in deck.s.column0(
        "select filename from media where description = 'latex'"):
        usedFiles[f] = 1
    # look through the media dir for any unused files, and delete
    for f in os.listdir(unicode(deck.mediaDir())):
        if f.startswith("."):
            continue
        path = os.path.join(deck.mediaDir(), f)
        if os.path.isdir(path):
            shutil.rmtree(path)
            continue
        if f in usedFiles:
            del mediaIds[f]
        else:
            os.unlink(path)
            unusedFileCount += 1
    deck.updateProgress(_("Delete stale references..."))
    for (fname, id) in mediaIds.items():
        # maybe delete from db
        if id:
            deck.s.statement("delete from media where id = :id", id=id)
            deck.s.statement("""
insert into mediaDeleted (mediaId, deletedTime)
values (:id, strftime('%s', 'now'))""", id=id)
    # update deck and save
    deck.flushMod()
    deck.save()
    deck.finishProgress()
    return missingFileCount, unusedFileCount - len(renamedFiles)

# Download missing
##########################################################################

def downloadMissing(deck):
    from anki.latex import renderLatex
    urls = dict(
        deck.s.all("select id, features from models where features != ''"))
    if not urls:
        return None
    mdir = deck.mediaDir(create=True)
    os.chdir(mdir)
    deck.startProgress()
    missing = {}
    for (id, fid, val, mid) in deck.s.all("""
select fields.id, factId, value, modelId from fields, facts
where facts.id = fields.factId"""):
        # add latex tags
        val = renderLatex(deck, val, False)
        for (full, fname, repl) in mediaRefs(val):
            if not os.path.exists(os.path.join(mdir, fname)) and mid in urls:
                missing[fname] = mid
    success = 0
    for c, file in enumerate(missing.keys()):
        deck.updateProgress(label=_("Downloading %(a)d of %(b)d...") % {
            'a': c,
            'b': len(missing),
            })
        try:
            data = urllib2.urlopen(urls[missing[file]] + file).read()
            open(file, "wb").write(data)
            success += 1
        except:
            pass
    deck.finishProgress()
    return len(missing), success

# Export original files
##########################################################################

def exportOriginalFiles(deck):
    deck.startProgress()
    origDir = deck.mediaDir(create=True)
    newDir = origDir.replace(".media", ".originals")
    try:
        os.mkdir(newDir)
    except (IOError, OSError):
        pass
    cnt = 0
    for row in deck.s.all("""
select filename, originalPath from media
where description != 'latex'"""):
        (fname, path) = row
        base = os.path.basename(path)
        if base == fname:
            continue
        cnt += 1
        deck.updateProgress(label="Exporting %s" % base)
        old = os.path.join(origDir, fname)
        new = os.path.join(newDir, base)
        if os.path.exists(new):
            new = re.sub("(.*)(\..*?)$", "\\1-%s\\2" %
                         os.path.splitext(fname)[0], new)
        shutil.copy2(old, new)
    deck.finishProgress()
    return cnt
