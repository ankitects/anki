# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

# fixme: lossy utf8 handling
# fixme: progress

from sqlite3 import Cursor
from sqlite3 import dbapi2 as sqlite
from typing import Any, Iterable, List


class DBProxy:
    # Lifecycle
    ###############

    def __init__(self, path: str) -> None:
        self._db = sqlite.connect(path, timeout=0)
        self._path = path
        self.mod = False

    def close(self) -> None:
        self._db.close()

    # Transactions
    ###############

    def commit(self) -> None:
        self._db.commit()

    def rollback(self) -> None:
        self._db.rollback()

    def setAutocommit(self, autocommit: bool) -> None:
        if autocommit:
            self._db.isolation_level = None
        else:
            self._db.isolation_level = ""

    # Querying
    ################

    def all(self, sql: str, *args) -> List:
        return self.execute(sql, *args).fetchall()

    def first(self, sql: str, *args) -> Any:
        c = self.execute(sql, *args)
        res = c.fetchone()
        c.close()
        return res

    def list(self, sql: str, *args) -> List:
        return [x[0] for x in self.execute(sql, *args)]

    def scalar(self, sql: str, *args) -> Any:
        res = self.execute(sql, *args).fetchone()
        if res:
            return res[0]
        return None

    # Updates
    ################

    def executemany(self, sql: str, args: Iterable) -> None:
        self.mod = True
        self._db.executemany(sql, args)

    def executescript(self, sql: str) -> None:
        self.mod = True
        self._db.executescript(sql)

    # Cursor API
    ###############

    def execute(self, sql: str, *args) -> Cursor:
        s = sql.strip().lower()
        # mark modified?
        for stmt in "insert", "update", "delete":
            if s.startswith(stmt):
                self.mod = True
        res = self._db.execute(sql, args)
        return res
