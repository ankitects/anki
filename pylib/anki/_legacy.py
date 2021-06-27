# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import functools
import os
import pathlib
import traceback
from typing import Any, Callable, Dict, Optional, Tuple, Union, no_type_check

import stringcase

VariableTarget = Tuple[Any, str]
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

    # the @no_type_check lines are required to prevent mypy allowing arbitrary
    # attributes on the consuming class

    _deprecated_aliases: Dict[str, str] = {}

    @no_type_check
    def __getattr__(self, name: str) -> Any:
        remapped = self._deprecated_aliases.get(name) or stringcase.snakecase(name)
        if remapped == name:
            raise AttributeError

        out = getattr(self, remapped)
        _print_warning(f"'{name}'", f"please use '{remapped}'")

        return out

    @no_type_check
    @classmethod
    def register_deprecated_aliases(cls, **kwargs: DeprecatedAliasTarget) -> None:
        """Manually add aliases that are not a simple transform.

        Either pass in a method, or a tuple of (variable, "variable"). The
        latter is required because we want to ensure the provided arguments
        are valid symbols, and we can't get a variable's name easily.
        """
        cls._deprecated_aliases = {k: _target_to_string(v) for k, v in kwargs.items()}


def deprecated(replaced_by: Optional[Callable] = None, info: str = "") -> Callable:
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
