# Copyright: Daveight and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
"""
Template Generator API
"""

from abc import abstractmethod

from testing.framework.types import TestSuite
from testing.framework.syntax.syntax_tree import SyntaxTree


class TemplateGenerator:
    """
    Base class for a solution template generation
    """

    @abstractmethod
    def get_template(self, tree: SyntaxTree, ts: TestSuite) -> str:
        """
        Generates a code template for a single quiz and specific language
        :param tree: source syntax tree
        :param ts: source test suite
        :return solution template source code
        """
        pass
