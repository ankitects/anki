# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

"""\
Tags
====================
"""
__docformat__ = 'restructuredtext'


from anki.db import *

#src 0 = fact
#src 1 = model
#src 2 = card model

# Tables
##########################################################################

def initTagTables(s):
    print "init"
    s.statement("""
create table tags (
id integer not null,
tag text not null collate nocase,
priority integer not null default 2,
primary key(id))""")
    s.statement("""
create table cardTags (
id integer not null,
cardId integer not null,
tagId integer not null,
src integer not null,
primary key(id))""")

def tagId(s, tag):
    "Return ID for tag, creating if necessary."
    id = s.scalar("select id from tags where tag = :tag", tag=tag)
    if id:
        return id
    s.statement("""
insert or ignore into tags
(tag) values (:tag)""", tag=tag)
    return s.scalar("select id from tags where tag = :tag", tag=tag)

def tagIds(s, tags):
    ids = {}
    for tag in tags:
        ids[tag.lower()] = tagId(s, tag)
    return ids
