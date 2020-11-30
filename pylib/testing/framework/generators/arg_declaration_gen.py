from abc import abstractmethod, ABC
from typing import List, Tuple

from testing.framework.dto.test_arg import TestArg
from testing.framework.syntax.syntax_tree import SyntaxTree, SyntaxTreeVisitor


class ArgDeclarationGenerator(SyntaxTreeVisitor, ABC):

    def get_arg_declarations(self, tree: SyntaxTree) -> Tuple[List[TestArg], str]:
        i = 0
        args = []
        for node in tree.nodes:
            name = node.name
            if name is None or name == '':
                name = 'var' + str(i)
                i += 1
            args.append(TestArg(self.render(node), name))
        return args[:-1], args[-1].arg_type
