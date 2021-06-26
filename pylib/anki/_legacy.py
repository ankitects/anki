# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from typing import Any, Callable, Dict, Optional, Tuple, Union

import stringcase

VariableTarget = Tuple[Any, str]
DeprecatedAliasTarget = Union[Callable, VariableTarget]


def _target_to_string(target: DeprecatedAliasTarget) -> str:
    if name := getattr(target, "__name__", None):
        return name
    else:
        return target[1]  # type: ignore


class DeprecatedNamesMixin:
    "Expose instance methods/vars as camelCase for legacy callers."

    _deprecated_aliases: Dict[str, str] = {}

    def __getattr__(self, name: str) -> Any:
        remapped = self._deprecated_aliases.get(name) or stringcase.snakecase(name)
        if remapped == name:
            raise AttributeError

        out = getattr(self, remapped)
        print(f"please use {remapped} instead of {name} on {self}")
        return out

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
        def decorated_func(*args: Any, **kwargs: Any) -> Any:
            if replaced_by:
                doc = f"please use {replaced_by.__name__} instead."
            else:
                doc = info
            print(
                f"'{func.__name__}' is deprecated, and will be removed in the future: ",
                doc,
            )
            return func(*args, **kwargs)

        return decorated_func

    return decorator
