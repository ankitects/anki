# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

def upgradeSchema(s):
    "Alter tables prior to ORM initialization."
    ver = s.scalar("select version from decks limit 1")
    # add a checksum to fields
    if ver < 71:
        try:
            s.execute(
                "alter table fields add column chksum text "+
                "not null default ''")
        except:
            pass
    return ver

def updateIndices(deck):
    "Add indices to the DB."
    # counts, failed cards
    deck.s.statement("""
create index if not exists ix_cards_typeCombined on cards
(type, combinedDue, factId)""")
    # scheduler-agnostic type
    deck.s.statement("""
create index if not exists ix_cards_relativeDelay on cards
(relativeDelay)""")
    # index on modified, to speed up sync summaries
    deck.s.statement("""
create index if not exists ix_cards_modified on cards
(modified)""")
    deck.s.statement("""
create index if not exists ix_facts_modified on facts
(modified)""")
    # priority - temporary index to make compat code faster. this can be
    # removed when all clients are on 1.2, as can the ones below
    deck.s.statement("""
create index if not exists ix_cards_priority on cards
(priority)""")
    # average factor
    deck.s.statement("""
create index if not exists ix_cards_factor on cards
(type, factor)""")
    # card spacing
    deck.s.statement("""
create index if not exists ix_cards_factId on cards (factId)""")
    # stats
    deck.s.statement("""
create index if not exists ix_stats_typeDay on stats (type, day)""")
    # fields
    deck.s.statement("""
create index if not exists ix_fields_factId on fields (factId)""")
    deck.s.statement("""
create index if not exists ix_fields_fieldModelId on fields (fieldModelId)""")
    deck.s.statement("""
create index if not exists ix_fields_chksum on fields (chksum)""")
    # media
    deck.s.statement("""
create unique index if not exists ix_media_filename on media (filename)""")
    deck.s.statement("""
create index if not exists ix_media_originalPath on media (originalPath)""")
    # deletion tracking
    deck.s.statement("""
create index if not exists ix_cardsDeleted_cardId on cardsDeleted (cardId)""")
    deck.s.statement("""
create index if not exists ix_modelsDeleted_modelId on modelsDeleted (modelId)""")
    deck.s.statement("""
create index if not exists ix_factsDeleted_factId on factsDeleted (factId)""")
    deck.s.statement("""
create index if not exists ix_mediaDeleted_factId on mediaDeleted (mediaId)""")
    # tags
    txt = "create unique index if not exists ix_tags_tag on tags (tag)"
    try:
        deck.s.statement(txt)
    except:
        deck.s.statement("""
delete from tags where exists (select 1 from tags t2 where tags.tag = t2.tag
and tags.rowid > t2.rowid)""")
        deck.s.statement(txt)
    deck.s.statement("""
create index if not exists ix_cardTags_tagCard on cardTags (tagId, cardId)""")
    deck.s.statement("""
create index if not exists ix_cardTags_cardId on cardTags (cardId)""")

def upgradeDeck(deck):
    "Upgrade deck to the latest version."
    from anki.deck import DECK_VERSION
    if deck.version < DECK_VERSION:
        prog = True
        deck.startProgress()
        deck.updateProgress(_("Upgrading Deck..."))
        if deck.utcOffset == -1:
            # we're opening a shared deck with no indices - we'll need
            # them if we want to rebuild the queue
            updateIndices(deck)
        oldmod = deck.modified
    else:
        prog = False
    if deck.version < 43:
        raise Exception("oldDeckVersion")
    if deck.version < 44:
        # leaner indices
        deck.s.statement("drop index if exists ix_cards_factId")
        deck.version = 44
        deck.s.commit()
    if deck.version < 48:
        deck.updateFieldCache(deck.s.column0("select id from facts"))
        deck.version = 48
        deck.s.commit()
    if deck.version < 52:
        dname = deck.name()
        sname = deck.syncName
        if sname and dname != sname:
            deck.notify(_("""\
When syncing, Anki now uses the same deck name on the server as the deck \
name on your computer. Because you had '%(dname)s' set to sync to \
'%(sname)s' on the server, syncing has been temporarily disabled.

If you want to keep your changes to the online version, please use \
File>Download>Personal Deck to download the online version.

If you want to keep the version on your computer, please enable \
syncing again via Settings>Deck Properties>Synchronisation.

If you have syncing disabled in the preferences, you can ignore \
this message. (ERR-0101)""") % {
                    'sname':sname, 'dname':dname})
            deck.disableSyncing()
        elif sname:
            deck.enableSyncing()
        deck.version = 52
        deck.s.commit()
    if deck.version < 53:
        if deck.getBool("perDay"):
            if deck.hardIntervalMin == 0.333:
                deck.hardIntervalMin = max(1.0, deck.hardIntervalMin)
                deck.hardIntervalMax = max(1.1, deck.hardIntervalMax)
        deck.version = 53
        deck.s.commit()
    if deck.version < 54:
        # broken versions of the DB orm die if this is a bool with a
        # non-int value
        deck.s.statement("update fieldModels set editFontFamily = 1");
        deck.version = 54
        deck.s.commit()
    if deck.version < 61:
        # do our best to upgrade templates to the new style
        txt = '''\
<span style="font-family: %s; font-size: %spx; color: %s; white-space: pre-wrap;">%s</span>'''
        for m in deck.models:
            unstyled = []
            for fm in m.fieldModels:
                # find which fields had explicit formatting
                if fm.quizFontFamily or fm.quizFontSize or fm.quizFontColour:
                    pass
                else:
                    unstyled.append(fm.name)
                # fill out missing info
                fm.quizFontFamily = fm.quizFontFamily or u"Arial"
                fm.quizFontSize = fm.quizFontSize or 20
                fm.quizFontColour = fm.quizFontColour or "#000000"
                fm.editFontSize = fm.editFontSize or 20
            unstyled = set(unstyled)
            for cm in m.cardModels:
                # embed the old font information into card templates
                cm.qformat = txt % (
                    cm.questionFontFamily,
                    cm.questionFontSize,
                    cm.questionFontColour,
                    cm.qformat)
                cm.aformat = txt % (
                    cm.answerFontFamily,
                    cm.answerFontSize,
                    cm.answerFontColour,
                    cm.aformat)
                # escape fields that had no previous styling
                for un in unstyled:
                    cm.qformat = cm.qformat.replace("%("+un+")s", "{{{%s}}}"%un)
                    cm.aformat = cm.aformat.replace("%("+un+")s", "{{{%s}}}"%un)
        # rebuild q/a for the above & because latex has changed
        for m in deck.models:
            deck.updateCardsFromModel(m, dirty=False)
        # rebuild the media db based on new format
        rebuildMediaDir(deck, dirty=False)
        deck.version = 61
        deck.s.commit()
    if deck.version < 62:
        # updated indices
        deck.s.statement("drop index if exists ix_cards_typeCombined")
        updateIndices(deck)
        deck.version = 62
        deck.s.commit()
    if deck.version < 64:
        # remove old static indices, as all clients should be libanki1.2+
        for d in ("ix_cards_duePriority",
                  "ix_cards_priorityDue"):
            deck.s.statement("drop index if exists %s" % d)
        deck.version = 64
        deck.s.commit()
        # note: we keep the priority index for now
    if deck.version < 65:
        # we weren't correctly setting relativeDelay when answering cards
        # in previous versions, so ensure everything is set correctly
        deck.rebuildTypes()
        deck.version = 65
        deck.s.commit()
    # skip a few to allow for updates to stable tree
    if deck.version < 70:
        # update dynamic indices given we don't use priority anymore
        for d in ("intervalDesc", "intervalAsc", "randomOrder",
                  "dueAsc", "dueDesc"):
            deck.s.statement("drop index if exists ix_cards_%s2" % d)
            deck.s.statement("drop index if exists ix_cards_%s" % d)
        deck.updateDynamicIndices()
        # remove old views
        for v in ("failedCards", "revCardsOld", "revCardsNew",
                  "revCardsDue", "revCardsRandom", "acqCardsRandom",
                  "acqCardsOld", "acqCardsNew"):
            deck.s.statement("drop view if exists %s" % v)
        deck.version = 70
        deck.s.commit()
    if deck.version < 71:
        # remove the expensive value cache
        deck.s.statement("drop index if exists ix_fields_value")
        # add checksums and index
        deck.updateAllFieldChecksums()
        updateIndices(deck)
        deck.s.execute("vacuum")
        deck.s.execute("analyze")
        deck.version = 71
        deck.s.commit()
    # executing a pragma here is very slow on large decks, so we store
    # our own record
    if not deck.getInt("pageSize") == 4096:
        deck.s.commit()
        deck.s.execute("pragma page_size = 4096")
        deck.s.execute("pragma legacy_file_format = 0")
        deck.s.execute("vacuum")
        deck.setVar("pageSize", 4096, mod=False)
        deck.s.commit()
    if prog:
        assert deck.modified == oldmod
        deck.finishProgress()
