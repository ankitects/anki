# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import copy
from typing import TYPE_CHECKING, Any, Iterable, NewType, Sequence, no_type_check

if TYPE_CHECKING:
    import anki

import anki.cards
import anki.collection
from anki import deckconfig_pb2, decks_pb2
from anki._legacy import DeprecatedNamesMixin, deprecated, print_deprecation_warning
from anki.collection import OpChanges, OpChangesWithCount, OpChangesWithId
from anki.consts import *
from anki.errors import NotFoundError
from anki.utils import from_json_bytes, ids2str, int_time, to_json_bytes

# public exports
DeckTreeNode = decks_pb2.DeckTreeNode
DeckNameId = decks_pb2.DeckNameId
FilteredDeckConfig = decks_pb2.Deck.Filtered
DeckCollapseScope = decks_pb2.SetDeckCollapsedRequest.Scope
DeckConfigsForUpdate = deckconfig_pb2.DeckConfigsForUpdate
UpdateDeckConfigs = deckconfig_pb2.UpdateDeckConfigsRequest

# type aliases until we can move away from dicts
DeckDict = dict[str, Any]
DeckConfigDict = dict[str, Any]

DeckId = NewType("DeckId", int)
DeckConfigId = NewType("DeckConfigId", int)

DEFAULT_DECK_ID = DeckId(1)
DEFAULT_DECK_CONF_ID = DeckConfigId(1)


class DecksDictProxy:
    def __init__(self, col: anki.collection.Collection):
        self._col = col.weakref()

    def _warn(self) -> None:
        print_deprecation_warning(
            "add-on should use methods on col.decks, not col.decks.decks dict"
        )

    def __getitem__(self, item: Any) -> Any:
        self._warn()
        return self._col.decks.get(DeckId(int(item)))

    def __setitem__(self, key: Any, val: Any) -> None:
        self._warn()
        self._col.decks.save(val)

    def __len__(self) -> int:
        self._warn()
        return len(self._col.decks.all_names_and_ids())

    def keys(self) -> Any:
        self._warn()
        return [str(nt.id) for nt in self._col.decks.all_names_and_ids()]

    def values(self) -> Any:
        self._warn()
        return self._col.decks.all()

    def items(self) -> Any:
        self._warn()
        return [(str(nt["id"]), nt) for nt in self._col.decks.all()]

    def __contains__(self, item: Any) -> bool:
        self._warn()
        return self._col.decks.have(item)


class DeckManager(DeprecatedNamesMixin):
    # Registry save/load
    #############################################################

    def __init__(self, col: anki.collection.Collection) -> None:
        self.col = col.weakref()
        self.decks = DecksDictProxy(col)

    def save(self, deck_or_config: DeckDict | DeckConfigDict = None) -> None:
        "Can be called with either a deck or a deck configuration."
        if not deck_or_config:
            print("col.decks.save() should be passed the changed deck")
            return

        # deck conf?
        if "maxTaken" in deck_or_config:
            self.update_config(deck_or_config)
            return
        else:
            self.update(deck_or_config, preserve_usn=False)

    # Deck save/load
    #############################################################

    def add_normal_deck_with_name(self, name: str) -> OpChangesWithId:
        "If deck exists, return existing id."
        if id := self.col.decks.id_for_name(name):
            return OpChangesWithId(id=id)
        else:
            deck = self.col.decks.new_deck_legacy(filtered=False)
            deck["name"] = name
            return self.add_deck_legacy(deck)

    def add_deck_legacy(self, deck: DeckDict) -> OpChangesWithId:
        "Add a deck created with new_deck_legacy(). Must have id of 0."
        if not deck["id"] == 0:
            raise Exception("id should be 0")
        return self.col._backend.add_deck_legacy(to_json_bytes(deck))

    def id(
        self,
        name: str,
        create: bool = True,
        type: DeckConfigId = DeckConfigId(0),
    ) -> DeckId | None:
        "Add a deck with NAME. Reuse deck if already exists. Return id as int."
        id = self.id_for_name(name)
        if id:
            return id
        elif not create:
            return None

        deck = self.new_deck_legacy(bool(type))
        deck["name"] = name
        out = self.add_deck_legacy(deck)
        return DeckId(out.id)

    def remove(self, dids: Sequence[DeckId]) -> OpChangesWithCount:
        return self.col._backend.remove_decks(dids)

    def all_names_and_ids(
        self, skip_empty_default: bool = False, include_filtered: bool = True
    ) -> Sequence[DeckNameId]:
        "A sorted sequence of deck names and IDs."
        return self.col._backend.get_deck_names(
            skip_empty_default=skip_empty_default, include_filtered=include_filtered
        )

    def id_for_name(self, name: str) -> DeckId | None:
        try:
            return DeckId(self.col._backend.get_deck_id_by_name(name))
        except NotFoundError:
            return None

    def get_legacy(self, did: DeckId) -> DeckDict | None:
        try:
            return from_json_bytes(self.col._backend.get_deck_legacy(did))
        except NotFoundError:
            return None

    def have(self, id: DeckId) -> bool:
        return bool(self.get_legacy(id))

    def get_all_legacy(self) -> list[DeckDict]:
        return list(from_json_bytes(self.col._backend.get_all_decks_legacy()).values())

    def new_deck_legacy(self, filtered: bool) -> DeckDict:
        deck = from_json_bytes(self.col._backend.new_deck_legacy(filtered))
        if deck["dyn"]:
            # Filtered decks are now created via a scheduler method, but old unit
            # tests still use this method. Set the default values to what the tests
            # expect: one empty search term, and ordering by oldest first.
            del deck["terms"][1]
            deck["terms"][0][0] = ""
            deck["terms"][0][2] = 0

        return deck

    def deck_tree(self) -> DeckTreeNode:
        return self.col._backend.deck_tree(now=0)

    @classmethod
    def find_deck_in_tree(
        cls, node: DeckTreeNode, deck_id: DeckId
    ) -> DeckTreeNode | None:
        if node.deck_id == deck_id:
            return node
        for child in node.children:
            match = cls.find_deck_in_tree(child, deck_id)
            if match:
                return match
        return None

    def all(self) -> list[DeckDict]:
        "All decks. Expensive; prefer all_names_and_ids()"
        return self.get_all_legacy()

    def set_collapsed(
        self, deck_id: DeckId, collapsed: bool, scope: DeckCollapseScope.V
    ) -> OpChanges:
        return self.col._backend.set_deck_collapsed(
            deck_id=deck_id, collapsed=collapsed, scope=scope
        )

    def collapse(self, did: DeckId) -> None:
        deck = self.get(did)
        deck["collapsed"] = not deck["collapsed"]
        self.save(deck)

    def collapse_browser(self, did: DeckId) -> None:
        deck = self.get(did)
        collapsed = deck.get("browserCollapsed", False)
        deck["browserCollapsed"] = not collapsed
        self.save(deck)

    def count(self) -> int:
        return len(self.all_names_and_ids())

    def card_count(
        self, dids: DeckId | Iterable[DeckId], include_subdecks: bool
    ) -> Any:
        if isinstance(dids, int):
            dids = {dids}
        else:
            dids = set(dids)
        if include_subdecks:
            dids.update([child[1] for did in dids for child in self.children(did)])
        str_ids = ids2str(dids)
        count = self.col.db.scalar(
            f"select count() from cards where did in {str_ids} or odid in {str_ids}"
        )
        return count

    def get(self, did: DeckId | str, default: bool = True) -> DeckDict | None:
        if not did:
            if default:
                return self.get_legacy(DEFAULT_DECK_ID)
            else:
                return None
        id = DeckId(int(did))
        deck = self.get_legacy(id)
        if deck:
            return deck
        elif default:
            return self.get_legacy(DEFAULT_DECK_ID)
        else:
            return None

    def by_name(self, name: str) -> DeckDict | None:
        """Get deck with NAME, ignoring case."""
        id = self.id_for_name(name)
        if id:
            return self.get_legacy(id)
        return None

    def update(self, deck: DeckDict, preserve_usn: bool = True) -> None:
        "Add or update an existing deck. Used for syncing and merging."
        deck["id"] = self.col._backend.add_or_update_deck_legacy(
            deck=to_json_bytes(deck), preserve_usn_and_mtime=preserve_usn
        )

    def update_dict(self, deck: DeckDict) -> OpChanges:
        return self.col._backend.update_deck_legacy(json=to_json_bytes(deck))

    def rename(self, deck: DeckDict | DeckId, new_name: str) -> OpChanges:
        "Rename deck prefix to NAME if not exists. Updates children."
        if isinstance(deck, int):
            deck_id = deck
        else:
            deck_id = deck["id"]
        return self.col._backend.rename_deck(deck_id=deck_id, new_name=new_name)

    # Drag/drop
    #############################################################

    def reparent(
        self, deck_ids: Sequence[DeckId], new_parent: DeckId
    ) -> OpChangesWithCount:
        """Rename one or more source decks that were dropped on `new_parent`.
        If new_parent is 0, decks will be placed at the top level."""
        return self.col._backend.reparent_decks(
            deck_ids=deck_ids, new_parent=new_parent
        )

    # Deck configurations
    #############################################################

    def get_deck_configs_for_update(self, deck_id: DeckId) -> DeckConfigsForUpdate:
        return self.col._backend.get_deck_configs_for_update(deck_id)

    def update_deck_configs(self, input: UpdateDeckConfigs) -> OpChanges:
        op_bytes = self.col._backend.update_deck_configs_raw(input.SerializeToString())
        return OpChanges.FromString(op_bytes)

    def all_config(self) -> list[DeckConfigDict]:
        "A list of all deck config."
        return list(from_json_bytes(self.col._backend.all_deck_config_legacy()))

    def config_dict_for_deck_id(self, did: DeckId) -> DeckConfigDict:
        deck = self.get(did, default=False)
        assert deck
        if "conf" in deck:
            dcid = DeckConfigId(int(deck["conf"]))  # may be a string
            conf = self.get_config(dcid)
            if not conf:
                # fall back on default
                conf = self.get_config(DEFAULT_DECK_CONF_ID)
            conf["dyn"] = False
            return conf
        # dynamic decks have embedded conf
        return deck

    def get_config(self, conf_id: DeckConfigId) -> DeckConfigDict | None:
        try:
            return from_json_bytes(self.col._backend.get_deck_config_legacy(conf_id))
        except NotFoundError:
            return None

    def update_config(self, conf: DeckConfigDict, preserve_usn: bool = False) -> None:
        "preserve_usn is ignored"
        conf["id"] = self.col._backend.add_or_update_deck_config_legacy(
            json=to_json_bytes(conf)
        )

    def add_config(
        self, name: str, clone_from: DeckConfigDict | None = None
    ) -> DeckConfigDict:
        if clone_from is not None:
            conf = copy.deepcopy(clone_from)
            conf["id"] = 0
        else:
            conf = from_json_bytes(self.col._backend.new_deck_config_legacy())
        conf["name"] = name
        self.update_config(conf)
        return conf

    def add_config_returning_id(
        self, name: str, clone_from: DeckConfigDict | None = None
    ) -> DeckConfigId:
        return self.add_config(name, clone_from)["id"]

    def remove_config(self, id: DeckConfigId) -> None:
        "Remove a configuration and update all decks using it."
        self.col.mod_schema(check=True)
        for deck in self.all():
            # ignore cram decks
            if "conf" not in deck:
                continue
            if str(deck["conf"]) == str(id):
                deck["conf"] = 1
                self.save(deck)
        self.col._backend.remove_deck_config(id)

    def set_config_id_for_deck_dict(self, deck: DeckDict, id: DeckConfigId) -> None:
        deck["conf"] = id
        self.save(deck)

    def decks_using_config(self, conf: DeckConfigDict) -> list[DeckId]:
        dids = []
        for deck in self.all():
            if "conf" in deck and deck["conf"] == conf["id"]:
                dids.append(deck["id"])
        return dids

    def restore_to_default(self, conf: DeckConfigDict) -> None:
        old_order = conf["new"]["order"]
        new = from_json_bytes(self.col._backend.new_deck_config_legacy())
        new["id"] = conf["id"]
        new["name"] = conf["name"]
        self.update_config(new)
        # if it was previously randomized, re-sort
        if not old_order:
            self.col.sched.resort_conf(new)

    # Deck utils
    #############################################################

    def name(self, did: DeckId, default: bool = False) -> str:
        deck = self.get(did, default=default)
        if deck:
            return deck["name"]
        return self.col.tr.decks_no_deck()

    def name_if_exists(self, did: DeckId) -> str | None:
        deck = self.get(did, default=False)
        if deck:
            return deck["name"]
        return None

    def cids(self, did: DeckId, children: bool = False) -> list[anki.cards.CardId]:
        if not children:
            return self.col.db.list("select id from cards where did=?", did)
        dids = [did]
        for name, id in self.children(did):
            dids.append(id)
        return self.col.db.list(f"select id from cards where did in {ids2str(dids)}")

    def for_card_ids(self, cids: list[anki.cards.CardId]) -> list[DeckId]:
        return self.col.db.list(f"select did from cards where id in {ids2str(cids)}")

    # Deck selection
    #############################################################

    def set_current(self, deck: DeckId) -> OpChanges:
        return self.col._backend.set_current_deck(deck)

    def get_current_id(self) -> DeckId:
        "The currently selected deck ID."
        return DeckId(self.col._backend.get_current_deck().id)

    def current(self) -> DeckDict:
        return self.get(self.selected())

    def active(self) -> list[DeckId]:
        # some add-ons assume this will always be non-empty
        return self.col.sched.active_decks or [DeckId(1)]

    def select(self, did: DeckId) -> None:
        # make sure arg is an int; legacy callers may be passing in a string
        did = DeckId(did)
        self.set_current(did)
        self.col.reset()

    selected = get_current_id

    # Parents/children
    #############################################################

    @staticmethod
    def path(name: str) -> list[str]:
        return name.split("::")

    @classmethod
    def basename(cls, name: str) -> str:
        return cls.path(name)[-1]

    @classmethod
    def immediate_parent_path(cls, name: str) -> list[str]:
        return cls.path(name)[:-1]

    @classmethod
    def immediate_parent(cls, name: str) -> str | None:
        parent_path = cls.immediate_parent_path(name)
        if parent_path:
            return "::".join(parent_path)
        return None

    @classmethod
    def key(cls, deck: DeckDict) -> list[str]:
        return cls.path(deck["name"])

    def deck_and_child_name_ids(self, deck_id: DeckId) -> Iterable[tuple[str, DeckId]]:
        """The deck of did and all its children, as (name, id)."""
        return (
            (entry.name, DeckId(entry.id))
            for entry in self.col._backend.get_deck_and_child_names(deck_id)
        )

    def children(self, did: DeckId) -> list[tuple[str, DeckId]]:
        "All children of did, as (name, id)."
        return [
            name_id
            for name_id in self.deck_and_child_name_ids(did)
            if name_id[1] != did
        ]

    def child_ids(self, parent_name: str) -> Iterable[DeckId]:
        if not (parent_id := self.id_for_name(parent_name)):
            return []
        return (name_id[1] for name_id in self.children(parent_id))

    def deck_and_child_ids(self, deck_id: DeckId) -> list[DeckId]:
        return [
            DeckId(entry.id)
            for entry in self.col._backend.get_deck_and_child_names(deck_id)
        ]

    def parents(
        self, did: DeckId, name_map: dict[str, DeckDict] | None = None
    ) -> list[DeckDict]:
        "All parents of did."
        # get parent and grandparent names
        parents_names: list[str] = []
        for part in self.immediate_parent_path(self.get(did)["name"]):
            if not parents_names:
                parents_names.append(part)
            else:
                parents_names.append(f"{parents_names[-1]}::{part}")
        parents: list[DeckDict] = []
        # convert to objects
        for parent_name in parents_names:
            if name_map:
                deck = name_map[parent_name]
            else:
                deck = self.get(self.id(parent_name))
            parents.append(deck)
        return parents

    def parents_by_name(self, name: str) -> list[DeckDict]:
        "All existing parents of name"
        if "::" not in name:
            return []
        names = self.immediate_parent_path(name)
        head = []
        parents: list[DeckDict] = []

        while names:
            head.append(names.pop(0))
            deck = self.by_name("::".join(head))
            if deck:
                parents.append(deck)

        return parents

    # Filtered decks
    ##########################################################################

    def new_filtered(self, name: str) -> DeckId:
        "For new code, prefer col.sched.get_or_create_filtered_deck()."
        did = self.id(name, type=DEFAULT_DECK_CONF_ID)
        self.select(did)
        return did

    def is_filtered(self, did: DeckId | str) -> bool:
        return bool(self.get(did)["dyn"])

    # Legacy
    #############

    @deprecated(info="no longer required")
    def flush(self) -> None:
        pass

    @deprecated(replaced_by=remove)
    def rem(
        self,
        did: DeckId,
        **legacy_args: bool,
    ) -> None:
        "Remove the deck. If cardsToo, delete any cards inside."
        if isinstance(did, str):
            did = int(did)
        self.remove([did])

    @deprecated(replaced_by=all_names_and_ids)
    def name_map(self) -> dict[str, DeckDict]:
        return {d["name"]: d for d in self.all()}

    @deprecated(info="use col.set_deck() instead")
    def set_deck(self, cids: list[anki.cards.CardId], did: DeckId) -> None:
        self.col.set_deck(card_ids=cids, deck_id=did)
        self.col.db.execute(
            f"update cards set did=?,usn=?,mod=? where id in {ids2str(cids)}",
            did,
            self.col.usn(),
            int_time(),
        )

    @deprecated(replaced_by=all_names_and_ids)
    def all_ids(self) -> list[str]:
        return [str(x.id) for x in self.all_names_and_ids()]

    @deprecated(replaced_by=all_names_and_ids)
    def all_names(self, dyn: bool = True, force_default: bool = True) -> list[str]:
        return [
            x.name
            for x in self.all_names_and_ids(
                skip_empty_default=not force_default, include_filtered=dyn
            )
        ]


DeckManager.register_deprecated_aliases(
    confForDid=DeckManager.config_dict_for_deck_id,
    setConf=DeckManager.set_config_id_for_deck_dict,
    didsForConf=DeckManager.decks_using_config,
    allConf=DeckManager.all_config,
    getConf=DeckManager.get_config,
    updateConf=DeckManager.update_config,
    remConf=DeckManager.remove_config,
    confId=DeckManager.add_config_returning_id,
    newDyn=DeckManager.new_filtered,
    isDyn=DeckManager.is_filtered,
    nameOrNone=DeckManager.name_if_exists,
)


@no_type_check
def __getattr__(name):
    if name == "defaultDeck":
        print_deprecation_warning(
            "defaultDeck is deprecated; call decks.id() without it"
        )
        return 0
    elif name == "defaultDynamicDeck":
        print_deprecation_warning("defaultDynamicDeck is replaced with new_filtered()")
        return 1
    else:
        raise AttributeError(f"module {__name__} has no attribute {name}")
