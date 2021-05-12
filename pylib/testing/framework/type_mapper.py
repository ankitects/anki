# Copyright: Daveight and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
"""
Type Mapper API
"""
from abc import ABC
from typing import Tuple, List, Dict

from testing.framework.types import Arg
from testing.framework.syntax.syntax_tree import SyntaxTreeVisitor, SyntaxTree


def make_idx(start: int = 0):
    """
    Creates counter function

    :param start: starting number
    :return: counter function, which counts every its invocation and return a counter's value
    """
    idx = start

    def counter():
        """
        self-incrementing counter function, uses context variable idx
        which is get incremented on every function's invocation

        :return: next counter's value
        """
        nonlocal idx
        val = idx
        idx += 1
        return val

    return counter


class TypeMapper(SyntaxTreeVisitor, ABC):
    """
    Base class for language specific type mappers.

    TypeMapper maps AnkiCodee's types to a language specific types. It is used during the generation of
    a solution's template, to build correct argument types.

    NOTE: for a non-standard types (such as classes or structs) it also builds a class definition and puts
    it to a type registry.
    """

    def get_args(self, tree: SyntaxTree, registry: Dict[str, str] = None) -> Tuple[List[Arg], Dict[str, str]]:
        """
        Provides arguments type mappings for a specific language

        :param tree: source syntax tree
        :param registry: dictionary holding custom type definitions (structs, classes, etc...)
        :return: tuple of argument type mapper list, map with type definitions (type name -> type definition)
        """
        if registry is None:
            registry = {}
        idx = make_idx()
        return [Arg(n.name if n.name else f'var{idx()}', self.render(n, registry)) for n in tree.nodes], registry
