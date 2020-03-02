# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

# fixme: lossy utf8 handling

import os
import time
from sqlite3 import Cursor
from sqlite3 import dbapi2 as sqlite
from typing import Any, List, Type


class DBProxy:
    def __init__(self, path: str, timeout: int = 0) -> None:
        self._db = sqlite.connect(path, timeout=timeout)
        self._path = path
        self.mod = False

    def execute(self, sql: str, *a, **ka) -> Cursor:
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
        return res

    def executemany(self, sql: str, l: Any) -> None:
        self.mod = True
        t = time.time()
        self._db.executemany(sql, l)

    def commit(self) -> None:
        t = time.time()
        self._db.commit()

    def executescript(self, sql: str) -> None:
        self.mod = True
        self._db.executescript(sql)

    def rollback(self) -> None:
        self._db.rollback()

    def scalar(self, *a, **kw) -> Any:
        res = self.execute(*a, **kw).fetchone()
        if res:
            return res[0]
        return None

    def all(self, *a, **kw) -> List:
        return self.execute(*a, **kw).fetchall()

    def first(self, *a, **kw) -> Any:
        c = self.execute(*a, **kw)
        res = c.fetchone()
        c.close()
        return res

    def list(self, *a, **kw) -> List:
        return [x[0] for x in self.execute(*a, **kw)]

    def close(self) -> None:
        self._db.text_factory = None
        self._db.close()

    def set_progress_handler(self, *args) -> None:
        self._db.set_progress_handler(*args)

    def __enter__(self) -> "DBProxy":
        self._db.execute("begin")
        return self

    def __exit__(self, exc_type, *args) -> None:
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

    def cursor(self, factory: Type[Cursor] = Cursor) -> Cursor:
        return self._db.cursor(factory)
