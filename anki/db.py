# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import os
try:
    from pysqlite2 import dbapi2 as sqlite
except ImportError:
    try:
        from sqlite3 import dbapi2 as sqlite
    except:
        raise Exception("Please install pysqlite2 or python2.5")

from anki.hooks import runHook

class DB(object):
    def __init__(self, path, text=None):
        self._db = sqlite.connect(path, timeout=0)
        if text:
            self._db.text_factory = text
        self._path = path
        self.echo = os.environ.get("DBECHO")

    def execute(self, sql, *a, **ka):
        if self.echo:
            print sql #, a, ka
        if ka:
            # execute("...where id = :id", id=5)
            res = self._db.execute(sql, ka)
        else:
            # execute("...where id = ?", 5)
            res = self._db.execute(sql, a)
        return res

    def executemany(self, sql, l):
        if self.echo:
            print sql #, l
        self._db.executemany(sql, l)

    def commit(self):
        self._db.commit()

    def executescript(self, sql):
        if self.echo:
            print sql
        self._db.executescript(sql)

    def rollback(self):
        self._db.rollback()

    def scalar(self, *a, **kw):
        res = self.execute(*a, **kw).fetchone()
        if res:
            return res[0]
        return None

    def all(self, *a, **kw):
        return self.execute(*a, **kw).fetchall()

    def first(self, *a, **kw):
        c = self.execute(*a, **kw)
        res = c.fetchone()
        c.close()
        return res

    def list(self, *a, **kw):
        return [x[0] for x in self.execute(*a, **kw)]

    def close(self):
        self._db.close()

    def set_progress_handler(self, *args):
        self._db.set_progress_handler(*args)
