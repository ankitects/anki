# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from typing import Any, Iterable, List, Optional, Sequence, Union

import anki

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

    # Transactions
    ###############

    def begin(self) -> None:
        self._backend.db_begin()

    def commit(self) -> None:
        self._backend.db_commit()

    def rollback(self) -> None:
        self._backend.db_rollback()

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
        assert ":" not in sql
        # fetch rows
        return self._backend.db_query(sql, args, first_row_only)

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

    def executemany(self, sql: str, args: Iterable[Sequence[ValueForDB]]) -> None:
        self.mod = True
        assert ":" not in sql
        if isinstance(args, list):
            list_args = args
        else:
            list_args = list(args)
        self._backend.db_execute_many(sql, list_args)
