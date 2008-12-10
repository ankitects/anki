# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

"""\
Media support
====================
"""
__docformat__ = 'restructuredtext'

import os, stat, time, shutil, re, sys
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
    ext = os.path.splitext(path)[1]
    return "%s%s" % (new, ext)

def copyToMedia(deck, path):
    """Copy PATH to MEDIADIR, and return new filename.
Update media table. If file already exists, don't copy."""
    newBase = mediaFilename(path)
    new = os.path.join(deck.mediaDir(create=True), newBase)
    # copy if not existing
    if not os.path.exists(new):
        shutil.copy2(path, new.encode(sys.getfilesystemencoding()))
    newSize = os.stat(new)[stat.ST_SIZE]
    if not deck.s.scalar(
        "select 1 from media where filename = :f",
        f=newBase):
        deck.s.statement("""
insert into media (id, filename, size, created, originalPath,
description)
values (:id, :filename, :size, :created, :originalPath,
:description)""",
                         id=genID(),
                         filename=newBase,
                         size=newSize,
                         created=time.time(),
                         originalPath=unicode(
            path, sys.getfilesystemencoding()),
                         description=os.path.splitext(
            os.path.basename(path))[0])
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
    # rename all files to checksum versions, note non-renamed ones
    for oldBase in os.listdir(unicode(deck.mediaDir())):
        oldPath = os.path.join(deck.mediaDir(), oldBase)
        if oldBase.startswith("."):
            continue
        if oldBase.startswith("latex-"):
            continue
        if os.path.isdir(oldPath):
            continue
        newBase = copyToMedia(deck, oldPath)
        if oldBase == newBase:
            existingFiles[oldBase] = 1
        else:
            renamedFiles[oldBase] = newBase
    # now look through all fields, and update references to files
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
            unmodifiedFacts[fid] = 1
    # update modified fields
    if modifiedFacts:
        _modifyFields(deck, updateFields, modifiedFacts, dirty)
    # fix tags
    if dirty:
        if deleteRefs:
            deck.deleteTags(modifiedFacts.keys(), _("Media Missing"))
        else:
            deck.addTags(modifiedFacts.keys(), _("Media Missing"))
        deck.deleteTags(unmodifiedFacts.keys(), _("Media Missing"))
    # build cache of db records
    mediaIds = dict(deck.s.all("select filename, id from media"))
    # look through the media dir for any unused files, and delete
    for f in os.listdir(unicode(deck.mediaDir())):
        if f.startswith("."):
            continue
        if f.startswith("latex-"):
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
    return missingFileCount, unusedFileCount

def mediaRefs(string):
    "Return list of (fullMatch, filename, replacementString)."
    l = []
    for (reg, repl) in regexps:
        for (full, fname) in re.findall(reg, string):
            l.append((full, fname, repl))
    return l
