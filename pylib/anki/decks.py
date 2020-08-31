# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import copy
import pprint
from typing import Any, Dict, Iterable, List, Optional, Sequence, Tuple, Union

import anki  # pylint: disable=unused-import
import anki.backend_pb2 as pb
from anki.consts import *
from anki.errors import DeckRenameError
from anki.lang import _
from anki.rsbackend import DeckTreeNode, NotFoundError, from_json_bytes, to_json_bytes
from anki.utils import ids2str, intTime

# legacy code may pass this in as the type argument to .id()
defaultDeck = 0
defaultDynamicDeck = 1

NonFilteredDeck = Dict[str, Any]
FilteredDeck = Dict[str, Any]

"""Any kind of deck """
Deck = Union[NonFilteredDeck, FilteredDeck]

"""Configuration of standard deck, as seen from the deck picker's gear."""
Config = Dict[str, Any]

"""Configurationf of some deck, filtered deck for filtered deck, config for standard deck"""
DeckConfig = Union[FilteredDeck, Config]

""" New/lrn/rev conf, from deck config"""
QueueConfig = Dict[str, Any]


class DecksDictProxy:
    def __init__(self, col: anki.collection.Collection):
        self._col = col.weakref()

    def _warn(self):
        print("add-on should use methods on col.decks, not col.decks.decks dict")

    def __getitem__(self, item):
        self._warn()
        return self._col.decks.get(int(item))

    def __setitem__(self, key, val):
        self._warn()
        self._col.decks.save(val)

    def __len__(self):
        self._warn()
        return len(self._col.decks.all_names_and_ids())

    def keys(self):
        self._warn()
        return [str(nt.id) for nt in self._col.decks.all_names_and_ids()]

    def values(self):
        self._warn()
        return self._col.decks.all()

    def items(self):
        self._warn()
        return [(str(nt["id"]), nt) for nt in self._col.decks.all()]

    def __contains__(self, item):
        self._warn()
        self._col.decks.have(item)


class DeckManager:
    # Registry save/load
    #############################################################

    def __init__(self, col: anki.collection.Collection) -> None:
        self.col = col.weakref()
        self.decks = DecksDictProxy(col)

    def save(self, g: Union[Deck, Config] = None) -> None:
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
    def flush(self):
        pass

    def __repr__(self) -> str:
        d = dict(self.__dict__)
        del d["col"]
        return f"{super().__repr__()} {pprint.pformat(d, width=300)}"

    # Deck save/load
    #############################################################

    def id(
        self,
        name: str,
        create: bool = True,
        type: int = 0,
    ) -> Optional[int]:
        "Add a deck with NAME. Reuse deck if already exists. Return id as int."
        id = self.id_for_name(name)
        if id:
            return id
        elif not create:
            return None

        deck = self.new_deck_legacy(bool(type))
        deck["name"] = name
        self.update(deck, preserve_usn=False)

        return deck["id"]

    def rem(self, did: int, cardsToo: bool = True, childrenToo: bool = True) -> None:
        "Remove the deck. If cardsToo, delete any cards inside."
        if isinstance(did, str):
            did = int(did)
        assert cardsToo and childrenToo
        self.col.backend.remove_deck(did)

    def all_names_and_ids(
        self, skip_empty_default=False, include_filtered=True
    ) -> Sequence[pb.DeckNameID]:
        "A sorted sequence of deck names and IDs."
        return self.col.backend.get_deck_names(
            skip_empty_default=skip_empty_default, include_filtered=include_filtered
        )

    def id_for_name(self, name: str) -> Optional[int]:
        try:
            return self.col.backend.get_deck_id_by_name(name)
        except NotFoundError:
            return None

    def get_legacy(self, did: int) -> Optional[Deck]:
        try:
            return from_json_bytes(self.col.backend.get_deck_legacy(did))
        except NotFoundError:
            return None

    def have(self, id: int) -> bool:
        return not self.get_legacy(int(id))

    def get_all_legacy(self) -> List[Deck]:
        return list(from_json_bytes(self.col.backend.get_all_decks_legacy()).values())

    def new_deck_legacy(self, filtered: bool) -> Deck:
        return from_json_bytes(self.col.backend.new_deck_legacy(filtered))

    def deck_tree(self) -> pb.DeckTreeNode:
        return self.col.backend.deck_tree(top_deck_id=0, now=0)

    @classmethod
    def find_deck_in_tree(
        cls, node: DeckTreeNode, deck_id: int
    ) -> Optional[DeckTreeNode]:
        if node.deck_id == deck_id:
            return node
        for child in node.children:
            match = cls.find_deck_in_tree(child, deck_id)
            if match:
                return match
        return None

    def all(self) -> List[Deck]:
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

    def collapse(self, did) -> None:
        deck = self.get(did)
        deck["collapsed"] = not deck["collapsed"]
        self.save(deck)

    def collapseBrowser(self, did) -> None:
        deck = self.get(did)
        collapsed = deck.get("browserCollapsed", False)
        deck["browserCollapsed"] = not collapsed
        self.save(deck)

    def count(self) -> int:
        return len(self.all_names_and_ids())

    def get(self, did: Union[int, str], default: bool = True) -> Optional[Deck]:
        if not did:
            if default:
                return self.get_legacy(1)
            else:
                return None
        id = int(did)
        deck = self.get_legacy(id)
        if deck:
            return deck
        elif default:
            return self.get_legacy(1)
        else:
            return None

    def byName(self, name: str) -> Optional[Deck]:
        """Get deck with NAME, ignoring case."""
        id = self.id_for_name(name)
        if id:
            return self.get_legacy(id)
        return None

    def update(self, g: Deck, preserve_usn=True) -> None:
        "Add or update an existing deck. Used for syncing and merging."
        try:
            g["id"] = self.col.backend.add_or_update_deck_legacy(
                deck=to_json_bytes(g), preserve_usn_and_mtime=preserve_usn
            )
        except anki.rsbackend.DeckIsFilteredError as exc:
            raise DeckRenameError("deck was filtered") from exc

    def rename(self, g: Deck, newName: str) -> None:
        "Rename deck prefix to NAME if not exists. Updates children."
        g["name"] = newName
        self.update(g, preserve_usn=False)
        return

    # Drag/drop
    #############################################################

    def renameForDragAndDrop(
        self, draggedDeckDid: int, ontoDeckDid: Optional[Union[int, str]]
    ) -> None:
        draggedDeck = self.get(draggedDeckDid)
        draggedDeckName = draggedDeck["name"]
        ontoDeckName = self.get(ontoDeckDid)["name"]

        if ontoDeckDid is None or ontoDeckDid == "":
            if len(self.path(draggedDeckName)) > 1:
                self.rename(draggedDeck, self.basename(draggedDeckName))
        elif self._canDragAndDrop(draggedDeckName, ontoDeckName):
            draggedDeck = self.get(draggedDeckDid)
            draggedDeckName = draggedDeck["name"]
            ontoDeckName = self.get(ontoDeckDid)["name"]
            assert ontoDeckName.strip()
            self.rename(
                draggedDeck, ontoDeckName + "::" + self.basename(draggedDeckName)
            )

    def _canDragAndDrop(self, draggedDeckName: str, ontoDeckName: str) -> bool:
        if (
            draggedDeckName == ontoDeckName
            or self._isParent(ontoDeckName, draggedDeckName)
            or self._isAncestor(draggedDeckName, ontoDeckName)
        ):
            return False
        else:
            return True

    def _isParent(self, parentDeckName: str, childDeckName: str) -> bool:
        return self.path(childDeckName) == self.path(parentDeckName) + [
            self.basename(childDeckName)
        ]

    def _isAncestor(self, ancestorDeckName: str, descendantDeckName: str) -> bool:
        ancestorPath = self.path(ancestorDeckName)
        return ancestorPath == self.path(descendantDeckName)[0 : len(ancestorPath)]

    # Deck configurations
    #############################################################

    def all_config(self) -> List[Config]:
        "A list of all deck config."
        return list(from_json_bytes(self.col.backend.all_deck_config_legacy()))

    def confForDid(self, did: int) -> DeckConfig:
        deck = self.get(did, default=False)
        assert deck
        if "conf" in deck:
            dcid = int(deck["conf"])  # may be a string
            conf = self.get_config(dcid)
            if not conf:
                # fall back on default
                conf = self.get_config(1)
            conf["dyn"] = False
            return conf
        # dynamic decks have embedded conf
        return deck

    def get_config(self, conf_id: int) -> Optional[DeckConfig]:
        try:
            return from_json_bytes(self.col.backend.get_deck_config_legacy(conf_id))
        except NotFoundError:
            return None

    def update_config(self, conf: DeckConfig, preserve_usn=False) -> None:
        conf["id"] = self.col.backend.add_or_update_deck_config_legacy(
            config=to_json_bytes(conf), preserve_usn_and_mtime=preserve_usn
        )

    def add_config(
        self, name: str, clone_from: Optional[DeckConfig] = None
    ) -> DeckConfig:
        if clone_from is not None:
            conf = copy.deepcopy(clone_from)
            conf["id"] = 0
        else:
            conf = from_json_bytes(self.col.backend.new_deck_config_legacy())
        conf["name"] = name
        self.update_config(conf)
        return conf

    def add_config_returning_id(
        self, name: str, clone_from: Optional[DeckConfig] = None
    ) -> int:
        return self.add_config(name, clone_from)["id"]

    def remove_config(self, id) -> None:
        "Remove a configuration and update all decks using it."
        self.col.modSchema(check=True)
        for g in self.all():
            # ignore cram decks
            if "conf" not in g:
                continue
            if str(g["conf"]) == str(id):
                g["conf"] = 1
                self.save(g)
        self.col.backend.remove_deck_config(id)

    def setConf(self, grp: DeckConfig, id: int) -> None:
        grp["conf"] = id
        self.save(grp)

    def didsForConf(self, conf) -> List[int]:
        dids = []
        for deck in self.all():
            if "conf" in deck and deck["conf"] == conf["id"]:
                dids.append(deck["id"])
        return dids

    def restoreToDefault(self, conf) -> None:
        oldOrder = conf["new"]["order"]
        new = from_json_bytes(self.col.backend.new_deck_config_legacy())
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

    def name(self, did: int, default: bool = False) -> str:
        deck = self.get(did, default=default)
        if deck:
            return deck["name"]
        return _("[no deck]")

    def nameOrNone(self, did: int) -> Optional[str]:
        deck = self.get(did, default=False)
        if deck:
            return deck["name"]
        return None

    def setDeck(self, cids, did) -> None:
        self.col.db.execute(
            "update cards set did=?,usn=?,mod=? where id in " + ids2str(cids),
            did,
            self.col.usn(),
            intTime(),
        )

    def cids(self, did: int, children: bool = False) -> List[int]:
        if not children:
            return self.col.db.list("select id from cards where did=?", did)
        dids = [did]
        for name, id in self.children(did):
            dids.append(id)
        return self.col.db.list("select id from cards where did in " + ids2str(dids))

    def for_card_ids(self, cids: List[int]) -> List[int]:
        return self.col.db.list(f"select did from cards where id in {ids2str(cids)}")

    # Deck selection
    #############################################################

    def active(self) -> List[int]:
        "The currrently active dids."
        return self.col.get_config("activeDecks", [1])

    def selected(self) -> int:
        "The currently selected did."
        return self.col.conf["curDeck"]

    def current(self) -> Deck:
        return self.get(self.selected())

    def select(self, did: int) -> None:
        "Select a new branch."
        # make sure arg is an int
        did = int(did)
        current = self.selected()
        active = self.deck_and_child_ids(did)
        if current != did or active != self.active():
            self.col.conf["curDeck"] = did
            self.col.conf["activeDecks"] = active

    # don't use this, it will likely go away
    def update_active(self):
        self.select(self.current()["id"])

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
    def key(cls, deck: Deck) -> List[str]:
        return cls.path(deck["name"])

    def children(self, did: int) -> List[Tuple[str, int]]:
        "All children of did, as (name, id)."
        name = self.get(did)["name"]
        actv = []
        for g in self.all_names_and_ids():
            if g.name.startswith(name + "::"):
                actv.append((g.name, g.id))
        return actv

    def child_ids(self, parent_name: str) -> Iterable[int]:
        prefix = parent_name + "::"
        return (d.id for d in self.all_names_and_ids() if d.name.startswith(prefix))

    def deck_and_child_ids(self, deck_id: int) -> List[int]:
        parent_name = self.get_legacy(deck_id)["name"]
        out = [deck_id]
        out.extend(self.child_ids(parent_name))
        return out

    childMapNode = Dict[int, Any]
    # Change to Dict[int, "DeckManager.childMapNode"] when MyPy allow recursive type

    def childDids(self, did: int, childMap: DeckManager.childMapNode) -> List:
        def gather(node: DeckManager.childMapNode, arr):
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
        self, did: int, nameMap: Optional[Dict[str, Deck]] = None
    ) -> List[Deck]:
        "All parents of did."
        # get parent and grandparent names
        parents_names: List[str] = []
        for part in self.immediate_parent_path(self.get(did)["name"]):
            if not parents_names:
                parents_names.append(part)
            else:
                parents_names.append(parents_names[-1] + "::" + part)
        parents: List[Deck] = []
        # convert to objects
        for parent_name in parents_names:
            if nameMap:
                deck = nameMap[parent_name]
            else:
                deck = self.get(self.id(parent_name))
            parents.append(deck)
        return parents

    def parentsByName(self, name: str) -> List[Deck]:
        "All existing parents of name"
        if "::" not in name:
            return []
        names = self.immediate_parent_path(name)
        head = []
        parents: List[Deck] = []

        while names:
            head.append(names.pop(0))
            deck = self.byName("::".join(head))
            if deck:
                parents.append(deck)

        return parents

    def nameMap(self) -> Dict[str, Deck]:
        return dict((d["name"], d) for d in self.all())

    # Dynamic decks
    ##########################################################################

    def newDyn(self, name: str) -> int:
        "Return a new dynamic deck and set it as the current deck."
        did = self.id(name, type=1)
        self.select(did)
        return did

    # 1 for dyn, 0 for standard
    def isDyn(self, did: Union[int, str]) -> int:
        return self.get(did)["dyn"]
