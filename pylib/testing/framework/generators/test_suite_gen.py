from abc import ABC, abstractmethod

from testing.framework.dto.test_suite import TestSuite
from testing.framework.syntax.syntax_tree import SyntaxTree


class TestSuiteGenerator(ABC):
    """
    Base class for a test suite code generators.
    """

    @abstractmethod
    def generate_testing_src(self, solution_src: str, ts: TestSuite, tree: SyntaxTree) -> str:
        """
        Generates full source code for a particular test suite, ready to compile and run
        :param ts: input test suite
        :param tree: input source syntax tree
        :param solution_src: input user source code
        :return: full testing source code for the user solution provided
        """
        pass

