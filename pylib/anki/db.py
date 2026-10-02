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
    """A thin wrapper around `sqlite3`, kept for add-ons.

    The collection itself no longer goes through this class (it uses
    `anki.dbproxy` instead), but this is still public API and a number of
    add-ons rely on it.

    Most methods simply forward to the underlying `sqlite3.Connection`. On top
    of that, it offers:

    - `execute()`, which accepts either positional or named parameters
    - `mod`, set when a statement that may modify the database is run
    - `echo`, taken from the `DBECHO` environment variable, to time queries
    - `scalar()`, `all()`, `first()` and `list()` for the common cases

    Text is read back with invalid UTF-8 stripped rather than raising
    `UnicodeDecodeError`, so a corrupt row comes back silently truncated
    instead of failing.
    """

    def __init__(self, path: str, timeout: int = 0) -> None:
        """Open the database at `path`, creating it if it does not exist.

        `path` may be `":memory:"` for a temporary in-memory database.
        `timeout` is how long to wait, in seconds, for a lock held by another
        connection before raising `sqlite3.OperationalError`.
        """
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
        """Run `sql` and return a `sqlite3.Cursor` positioned on the result.

        Parameters can be given positionally with `?`, or by name with
        `:name`. Named parameters are safer unless you control the order of the
        placeholders.

        >>> db = DB(":memory:")
        >>> _ = db.execute("create table fruit(name text, quantity integer)")
        >>> _ = db.execute("insert into fruit values (:name, :quantity)",
        ...                name="apple", quantity=3)
        >>> _ = db.execute("insert into fruit values (?, ?)", "banana", 5)
        >>> db.all("select name, quantity from fruit order by name")
        [('apple', 3), ('banana', 5)]

        Sets `mod` if the statement starts with `insert`, `update` or `delete`.
        """
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
        """Run `sql` once for each item in `iterable`, and set `mod`.

        Each item supplies the parameters for one execution, in whichever form
        `execute()` accepts.
        """
        self.mod = True
        start_time = time.time()
        self._db.executemany(sql, iterable)
        if self.echo:
            print(sql, f"{(time.time() - start_time) * 1000:0.3f}ms")
            if self.echo == "2":
                print(iterable)

    def commit(self) -> None:
        """Commit the current transaction."""
        start_time = time.time()
        self._db.commit()
        if self.echo:
            print(f"commit {(time.time() - start_time) * 1000:0.3f}ms")

    def executescript(self, sql: str) -> None:
        """Run a `sql` script of semicolon-separated statements, and set `mod`.

        As with `sqlite3.Connection.executescript()`, any pending transaction
        is committed before the script runs.
        """
        self.mod = True
        if self.echo:
            print(sql)
        self._db.executescript(sql)

    def rollback(self) -> None:
        """Roll back the current transaction."""
        self._db.rollback()

    def scalar(self, *a: Any, **kw: Any) -> Any:
        """Run a query and return the first column of the first row.

        Returns `None` if the query matched no rows.

        >>> db = DB(":memory:")
        >>> _ = db.execute("create table fruit(name text, quantity integer)")
        >>> _ = db.execute("insert into fruit values ('apple', 3)")
        >>> db.scalar("select quantity from fruit where name = ?", "apple")
        3
        >>> db.scalar("select quantity from fruit where name = 'pear'") is None
        True
        """
        res = self.execute(*a, **kw).fetchone()
        if res:
            return res[0]
        return None

    def all(self, *a: Any, **kw: Any) -> list:
        """Run a query and return every row, as a list of tuples."""
        return self.execute(*a, **kw).fetchall()

    def first(self, *a: Any, **kw: Any) -> Any:
        """Run a query and return the first row as a tuple, or `None`.

        The cursor is closed before returning, so the remaining rows are not
        available afterwards.
        """
        cursor = self.execute(*a, **kw)
        res = cursor.fetchone()
        cursor.close()
        return res

    def list(self, *a: Any, **kw: Any) -> list:
        """Run a query and return the first column of every row.

        >>> db = DB(":memory:")
        >>> _ = db.execute("create table fruit(name text, quantity integer)")
        >>> _ = db.executemany("insert into fruit values (?, ?)",
        ...                     [("apple", 3), ("banana", 5)])
        >>> db.list("select name from fruit order by name")
        ['apple', 'banana']
        """
        return [x[0] for x in self.execute(*a, **kw)]

    def close(self) -> None:
        """Close the connection to the database."""
        self._db.text_factory = None
        self._db.close()

    def set_progress_handler(self, *args: Any) -> None:
        """Install a handler SQLite calls back every `n` opcodes mid-statement.

        Returning a non-zero value from the handler aborts the statement. This
        is how a long query gets made cancellable, usually together with
        `interrupt()`.
        """
        self._db.set_progress_handler(*args)

    def __enter__(self) -> "DB":
        """Begin a transaction and return the connection.

        Note that leaving the block does not commit; see `__exit__()`.
        """
        self._db.execute("begin")
        return self

    def __exit__(self, *args: Any) -> None:
        """Close the connection, discarding any uncommitted changes."""
        self._db.close()

    def total_changes(self) -> Any:
        """Number of rows inserted, updated or deleted since the connection
        was opened.
        """
        return self._db.total_changes

    def interrupt(self) -> None:
        """Abort a statement currently running on another thread.

        Returns immediately; the interrupted statement raises
        `sqlite3.OperationalError` on the thread running it.
        """
        self._db.interrupt()

    def set_autocommit(self, autocommit: bool) -> None:
        """Enable or disable SQLite's implicit transaction handling.

        With `autocommit` set, statements run outside of an explicit
        transaction, so changes are committed as they are made and `rollback()`
        has nothing to undo.
        """
        if autocommit:
            self._db.isolation_level = None
        else:
            self._db.isolation_level = ""

    # strip out invalid utf-8 when reading from db
    def _text_factory(self, data: bytes) -> str:
        return str(data, errors="ignore")

    def cursor(self, factory: type[Cursor] = Cursor) -> Cursor:
        """Return a new cursor of the given `factory` type."""
        return self._db.cursor(factory)
