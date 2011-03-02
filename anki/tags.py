# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

from anki.db import *

# Type: 0=fact, 1=model, 2=template

def tagId(s, tag, create=True):
    "Return ID for tag, creating if necessary."
    id = s.scalar("select id from tags where name = :tag", tag=tag)
    if id or not create:
        return id
    s.statement("""
insert or ignore into tags
(name) values (:tag)""", tag=tag)
    return s.scalar("select id from tags where name = :tag", tag=tag)

def tagIds(s, tags, create=True):
    "Return an ID for all tags, creating if necessary."
    ids = {}
    if create:
        s.statements("insert or ignore into tags (name) values (:tag)",
                    [{'tag': t} for t in tags])
    tagsD = dict([(x.lower(), y) for (x, y) in s.all("""
select name, id from tags
where name in (%s)""" % ",".join([
        "'%s'" % t.replace("'", "''") for t in tags]))])
    return tagsD
