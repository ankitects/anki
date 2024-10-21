# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from collections.abc import Generator, Iterable, Sequence
from typing import Any, Literal, Union, cast

from anki import (
    ankiweb_pb2,
    card_rendering_pb2,
    collection_pb2,
    config_pb2,
    generic_pb2,
    image_occlusion_pb2,
    import_export_pb2,
    links_pb2,
    notes_pb2,
    scheduler_pb2,
    search_pb2,
    stats_pb2,
    sync_pb2,
)
from anki._legacy import DeprecatedNamesMixin, deprecated
from anki.sync_pb2 import SyncLoginRequest

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
OpChangesOnly = collection_pb2.OpChangesOnly
OpChangesWithCount = collection_pb2.OpChangesWithCount
OpChangesWithId = collection_pb2.OpChangesWithId
OpChangesAfterUndo = collection_pb2.OpChangesAfterUndo
BrowserRow = search_pb2.BrowserRow
BrowserColumns = search_pb2.BrowserColumns
StripHtmlMode = card_rendering_pb2.StripHtmlRequest
ImportLogWithChanges = import_export_pb2.ImportResponse
ImportAnkiPackageRequest = import_export_pb2.ImportAnkiPackageRequest
ImportAnkiPackageOptions = import_export_pb2.ImportAnkiPackageOptions
ExportAnkiPackageOptions = import_export_pb2.ExportAnkiPackageOptions
ImportCsvRequest = import_export_pb2.ImportCsvRequest
CsvMetadata = import_export_pb2.CsvMetadata
DupeResolution = CsvMetadata.DupeResolution
Delimiter = import_export_pb2.CsvMetadata.Delimiter
TtsVoice = card_rendering_pb2.AllTtsVoicesResponse.TtsVoice
GetImageForOcclusionResponse = image_occlusion_pb2.GetImageForOcclusionResponse
AddImageOcclusionNoteRequest = image_occlusion_pb2.AddImageOcclusionNoteRequest
GetImageOcclusionNoteResponse = image_occlusion_pb2.GetImageOcclusionNoteResponse
AddonInfo = ankiweb_pb2.AddonInfo
CheckForUpdateResponse = ankiweb_pb2.CheckForUpdateResponse
MediaSyncStatus = sync_pb2.MediaSyncStatusResponse
FsrsItem = scheduler_pb2.FsrsItem
FsrsReview = scheduler_pb2.FsrsReview

import os
import sys
import time
import traceback
import weakref
from dataclasses import dataclass

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
from anki.scheduler.dummy import DummyScheduler
from anki.scheduler.v3 import Scheduler as V3Scheduler
from anki.sync import SyncAuth, SyncOutput, SyncStatus
from anki.tags import TagManager
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
class DeckIdLimit:
    deck_id: DeckId


@dataclass
class NoteIdsLimit:
    note_ids: Sequence[NoteId]


@dataclass
class CardIdsLimit:
    card_ids: Sequence[CardId]


ExportLimit = Union[DeckIdLimit, NoteIdsLimit, CardIdsLimit, None]


@dataclass
class ComputedMemoryState:
    desired_retention: float
    stability: float | None = None
    difficulty: float | None = None


@dataclass
class AddNoteRequest:
    note: Note
    deck_id: DeckId


class Collection(DeprecatedNamesMixin):
    sched: V3Scheduler | DummyScheduler

    @staticmethod
    def initialize_backend_logging() -> None:
        """Enable terminal logging. Must be called only once."""
        RustBackend.initialize_logging(None)

    def __init__(
        self,
        path: str,
        backend: RustBackend | None = None,
        server: bool = False,
    ) -> None:
        self._backend = backend or RustBackend(server=server)
        self.db: DBProxy | None = None
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
            self.sched = DummyScheduler(self)
        elif ver == 2:
            if self.v3_scheduler():
                self.sched = V3Scheduler(self)
                # enable new timezone if not already enabled
                if self.conf.get("creationOffset") is None:
                    prefs = self._backend.get_preferences()
                    prefs.scheduling.new_timezone = True
                    self._backend.set_preferences(prefs)
            else:
                self.sched = DummyScheduler(self)

    def upgrade_to_v2_scheduler(self) -> None:
        self._backend.upgrade_scheduler()
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

    @deprecated(info="saving is automatic")
    def save(self, **args: Any) -> None:
        pass

    @deprecated(info="saving is automatic")
    def autosave(self) -> None:
        pass

    def close(
        self,
        downgrade: bool = False,
    ) -> None:
        "Disconnect from DB."
        if self.db:
            self._clear_caches()
            self._backend.close_collection(
                downgrade_to_schema11=downgrade,
            )
            self.db = None

    def close_for_full_sync(self) -> None:
        # save and cleanup, but backend will take care of collection close
        if self.db:
            self._clear_caches()
            self.db = None

    def _clear_caches(self) -> None:
        self.models._clear_cache()

    def reopen(self, after_full_sync: bool = False) -> None:
        if self.db:
            raise Exception("reopen() called with open db")

        (media_dir, media_db) = media_paths_from_col_path(self.path)

        # connect
        if not after_full_sync:
            self._backend.open_collection(
                collection_path=self.path,
                media_folder_path=media_dir,
                media_db_path=media_db,
            )
        self.db = DBProxy(weakref.proxy(self._backend))
        if after_full_sync:
            self._load_scheduler()

    def set_schema_modified(self) -> None:
        self.db.execute("update col set scm=?", int_time(1000))

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

    # Import/export
    ##########################################################################

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

        Throws on failure of current backup, or the previous backup if it was not
        awaited.
        """
        # ensure any pending transaction from legacy code/add-ons has been committed
        created = self._backend.create_backup(
            backup_folder=backup_folder,
            force=force,
            wait_for_completion=wait_for_completion,
        )
        return created

    def await_backup_completion(self) -> None:
        "Throws if backup creation failed."
        self._backend.await_backup_completion()

    def export_collection_package(
        self, out_path: str, include_media: bool, legacy: bool
    ) -> None:
        self.close_for_full_sync()
        self._backend.export_collection_package(
            out_path=out_path, include_media=include_media, legacy=legacy
        )

    def import_anki_package(
        self, request: ImportAnkiPackageRequest
    ) -> ImportLogWithChanges:
        log = self._backend.import_anki_package_raw(request.SerializeToString())
        return ImportLogWithChanges.FromString(log)

    def export_anki_package(
        self, *, out_path: str, options: ExportAnkiPackageOptions, limit: ExportLimit
    ) -> int:
        return self._backend.export_anki_package(
            out_path=out_path,
            options=options,
            limit=pb_export_limit(limit),
        )

    def get_csv_metadata(self, path: str, delimiter: Delimiter.V | None) -> CsvMetadata:
        request = import_export_pb2.CsvMetadataRequest(path=path, delimiter=delimiter)
        return self._backend.get_csv_metadata(request)

    def import_csv(self, request: ImportCsvRequest) -> ImportLogWithChanges:
        log = self._backend.import_csv_raw(request.SerializeToString())
        return ImportLogWithChanges.FromString(log)

    def export_note_csv(
        self,
        *,
        out_path: str,
        limit: ExportLimit,
        with_html: bool,
        with_tags: bool,
        with_deck: bool,
        with_notetype: bool,
        with_guid: bool,
    ) -> int:
        return self._backend.export_note_csv(
            out_path=out_path,
            with_html=with_html,
            with_tags=with_tags,
            with_deck=with_deck,
            with_notetype=with_notetype,
            with_guid=with_guid,
            limit=pb_export_limit(limit),
        )

    def export_card_csv(
        self,
        *,
        out_path: str,
        limit: ExportLimit,
        with_html: bool,
    ) -> int:
        return self._backend.export_card_csv(
            out_path=out_path,
            with_html=with_html,
            limit=pb_export_limit(limit),
        )

    def import_json_file(self, path: str) -> ImportLogWithChanges:
        return self._backend.import_json_file(path)

    def import_json_string(self, json: str) -> ImportLogWithChanges:
        return self._backend.import_json_string(json)

    def export_dataset_for_research(
        self, target_path: str, min_entries: int = 0
    ) -> None:
        self._backend.export_dataset(min_entries=min_entries, target_path=target_path)

    # Image Occlusion
    ##########################################################################

    def get_image_for_occlusion(self, path: str | None) -> GetImageForOcclusionResponse:
        return self._backend.get_image_for_occlusion(path=path)

    def add_image_occlusion_notetype(self) -> None:
        "Add notetype if missing."
        self._backend.add_image_occlusion_notetype()

    def add_image_occlusion_note(
        self,
        notetype_id: int,
        image_path: str,
        occlusions: str,
        header: str,
        back_extra: str,
        tags: list[str],
    ) -> OpChanges:
        return self._backend.add_image_occlusion_note(
            notetype_id=notetype_id,
            image_path=image_path,
            occlusions=occlusions,
            header=header,
            back_extra=back_extra,
            tags=tags,
        )

    def get_image_occlusion_note(
        self, note_id: int | None
    ) -> GetImageOcclusionNoteResponse:
        return self._backend.get_image_occlusion_note(note_id=note_id)

    def update_image_occlusion_note(
        self,
        note_id: int | None,
        occlusions: str | None,
        header: str | None,
        back_extra: str | None,
        tags: list[str] | None,
    ) -> OpChanges:
        return self._backend.update_image_occlusion_note(
            note_id=note_id,
            occlusions=occlusions,
            header=header,
            back_extra=back_extra,
            tags=tags,
        )

    # Object helpers
    ##########################################################################

    def get_card(self, id: CardId | None) -> Card:
        return Card(self, id)

    def update_cards(
        self, cards: Sequence[Card], skip_undo_entry: bool = False
    ) -> OpChanges:
        """Save card changes to database."""
        return self._backend.update_cards(
            cards=[c._to_backend_card() for c in cards], skip_undo_entry=skip_undo_entry
        )

    def update_card(self, card: Card, skip_undo_entry: bool = False) -> OpChanges:
        """Save card changes to database."""
        return self.update_cards([card], skip_undo_entry=skip_undo_entry)

    def get_note(self, id: NoteId) -> Note:
        return Note(self, id=id)

    def update_notes(
        self, notes: Sequence[Note], skip_undo_entry: bool = False
    ) -> OpChanges:
        """Save note changes to database."""
        return self._backend.update_notes(
            notes=[n._to_backend_note() for n in notes], skip_undo_entry=skip_undo_entry
        )

    def update_note(self, note: Note, skip_undo_entry: bool = False) -> OpChanges:
        """Save note changes to database."""
        return self.update_notes([note], skip_undo_entry=skip_undo_entry)

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

    @deprecated(info="no longer required")
    def reset(self) -> None:
        pass

    # Notes
    ##########################################################################

    def new_note(self, notetype: NotetypeDict) -> Note:
        return Note(self, notetype)

    def add_note(self, note: Note, deck_id: DeckId) -> OpChanges:
        hooks.note_will_be_added(self, note, deck_id)
        out = self._backend.add_note(note=note._to_backend_note(), deck_id=deck_id)
        note.id = NoteId(out.note_id)
        return out.changes

    def add_notes(self, requests: Iterable[AddNoteRequest]) -> OpChanges:
        for request in requests:
            hooks.note_will_be_added(self, request.note, request.deck_id)
        out = self._backend.add_notes(
            requests=[
                notes_pb2.AddNoteRequest(
                    note=request.note._to_backend_note(), deck_id=request.deck_id
                )
                for request in requests
            ]
        )
        for idx, request in enumerate(requests):
            request.note.id = NoteId(out.nids[idx])

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

    def remove_cards_and_orphaned_notes(
        self, card_ids: Sequence[CardId]
    ) -> OpChangesWithCount:
        "You probably want .remove_notes_by_card() instead."
        return self._backend.remove_cards(card_ids=card_ids)

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
        or browser.table._model.columns and support sorting cards unless column.sorting_cards
        is set to BrowserColumns.SORTING_NONE, .SORTING_NOTES_ASCENDING, or
        .SORTING_NOTES_DESCENDING.

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
        if (
            isinstance(order, BrowserColumns.Column)
            and (order.sorting_notes if finding_notes else order.sorting_cards)
            is not BrowserColumns.SORTING_NONE
        ):
            return search_pb2.SortOrder(
                builtin=search_pb2.SortOrder.Builtin(column=order.key, reverse=reverse)
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

    def browser_row_for_id(self, id_: int) -> tuple[
        Generator[tuple[str, bool, BrowserRow.Cell.TextElideMode.V], None, None],
        BrowserRow.Color.V,
        str,
        int,
    ]:
        row = self._backend.browser_row_for_id(id_)
        return (
            ((cell.text, cell.is_rtl, cell.elide_mode) for cell in row.cells),
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

    def get_config(self, key: str, default: Any | None = None) -> Any:
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
        self, id: NotetypeId, key: str, default: Any | None = None
    ) -> Any:
        key = self._backend.get_aux_notetype_config_key(id=id, key=key)
        return self.get_config(key, default=default)

    def set_aux_notetype_config(
        self, id: NotetypeId, key: str, value: Any, *, undoable: bool = False
    ) -> OpChanges:
        key = self._backend.get_aux_notetype_config_key(id=id, key=key)
        return self.set_config(key, value, undoable=undoable)

    def get_aux_template_config(
        self, id: NotetypeId, card_ordinal: int, key: str, default: Any | None = None
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

    def _get_enable_load_balancer(self) -> bool:
        return self.get_config_bool(Config.Bool.LOAD_BALANCER_ENABLED)

    def _set_enable_load_balancer(self, value: bool) -> None:
        self.set_config_bool(Config.Bool.LOAD_BALANCER_ENABLED, value)

    load_balancer_enabled = property(
        fget=_get_enable_load_balancer, fset=_set_enable_load_balancer
    )

    def _get_enable_fsrs_short_term_with_steps(self) -> bool:
        return self.get_config_bool(Config.Bool.FSRS_SHORT_TERM_WITH_STEPS_ENABLED)

    def _set_enable_fsrs_short_term_with_steps(self, value: bool) -> None:
        self.set_config_bool(Config.Bool.FSRS_SHORT_TERM_WITH_STEPS_ENABLED, value)

    fsrs_short_term_with_steps_enabled = property(
        fget=_get_enable_fsrs_short_term_with_steps,
        fset=_set_enable_fsrs_short_term_with_steps,
    )
    # Stats
    ##########################################################################

    def stats(self) -> anki.stats.CollectionStats:
        from anki.stats import CollectionStats

        return CollectionStats(self)

    def card_stats_data(self, card_id: CardId) -> stats_pb2.CardStatsResponse:
        """Returns the data required to show card stats.

        If you wish to display the stats in a HTML table like Anki does,
        you can use the .js file directly - see this add-on for an example:
        https://ankiweb.net/shared/info/2179254157
        """
        return self._backend.card_stats(card_id)

    def get_review_logs(
        self, card_id: CardId
    ) -> Sequence[stats_pb2.CardStatsResponse.StatsRevlogEntry]:
        return self._backend.get_review_logs(card_id)

    def studied_today(self) -> str:
        return self._backend.studied_today()

    # Undo
    ##########################################################################

    def undo_status(self) -> UndoStatus:
        "Return the undo status."
        return self._check_backend_undo_status() or UndoStatus()

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

    def undo(self) -> OpChangesAfterUndo:
        """Returns result of backend undo operation, or throws UndoEmpty."""
        out = self._backend.undo()
        if out.changes.notetype:
            self.models._clear_cache()
        return out

    def redo(self) -> OpChangesAfterUndo:
        """Returns result of backend redo operation, or throws UndoEmpty."""
        out = self._backend.redo()
        if out.changes.notetype:
            self.models._clear_cache()
        return out

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
            return status
        else:
            return None

    # DB maintenance
    ##########################################################################

    def fix_integrity(self) -> tuple[str, bool]:
        """Fix possible problems and rebuild caches.

        Returns tuple of (error: str, ok: bool). 'ok' will be true if no
        problems were found.
        """
        try:
            problems = list(self._backend.check_database())
            ok = not problems
            problems.append(self.tr.database_check_rebuilt())
        except DBError as err:
            problems = [str(err)]
            ok = False
        return ("\n".join(problems), ok)

    def optimize(self) -> None:
        self.db.execute("vacuum")
        self.db.execute("analyze")

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

    def full_upload_or_download(
        self, *, auth: SyncAuth, server_usn: int | None, upload: bool
    ) -> None:
        self._backend.full_upload_or_download(
            sync_pb2.FullUploadOrDownloadRequest(
                auth=auth, server_usn=server_usn, upload=upload
            )
        )

    def sync_login(
        self, username: str, password: str, endpoint: str | None
    ) -> SyncAuth:
        return self._backend.sync_login(
            SyncLoginRequest(username=username, password=password, endpoint=endpoint)
        )

    def sync_collection(self, auth: SyncAuth, sync_media: bool) -> SyncOutput:
        return self._backend.sync_collection(auth=auth, sync_media=sync_media)

    def sync_media(self, auth: SyncAuth) -> None:
        self._backend.sync_media(auth)

    def sync_status(self, auth: SyncAuth) -> SyncStatus:
        return self._backend.sync_status(auth)

    def media_sync_status(self) -> MediaSyncStatus:
        "This will throw if the sync failed with an error."
        return self._backend.media_sync_status()

    def ankihub_login(self, id: str, password: str) -> str:
        return self._backend.ankihub_login(id=id, password=password)

    def ankihub_logout(self, token: str) -> None:
        self._backend.ankihub_logout(token=token)

    def get_preferences(self) -> Preferences:
        return self._backend.get_preferences()

    def set_preferences(self, prefs: Preferences) -> OpChanges:
        return self._backend.set_preferences(prefs)

    def render_markdown(self, text: str, sanitize: bool = True) -> str:
        "Not intended for public consumption at this time."
        return self._backend.render_markdown(markdown=text, sanitize=sanitize)

    def compare_answer(
        self, expected: str, provided: str, combining: bool = True
    ) -> str:
        return self._backend.compare_answer(
            expected=expected, provided=provided, combining=combining
        )

    def extract_cloze_for_typing(self, text: str, ordinal: int) -> str:
        return self._backend.extract_cloze_for_typing(text=text, ordinal=ordinal)

    def compute_memory_state(self, card_id: CardId) -> ComputedMemoryState:
        resp = self._backend.compute_memory_state(card_id)
        if resp.HasField("state"):
            return ComputedMemoryState(
                desired_retention=resp.desired_retention,
                stability=resp.state.stability,
                difficulty=resp.state.difficulty,
            )
        else:
            return ComputedMemoryState(desired_retention=resp.desired_retention)

    def fuzz_delta(self, card_id: CardId, interval: int) -> int:
        "The delta days of fuzz applied if reviewing the card in v3."
        return self._backend.fuzz_delta(card_id=card_id, interval=interval)

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
    findReplace=Collection.find_and_replace,
    remCards=Collection.remove_cards_and_orphaned_notes,
)


# legacy name
_Collection = Collection


def pb_export_limit(limit: ExportLimit) -> import_export_pb2.ExportLimit:
    message = import_export_pb2.ExportLimit()
    if isinstance(limit, DeckIdLimit):
        message.deck_id = limit.deck_id
    elif isinstance(limit, NoteIdsLimit):
        message.note_ids.note_ids.extend(limit.note_ids)
    elif isinstance(limit, CardIdsLimit):
        message.card_ids.cids.extend(limit.card_ids)
    else:
        message.whole_collection.SetInParent()
    return message
