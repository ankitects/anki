from typing import List

from testing.framework.dto.test_case import TestCase
from testing.framework.syntax.syntax_tree import SyntaxTree, SyntaxTreeVisitor


class TestCaseGenerator(SyntaxTreeVisitor):

   def get_test_case(self, tree: SyntaxTree, test_data_row: List) -> TestCase:
      result = []
      for i, node in enumerate(tree.nodes):
         result.append(''.join(self.render(node, test_data_row[i])))
      return TestCase(result[:-1], result[-1])
