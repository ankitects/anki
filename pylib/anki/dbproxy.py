# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

# fixme: lossy utf8 handling
# fixme: progress

from sqlite3 import dbapi2 as sqlite
from typing import Any, Iterable, List, Optional, Sequence, Union

# DBValue is actually Union[str, int, float, None], but if defined
# that way, every call site needs to do a type check prior to using
# the return values.
ValueFromDB = Any
Row = Sequence[ValueFromDB]

ValueForDB = Union[str, int, float, None]


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

    def _query(
        self, sql: str, *args: ValueForDB, first_row_only: bool = False
    ) -> List[Row]:
        # mark modified?
        s = sql.strip().lower()
        for stmt in "insert", "update", "delete":
            if s.startswith(stmt):
                self.mod = True
        # fetch rows
        curs = self._db.execute(sql, args)
        if first_row_only:
            row = curs.fetchone()
            curs.close()
            if row is not None:
                return [row]
            else:
                return []
        else:
            return curs.fetchall()

    # Query shortcuts
    ###################

    def all(self, sql: str, *args: ValueForDB) -> List[Row]:
        return self._query(sql, *args)

    def list(self, sql: str, *args: ValueForDB) -> List[ValueFromDB]:
        return [x[0] for x in self._query(sql, *args)]

    def first(self, sql: str, *args: ValueForDB) -> Optional[Row]:
        rows = self._query(sql, *args, first_row_only=True)
        if rows:
            return rows[0]
        else:
            return None

    def scalar(self, sql: str, *args: ValueForDB) -> ValueFromDB:
        rows = self._query(sql, *args, first_row_only=True)
        if rows:
            return rows[0][0]
        else:
            return None

    # execute used to return a pysqlite cursor, but now is synonymous
    # with .all()
    execute = all

    # Updates
    ################

    def executemany(self, sql: str, args: Iterable[Iterable[ValueForDB]]) -> None:
        self.mod = True
        self._db.executemany(sql, args)

    def executescript(self, sql: str) -> None:
        self.mod = True
        self._db.executescript(sql)
