# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from typing import Any, Iterable, List, Optional, Sequence, Union

import anki

# fixme: remember to null on close to avoid circular ref
# fixme: progress

# DBValue is actually Union[str, int, float, None], but if defined
# that way, every call site needs to do a type check prior to using
# the return values.
ValueFromDB = Any
Row = Sequence[ValueFromDB]

ValueForDB = Union[str, int, float, None]


class DBProxy:
    # Lifecycle
    ###############

    def __init__(self, backend: anki.rsbackend.RustBackend, path: str) -> None:
        self._backend = backend
        self._path = path
        self.mod = False

    def close(self) -> None:
        # fixme
        pass

    # Transactions
    ###############

    def commit(self) -> None:
        # fixme
        pass

    def rollback(self) -> None:
        # fixme
        pass

    def setAutocommit(self, autocommit: bool) -> None:
        # fixme
        pass

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
        # fixme: first_row_only
        return self._backend.db_query(sql, args)

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
        raise Exception("fixme")

    def executescript(self, sql: str) -> None:
        self.mod = True
        raise Exception("fixme")
