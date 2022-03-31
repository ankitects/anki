# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from typing import Any, Generator, Literal, Sequence, Union, cast

from anki import (
    card_rendering_pb2,
    collection_pb2,
    config_pb2,
    generic_pb2,
    links_pb2,
    search_pb2,
    stats_pb2,
)
from anki._legacy import DeprecatedNamesMixin, deprecated

# protobuf we publicly export - listed first to avoid circular imports
HelpPage = links_pb2.HelpPageLinkRequest.HelpPage
SearchNode = search_pb2.SearchNode
Progress = collection_pb2.Progress
EmptyCardsReport = card_rendering_pb2.EmptyCardsReport
GraphPreferences = stats_pb2.GraphPreferences
CardStats = stats_pb2.CardStatsResponse
Preferences = config_pb2.Preferences
UndoStatus = collection_pb2.UndoStatus
OpChanges = collection_pb2.OpChanges
OpChangesWithCount = collection_pb2.OpChangesWithCount
OpChangesWithId = collection_pb2.OpChangesWithId
OpChangesAfterUndo = collection_pb2.OpChangesAfterUndo
BrowserRow = search_pb2.BrowserRow
BrowserColumns = search_pb2.BrowserColumns
StripHtmlMode = card_rendering_pb2.StripHtmlRequest

import copy
import os
import sys
import time
import traceback
import weakref
from dataclasses import dataclass, field

import anki.latex
from anki import hooks
from anki._backend import RustBackend, Translations
from anki.browser import BrowserConfig, BrowserDefaults
from anki.cards import Card, CardId
from anki.config import Config, ConfigManager
from anki.consts import *
from anki.dbproxy import DBProxy
from anki.decks import DeckId, DeckManager
from anki.errors import AbortSchemaModification, DBError
from anki.lang import FormatTimeSpan
from anki.media import MediaManager, media_paths_from_col_path
from anki.models import ModelManager, NotetypeDict, NotetypeId
from anki.notes import Note, NoteId
from anki.scheduler.v1 import Scheduler as V1Scheduler
from anki.scheduler.v2 import Scheduler as V2Scheduler
from anki.scheduler.v3 import Scheduler as V3Scheduler
from anki.sync import SyncAuth, SyncOutput, SyncStatus
from anki.tags import TagManager
from anki.types import assert_exhaustive
from anki.utils import (
    from_json_bytes,
    ids2str,
    int_time,
    split_fields,
    strip_html_media,
    to_json_bytes,
)

anki.latex.setup_hook()


SearchJoiner = Literal["AND", "OR"]


@dataclass
class LegacyReviewUndo:
    card: Card
    was_leech: bool


@dataclass
class LegacyCheckpoint:
    name: str


LegacyUndoResult = Union[None, LegacyCheckpoint, LegacyReviewUndo]


class Collection(DeprecatedNamesMixin):
    sched: V1Scheduler | V2Scheduler | V3Scheduler

    def __init__(
        self,
        path: str,
        backend: RustBackend | None = None,
        server: bool = False,
        log: bool = False,
    ) -> None:
        self._backend = backend or RustBackend(server=server)
        self.db: DBProxy | None = None
        self._should_log = log
        self.server = server
        self.path = os.path.abspath(path)
        self.reopen()

        self.tr = Translations(weakref.ref(self._backend))
        self.media = MediaManager(self, server)
        self.models = ModelManager(self)
        self.decks = DeckManager(self)
        self.tags = TagManager(self)
        self.conf = ConfigManager(self)
        self._load_scheduler()
        self._startReps = 0  # pylint: disable=invalid-name

    def name(self) -> Any:
        return os.path.splitext(os.path.basename(self.path))[0]

    def weakref(self) -> Collection:
        "Shortcut to create a weak reference that doesn't break code completion."
        return weakref.proxy(self)

    @property
    def backend(self) -> RustBackend:
        traceback.print_stack(file=sys.stdout)
        print()
        print(
            "Accessing the backend directly will break in the future. Please use the public methods on Collection instead."
        )
        return self._backend

    # I18n/messages
    ##########################################################################

    def format_timespan(
        self,
        seconds: float,
        context: FormatTimeSpan.Context.V = FormatTimeSpan.INTERVALS,
    ) -> str:
        return self._backend.format_timespan(seconds=seconds, context=context)

    # Progress
    ##########################################################################

    def latest_progress(self) -> Progress:
        return self._backend.latest_progress()

    # Scheduler
    ##########################################################################

    _supported_scheduler_versions = (1, 2)

    def sched_ver(self) -> Literal[1, 2]:
        """For backwards compatibility, the v3 scheduler currently returns 2.
        Use the separate v3_scheduler() method to check if it is active."""
        # for backwards compatibility, v3 is represented as 2
        ver = self.conf.get("schedVer", 1)
        if ver in self._supported_scheduler_versions:
            return ver
        else:
            raise Exception("Unsupported scheduler version")

    def _load_scheduler(self) -> None:
        ver = self.sched_ver()
        if ver == 1:
            self.sched = V1Scheduler(self)
        elif ver == 2:
            if self.v3_scheduler():
                self.sched = V3Scheduler(self)
            else:
                self.sched = V2Scheduler(self)

    def upgrade_to_v2_scheduler(self) -> None:
        self._backend.upgrade_scheduler()
        self.clear_python_undo()
        self._load_scheduler()

    def v3_scheduler(self) -> bool:
        return self.sched_ver() == 2 and self.get_config_bool(Config.Bool.SCHED_2021)

    def set_v3_scheduler(self, enabled: bool) -> None:
        if self.v3_scheduler() != enabled:
            if enabled and self.sched_ver() != 2:
                raise Exception("must upgrade to v2 scheduler first")
            self.set_config_bool(Config.Bool.SCHED_2021, enabled)
            self._load_scheduler()

    # DB-related
    ##########################################################################

    # legacy properties; these will likely go away in the future

    @property
    def crt(self) -> int:
        return self.db.scalar("select crt from col")

    @crt.setter
    def crt(self, crt: int) -> None:
        self.db.execute("update col set crt = ?", crt)

    @property
    def mod(self) -> int:
        return self.db.scalar("select mod from col")

    def modified_by_backend(self) -> bool:
        # Until we can move away from long-running transactions, the Python
        # code needs to know if the transaction should be committed, so we need
        # to check if the backend updated the modification time.
        return self.db.last_begin_at != self.mod

    def save(self, name: str | None = None, trx: bool = True) -> None:
        "Flush, commit DB, and take out another write lock if trx=True."
        # commit needed?
        if self.db.modified_in_python or self.modified_by_backend():
            self.db.modified_in_python = False
            self.db.commit()
            if trx:
                self.db.begin()
        elif not trx:
            # if no changes were pending but calling code expects to be
            # outside of a transaction, we need to roll back
            self.db.rollback()

        self._save_checkpoint(name)

    def autosave(self) -> None:
        """Save any pending changes.
        If a checkpoint was taken in the last 5 minutes, don't save."""
        if not self._have_outstanding_checkpoint():
            # if there's no active checkpoint, we can save immediately
            self.save()
        elif time.time() - self._last_checkpoint_at > 300:
            self.save()

    def close(
        self,
        save: bool = True,
        downgrade: bool = False,
    ) -> None:
        "Disconnect from DB."
        if self.db:
            if save:
                self.save(trx=False)
            else:
                self.db.rollback()
            self._clear_caches()
            self._backend.close_collection(
                downgrade_to_schema11=downgrade,
            )
            self.db = None

    def close_for_full_sync(self) -> None:
        # save and cleanup, but backend will take care of collection close
        if self.db:
            self.save(trx=False)
            self._clear_caches()
            self.db = None

    def export_collection(
        self, out_path: str, include_media: bool, legacy: bool
    ) -> None:
        self.close_for_full_sync()
        self._backend.export_collection_package(
            out_path=out_path, include_media=include_media, legacy=legacy
        )

    def rollback(self) -> None:
        self._clear_caches()
        self.db.rollback()
        self.db.begin()

    def _clear_caches(self) -> None:
        self.models._clear_cache()

    def reopen(self, after_full_sync: bool = False) -> None:
        if self.db:
            raise Exception("reopen() called with open db")

        self._last_checkpoint_at = time.time()
        self._undo: _UndoInfo = None

        (media_dir, media_db) = media_paths_from_col_path(self.path)

        log_path = ""
        should_log = not self.server and self._should_log
        if should_log:
            log_path = self.path.replace(".anki2", ".log")

        # connect
        if not after_full_sync:
            self._backend.open_collection(
                collection_path=self.path,
                media_folder_path=media_dir,
                media_db_path=media_db,
                log_path=log_path,
            )
        self.db = DBProxy(weakref.proxy(self._backend))
        self.db.begin()

    def set_schema_modified(self) -> None:
        self.db.execute("update col set scm=?", int_time(1000))
        self.save()

    def mod_schema(self, check: bool) -> None:
        "Mark schema modified. GUI catches this and will ask user if required."
        if not self.schema_changed():
            if check and not hooks.schema_will_change(proceed=True):
                raise AbortSchemaModification()
        self.set_schema_modified()

    def schema_changed(self) -> bool:
        "True if schema changed since last sync."
        return self.db.scalar("select scm > ls from col")

    def usn(self) -> int:
        if self.server:
            return self.db.scalar("select usn from col")
        else:
            return -1

    def create_backup(
        self,
        *,
        backup_folder: str,
        force: bool,
        wait_for_completion: bool,
    ) -> bool:
        """Create a backup if enough time has elapsed, and rotate old backups.

        If `force` is true, the user's configured backup interval is ignored.
        Returns true if backup created. This may be false in the force=True case,
        if no changes have been made to the collection.

        Commits any outstanding changes, which clears any active legacy checkpoint.

        Throws on failure of current backup, or the previous backup if it was not
        awaited.
        """
        # ensure any pending transaction from legacy code/add-ons has been committed
        self.save(trx=False)
        created = self._backend.create_backup(
            backup_folder=backup_folder,
            force=force,
            wait_for_completion=wait_for_completion,
        )
        self.db.begin()
        return created

    def await_backup_completion(self) -> None:
        "Throws if backup creation failed."
        self._backend.await_backup_completion()

    def legacy_checkpoint_pending(self) -> bool:
        return (
            self._have_outstanding_checkpoint()
            and time.time() - self._last_checkpoint_at < 300
        )

    # Object helpers
    ##########################################################################

    def get_card(self, id: CardId) -> Card:
        return Card(self, id)

    def update_cards(self, cards: Sequence[Card]) -> OpChanges:
        """Save card changes to database, and add an undo entry.
        Unlike card.flush(), this will invalidate any current checkpoint."""
        return self._backend.update_cards(
            cards=[c._to_backend_card() for c in cards], skip_undo_entry=False
        )

    def update_card(self, card: Card) -> OpChanges:
        """Save card changes to database, and add an undo entry.
        Unlike card.flush(), this will invalidate any current checkpoint."""
        return self.update_cards([card])

    def get_note(self, id: NoteId) -> Note:
        return Note(self, id=id)

    def update_notes(self, notes: Sequence[Note]) -> OpChanges:
        """Save note changes to database, and add an undo entry.
        Unlike note.flush(), this will invalidate any current checkpoint."""
        return self._backend.update_notes(
            notes=[n._to_backend_note() for n in notes], skip_undo_entry=False
        )

    def update_note(self, note: Note) -> OpChanges:
        """Save note changes to database, and add an undo entry.
        Unlike note.flush(), this will invalidate any current checkpoint."""
        return self.update_notes([note])

    # Utils
    ##########################################################################

    def nextID(  # pylint: disable=invalid-name
        self, type: str, inc: bool = True
    ) -> Any:
        type = f"next{type.capitalize()}"
        id = self.conf.get(type, 1)
        if inc:
            self.conf[type] = id + 1
        return id

    def reset(self) -> None:
        "Rebuild the queue and reload data after DB modified."
        self.autosave()
        self.sched.reset()

    # Notes
    ##########################################################################

    def new_note(self, notetype: NotetypeDict) -> Note:
        return Note(self, notetype)

    def add_note(self, note: Note, deck_id: DeckId) -> OpChanges:
        out = self._backend.add_note(note=note._to_backend_note(), deck_id=deck_id)
        note.id = NoteId(out.note_id)
        return out.changes

    def remove_notes(self, note_ids: Sequence[NoteId]) -> OpChangesWithCount:
        hooks.notes_will_be_deleted(self, note_ids)
        return self._backend.remove_notes(note_ids=note_ids, card_ids=[])

    def remove_notes_by_card(self, card_ids: list[CardId]) -> None:
        if hooks.notes_will_be_deleted.count():
            nids = self.db.list(
                f"select nid from cards where id in {ids2str(card_ids)}"
            )
            hooks.notes_will_be_deleted(self, nids)
        self._backend.remove_notes(note_ids=[], card_ids=card_ids)

    def card_ids_of_note(self, note_id: NoteId) -> Sequence[CardId]:
        return [CardId(id) for id in self._backend.cards_of_note(note_id)]

    def defaults_for_adding(
        self, *, current_review_card: Card | None
    ) -> anki.notes.DefaultsForAdding:
        """Get starting deck and notetype for add screen.
        An option in the preferences controls whether this will be based on the current deck
        or current notetype.
        """
        if card := current_review_card:
            home_deck = card.current_deck_id()
        else:
            home_deck = DeckId(0)

        return self._backend.defaults_for_adding(
            home_deck_of_current_review_card=home_deck,
        )

    def default_deck_for_notetype(self, notetype_id: NotetypeId) -> DeckId | None:
        """If 'change deck depending on notetype' is enabled in the preferences,
        return the last deck used with the provided notetype, if any.."""
        if self.get_config_bool(Config.Bool.ADDING_DEFAULTS_TO_CURRENT_DECK):
            return None

        return (
            DeckId(
                self._backend.default_deck_for_notetype(
                    ntid=notetype_id,
                )
            )
            or None
        )

    def note_count(self) -> int:
        return self.db.scalar("select count() from notes")

    # Cards
    ##########################################################################

    def is_empty(self) -> bool:
        return not self.db.scalar("select 1 from cards limit 1")

    def card_count(self) -> Any:
        return self.db.scalar("select count() from cards")

    def remove_cards_and_orphaned_notes(self, card_ids: Sequence[CardId]) -> None:
        "You probably want .remove_notes_by_card() instead."
        self._backend.remove_cards(card_ids=card_ids)

    def set_deck(self, card_ids: Sequence[CardId], deck_id: int) -> OpChangesWithCount:
        return self._backend.set_deck(card_ids=card_ids, deck_id=deck_id)

    def get_empty_cards(self) -> EmptyCardsReport:
        return self._backend.get_empty_cards()

    # Card generation & field checksums/sort fields
    ##########################################################################

    def after_note_updates(
        self, nids: list[NoteId], mark_modified: bool, generate_cards: bool = True
    ) -> None:
        "If notes modified directly in database, call this afterwards."
        self._backend.after_note_updates(
            nids=nids, generate_cards=generate_cards, mark_notes_modified=mark_modified
        )

    # Finding cards
    ##########################################################################

    def find_cards(
        self,
        query: str,
        order: bool | str | BrowserColumns.Column = False,
        reverse: bool = False,
    ) -> Sequence[CardId]:
        """Return card ids matching the provided search.

        To programmatically construct a search string, see .build_search_string().

        If order=True, use the sort order stored in the collection config
        If order=False, do no ordering

        If order is a string, that text is added after 'order by' in the sql statement.
        You must add ' asc' or ' desc' to the order, as Anki will replace asc with
        desc and vice versa when reverse is set in the collection config, eg
        order="c.ivl asc, c.due desc".

        If order is a BrowserColumns.Column that supports sorting, sort using that
        column. All available columns are available through col.all_browser_columns()
        or browser.table._model.columns and support sorting unless column.sorting
        is set to BrowserColumns.SORTING_NONE.

        The reverse argument only applies when a BrowserColumns.Column is provided;
        otherwise the collection config defines whether reverse is set or not.
        """
        mode = self._build_sort_mode(order, reverse, False)
        return cast(
            Sequence[CardId], self._backend.search_cards(search=query, order=mode)
        )

    def find_notes(
        self,
        query: str,
        order: bool | str | BrowserColumns.Column = False,
        reverse: bool = False,
    ) -> Sequence[NoteId]:
        """Return note ids matching the provided search.

        To programmatically construct a search string, see .build_search_string().
        The order parameter is documented in .find_cards().
        """
        mode = self._build_sort_mode(order, reverse, True)
        return cast(
            Sequence[NoteId], self._backend.search_notes(search=query, order=mode)
        )

    def _build_sort_mode(
        self,
        order: bool | str | BrowserColumns.Column,
        reverse: bool,
        finding_notes: bool,
    ) -> search_pb2.SortOrder:
        if isinstance(order, str):
            return search_pb2.SortOrder(custom=order)
        if isinstance(order, bool):
            if order is False:
                return search_pb2.SortOrder(none=generic_pb2.Empty())
            # order=True: set args to sort column and reverse from config
            sort_key = BrowserConfig.sort_column_key(finding_notes)
            order = self.get_browser_column(self.get_config(sort_key))
            reverse_key = BrowserConfig.sort_backwards_key(finding_notes)
            reverse = self.get_config(reverse_key)
        if isinstance(order, BrowserColumns.Column):
            if order.sorting != BrowserColumns.SORTING_NONE:
                return search_pb2.SortOrder(
                    builtin=search_pb2.SortOrder.Builtin(
                        column=order.key, reverse=reverse
                    )
                )

        # eg, user is ordering on an add-on field with the add-on not installed
        print(f"{order} is not a valid sort order.")
        return search_pb2.SortOrder(none=generic_pb2.Empty())

    def find_and_replace(
        self,
        *,
        note_ids: Sequence[NoteId],
        search: str,
        replacement: str,
        regex: bool = False,
        field_name: str | None = None,
        match_case: bool = False,
    ) -> OpChangesWithCount:
        "Find and replace fields in a note. Returns changed note count."
        return self._backend.find_and_replace(
            nids=note_ids,
            search=search,
            replacement=replacement,
            regex=regex,
            match_case=match_case,
            field_name=field_name or "",
        )

    def field_names_for_note_ids(self, nids: Sequence[int]) -> Sequence[str]:
        return self._backend.field_names_for_notes(nids)

    # returns array of ("dupestr", [nids])
    def find_dupes(self, field_name: str, search: str = "") -> list[tuple[str, list]]:
        nids = self.find_notes(
            self.build_search_string(search, SearchNode(field_name=field_name))
        )
        # go through notes
        vals: dict[str, list[int]] = {}
        dupes = []
        fields: dict[int, int] = {}

        def ord_for_mid(mid: NotetypeId) -> int:
            if mid not in fields:
                model = self.models.get(mid)
                for idx, field in enumerate(model["flds"]):
                    if field["name"].lower() == field_name.lower():
                        fields[mid] = idx
                        break
            return fields[mid]

        for nid, mid, flds in self.db.all(
            f"select id, mid, flds from notes where id in {ids2str(nids)}"
        ):
            flds = split_fields(flds)
            ord = ord_for_mid(mid)
            if ord is None:
                continue
            val = flds[ord]
            val = strip_html_media(val)
            # empty does not count as duplicate
            if not val:
                continue
            vals.setdefault(val, []).append(nid)
            if len(vals[val]) == 2:
                dupes.append((val, vals[val]))
        return dupes

    # Search Strings
    ##########################################################################

    def build_search_string(
        self,
        *nodes: str | SearchNode,
        joiner: SearchJoiner = "AND",
    ) -> str:
        """Join one or more searches, and return a normalized search string.

        To negate, wrap in a negated search term:

            term = SearchNode(negated=col.group_searches(...))

        Invalid searches will throw an exception.
        """
        term = self.group_searches(*nodes, joiner=joiner)
        return self._backend.build_search_string(term)

    def group_searches(
        self,
        *nodes: str | SearchNode,
        joiner: SearchJoiner = "AND",
    ) -> SearchNode:
        """Join provided search nodes and strings into a single SearchNode.
        If a single SearchNode is provided, it is returned as-is.
        At least one node must be provided.
        """
        assert nodes

        # convert raw text to SearchNodes
        search_nodes = [
            node if isinstance(node, SearchNode) else SearchNode(parsable_text=node)
            for node in nodes
        ]

        # if there's more than one, wrap them in a group
        if len(search_nodes) > 1:
            return SearchNode(
                group=SearchNode.Group(
                    nodes=search_nodes, joiner=self._pb_search_separator(joiner)
                )
            )
        else:
            return search_nodes[0]

    def join_searches(
        self,
        existing_node: SearchNode,
        additional_node: SearchNode,
        operator: Literal["AND", "OR"],
    ) -> str:
        """
        AND or OR `additional_term` to `existing_term`, without wrapping `existing_term` in brackets.
        Used by the Browse screen to avoid adding extra brackets when joining.
        If you're building a search query yourself, you probably don't need this.
        """
        search_string = self._backend.join_search_nodes(
            joiner=self._pb_search_separator(operator),
            existing_node=existing_node,
            additional_node=additional_node,
        )

        return search_string

    def replace_in_search_node(
        self, existing_node: SearchNode, replacement_node: SearchNode
    ) -> str:
        """If nodes of the same type as `replacement_node` are found in existing_node, replace them.

        You can use this to replace any "deck" clauses in a search with a different deck for example.
        """
        return self._backend.replace_search_node(
            existing_node=existing_node, replacement_node=replacement_node
        )

    def _pb_search_separator(self, operator: SearchJoiner) -> SearchNode.Group.Joiner.V:
        # pylint: disable=no-member
        if operator == "AND":
            return SearchNode.Group.Joiner.AND
        else:
            return SearchNode.Group.Joiner.OR

    # Browser Table
    ##########################################################################

    def all_browser_columns(self) -> Sequence[BrowserColumns.Column]:
        return self._backend.all_browser_columns()

    def get_browser_column(self, key: str) -> BrowserColumns.Column | None:
        for column in self._backend.all_browser_columns():
            if column.key == key:
                return column
        return None

    def browser_row_for_id(
        self, id_: int
    ) -> tuple[Generator[tuple[str, bool], None, None], BrowserRow.Color.V, str, int]:
        row = self._backend.browser_row_for_id(id_)
        return (
            ((cell.text, cell.is_rtl) for cell in row.cells),
            row.color,
            row.font_name,
            row.font_size,
        )

    def load_browser_card_columns(self) -> list[str]:
        """Return the stored card column names and ensure the backend columns are set and in sync."""
        columns = self.get_config(
            BrowserConfig.ACTIVE_CARD_COLUMNS_KEY, BrowserDefaults.CARD_COLUMNS
        )
        self._backend.set_active_browser_columns(columns)
        return columns

    def set_browser_card_columns(self, columns: list[str]) -> None:
        self.set_config(BrowserConfig.ACTIVE_CARD_COLUMNS_KEY, columns)
        self._backend.set_active_browser_columns(columns)

    def load_browser_note_columns(self) -> list[str]:
        """Return the stored note column names and ensure the backend columns are set and in sync."""
        columns = self.get_config(
            BrowserConfig.ACTIVE_NOTE_COLUMNS_KEY, BrowserDefaults.NOTE_COLUMNS
        )
        self._backend.set_active_browser_columns(columns)
        return columns

    def set_browser_note_columns(self, columns: list[str]) -> None:
        self.set_config(BrowserConfig.ACTIVE_NOTE_COLUMNS_KEY, columns)
        self._backend.set_active_browser_columns(columns)

    # Config
    ##########################################################################

    def get_config(self, key: str, default: Any = None) -> Any:
        try:
            return self.conf.get_immutable(key)
        except KeyError:
            return default

    def set_config(self, key: str, val: Any, *, undoable: bool = False) -> OpChanges:
        """Set a single config variable to any JSON-serializable value. The config
        is currently sent on every sync, so please don't store more than a few
        kilobytes in it.

        By default, no undo entry will be created, but the existing undo history
        will be preserved. Set `undoable=True` to allow the change to be undone;
        see undo code for how you can merge multiple undo entries."""
        return self._backend.set_config_json(
            key=key, value_json=to_json_bytes(val), undoable=undoable
        )

    def remove_config(self, key: str) -> OpChanges:
        return self.conf.remove(key)

    def all_config(self) -> dict[str, Any]:
        "This is a debugging aid. Prefer .get_config() when you know the key you need."
        return from_json_bytes(self._backend.get_all_config())

    def get_config_bool(self, key: Config.Bool.V) -> bool:
        return self._backend.get_config_bool(key)

    def set_config_bool(
        self, key: Config.Bool.V, value: bool, *, undoable: bool = False
    ) -> OpChanges:
        return self._backend.set_config_bool(key=key, value=value, undoable=undoable)

    def get_config_string(self, key: Config.String.V) -> str:
        return self._backend.get_config_string(key)

    def set_config_string(
        self, key: Config.String.V, value: str, undoable: bool = False
    ) -> OpChanges:
        return self._backend.set_config_string(key=key, value=value, undoable=undoable)

    def get_aux_notetype_config(
        self, id: NotetypeId, key: str, default: Any = None
    ) -> Any:
        key = self._backend.get_aux_notetype_config_key(id=id, key=key)
        return self.get_config(key, default=default)

    def set_aux_notetype_config(
        self, id: NotetypeId, key: str, value: Any, *, undoable: bool = False
    ) -> OpChanges:
        key = self._backend.get_aux_notetype_config_key(id=id, key=key)
        return self.set_config(key, value, undoable=undoable)

    def get_aux_template_config(
        self, id: NotetypeId, card_ordinal: int, key: str, default: Any = None
    ) -> Any:
        key = self._backend.get_aux_template_config_key(
            notetype_id=id, card_ordinal=card_ordinal, key=key
        )
        return self.get_config(key, default=default)

    def set_aux_template_config(
        self,
        id: NotetypeId,
        card_ordinal: int,
        key: str,
        value: Any,
        *,
        undoable: bool = False,
    ) -> OpChanges:
        key = self._backend.get_aux_template_config_key(
            notetype_id=id, card_ordinal=card_ordinal, key=key
        )
        return self.set_config(key, value, undoable=undoable)

    # Stats
    ##########################################################################

    def stats(self) -> anki.stats.CollectionStats:
        from anki.stats import CollectionStats

        return CollectionStats(self)

    def card_stats_data(self, card_id: CardId) -> stats_pb2.CardStatsResponse:
        return self._backend.card_stats(card_id)

    def studied_today(self) -> str:
        return self._backend.studied_today()

    # Undo
    ##########################################################################

    def undo_status(self) -> UndoStatus:
        "Return the undo status."
        # check backend first
        if status := self._check_backend_undo_status():
            return status

        if not self._undo:
            return UndoStatus()

        if isinstance(self._undo, _ReviewsUndo):
            return UndoStatus(undo=self.tr.scheduling_review())
        elif isinstance(self._undo, LegacyCheckpoint):
            return UndoStatus(undo=self._undo.name)
        else:
            assert_exhaustive(self._undo)
            assert False

    def add_custom_undo_entry(self, name: str) -> int:
        """Add an empty undo entry with the given name.
        The return value can be used to merge subsequent changes
        with `merge_undo_entries()`.

        You should only use this with your own custom actions - when
        extending default Anki behaviour, you should merge into an
        existing undo entry instead, so the existing undo name is
        preserved, and changes are processed correctly.
        """
        return self._backend.add_custom_undo_entry(name)

    def merge_undo_entries(self, target: int) -> OpChanges:
        """Combine multiple undoable operations into one.

        After a standard Anki action, you can use col.undo_status().last_step
        to retrieve the target to merge into. When defining your own custom
        actions, you can use `add_custom_undo_entry()` to define a custom
        undo name.
        """
        return self._backend.merge_undo_entries(target)

    def clear_python_undo(self) -> None:
        """Clear the Python undo state.
        The backend will automatically clear backend undo state when
        any SQL DML is executed, or an operation that doesn't support undo
        is run."""
        self._undo = None

    def undo(self) -> OpChangesAfterUndo:
        """Returns result of backend undo operation, or throws UndoEmpty.
        If UndoEmpty is received, caller should try undo_legacy()."""
        out = self._backend.undo()
        self.clear_python_undo()
        if out.changes.notetype:
            self.models._clear_cache()
        return out

    def redo(self) -> OpChangesAfterUndo:
        """Returns result of backend redo operation, or throws UndoEmpty."""
        out = self._backend.redo()
        self.clear_python_undo()
        if out.changes.notetype:
            self.models._clear_cache()
        return out

    def undo_legacy(self) -> LegacyUndoResult:
        "Returns None if the legacy undo queue is empty."
        if isinstance(self._undo, _ReviewsUndo):
            return self._undo_review()
        elif isinstance(self._undo, LegacyCheckpoint):
            return self._undo_checkpoint()
        elif self._undo is None:
            return None
        else:
            assert_exhaustive(self._undo)
            assert False

    def op_made_changes(self, changes: OpChanges) -> bool:
        for field in changes.DESCRIPTOR.fields:
            if field.name != "kind":
                if getattr(changes, field.name, False):
                    return True
        return False

    def _check_backend_undo_status(self) -> UndoStatus | None:
        """Return undo status if undo available on backend.
        If backend has undo available, clear the Python undo state."""
        status = self._backend.get_undo_status()
        if status.undo or status.redo:
            self.clear_python_undo()
            return status
        else:
            return None

    def save_card_review_undo_info(self, card: Card) -> None:
        "Used by V1 and V2 schedulers to record state prior to review."
        if not isinstance(self._undo, _ReviewsUndo):
            self._undo = _ReviewsUndo()

        was_leech = card.note().has_tag("leech")
        entry = LegacyReviewUndo(card=copy.copy(card), was_leech=was_leech)
        self._undo.entries.append(entry)

    def _have_outstanding_checkpoint(self) -> bool:
        self._check_backend_undo_status()
        return isinstance(self._undo, LegacyCheckpoint)

    def _undo_checkpoint(self) -> LegacyCheckpoint:
        assert isinstance(self._undo, LegacyCheckpoint)
        self.rollback()
        undo = self._undo
        self.clear_python_undo()
        return undo

    def _save_checkpoint(self, name: str | None) -> None:
        "Call via .save(). If name not provided, clear any existing checkpoint."
        self._last_checkpoint_at = time.time()
        if name:
            self._undo = LegacyCheckpoint(name=name)
        else:
            # saving disables old checkpoint, but not review undo
            if not isinstance(self._undo, _ReviewsUndo):
                self.clear_python_undo()

    def _undo_review(self) -> LegacyReviewUndo:
        "Undo a v1/v2 review."
        assert isinstance(self._undo, _ReviewsUndo)
        entry = self._undo.entries.pop()
        if not self._undo.entries:
            self.clear_python_undo()

        card = entry.card

        # remove leech tag if it didn't have it before
        if not entry.was_leech and card.note().has_tag("leech"):
            card.note().remove_tag("leech")
            card.note().flush()

        # write old data
        card.flush()

        # and delete revlog entry if not previewing
        conf = self.sched._cardConf(card)
        previewing = conf["dyn"] and not conf["resched"]
        if not previewing:
            last = self.db.scalar(
                "select id from revlog where cid = ? " "order by id desc limit 1",
                card.id,
            )
            self.db.execute("delete from revlog where id = ?", last)

        # restore any siblings
        self.db.execute(
            "update cards set queue=type,mod=?,usn=? where queue=-2 and nid=?",
            int_time(),
            self.usn(),
            card.nid,
        )

        # update daily counts
        idx = card.queue
        if card.queue in (QUEUE_TYPE_DAY_LEARN_RELEARN, QUEUE_TYPE_PREVIEW):
            idx = QUEUE_TYPE_LRN
        type = ("new", "lrn", "rev")[idx]
        self.sched._updateStats(card, type, -1)
        self.sched.reps -= 1
        self._startReps -= 1

        # and refresh the queues
        self.sched.reset()

        return entry

    # DB maintenance
    ##########################################################################

    def fix_integrity(self) -> tuple[str, bool]:
        """Fix possible problems and rebuild caches.

        Returns tuple of (error: str, ok: bool). 'ok' will be true if no
        problems were found.
        """
        self.save(trx=False)
        try:
            problems = list(self._backend.check_database())
            ok = not problems
            problems.append(self.tr.database_check_rebuilt())
        except DBError as err:
            problems = [str(err)]
            ok = False
        finally:
            try:
                self.db.begin()
            except:
                # may fail if the DB is very corrupt
                pass
        return ("\n".join(problems), ok)

    def optimize(self) -> None:
        self.save(trx=False)
        self.db.execute("vacuum")
        self.db.execute("analyze")
        self.db.begin()

    ##########################################################################

    def set_user_flag_for_cards(
        self, flag: int, cids: Sequence[CardId]
    ) -> OpChangesWithCount:
        return self._backend.set_flag(card_ids=cids, flag=flag)

    def set_wants_abort(self) -> None:
        self._backend.set_wants_abort()

    def i18n_resources(self, modules: Sequence[str]) -> bytes:
        return self._backend.i18n_resources(modules=modules)

    def abort_media_sync(self) -> None:
        self._backend.abort_media_sync()

    def abort_sync(self) -> None:
        self._backend.abort_sync()

    def full_upload(self, auth: SyncAuth) -> None:
        self._backend.full_upload(auth)

    def full_download(self, auth: SyncAuth) -> None:
        self._backend.full_download(auth)

    def sync_login(self, username: str, password: str) -> SyncAuth:
        return self._backend.sync_login(username=username, password=password)

    def sync_collection(self, auth: SyncAuth) -> SyncOutput:
        return self._backend.sync_collection(auth)

    def sync_media(self, auth: SyncAuth) -> None:
        self._backend.sync_media(auth)

    def sync_status(self, auth: SyncAuth) -> SyncStatus:
        return self._backend.sync_status(auth)

    def get_preferences(self) -> Preferences:
        return self._backend.get_preferences()

    def set_preferences(self, prefs: Preferences) -> OpChanges:
        return self._backend.set_preferences(prefs)

    def render_markdown(self, text: str, sanitize: bool = True) -> str:
        "Not intended for public consumption at this time."
        return self._backend.render_markdown(markdown=text, sanitize=sanitize)

    # Timeboxing
    ##########################################################################
    # fixme: there doesn't seem to be a good reason why this code is in main.py
    # instead of covered in reviewer, and the reps tracking is covered by both
    # the scheduler and reviewer.py. in the future, we should probably move
    # reps tracking to reviewer.py, and remove the startTimebox() calls from
    # other locations like overview.py. We just need to make sure not to reset
    # the count on things like edits, which we probably could do by checking
    # the previous state in moveToState.

    # pylint: disable=invalid-name

    def startTimebox(self) -> None:
        self._startTime = time.time()
        self._startReps = self.sched.reps

    def timeboxReached(self) -> Literal[False] | tuple[Any, int]:
        "Return (elapsedTime, reps) if timebox reached, or False."
        if not self.conf["timeLim"]:
            # timeboxing disabled
            return False
        elapsed = time.time() - self._startTime
        if elapsed > self.conf["timeLim"]:
            return (self.conf["timeLim"], self.sched.reps - self._startReps)
        return False

    # Legacy
    ##########################################################################

    @deprecated(info="no longer used")
    def log(self, *args: Any, **kwargs: Any) -> None:
        print(args, kwargs)

    @deprecated(replaced_by=undo_status)
    def undo_name(self) -> str | None:
        "Undo menu item name, or None if undo unavailable."
        status = self.undo_status()
        return status.undo or None

    # @deprecated(replaced_by=new_note)
    def newNote(self, forDeck: bool = True) -> Note:
        "Return a new note with the current model."
        return Note(self, self.models.current(forDeck))

    # @deprecated(replaced_by=add_note)
    def addNote(self, note: Note) -> int:
        self.add_note(note, note.note_type()["did"])
        return len(note.cards())

    @deprecated(replaced_by=remove_notes)
    def remNotes(self, ids: Sequence[NoteId]) -> None:
        self.remove_notes(ids)

    @deprecated(replaced_by=remove_notes)
    def _remNotes(self, ids: list[NoteId]) -> None:
        pass

    @deprecated(replaced_by=card_stats_data)
    def card_stats(self, card_id: CardId, include_revlog: bool) -> str:
        from anki.stats import _legacy_card_stats

        return _legacy_card_stats(self, card_id, include_revlog)

    @deprecated(replaced_by=card_stats_data)
    def cardStats(self, card: Card) -> str:
        from anki.stats import _legacy_card_stats

        return _legacy_card_stats(self, card.id, False)

    @deprecated(replaced_by=after_note_updates)
    def updateFieldCache(self, nids: list[NoteId]) -> None:
        self.after_note_updates(nids, mark_modified=False, generate_cards=False)

    @deprecated(replaced_by=after_note_updates)
    def genCards(self, nids: list[NoteId]) -> list[int]:
        self.after_note_updates(nids, mark_modified=False, generate_cards=True)
        # previously returned empty cards, no longer does
        return []

    @deprecated(info="no longer used")
    def emptyCids(self) -> list[CardId]:
        return []

    @deprecated(info="handled by backend")
    def _logRem(self, ids: list[int | NoteId], type: int) -> None:
        self.db.executemany(
            "insert into graves values (%d, ?, %d)" % (self.usn(), type),
            ([x] for x in ids),
        )

    @deprecated(info="no longer required")
    def setMod(self) -> None:
        pass

    @deprecated(info="no longer required")
    def flush(self) -> None:
        pass


Collection.register_deprecated_aliases(
    clearUndo=Collection.clear_python_undo,
    markReview=Collection.save_card_review_undo_info,
    findReplace=Collection.find_and_replace,
    remCards=Collection.remove_cards_and_orphaned_notes,
)


# legacy name
_Collection = Collection


@dataclass
class _ReviewsUndo:
    entries: list[LegacyReviewUndo] = field(default_factory=list)


_UndoInfo = Union[_ReviewsUndo, LegacyCheckpoint, None]
