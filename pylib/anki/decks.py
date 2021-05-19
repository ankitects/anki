# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import copy
import pprint
import sys
import traceback
from typing import Any, Dict, Iterable, List, NewType, Optional, Sequence, Tuple, Union

import anki  # pylint: disable=unused-import
import anki._backend.backend_pb2 as _pb
from anki.cards import CardId
from anki.collection import OpChanges, OpChangesWithCount, OpChangesWithId
from anki.consts import *
from anki.errors import NotFoundError
from anki.utils import from_json_bytes, ids2str, intTime, legacy_func, to_json_bytes

# public exports
DeckTreeNode = _pb.DeckTreeNode
DeckNameId = _pb.DeckNameId
FilteredDeckConfig = _pb.Deck.Filtered
DeckCollapseScope = _pb.SetDeckCollapsedIn.Scope
DeckConfigsForUpdate = _pb.DeckConfigsForUpdate
UpdateDeckConfigs = _pb.UpdateDeckConfigsIn

# legacy code may pass this in as the type argument to .id()
defaultDeck = 0
defaultDynamicDeck = 1

# type aliases until we can move away from dicts
DeckDict = Dict[str, Any]
DeckConfigDict = Dict[str, Any]

# currently only supports read-only access
Deck = _pb.Deck
DeckConfig = _pb.DeckConfig

DeckId = NewType("DeckId", int)
DeckConfigId = NewType("DeckConfigId", int)

DEFAULT_DECK_ID = DeckId(1)
DEFAULT_DECK_CONF_ID = DeckConfigId(1)


class DecksDictProxy:
    def __init__(self, col: anki.collection.Collection):
        self._col = col.weakref()

    def _warn(self) -> None:
        traceback.print_stack(file=sys.stdout)
        print("add-on should use methods on col.decks, not col.decks.decks dict")

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


class DeckManager:
    # Registry save/load
    #############################################################

    def __init__(self, col: anki.collection.Collection) -> None:
        self.col = col.weakref()
        self.decks = DecksDictProxy(col)

    def save(self, g: Union[DeckDict, DeckConfigDict] = None) -> None:
        "Can be called with either a deck or a deck configuration."
        if not g:
            print("col.decks.save() should be passed the changed deck")
            return

        # deck conf?
        if "maxTaken" in g:
            self.update_config(g)
            return
        else:
            self.update(g, preserve_usn=False)

    # legacy
    def flush(self) -> None:
        pass

    def __repr__(self) -> str:
        d = dict(self.__dict__)
        del d["col"]
        return f"{super().__repr__()} {pprint.pformat(d, width=300)}"

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
        assert deck["id"] == 0
        return self.col._backend.add_deck_legacy(to_json_bytes(deck))

    def id(
        self,
        name: str,
        create: bool = True,
        type: DeckConfigId = DeckConfigId(0),
    ) -> Optional[DeckId]:
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

    @legacy_func(sub="remove")
    def rem(self, did: DeckId, cardsToo: bool = True, childrenToo: bool = True) -> None:
        "Remove the deck. If cardsToo, delete any cards inside."
        if isinstance(did, str):
            did = int(did)
        assert cardsToo and childrenToo
        self.remove([did])

    def remove(self, dids: Sequence[DeckId]) -> OpChangesWithCount:
        return self.col._backend.remove_decks(dids)

    def all_names_and_ids(
        self, skip_empty_default: bool = False, include_filtered: bool = True
    ) -> Sequence[DeckNameId]:
        "A sorted sequence of deck names and IDs."
        return self.col._backend.get_deck_names(
            skip_empty_default=skip_empty_default, include_filtered=include_filtered
        )

    def id_for_name(self, name: str) -> Optional[DeckId]:
        try:
            return DeckId(self.col._backend.get_deck_id_by_name(name))
        except NotFoundError:
            return None

    def get_legacy(self, did: DeckId) -> Optional[DeckDict]:
        try:
            return from_json_bytes(self.col._backend.get_deck_legacy(did))
        except NotFoundError:
            return None

    def have(self, id: DeckId) -> bool:
        return not self.get_legacy(id)

    def get_all_legacy(self) -> List[DeckDict]:
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
        return self.col._backend.deck_tree(top_deck_id=0, now=0)

    @classmethod
    def find_deck_in_tree(
        cls, node: DeckTreeNode, deck_id: DeckId
    ) -> Optional[DeckTreeNode]:
        if node.deck_id == deck_id:
            return node
        for child in node.children:
            match = cls.find_deck_in_tree(child, deck_id)
            if match:
                return match
        return None

    def all(self) -> List[DeckDict]:
        "All decks. Expensive; prefer all_names_and_ids()"
        return self.get_all_legacy()

    def allIds(self) -> List[str]:
        print("decks.allIds() is deprecated, use .all_names_and_ids()")
        return [str(x.id) for x in self.all_names_and_ids()]

    def allNames(self, dyn: bool = True, force_default: bool = True) -> List[str]:
        print("decks.allNames() is deprecated, use .all_names_and_ids()")
        return [
            x.name
            for x in self.all_names_and_ids(
                skip_empty_default=not force_default, include_filtered=dyn
            )
        ]

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

    def collapseBrowser(self, did: DeckId) -> None:
        deck = self.get(did)
        collapsed = deck.get("browserCollapsed", False)
        deck["browserCollapsed"] = not collapsed
        self.save(deck)

    def count(self) -> int:
        return len(self.all_names_and_ids())

    def card_count(
        self, dids: Union[DeckId, Iterable[DeckId]], include_subdecks: bool
    ) -> Any:
        if isinstance(dids, int):
            dids = {dids}
        else:
            dids = set(dids)
        if include_subdecks:
            dids.update([child[1] for did in dids for child in self.children(did)])
        count = self.col.db.scalar(
            "select count() from cards where did in {0} or "
            "odid in {0}".format(ids2str(dids))
        )
        return count

    def get(self, did: Union[DeckId, str], default: bool = True) -> Optional[DeckDict]:
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

    def byName(self, name: str) -> Optional[DeckDict]:
        """Get deck with NAME, ignoring case."""
        id = self.id_for_name(name)
        if id:
            return self.get_legacy(id)
        return None

    def update(self, g: DeckDict, preserve_usn: bool = True) -> None:
        "Add or update an existing deck. Used for syncing and merging."
        g["id"] = self.col._backend.add_or_update_deck_legacy(
            deck=to_json_bytes(g), preserve_usn_and_mtime=preserve_usn
        )

    def rename(self, deck: Union[DeckDict, DeckId], new_name: str) -> OpChanges:
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

    # legacy
    def renameForDragAndDrop(
        self,
        draggedDeckDid: Union[DeckId, str],
        ontoDeckDid: Optional[Union[DeckId, str]],
    ) -> None:
        if not ontoDeckDid:
            onto = 0
        else:
            onto = int(ontoDeckDid)
        self.reparent([DeckId(int(draggedDeckDid))], DeckId(onto))

    # Deck configurations
    #############################################################

    def get_deck_configs_for_update(self, deck_id: DeckId) -> DeckConfigsForUpdate:
        return self.col._backend.get_deck_configs_for_update(deck_id)

    def update_deck_configs(self, input: UpdateDeckConfigs) -> OpChanges:
        return self.col._backend.update_deck_configs(input=input)

    def all_config(self) -> List[DeckConfigDict]:
        "A list of all deck config."
        return list(from_json_bytes(self.col._backend.all_deck_config_legacy()))

    def confForDid(self, did: DeckId) -> DeckConfigDict:
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

    def get_config(self, conf_id: DeckConfigId) -> Optional[DeckConfigDict]:
        try:
            return from_json_bytes(self.col._backend.get_deck_config_legacy(conf_id))
        except NotFoundError:
            return None

    def update_config(self, conf: DeckConfigDict, preserve_usn: bool = False) -> None:
        conf["id"] = self.col._backend.add_or_update_deck_config_legacy(
            config=to_json_bytes(conf), preserve_usn_and_mtime=preserve_usn
        )

    def add_config(
        self, name: str, clone_from: Optional[DeckConfigDict] = None
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
        self, name: str, clone_from: Optional[DeckConfigDict] = None
    ) -> DeckConfigId:
        return self.add_config(name, clone_from)["id"]

    def remove_config(self, id: DeckConfigId) -> None:
        "Remove a configuration and update all decks using it."
        self.col.modSchema(check=True)
        for g in self.all():
            # ignore cram decks
            if "conf" not in g:
                continue
            if str(g["conf"]) == str(id):
                g["conf"] = 1
                self.save(g)
        self.col._backend.remove_deck_config(id)

    def setConf(self, grp: DeckConfigDict, id: DeckConfigId) -> None:
        grp["conf"] = id
        self.save(grp)

    def didsForConf(self, conf: DeckConfigDict) -> List[DeckId]:
        dids = []
        for deck in self.all():
            if "conf" in deck and deck["conf"] == conf["id"]:
                dids.append(deck["id"])
        return dids

    def restoreToDefault(self, conf: DeckConfigDict) -> None:
        oldOrder = conf["new"]["order"]
        new = from_json_bytes(self.col._backend.new_deck_config_legacy())
        new["id"] = conf["id"]
        new["name"] = conf["name"]
        self.update_config(new)
        # if it was previously randomized, re-sort
        if not oldOrder:
            self.col.sched.resortConf(new)

    # legacy
    allConf = all_config
    getConf = get_config
    updateConf = update_config
    remConf = remove_config
    confId = add_config_returning_id

    # Deck utils
    #############################################################

    def name(self, did: DeckId, default: bool = False) -> str:
        deck = self.get(did, default=default)
        if deck:
            return deck["name"]
        return self.col.tr.decks_no_deck()

    def name_if_exists(self, did: DeckId) -> Optional[str]:
        deck = self.get(did, default=False)
        if deck:
            return deck["name"]
        return None

    def setDeck(self, cids: List[CardId], did: DeckId) -> None:
        self.col.db.execute(
            f"update cards set did=?,usn=?,mod=? where id in {ids2str(cids)}",
            did,
            self.col.usn(),
            intTime(),
        )

    def cids(self, did: DeckId, children: bool = False) -> List[CardId]:
        if not children:
            return self.col.db.list("select id from cards where did=?", did)
        dids = [did]
        for name, id in self.children(did):
            dids.append(id)
        return self.col.db.list(f"select id from cards where did in {ids2str(dids)}")

    def for_card_ids(self, cids: List[CardId]) -> List[DeckId]:
        return self.col.db.list(f"select did from cards where id in {ids2str(cids)}")

    # Deck selection
    #############################################################

    def get_current(self) -> Deck:
        return self.col._backend.get_current_deck()

    def set_current(self, deck: DeckId) -> OpChanges:
        return self.col._backend.set_current_deck(deck)

    def get_current_id(self) -> DeckId:
        "The currently selected deck ID."
        return DeckId(self.get_current().id)

    # legacy

    def current(self) -> DeckDict:
        return self.get(self.selected())

    def select(self, did: DeckId) -> None:
        # make sure arg is an int; legacy callers may be passing in a string
        did = DeckId(did)
        self.set_current(did)
        self.col.reset()

    def active(self) -> List[DeckId]:
        # some add-ons assume this will always be non-empty
        return self.col.sched.active_decks or [DeckId(1)]

    selected = get_current_id

    # Parents/children
    #############################################################

    @staticmethod
    def path(name: str) -> List[str]:
        return name.split("::")

    _path = path

    @classmethod
    def basename(cls, name: str) -> str:
        return cls.path(name)[-1]

    _basename = basename

    @classmethod
    def immediate_parent_path(cls, name: str) -> List[str]:
        return cls._path(name)[:-1]

    @classmethod
    def immediate_parent(cls, name: str) -> Optional[str]:
        pp = cls.immediate_parent_path(name)
        if pp:
            return "::".join(pp)
        return None

    @classmethod
    def key(cls, deck: DeckDict) -> List[str]:
        return cls.path(deck["name"])

    def children(self, did: DeckId) -> List[Tuple[str, DeckId]]:
        "All children of did, as (name, id)."
        name = self.get(did)["name"]
        actv = []
        for g in self.all_names_and_ids():
            if g.name.startswith(f"{name}::"):
                actv.append((g.name, DeckId(g.id)))
        return actv

    def child_ids(self, parent_name: str) -> Iterable[DeckId]:
        prefix = f"{parent_name}::"
        return (
            DeckId(d.id) for d in self.all_names_and_ids() if d.name.startswith(prefix)
        )

    def deck_and_child_ids(self, deck_id: DeckId) -> List[DeckId]:
        parent_name = self.col.get_deck(deck_id).name
        out = [deck_id]
        out.extend(self.child_ids(parent_name))
        return out

    childMapNode = Dict[DeckId, Any]
    # Change to Dict[int, "DeckManager.childMapNode"] when MyPy allow recursive type

    def childDids(self, did: DeckId, childMap: DeckManager.childMapNode) -> List:
        def gather(node: DeckManager.childMapNode, arr: List) -> None:
            for did, child in node.items():
                arr.append(did)
                gather(child, arr)

        arr: List[int] = []
        gather(childMap[did], arr)
        return arr

    def childMap(self) -> DeckManager.childMapNode:
        nameMap = self.nameMap()
        childMap: DeckManager.childMapNode = {}

        # go through all decks, sorted by name
        for deck in sorted(self.all(), key=self.key):
            node: Dict[int, Any] = {}
            childMap[deck["id"]] = node

            # add note to immediate parent
            immediateParent = self.immediate_parent(deck["name"])
            if immediateParent is not None:
                pid = nameMap[immediateParent]["id"]
                childMap[pid][deck["id"]] = node

        return childMap

    def parents(
        self, did: DeckId, nameMap: Optional[Dict[str, DeckDict]] = None
    ) -> List[DeckDict]:
        "All parents of did."
        # get parent and grandparent names
        parents_names: List[str] = []
        for part in self.immediate_parent_path(self.get(did)["name"]):
            if not parents_names:
                parents_names.append(part)
            else:
                parents_names.append(f"{parents_names[-1]}::{part}")
        parents: List[DeckDict] = []
        # convert to objects
        for parent_name in parents_names:
            if nameMap:
                deck = nameMap[parent_name]
            else:
                deck = self.get(self.id(parent_name))
            parents.append(deck)
        return parents

    def parentsByName(self, name: str) -> List[DeckDict]:
        "All existing parents of name"
        if "::" not in name:
            return []
        names = self.immediate_parent_path(name)
        head = []
        parents: List[DeckDict] = []

        while names:
            head.append(names.pop(0))
            deck = self.byName("::".join(head))
            if deck:
                parents.append(deck)

        return parents

    def nameMap(self) -> Dict[str, DeckDict]:
        return {d["name"]: d for d in self.all()}

    # Filtered decks
    ##########################################################################

    def new_filtered(self, name: str) -> DeckId:
        "For new code, prefer col.sched.get_or_create_filtered_deck()."
        did = self.id(name, type=DEFAULT_DECK_CONF_ID)
        self.select(did)
        return did

    # 1 for dyn, 0 for standard
    def isDyn(self, did: Union[DeckId, str]) -> int:
        return self.get(did)["dyn"]

    # legacy

    newDyn = new_filtered
    nameOrNone = name_if_exists
