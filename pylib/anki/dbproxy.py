# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

# fixme: lossy utf8 handling
# fixme: progress

import time
from sqlite3 import Cursor
from sqlite3 import dbapi2 as sqlite
from typing import Any, List, Type


class DBProxy:
    def __init__(self, path: str) -> None:
        self._db = sqlite.connect(path, timeout=0)
        self._path = path
        self.mod = False

    def execute(self, sql: str, *args) -> Cursor:
        s = sql.strip().lower()
        # mark modified?
        for stmt in "insert", "update", "delete":
            if s.startswith(stmt):
                self.mod = True
        res = self._db.execute(sql, args)
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

    def scalar(self, sql: str, *args) -> Any:
        res = self.execute(sql, *args).fetchone()
        if res:
            return res[0]
        return None

    def all(self, sql: str, *args) -> List:
        return self.execute(sql, *args).fetchall()

    def first(self, sql: str, *args) -> Any:
        c = self.execute(sql, *args)
        res = c.fetchone()
        c.close()
        return res

    def list(self, sql: str, *args) -> List:
        return [x[0] for x in self.execute(sql, *args)]

    def close(self) -> None:
        self._db.close()

    def totalChanges(self) -> Any:
        return self._db.total_changes

    def setAutocommit(self, autocommit: bool) -> None:
        if autocommit:
            self._db.isolation_level = None
        else:
            self._db.isolation_level = ""

    def cursor(self, factory: Type[Cursor] = Cursor) -> Cursor:
        return self._db.cursor(factory)
