# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import copy
import unicodedata
from typing import Any, Dict, List, Optional, Set, Tuple, Union

import anki  # pylint: disable=unused-import
import anki.backend_pb2 as pb
from anki import hooks
from anki.consts import *
from anki.errors import DeckRenameError
from anki.lang import _
from anki.utils import ids2str, intTime

# fixmes:
# - make sure users can't set grad interval < 1

defaultDeck = {
    "newToday": [0, 0],  # currentDay, count
    "revToday": [0, 0],
    "lrnToday": [0, 0],
    "timeToday": [0, 0],  # time in ms
    "conf": 1,
    "usn": 0,
    "desc": "",
    "dyn": DECK_STD,
    "collapsed": False,
    # added in beta11
    "extendNew": 10,
    "extendRev": 50,
    # fixme: if we keep this, mod must be set or handled in serde
    "mod": 0,
}

defaultDynamicDeck = {
    "newToday": [0, 0],
    "revToday": [0, 0],
    "lrnToday": [0, 0],
    "timeToday": [0, 0],
    "collapsed": False,
    "dyn": DECK_DYN,
    "desc": "",
    "usn": 0,
    "delays": None,
    "separate": True,  # unused
    # list of (search, limit, order); we only use first two elements for now
    "terms": [["", 100, 0]],
    "resched": True,
    "return": True,  # currently unused
    # v2 scheduler
    "previewDelay": 10,
    "mod": 0,
}


class DecksDictProxy:
    def __init__(self, col: anki.storage._Collection):
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

    def __init__(self, col: anki.storage._Collection) -> None:
        self.col = col.weakref()
        self.decks = DecksDictProxy(col)
        # do not access this directly!
        # self._cache: Dict[int, ] = {}
        #        self.decks = {}
        self._dconf_cache: Optional[Dict[int, Dict[str, Any]]] = None

    def save(self, g: Dict = None) -> None:
        "Can be called with either a deck or a deck configuration."
        if not g:
            print("col.decks.save() should be passed the changed deck")
            return

        # deck conf?
        if "maxTaken" in g:
            self.update_config(g)
            return
        else:
            # g["mod"] = intTime()
            # g["usn"] = self.col.usn()
            self.update(g)

    # legacy
    def flush(self):
        pass

    # Deck save/load
    #############################################################

    # fixme: if we're stripping chars on add, then we need to do that on lookup as well
    # and need to make sure \x1f conversion

    def id(
        self, name: str, create: bool = True, type: Optional[Dict[str, Any]] = None
    ) -> Optional[int]:
        "Add a deck with NAME. Reuse deck if already exists. Return id as int."
        if type is None:
            type = defaultDeck

        id = self.id_for_name(name)
        if id:
            return id
        elif not create:
            return None

        deck = self.new_deck_legacy(bool(type["dyn"]))
        deck["name"] = name
        self.update(deck)

        # fixme
        self.maybeAddToActive()

        # fixme
        hooks.deck_added(deck)

        return deck["id"]

    def rem(self, did: int, cardsToo: bool = True, childrenToo: bool = True) -> None:
        "Remove the deck. If cardsToo, delete any cards inside."
        assert cardsToo and childrenToo
        self.col.backend.remove_deck(did)
        # fixme: default deck special case
        # if str(did) == "1":
        #     # we won't allow the default deck to be deleted, but if it's a
        #     # child of an existing deck then it needs to be renamed
        #     deck = self.get(did)
        #     if "::" in deck["name"]:
        #         base = self.basename(deck["name"])
        #         suffix = ""
        #         while True:
        #             # find an unused name
        #             name = base + suffix
        #             if not self.byName(name):
        #                 deck["name"] = name
        #                 self.save(deck)
        #                 break
        #             suffix += "1"
        #     return

        # fixme:
        #         # don't use cids(), as we want cards in cram decks too
        #         cids = self.col.db.list(
        #             "select id from cards where did=? or odid=?", did, did
        #         )

        # fixme
        # ensure we have an active deck
        if did in self.active():
            self.select(self.all_names_and_ids()[0].id)

    def allNames(self, dyn: bool = True, force_default: bool = True) -> List:
        "An unsorted list of all deck names."
        if dyn:
            return [x["name"] for x in self.all(force_default=force_default)]
        else:
            return [
                x["name"] for x in self.all(force_default=force_default) if not x["dyn"]
            ]

    def all_names_and_ids(self) -> List[pb.DeckNameID]:
        return self.col.backend.get_deck_names_and_ids()

    def id_for_name(self, name: str) -> Optional[int]:
        return self.col.backend.get_deck_id_by_name(name)

    def get_legacy(self, did: int) -> Optional[Dict]:
        return self.col.backend.get_deck_legacy(did)

    def have(self, id: int) -> bool:
        return not self.get_legacy(int(id))

    def get_all_legacy(self) -> List[Dict]:
        return list(self.col.backend.get_all_decks().values())

    def new_deck_legacy(self, filtered: bool) -> Dict:
        try:
            return self.col.backend.new_deck_legacy(filtered)
        except anki.rsbackend.DeckIsFilteredError:
            raise DeckRenameError("deck was filtered")
        except anki.rsbackend.ExistsError:
            raise DeckRenameError("deck already exists")

    def deck_tree(self) -> pb.DeckTreeNode:
        return self.col.backend.deck_tree(include_counts=False)

    def all(self, force_default: bool = True) -> List:
        """A list of all decks.

        list contains default deck if either:
        * force_default is True
        * there are no other deck
        * default deck contains a card
        * default deck has a child (assumed not to be the case if assume_no_child)
        """
        decks = self.get_all_legacy()
        if not force_default and not self.should_default_be_displayed(force_default):
            decks = [deck for deck in decks if deck["id"] != 1]
        return decks

    def allIds(self) -> List[str]:
        return [str(x.id) for x in self.all_names_and_ids()]

    def collapse(self, did) -> None:
        deck = self.get(did)
        deck["collapsed"] = not deck["collapsed"]
        self.save(deck)

    # fixme
    def collapseBrowser(self, did) -> None:
        deck = self.get(did)
        collapsed = deck.get("browserCollapsed", False)
        deck["browserCollapsed"] = not collapsed
        self.save(deck)

    def count(self) -> int:
        return len(self.all_names_and_ids())

    def get(self, did: Union[int, str], default: bool = True) -> Optional[Dict]:
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

    def byName(self, name: str) -> Optional[Dict]:
        """Get deck with NAME, ignoring case."""
        id = self.id_for_name(name)
        if id:
            return self.get_legacy(id)
        return None

    def update(self, g: Dict[str, Any], preserve_usn=False) -> None:
        "Add or update an existing deck. Used for syncing and merging."
        try:
            self.col.backend.add_or_update_deck_legacy(g, preserve_usn)
        except anki.rsbackend.DeckIsFilteredError:
            raise DeckRenameError("deck was filtered")
        except anki.rsbackend.ExistsError:
            raise DeckRenameError("deck already exists")

        #       self.decks[str(g["id"])] = g
        self.maybeAddToActive()
        # mark registry changed, but don't bump mod time

    #        self.save()

    def rename(self, g: Dict[str, Any], newName: str) -> None:
        "Rename deck prefix to NAME if not exists. Updates children."
        g["name"] = newName
        self.update(g)
        return

        # fixme: ensure rename of b in a::b::c generates new b
        # fixme: renaming may have altered active did order
        # self.maybeAddToActive()

    def renameForDragAndDrop(self, draggedDeckDid: int, ontoDeckDid: Any) -> None:
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

    def _isParent(self, parentDeckName: str, childDeckName: str) -> Any:
        return self.path(childDeckName) == self.path(parentDeckName) + [
            self.basename(childDeckName)
        ]

    def _isAncestor(self, ancestorDeckName: str, descendantDeckName: str) -> Any:
        ancestorPath = self.path(ancestorDeckName)
        return ancestorPath == self.path(descendantDeckName)[0 : len(ancestorPath)]

    @staticmethod
    def path(name: str) -> Any:
        return name.split("::")

    _path = path

    @classmethod
    def basename(cls, name: str) -> Any:
        return cls.path(name)[-1]

    _basename = basename

    @classmethod
    def immediate_parent_path(cls, name: str) -> Any:
        return cls._path(name)[:-1]

    @classmethod
    def immediate_parent(cls, name: str) -> Any:
        pp = cls.immediate_parent_path(name)
        if pp:
            return "::".join(pp)

    @classmethod
    def key(cls, deck: Dict[str, Any]) -> List[str]:
        return cls.path(deck["name"])

    def _ensureParents(self, name: str) -> Any:
        "Ensure parents exist, and return name with case matching parents."
        s = ""
        path = self.path(name)
        if len(path) < 2:
            return name
        for p in path[:-1]:
            if not s:
                s += p
            else:
                s += "::" + p
            # fetch or create
            did = self.id(s)
            # get original case
            s = self.name(did)
        name = s + "::" + path[-1]
        return name

    # Deck configurations
    #############################################################

    def all_config(self) -> List:
        "A list of all deck config."
        return list(self.col.backend.all_deck_config())

    def confForDid(self, did: int) -> Any:
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

    def get_config(self, conf_id: int) -> Any:
        if self._dconf_cache is not None:
            return self._dconf_cache.get(conf_id)
        return self.col.backend.get_deck_config(conf_id)

    def update_config(self, conf: Dict[str, Any], preserve_usn=False) -> None:
        self.col.backend.add_or_update_deck_config(conf, preserve_usn)

    def add_config(
        self, name: str, clone_from: Optional[Dict[str, Any]] = None
    ) -> Dict[str, Any]:
        if clone_from is not None:
            conf = copy.deepcopy(clone_from)
            conf["id"] = 0
        else:
            conf = self.col.backend.new_deck_config()
        conf["name"] = name
        self.update_config(conf)
        return conf

    def add_config_returning_id(
        self, name: str, clone_from: Optional[Dict[str, Any]] = None
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

    def setConf(self, grp: Dict[str, Any], id: int) -> None:
        grp["conf"] = id
        self.save(grp)

    # fixme: expensive
    def didsForConf(self, conf) -> List:
        dids = []
        for deck in list(self.decks.values()):
            if "conf" in deck and deck["conf"] == conf["id"]:
                dids.append(deck["id"])
        return dids

    def restoreToDefault(self, conf) -> None:
        oldOrder = conf["new"]["order"]
        new = self.col.backend.new_deck_config()
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

    # temporary caching - don't use this as it will be removed
    def _enable_dconf_cache(self):
        self._dconf_cache = {c["id"]: c for c in self.all_config()}

    def _disable_dconf_cache(self):
        self._dconf_cache = None

    # Deck utils
    #############################################################

    def name(self, did: int, default: bool = False) -> Any:
        deck = self.get(did, default=default)
        if deck:
            return deck["name"]
        return _("[no deck]")

    def nameOrNone(self, did: int) -> Any:
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

    def maybeAddToActive(self) -> None:
        # reselect current deck, or default if current has disappeared
        c = self.current()
        self.select(c["id"])

    def cids(self, did: int, children: bool = False) -> Any:
        if not children:
            return self.col.db.list("select id from cards where did=?", did)
        dids = [did]
        for name, id in self.children(did):
            dids.append(id)
        return self.col.db.list("select id from cards where did in " + ids2str(dids))

    def for_card_ids(self, cids: List[int]) -> List[int]:
        return self.col.db.list(f"select did from cards where id in {ids2str(cids)}")

    # fixme
    def _recoverOrphans(self) -> None:
        pass
        # dids = list(self.decks.keys())
        # mod = self.col.db.mod
        # self.col.db.execute(
        #     "update cards set did = 1 where did not in " + ids2str(dids)
        # )
        # self.col.db.mod = mod

    def _checkDeckTree(self) -> None:
        decks = self.all()
        decks.sort(key=self.key)
        names: Set[str] = set()

        for deck in decks:
            # two decks with the same name?
            if deck["name"] in names:
                self.col.log("fix duplicate deck name", deck["name"])
                deck["name"] += "%d" % intTime(1000)
                self.save(deck)

            # ensure no sections are blank
            if not all(self.path(deck["name"])):
                self.col.log("fix deck with missing sections", deck["name"])
                deck["name"] = "recovered%d" % intTime(1000)
                self.save(deck)

            # immediate parent must exist
            if "::" in deck["name"]:
                immediateParent = self.immediate_parent(deck["name"])
                if immediateParent not in names:
                    self.col.log("fix deck with missing parent", deck["name"])
                    self._ensureParents(deck["name"])
                    names.add(immediateParent)

            names.add(deck["name"])

    def checkIntegrity(self) -> None:
        self._recoverOrphans()
        self._checkDeckTree()

    def should_deck_be_displayed(
        self, deck, force_default: bool = True, assume_no_child: bool = False
    ) -> bool:
        """Whether the deck should appear in main window, browser side list, filter, deck selection...

        True, except for empty default deck without children"""
        if deck["id"] != "1":
            return True
        return self.should_default_be_displayed(force_default, assume_no_child)

    def should_default_be_displayed(
        self,
        force_default: bool = True,
        assume_no_child: bool = False,
        default_deck: Optional[Dict[str, Any]] = None,
    ) -> bool:
        """Whether the default deck should appear in main window, browser side list, filter, deck selection...

        True, except for empty default deck (without children)"""
        if force_default:
            return True
        if self.col.db.scalar("select 1 from cards where did = 1 limit 1"):
            return True
        # fixme
        return False
        # if len(self.all_names_and_ids()) == 1:
        #     return True
        # # looking for children
        # if assume_no_child:
        #     return False
        # if default_deck is None:
        #     default_deck = self.get(1)
        # defaultName = default_deck["name"]
        # for name in self.allNames():
        #     if name.startswith(f"{defaultName}::"):
        #         return True
        # return False

    # Deck selection
    #############################################################

    def active(self) -> Any:
        "The currrently active dids."
        return self.col.get_config("activeDecks", [1])

    def selected(self) -> Any:
        "The currently selected did."
        return self.col.conf["curDeck"]

    def current(self) -> Any:
        return self.get(self.selected())

    def select(self, did: int) -> None:
        "Select a new branch."
        # make sure arg is an int
        did = int(did)
        # current deck
        self.col.conf["curDeck"] = did
        # and active decks (current + all children)
        actv = self.children(did)
        actv.sort()
        self.col.conf["activeDecks"] = [did] + [a[1] for a in actv]
        self.col.setMod()

    def children(self, did: int) -> List[Tuple[Any, Any]]:
        "All children of did, as (name, id)."
        name = self.get(did)["name"]
        actv = []
        for g in self.all():
            if g["name"].startswith(name + "::"):
                actv.append((g["name"], g["id"]))
        return actv

    def childDids(self, did: int, childMap: Dict[int, Any]) -> List:
        def gather(node, arr):
            for did, child in node.items():
                arr.append(did)
                gather(child, arr)

        arr: List = []
        gather(childMap[did], arr)
        return arr

    def childMap(self) -> Dict[Any, Dict[Any, dict]]:
        nameMap = self.nameMap()
        childMap = {}

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

    def parents(self, did: int, nameMap: Optional[Any] = None) -> List:
        "All parents of did."
        # get parent and grandparent names
        parents: List[str] = []
        for part in self.immediate_parent_path(self.get(did)["name"]):
            if not parents:
                parents.append(part)
            else:
                parents.append(parents[-1] + "::" + part)
        # convert to objects
        for c, p in enumerate(parents):
            if nameMap:
                deck = nameMap[p]
            else:
                deck = self.get(self.id(p))
            parents[c] = deck
        return parents

    def parentsByName(self, name: str) -> List:
        "All existing parents of name"
        if "::" not in name:
            return []
        names = self.immediate_parent_path(name)
        head = []
        parents = []

        while names:
            head.append(names.pop(0))
            deck = self.byName("::".join(head))
            if deck:
                parents.append(deck)

        return parents

    def nameMap(self) -> dict:
        return dict((d["name"], d) for d in self.all())

    # Sync handling
    ##########################################################################

    def beforeUpload(self) -> None:
        for d in self.all():
            d["usn"] = 0
        self.save()

    # Dynamic decks
    ##########################################################################

    def newDyn(self, name: str) -> int:
        "Return a new dynamic deck and set it as the current deck."
        did = self.id(name, type=defaultDynamicDeck)
        self.select(did)
        return did

    def isDyn(self, did: Union[int, str]) -> Any:
        return self.get(did)["dyn"]

    @staticmethod
    def normalizeName(name: str) -> str:
        return unicodedata.normalize("NFC", name.lower())

    @staticmethod
    def equalName(name1: str, name2: str) -> bool:
        return DeckManager.normalizeName(name1) == DeckManager.normalizeName(name2)
