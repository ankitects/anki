# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""
Config handling

- To set a config value, use col.set_config(key, val).
- To get a config value, use col.get_config(key, default=None). In
the case of lists and dictionaries, any changes you make to the returned
value will not be saved unless you call set_config().
- To remove a config value, use col.remove_config(key).

For legacy reasons, the config is also exposed as a dict interface
as col.conf.  To support old code that was mutating inner values,
using col.conf["key"] needs to wrap lists and dicts when returning them.
As this is less efficient, please use the col.*_config() API in new code.
"""

from __future__ import annotations

import copy
import weakref
from typing import Any
from weakref import ref

import anki
from anki.errors import NotFoundError
from anki.utils import from_json_bytes, to_json_bytes


class ConfigManager:
    def __init__(self, col: anki.collection.Collection):
        self.col = col.weakref()

    def get_immutable(self, key: str) -> Any:
        try:
            return from_json_bytes(self.col._backend.get_config_json(key))
        except NotFoundError as exc:
            raise KeyError from exc

    def set(self, key: str, val: Any) -> None:
        self.col._backend.set_config_json(key=key, value_json=to_json_bytes(val))

    def remove(self, key: str) -> None:
        self.col._backend.remove_config(key)

    # Legacy dict interface
    #########################

    def __getitem__(self, key: str) -> Any:
        val = self.get_immutable(key)
        if isinstance(val, list):
            print(
                f"conf key {key} should be fetched with col.get_config(), and saved with col.set_config()"
            )
            return WrappedList(weakref.ref(self), key, val)
        elif isinstance(val, dict):
            print(
                f"conf key {key} should be fetched with col.get_config(), and saved with col.set_config()"
            )
            return WrappedDict(weakref.ref(self), key, val)
        else:
            return val

    def __setitem__(self, key: str, value: Any) -> None:
        self.set(key, value)

    def get(self, key: str, default: Any = None) -> Any:
        try:
            return self[key]
        except KeyError:
            return default

    def setdefault(self, key: str, default: Any) -> Any:
        if key not in self:
            self[key] = default
        return self[key]

    def __contains__(self, key: str) -> bool:
        try:
            self.get_immutable(key)
            return True
        except KeyError:
            return False

    def __delitem__(self, key: str) -> None:
        self.remove(key)


# Tracking changes to mutable objects
#########################################
# Because we previously allowed mutation of the conf
# structure directly, to allow col.conf["foo"]["bar"] = xx
# to continue to function, we apply changes as the object
# is dropped.


class WrappedList(list):
    def __init__(self, conf: ref[ConfigManager], key: str, val: Any) -> None:
        self.key = key
        self.conf = conf
        self.orig = copy.deepcopy(val)
        super().__init__(val)

    def __del__(self) -> None:
        cur = list(self)
        conf = self.conf()
        if conf and self.orig != cur:
            conf[self.key] = cur


class WrappedDict(dict):
    def __init__(self, conf: ref[ConfigManager], key: str, val: Any) -> None:
        self.key = key
        self.conf = conf
        self.orig = copy.deepcopy(val)
        super().__init__(val)

    def __del__(self) -> None:
        cur = dict(self)
        conf = self.conf()
        if conf and self.orig != cur:
            conf[self.key] = cur
