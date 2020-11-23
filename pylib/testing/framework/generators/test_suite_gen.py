from abc import ABC, abstractmethod
from typing import List, Dict

from testing.framework.dto.test_suite import TestSuite
from testing.framework.syntax.syntax_tree import SyntaxTree


class TestSuiteGenerator(ABC):

    @abstractmethod
    def generate_testing_src(self, solution_src: str, ts: TestSuite, tree: SyntaxTree, msg: Dict[str, str]) -> str:
        pass

