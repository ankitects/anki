# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/copyleft/agpl.html

from anki.db import DB

def check(path):
    "True if deck looks ok."
    db = DB(path)
    # corrupt?
    try:
        if db.scalar("pragma integrity_check") != "ok":
            return
    except:
        return
    # old version?
    if db.scalar("select version from decks") != 65:
        return
    # fields missing a field model?
    if db.list("""
select id from fields where fieldModelId not in (
select distinct id from fieldModels)"""):
        return
    # facts missing a field?
    if db.list("""
select distinct facts.id from facts, fieldModels where
facts.modelId = fieldModels.modelId and fieldModels.id not in
(select fieldModelId from fields where factId = facts.id)"""):
        return
    # cards missing a fact?
    if db.list("""
select id from cards where factId not in (select id from facts)"""):
        return
    # cards missing a card model?
    if db.list("""
select id from cards where cardModelId not in
(select id from cardModels)"""):
        return
    # cards with a card model from the wrong model?
    if db.list("""
select id from cards where cardModelId not in (select cm.id from
cardModels cm, facts f where cm.modelId = f.modelId and
f.id = cards.factId)"""):
        return
    # facts missing a card?
    if db.list("""
select facts.id from facts
where facts.id not in (select distinct factId from cards)"""):
        return
    # dangling fields?
    if db.list("""
select id from fields where factId not in (select id from facts)"""):
        return
    # fields without matching interval
    if db.list("""
select id from fields where ordinal != (select ordinal from fieldModels
where id = fieldModelId)"""):
        return
    # incorrect types
    if db.list("""
select id from cards where relativeDelay != (case
when successive then 1 when reps then 0 else 2 end)"""):
        return
    if db.list("""
select id from cards where type != (case
when type >= 0 then relativeDelay else relativeDelay - 3 end)"""):
        return
    return True
