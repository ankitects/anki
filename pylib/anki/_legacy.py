# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

# pylint: enable=invalid-name

from __future__ import annotations

import functools
import os
import pathlib
import traceback
from typing import Any, Callable, Union, no_type_check

import stringcase

VariableTarget = tuple[Any, str]
DeprecatedAliasTarget = Union[Callable, VariableTarget]


def _target_to_string(target: DeprecatedAliasTarget) -> str:
    if name := getattr(target, "__name__", None):
        return name
    else:
        return target[1]  # type: ignore


def partial_path(full_path: str, components: int) -> str:
    path = pathlib.Path(full_path)
    return os.path.join(*path.parts[-components:])


def print_deprecation_warning(msg: str, frame: int = 2) -> None:
    path, linenum, _, _ = traceback.extract_stack(limit=5)[frame]
    path = partial_path(path, components=3)
    print(f"{path}:{linenum}:{msg}")


def _print_warning(old: str, doc: str) -> None:
    return print_deprecation_warning(f"{old} is deprecated: {doc}", frame=1)


class DeprecatedNamesMixin:
    "Expose instance methods/vars as camelCase for legacy callers."

    # deprecated name -> new name
    _deprecated_aliases: dict[str, str] = {}
    # deprecated name -> [new internal name, new name shown to user]
    _deprecated_attributes: dict[str, tuple[str, str]] = {}

    # the @no_type_check lines are required to prevent mypy allowing arbitrary
    # attributes on the consuming class

    @no_type_check
    def __getattr__(self, name: str) -> Any:
        remapped, replacement = self._get_remapped_and_replacement(name)

        out = getattr(self, remapped)
        _print_warning(f"'{name}'", f"please use '{replacement}'")

        return out

    @no_type_check
    def _get_remapped_and_replacement(self, name: str) -> tuple[str, str]:
        if some_tuple := self._deprecated_attributes.get(name):
            return some_tuple

        remapped = self._deprecated_aliases.get(name) or stringcase.snakecase(name)
        if remapped == name:
            raise AttributeError
        return (remapped, remapped)

    @no_type_check
    @classmethod
    def register_deprecated_aliases(cls, **kwargs: DeprecatedAliasTarget) -> None:
        """Manually add aliases that are not a simple transform.

        Either pass in a method, or a tuple of (variable, "variable"). The
        latter is required because we want to ensure the provided arguments
        are valid symbols, and we can't get a variable's name easily.
        """
        cls._deprecated_aliases = {k: _target_to_string(v) for k, v in kwargs.items()}

    @no_type_check
    @classmethod
    def register_deprecated_attributes(
        cls,
        **kwargs: tuple[DeprecatedAliasTarget, DeprecatedAliasTarget],
    ) -> None:
        """Manually add deprecated attributes without exact substitutes.

        Pass a tuple of (alias, replacement), where alias is the attribute's new
        name (by convention: snakecase, prepended with '_legacy_'), and
        replacement is any callable to be used instead in new code.
        Also note the docstring of `register_deprecated_aliases`.

        E.g. given `def oldFunc(args): return new_func(additionalLogic(args))`,
        rename `oldFunc` to `_legacy_old_func` and call
        `register_deprecated_attributes(oldFunc=(_legacy_old_func, new_func))`.
        """
        cls._deprecated_attributes = {
            k: (_target_to_string(v[0]), _target_to_string(v[1]))
            for k, v in kwargs.items()
        }


class DeprecatedNamesMixinStandalone(DeprecatedNamesMixin):
    """Provides the functionality of DeprecatedNamesMixin for modules.

    It can be invoked like this:
    ```
        _deprecated_names = DeprecatedNamesMixinStandalone(globals())
        _deprecated_names.register_deprecated_aliases(...
        _deprecated_names.register_deprecated_attributes(...

        @no_type_check
        def __getattr__(name: str) -> Any:
            return _deprecated_names.__getattr__(name)
    ```
    """

    def __init__(self, module_globals: dict[str, Any]) -> None:
        self.module_globals = module_globals

    def __getattr__(self, name: str) -> Any:
        remapped, replacement = self._get_remapped_and_replacement(name)
        if out := self.module_globals.get(remapped):
            _print_warning(f"'{name}'", f"please use '{replacement}'")
            return out

        raise AttributeError


def deprecated(replaced_by: Callable | None = None, info: str = "") -> Callable:
    """Print a deprecation warning, telling users to use `replaced_by`, or show `doc`."""

    def decorator(func: Callable) -> Callable:
        @functools.wraps(func)
        def decorated_func(*args: Any, **kwargs: Any) -> Any:
            if replaced_by:
                doc = f"please use {replaced_by.__name__} instead."
            else:
                doc = info

            _print_warning(f"{func.__name__}()", doc)

            return func(*args, **kwargs)

        return decorated_func

    return decorator
