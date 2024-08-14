# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""
Code for generating hooks.
"""

from __future__ import annotations

import os
import subprocess
import sys
from dataclasses import dataclass
from operator import attrgetter

sys.path.append("pylib/anki/_vendor")

import stringcase


@dataclass
class Hook:
    # the name of the hook. _filter or _hook is appending automatically.
    name: str
    # string of the typed arguments passed to the callback, eg
    # ["kind: str", "val: int"]
    args: list[str] | None = None
    # string of the return type. if set, hook is a filter.
    return_type: str | None = None
    # if add-ons may be relying on the legacy hook name, add it here
    legacy_hook: str | None = None
    # if legacy hook takes no arguments but the new hook does, set this
    legacy_no_args: bool = False
    # if the hook replaces a deprecated one, add its name here
    replaces: str | None = None
    # arguments that the hook being replaced took
    replaced_hook_args: list[str] | None = None
    # docstring to add to hook class
    doc: str | None = None

    def callable(self) -> str:
        "Convert args into a Callable."
        types = []
        for arg in self.args or []:
            (name, type) = arg.split(":")
            type = f'"{type.strip()}"'
            types.append(type)
        types_str = ", ".join(types)
        return f"Callable[[{types_str}], {self.return_type or 'None'}]"

    def arg_names(self, args: list[str] | None) -> list[str]:
        names = []
        for arg in args or []:
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

    def classname(self) -> str:
        return f"_{stringcase.pascalcase(self.full_name())}"

    def list_code(self) -> str:
        return f"""\
    _hooks: list[{self.callable()}] = []
"""

    def code(self) -> str:
        appenddoc = f"({', '.join(self.args or [])})"
        if self.doc:
            classdoc = f"    '''{self.doc}'''\n"
        else:
            classdoc = ""
        code = f"""\
class {self.classname()}:
{classdoc}{self.list_code()}
    
    def append(self, callback: {self.callable()}) -> None:
        '''{appenddoc}'''
        self._hooks.append(callback)

    def remove(self, callback: {self.callable()}) -> None:
        if callback in self._hooks:
            self._hooks.remove(callback)

    def count(self) -> int:
        return len(self._hooks)

{self.fire_code()}
{self.name} = {self.classname()}()
"""
        return code

    def fire_code(self) -> str:
        if self.return_type is not None:
            # filter
            return self.filter_fire_code()
        else:
            # hook
            return self.hook_fire_code()

    def legacy_args(self) -> str:
        if self.legacy_no_args:
            # hook name only
            return f'"{self.legacy_hook}"'
        else:
            return ", ".join([f'"{self.legacy_hook}"'] + self.arg_names(self.args))

    def replaced_args(self) -> str:
        args = ", ".join(self.arg_names(self.replaced_hook_args))
        return f"{self.replaces}({args})"

    def hook_fire_code(self) -> str:
        arg_names = self.arg_names(self.args)
        args_including_self = ["self"] + (self.args or [])
        out = f"""\
    def __call__({", ".join(args_including_self)}) -> None:
        for hook in self._hooks:
            try:
                hook({", ".join(arg_names)})
            except Exception:
                # if the hook fails, remove it
                self._hooks.remove(hook)
                raise
"""
        if self.replaces and self.legacy_hook:
            raise Exception(
                f"Hook {self.name} replaces {self.replaces} and "
                "must therefore not define a legacy hook."
            )
        elif self.replaces:
            out += f"""\
        if {self.replaces}.count() > 0:
            print(
                "The hook {self.replaces} is deprecated.\\n"
                "Use {self.name} instead."
            )
        {self.replaced_args()}
"""
        elif self.legacy_hook:
            # don't run legacy hook if replaced hook exists
            # otherwise the legacy hook will be run twice
            out += f"""\
        # legacy support
        anki.hooks.runHook({self.legacy_args()})
"""
        return f"{out}\n\n"

    def filter_fire_code(self) -> str:
        arg_names = self.arg_names(self.args)
        args_including_self = ["self"] + (self.args or [])
        out = f"""\
    def __call__({", ".join(args_including_self)}) -> {self.return_type}:
        for filter in self._hooks:
            try:
                {arg_names[0]} = filter({", ".join(arg_names)})
            except Exception:
                # if the hook fails, remove it
                self._hooks.remove(filter)
                raise
"""
        if self.replaces and self.legacy_hook:
            raise Exception(
                f"Hook {self.name} replaces {self.replaces} and "
                "must therefore not define a legacy hook."
            )
        elif self.replaces:
            out += f"""\
        if {self.replaces}.count() > 0:
            print(
                "The hook {self.replaces} is deprecated.\\n"
                "Use {self.name} instead."
            )
        {arg_names[0]} = {self.replaced_args()}
"""
        elif self.legacy_hook:
            # don't run legacy hook if replaced hook exists
            # otherwise the legacy hook will be run twice
            out += f"""\
        # legacy support
        {arg_names[0]} = anki.hooks.runFilter({self.legacy_args()})
"""

        out += f"""\
        return {arg_names[0]}
"""
        return f"{out}\n\n"


def write_file(path: str, hooks: list[Hook], prefix: str, suffix: str):
    hooks.sort(key=attrgetter("name"))
    code = f"{prefix}\n"
    for hook in hooks:
        code += hook.code()

    code += f"\n{suffix}"

    # work around issue with latest black
    if sys.platform == "win32" and "HOME" in os.environ:
        os.environ["USERPROFILE"] = os.environ["HOME"]
    with open(path, "wb") as file:
        file.write(code.encode("utf8"))
    subprocess.run([sys.executable, "-m", "black", "-q", path], check=True)
