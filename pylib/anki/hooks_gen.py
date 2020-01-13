# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""
Code for generating parts of hooks.py
"""

import re
from dataclasses import dataclass
from operator import attrgetter
from typing import List, Optional


@dataclass
class Hook:
    # the name of the hook. _filter or _hook is appending automatically.
    name: str
    # string of the typed arguments passed to the callback, eg
    # "kind: str, val: int"
    cb_args: str = ""
    # string of the return type. if set, hook is a filter.
    return_type: Optional[str] = None
    # if add-ons may be relying on the legacy hook name, add it here
    legacy_hook: Optional[str] = None

    def callable(self) -> str:
        "Convert args into a Callable."
        types = []
        for arg in self.cb_args.split(","):
            if not arg:
                continue
            (name, type) = arg.split(":")
            types.append(type.strip())
        types_str = ", ".join(types)
        return f"Callable[[{types_str}], {self.return_type or 'None'}]"

    def arg_names(self) -> List[str]:
        names = []
        for arg in self.cb_args.split(","):
            if not arg:
                continue
            (name, type) = arg.split(":")
            names.append(name.strip())
        return names

    def full_name(self) -> str:
        return f"{self.name}_{self.kind()}"

    def kind(self) -> str:
        if self.return_type is not None:
            return "filter"
        else:
            return "hook"

    def list_code(self) -> str:
        return f"""\
{self.full_name()}: List[{self.callable()}] = []
"""

    def fire_code(self) -> str:
        if self.return_type is not None:
            # filter
            return self.filter_fire_code()
        else:
            # hook
            return self.hook_fire_code()

    def hook_fire_code(self) -> str:
        arg_names = self.arg_names()
        out = f"""\
def run_{self.full_name()}({self.cb_args}) -> None:
    for hook in {self.full_name()}:
        try:
            hook({", ".join(arg_names)})
        except:
            # if the hook fails, remove it
            {self.full_name()}.remove(hook)
            raise
"""
        if self.legacy_hook:
            args = ", ".join([f'"{self.legacy_hook}"'] + arg_names)
            out += f"""\
    # legacy support
    runHook({args})
"""
        return out + "\n\n"

    def filter_fire_code(self) -> str:
        arg_names = self.arg_names()
        out = f"""\
def run_{self.full_name()}({self.cb_args}) -> {self.return_type}:
    for filter in {self.full_name()}:
        try:
            {arg_names[0]} = filter({", ".join(arg_names)})
        except:
            # if the hook fails, remove it
            {self.full_name()}.remove(filter)
            raise
"""
        if self.legacy_hook:
            args = ", ".join([f'"{self.legacy_hook}"'] + arg_names)
            out += f"""\
    # legacy support
    runFilter({args})
"""

        out += f"""\
    return {arg_names[0]}
"""
        return out + "\n\n"


def update_file(path: str, hooks: List[Hook]):
    hooks.sort(key=attrgetter("name"))
    code = ""
    for hook in hooks:
        code += hook.list_code()
    code += "\n\n"
    for hook in hooks:
        code += hook.fire_code()

    orig = open(path).read()
    new = re.sub(
        "(?s)# @@AUTOGEN@@.*?# @@AUTOGEN@@\n",
        f"# @@AUTOGEN@@\n\n{code}# @@AUTOGEN@@\n",
        orig,
    )

    open(path, "wb").write(new.encode("utf8"))
