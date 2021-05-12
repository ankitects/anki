# Copyright: Daveight and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
"""
Type Converter API
"""

from abc import ABC
from typing import List, Tuple
from testing.framework.types import ConverterFn
from testing.framework.syntax.syntax_tree import SyntaxTreeVisitor, SyntaxTree


class TypeConverter(SyntaxTreeVisitor, ABC):
    """
    Base class for language specific type converters.

    TypeConverter generates conversion functions which convert an input argument (always one)
    to a desired type.

    There are 2 types of converters: input and output
    Input converters convert input JSON arguments, from a test-case file to a solution's typed arguments.
    Output converters convert result value to a JSON type.

    Conversion functions are used during a test suite source code generation.
    """

    def get_converters(self, tree: SyntaxTree, registry: List[ConverterFn] = None) -> \
            Tuple[List[ConverterFn], List[ConverterFn]]:
        """
        Generates converter functions for the syntax tree

        :param tree: source syntax tree
        :param registry: holds list of all functions generated
        :return: tuple of top-level converters and list of all converters
        """
        if registry is None:
            registry = []
        converters = [self.render(n, registry) for n in tree.nodes]
        return converters, registry

    def render(self, tree: SyntaxTree, registry):
        """
        Extends the base classes render function, by adding of a result function to the registry

        :param tree: source syntax tree
        :param registry: list of all converters
        :return: converter
        """
        converter = super().render(tree, registry)
        registry.append(converter)
        return converter
