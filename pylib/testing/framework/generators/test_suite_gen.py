from abc import ABC, abstractmethod
from typing import Dict

from testing.framework.dto.test_suite import TestSuite
from testing.framework.syntax.syntax_tree import SyntaxTree


class TestSuiteGenerator(ABC):
    """
    Base class for a test suite code generators.
    """

    @abstractmethod
    def generate_testing_src(self, solution_src: str, ts: TestSuite, tree: SyntaxTree, msg: Dict[str, str]) -> str:
        """
        Generates full source code for a particular test suite, ready to compile and run
        :param msg: map containing messages which will be displayed during the testing
        :param ts: input test suite
        :param tree: input source syntax tree
        :param solution_src: input user source code
        :return: full testing source code for the user solution provided
        """
        pass

