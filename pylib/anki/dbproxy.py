# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import re
from collections.abc import Callable, Iterable, Sequence
from re import Match
from typing import TYPE_CHECKING, Any, Union

if TYPE_CHECKING:
    import anki._backend
    from anki.collection import Collection

# DBValue is actually Union[str, int, float, None], but if defined
# that way, every call site needs to do a type check prior to using
# the return values.
ValueFromDB = Any
Row = Sequence[ValueFromDB]

ValueForDB = Union[str, int, float, None]


class DBProxy:
    # Lifecycle
    ###############

    def __init__(self, backend: anki._backend.RustBackend) -> None:
        self._backend = backend

    # Transactions
    ###############

    def transact(self, op: Callable[[], None]) -> None:
        """Run the provided operation inside a transaction.

        Please note that all backend methods automatically wrap changes in a transaction,
        so there is no need to use this when calling methods like update_cards(), unless
        you are making other changes at the same time and want to ensure they are applied
        completely or not at all.

        If the operation throws an exception, the changes will be automatically rolled
        back.
        """

        try:
            self._backend.db_begin()
            op()
            self._backend.db_commit()
        except BaseException as e:
            self._backend.db_rollback()
            raise e

    # Querying
    ################

    def _query(
        self,
        sql: str,
        *args: ValueForDB,
        first_row_only: bool = False,
        **kwargs: ValueForDB,
    ) -> list[Row]:
        sql, args2 = emulate_named_args(sql, args, kwargs)
        # fetch rows
        return self._backend.db_query(sql, args2, first_row_only)

    # Query shortcuts
    ###################

    def all(self, sql: str, *args: ValueForDB, **kwargs: ValueForDB) -> list[Row]:
        return self._query(sql, *args, first_row_only=False, **kwargs)

    def list(
        self, sql: str, *args: ValueForDB, **kwargs: ValueForDB
    ) -> list[ValueFromDB]:
        return [x[0] for x in self._query(sql, *args, first_row_only=False, **kwargs)]

    def first(self, sql: str, *args: ValueForDB, **kwargs: ValueForDB) -> Row | None:
        rows = self._query(sql, *args, first_row_only=True, **kwargs)
        if rows:
            return rows[0]
        else:
            return None

    def scalar(self, sql: str, *args: ValueForDB, **kwargs: ValueForDB) -> ValueFromDB:
        rows = self._query(sql, *args, first_row_only=True, **kwargs)
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
        if isinstance(args, list):
            list_args = args
        else:
            list_args = list(args)
        self._backend.db_execute_many(sql, list_args)


# convert kwargs to list format
def emulate_named_args(
    sql: str, args: tuple, kwargs: dict[str, Any]
) -> tuple[str, Sequence[ValueForDB]]:
    # nothing to do?
    if not kwargs:
        return sql, args
    print("named arguments in queries will go away in the future:", sql)
    # map args to numbers
    arg_num = {}
    args2 = list(args)
    for key, val in kwargs.items():
        args2.append(val)
        number = len(args2)
        arg_num[key] = number

    # update refs
    def repl(match: Match) -> str:
        arg = match.group(1)
        return f"?{arg_num[arg]}"

    sql = re.sub(":([a-zA-Z_0-9]+)", repl, sql)
    return sql, args2
