# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

DECK_VERSION = 75

from anki.db import *
from anki.lang import _
from anki.media import rebuildMediaDir
from anki.tags import initTagTables

def moveTable(s, table):
    sql = s.scalar(
        "select sql from sqlite_master where name = '%s'" % table)
    sql = sql.replace("TABLE "+table, "temporary table %s2" % table)
    s.execute(sql)
    s.execute("insert into %s2 select * from %s" % (table, table))
    s.execute("drop table "+table)

def upgradeSchema(engine, s):
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
    if ver < 75:
        # migrate cards
        moveTable(s, "cards")
        import cards
        metadata.create_all(engine, tables=[cards.cardsTable])
        # move data across
        s.execute("""
insert into cards select id, factId, cardModelId, created, modified,
question, answer, 0, ordinal, 0, relativeDelay, type, lastInterval, interval,
due, factor, reps, successive, noCount from cards2""")
        s.execute("drop table cards2")
        # migrate tags
        moveTable(s, "tags")
        moveTable(s, "cardTags")
        initTagTables(s)
        # move data across
        s.execute("insert or ignore into tags select id, tag, 0 from tags2")
        s.execute("""
insert or ignore into cardTags select cardId, tagId, src from cardTags2""")
        s.execute("drop table tags2")
        s.execute("drop table cardTags2")
        # migrate facts
        moveTable(s, "facts")
        import facts
        metadata.create_all(engine, tables=[facts.factsTable])
        # move data across
        s.execute("""
insert or ignore into facts select id, modelId, created, modified, tags,
spaceUntil from facts2""")
        s.execute("drop table facts2")
    return ver

def updateIndices(deck):
    "Add indices to the DB."
    # due counts, failed card queue
    deck.db.statement("""
create index if not exists ix_cards_queueDue on cards
(queue, due, factId)""")
    # counting cards of a given type
    deck.db.statement("""
create index if not exists ix_cards_type on cards
(type)""")
    # sync summaries
    deck.db.statement("""
create index if not exists ix_cards_modified on cards
(modified)""")
    deck.db.statement("""
create index if not exists ix_facts_modified on facts
(modified)""")
    # card spacing
    deck.db.statement("""
create index if not exists ix_cards_factId on cards (factId)""")
    # fields
    deck.db.statement("""
create index if not exists ix_fields_factId on fields (factId)""")
    deck.db.statement("""
create index if not exists ix_fields_fieldModelId on fields (fieldModelId)""")
    deck.db.statement("""
create index if not exists ix_fields_chksum on fields (chksum)""")
    # media
    deck.db.statement("""
create unique index if not exists ix_media_filename on media (filename)""")
    deck.db.statement("""
create index if not exists ix_media_originalPath on media (originalPath)""")
    # deletion tracking
    deck.db.statement("""
create index if not exists ix_cardsDeleted_cardId on cardsDeleted (cardId)""")
    deck.db.statement("""
create index if not exists ix_modelsDeleted_modelId on modelsDeleted (modelId)""")
    deck.db.statement("""
create index if not exists ix_factsDeleted_factId on factsDeleted (factId)""")
    deck.db.statement("""
create index if not exists ix_mediaDeleted_factId on mediaDeleted (mediaId)""")
    # tags
    deck.db.statement("""
create index if not exists ix_cardTags_cardId on cardTags (cardId)""")

def upgradeDeck(deck):
    "Upgrade deck to the latest version."
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
    if deck.version < 65:
        raise Exception("oldDeckVersion")
    # skip a few to allow for updates to stable tree
    if deck.version < 70:
        # update dynamic indices given we don't use priority anymore
        for d in ("intervalDesc", "intervalAsc", "randomOrder",
                  "dueAsc", "dueDesc"):
            deck.db.statement("drop index if exists ix_cards_%s2" % d)
            deck.db.statement("drop index if exists ix_cards_%s" % d)
        deck.updateDynamicIndices()
        # remove old views
        for v in ("failedCards", "revCardsOld", "revCardsNew",
                  "revCardsDue", "revCardsRandom", "acqCardsRandom",
                  "acqCardsOld", "acqCardsNew"):
            deck.db.statement("drop view if exists %s" % v)
        deck.version = 70
        deck.db.commit()
    if deck.version < 71:
        # remove the expensive value cache
        deck.db.statement("drop index if exists ix_fields_value")
        # add checksums and index
        deck.updateAllFieldChecksums()
        updateIndices(deck)
        deck.db.execute("vacuum")
        deck.db.execute("analyze")
        deck.version = 71
        deck.db.commit()
    if deck.version < 72:
        # this was only used for calculating average factor
        deck.db.statement("drop index if exists ix_cards_factor")
        deck.version = 72
        deck.db.commit()
    if deck.version < 73:
        # remove stats, as it's all in the revlog now
        deck.db.statement("drop table if exists stats")
        deck.version = 73
        deck.db.commit()
    if deck.version < 74:
        # migrate revlog data to new table
        deck.db.statement("""
insert or ignore into revlog select
cast(time*1000 as int), cardId, ease, reps, lastInterval, nextInterval, nextFactor,
cast(min(thinkingTime, 60)*1000 as int), 0 from reviewHistory""")
        deck.db.statement("drop table reviewHistory")
        # convert old ease0 into ease1
        deck.db.statement("update revlog set ease = 1 where ease = 0")
        # remove priority index
        deck.db.statement("drop index if exists ix_cards_priority")
        deck.version = 74
        deck.db.commit()
    if deck.version < 75:
        # suspended cards don't use ranges anymore
        deck.db.execute("update cards set queue=-1 where queue between -3 and -1")
        deck.db.execute("update cards set queue=-2 where queue between 3 and 5")
        deck.db.execute("update cards set queue=-3 where queue between 6 and 8")
        # new indices for new cards table
        updateIndices(deck)
        deck.version = 75
        deck.db.commit()

    # executing a pragma here is very slow on large decks, so we store
    # our own record
    if not deck.getInt("pageSize") == 4096:
        deck.db.commit()
        deck.db.execute("pragma page_size = 4096")
        deck.db.execute("pragma legacy_file_format = 0")
        deck.db.execute("vacuum")
        deck.setVar("pageSize", 4096, mod=False)
        deck.db.commit()
    if prog:
        assert deck.modified == oldmod
        deck.finishProgress()
