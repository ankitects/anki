# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""
A convenience wrapper over pysqlite.

Anki's Collection class now uses dbproxy.py instead of this class,
but this class is still used by aqt's profile manager, and a number
of add-ons rely on it.
"""

from __future__ import annotations

import os
import pprint
import time
from sqlite3 import Cursor
from sqlite3 import dbapi2 as sqlite
from typing import Any

from anki._legacy import DeprecatedNamesMixin

DBError = sqlite.Error


class DB(DeprecatedNamesMixin):
    def __init__(self, path: str, timeout: int = 0) -> None:
        self._db = sqlite.connect(path, timeout=timeout)
        self._db.text_factory = self._text_factory
        self._path = path
        self.echo = os.environ.get("DBECHO")
        self.mod = False

    def __repr__(self) -> str:
        dict_ = dict(self.__dict__)
        del dict_["_db"]
        return f"{super().__repr__()} {pprint.pformat(dict_, width=300)}"

    def execute(self, sql: str, *a: Any, **ka: Any) -> Cursor:
        canonized = sql.strip().lower()
        # mark modified?
        for stmt in "insert", "update", "delete":
            if canonized.startswith(stmt):
                self.mod = True
        start_time = time.time()
        if ka:
            # execute("...where id = :id", id=5)
            res = self._db.execute(sql, ka)
        else:
            # execute("...where id = ?", 5)
            res = self._db.execute(sql, a)
        if self.echo:
            # print a, ka
            print(sql, f"{(time.time() - start_time) * 1000:0.3f}ms")
            if self.echo == "2":
                print(a, ka)
        return res

    def executemany(self, sql: str, iterable: Any) -> None:
        self.mod = True
        start_time = time.time()
        self._db.executemany(sql, iterable)
        if self.echo:
            print(sql, f"{(time.time() - start_time) * 1000:0.3f}ms")
            if self.echo == "2":
                print(iterable)

    def commit(self) -> None:
        start_time = time.time()
        self._db.commit()
        if self.echo:
            print(f"commit {(time.time() - start_time) * 1000:0.3f}ms")

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

    def all(self, *a: Any, **kw: Any) -> list:
        return self.execute(*a, **kw).fetchall()

    def first(self, *a: Any, **kw: Any) -> Any:
        cursor = self.execute(*a, **kw)
        res = cursor.fetchone()
        cursor.close()
        return res

    def list(self, *a: Any, **kw: Any) -> list:
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

    def total_changes(self) -> Any:
        return self._db.total_changes

    def interrupt(self) -> None:
        self._db.interrupt()

    def set_autocommit(self, autocommit: bool) -> None:
        if autocommit:
            self._db.isolation_level = None
        else:
            self._db.isolation_level = ""

    # strip out invalid utf-8 when reading from db
    def _text_factory(self, data: bytes) -> str:
        return str(data, errors="ignore")

    def cursor(self, factory: type[Cursor] = Cursor) -> Cursor:
        return self._db.cursor(factory)
