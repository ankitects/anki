from abc import abstractmethod
from typing import List, Tuple

from testing.framework.dto.test_arg import TestArg
from testing.framework.syntax.syntax_tree import SyntaxTree, SyntaxTreeVisitor


class TestArgGenerator(SyntaxTreeVisitor):

    def get_args(self, tree: SyntaxTree) -> Tuple[List[TestArg], str]:
        args = []
        for node in tree.nodes:
            args.append(TestArg(self.render(node), node.name))
        return args[:-1], args[-1].arg_type
