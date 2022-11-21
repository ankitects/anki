# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import functools
import os
import pathlib
import sys
import traceback
from typing import TYPE_CHECKING, Any, Callable, Union

from anki._vendor import stringcase  # type: ignore

sys.modules["stringcase"] = stringcase

VariableTarget = tuple[Any, str]
DeprecatedAliasTarget = Union[Callable, VariableTarget]


def _target_to_string(target: DeprecatedAliasTarget | None) -> str:
    if target is None:
        return ""
    if name := getattr(target, "__name__", None):
        return name
    return target[1]  # type: ignore


def partial_path(full_path: str, components: int) -> str:
    path = pathlib.Path(full_path)
    return os.path.join(*path.parts[-components:])


def print_deprecation_warning(msg: str, frame: int = 1) -> None:
    # skip one frame to get to caller
    # then by default, skip one more frame as caller themself usually wants to
    # print their own caller
    path, linenum, _, _ = traceback.extract_stack(limit=frame + 2)[0]
    path = partial_path(path, components=3)
    print(f"{path}:{linenum}:{msg}")


def _print_warning(old: str, doc: str, frame: int = 1) -> None:
    return print_deprecation_warning(f"{old} is deprecated: {doc}", frame=frame + 1)


def _print_replacement_warning(old: str, new: str, frame: int = 1) -> None:
    doc = f"please use '{new}'" if new else "please implement your own"
    _print_warning(old, doc, frame=frame + 1)


def _get_remapped_and_replacement(
    mixin: DeprecatedNamesMixin | DeprecatedNamesMixinForModule, name: str
) -> tuple[str, str | None]:
    if some_tuple := mixin._deprecated_attributes.get(name):
        return some_tuple

    remapped = mixin._deprecated_aliases.get(name) or stringcase.snakecase(name)
    if remapped == name:
        raise AttributeError
    return (remapped, remapped)


class DeprecatedNamesMixin:
    "Expose instance methods/vars as camelCase for legacy callers."

    # deprecated name -> new name
    _deprecated_aliases: dict[str, str] = {}
    # deprecated name -> [new internal name, new name shown to user]
    _deprecated_attributes: dict[str, tuple[str, str | None]] = {}

    # TYPE_CHECKING check is required for https://github.com/python/mypy/issues/13319
    if not TYPE_CHECKING:

        def __getattr__(self, name: str) -> Any:
            try:
                remapped, replacement = _get_remapped_and_replacement(self, name)
                out = getattr(self, remapped)
            except AttributeError:
                raise AttributeError(
                    f"'{self.__class__.__name__}' object has no attribute '{name}'"
                ) from None

            _print_replacement_warning(name, replacement)
            return out

    @classmethod
    def register_deprecated_aliases(cls, **kwargs: DeprecatedAliasTarget) -> None:
        """Manually add aliases that are not a simple transform.

        Either pass in a method, or a tuple of (variable, "variable"). The
        latter is required because we want to ensure the provided arguments
        are valid symbols, and we can't get a variable's name easily.
        """
        cls._deprecated_aliases = {k: _target_to_string(v) for k, v in kwargs.items()}

    @classmethod
    def register_deprecated_attributes(
        cls,
        **kwargs: tuple[DeprecatedAliasTarget, DeprecatedAliasTarget | None],
    ) -> None:
        """Manually add deprecated attributes without exact substitutes.

        Pass a tuple of (alias, replacement), where alias is the attribute's new
        name (by convention: snakecase, prepended with '_legacy_'), and
        replacement is any callable to be used instead in new code or None.
        Also note the docstring of `register_deprecated_aliases`.

        E.g. given `def oldFunc(args): return new_func(additionalLogic(args))`,
        rename `oldFunc` to `_legacy_old_func` and call
        `register_deprecated_attributes(oldFunc=(_legacy_old_func, new_func))`.
        """
        cls._deprecated_attributes = {
            k: (_target_to_string(v[0]), _target_to_string(v[1]))
            for k, v in kwargs.items()
        }


class DeprecatedNamesMixinForModule:
    """Provides the functionality of DeprecatedNamesMixin for modules.

    It can be invoked like this:
    ```
        _deprecated_names = DeprecatedNamesMixinForModule(globals())
        _deprecated_names.register_deprecated_aliases(...
        _deprecated_names.register_deprecated_attributes(...

        if not TYPE_CHECKING:
            def __getattr__(name: str) -> Any:
                return _deprecated_names.__getattr__(name)
    ```
    See DeprecatedNamesMixin for more documentation.
    """

    def __init__(self, module_globals: dict[str, Any]) -> None:
        self.module_globals = module_globals
        self._deprecated_aliases: dict[str, str] = {}
        self._deprecated_attributes: dict[str, tuple[str, str | None]] = {}

    if not TYPE_CHECKING:

        def __getattr__(self, name: str) -> Any:
            try:
                remapped, replacement = _get_remapped_and_replacement(self, name)
                out = self.module_globals[remapped]
            except (AttributeError, KeyError):
                raise AttributeError(
                    f"Module '{self.module_globals['__name__']}' has no attribute '{name}'"
                ) from None

            # skip an additional frame as we are called from the module `__getattr__`
            _print_replacement_warning(name, replacement, frame=2)
            return out

    def register_deprecated_aliases(self, **kwargs: DeprecatedAliasTarget) -> None:
        self._deprecated_aliases = {k: _target_to_string(v) for k, v in kwargs.items()}

    def register_deprecated_attributes(
        self,
        **kwargs: tuple[DeprecatedAliasTarget, DeprecatedAliasTarget | None],
    ) -> None:
        self._deprecated_attributes = {
            k: (_target_to_string(v[0]), _target_to_string(v[1]))
            for k, v in kwargs.items()
        }


def deprecated(replaced_by: Callable | None = None, info: str = "") -> Callable:
    """Print a deprecation warning, telling users to use `replaced_by`, or show `doc`."""

    def decorator(func: Callable) -> Callable:
        @functools.wraps(func)
        def decorated_func(*args: Any, **kwargs: Any) -> Any:
            if info:
                _print_warning(f"{func.__name__}()", info)
            else:
                _print_replacement_warning(func.__name__, replaced_by.__name__)

            return func(*args, **kwargs)

        return decorated_func

    return decorator


def deprecated_keywords(**replaced_keys: str) -> Callable:
    """Pass `oldKey="new_key"` to map the former to the latter, if passed to the
    decorated function as a key word, and print a deprecation warning.
    """

    def decorator(func: Callable) -> Callable:
        @functools.wraps(func)
        def decorated_func(*args: Any, **kwargs: Any) -> Any:
            updated_kwargs = {}
            for key, val in kwargs.items():
                if replacement := replaced_keys.get(key):
                    _print_replacement_warning(key, replacement)
                updated_kwargs[replacement or key] = val

            return func(*args, **updated_kwargs)

        return decorated_func

    return decorator
