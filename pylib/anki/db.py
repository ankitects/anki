# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""
A convenience wrapper over pysqlite.

Anki's Collection class now uses dbproxy.py instead of this class,
but this class is still used by aqt's profile manager, and a number
of add-ons rely on it.
"""

import os
import pprint
import time
from sqlite3 import Cursor
from sqlite3 import dbapi2 as sqlite
from typing import Any, List, Type

DBError = sqlite.Error


class DB:
    def __init__(self, path: str, timeout: int = 0) -> None:
        self._db = sqlite.connect(path, timeout=timeout)
        self._db.text_factory = self._textFactory
        self._path = path
        self.echo = os.environ.get("DBECHO")
        self.mod = False

    def __repr__(self) -> str:
        d = dict(self.__dict__)
        del d["_db"]
        return f"{super().__repr__()} {pprint.pformat(d, width=300)}"

    def execute(self, sql: str, *a: Any, **ka: Any) -> Cursor:
        s = sql.strip().lower()
        # mark modified?
        for stmt in "insert", "update", "delete":
            if s.startswith(stmt):
                self.mod = True
        t = time.time()
        if ka:
            # execute("...where id = :id", id=5)
            res = self._db.execute(sql, ka)
        else:
            # execute("...where id = ?", 5)
            res = self._db.execute(sql, a)
        if self.echo:
            # print a, ka
            print(sql, "%0.3fms" % ((time.time() - t) * 1000))
            if self.echo == "2":
                print(a, ka)
        return res

    def executemany(self, sql: str, l: Any) -> None:
        self.mod = True
        t = time.time()
        self._db.executemany(sql, l)
        if self.echo:
            print(sql, "%0.3fms" % ((time.time() - t) * 1000))
            if self.echo == "2":
                print(l)

    def commit(self) -> None:
        t = time.time()
        self._db.commit()
        if self.echo:
            print("commit %0.3fms" % ((time.time() - t) * 1000))

    def executescript(self, sql: str) -> None:
        self.mod = True
        if self.echo:
            print(sql)
        self._db.executescript(sql)

    def rollback(self) -> None:
        self._db.rollback()

    def scalar(self, *a: Any, **kw: Any) -> Any:
        res = self.execute(*a, **kw).fetchone()
        if res:
            return res[0]
        return None

    def all(self, *a: Any, **kw: Any) -> List:
        return self.execute(*a, **kw).fetchall()

    def first(self, *a: Any, **kw: Any) -> Any:
        c = self.execute(*a, **kw)
        res = c.fetchone()
        c.close()
        return res

    def list(self, *a: Any, **kw: Any) -> List:
        return [x[0] for x in self.execute(*a, **kw)]

    def close(self) -> None:
        self._db.text_factory = None
        self._db.close()

    def set_progress_handler(self, *args: Any) -> None:
        self._db.set_progress_handler(*args)

    def __enter__(self) -> "DB":
        self._db.execute("begin")
        return self

    def __exit__(self, *args: Any) -> None:
        self._db.close()

    def totalChanges(self) -> Any:
        return self._db.total_changes

    def interrupt(self) -> None:
        self._db.interrupt()

    def setAutocommit(self, autocommit: bool) -> None:
        if autocommit:
            self._db.isolation_level = None
        else:
            self._db.isolation_level = ""

    # strip out invalid utf-8 when reading from db
    def _textFactory(self, data: bytes) -> str:
        return str(data, errors="ignore")

    def cursor(self, factory: Type[Cursor] = Cursor) -> Cursor:
        return self._db.cursor(factory)
