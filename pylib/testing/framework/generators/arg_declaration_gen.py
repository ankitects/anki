from abc import abstractmethod, ABC
from typing import List, Tuple

from testing.framework.dto.test_arg import TestArg
from testing.framework.syntax.syntax_tree import SyntaxTree, SyntaxTreeVisitor


class ArgNameGenerator:
    def __init__(self):
        self.n = 0

    def get_name(self):
        self.n += 1
        return 'var' + str(self.n)


class ArgDeclarationGenerator(SyntaxTreeVisitor, ABC):

    def __init__(self):
        self.name_generator = ArgNameGenerator()

    def get_arg_declarations(self, tree: SyntaxTree) -> Tuple[List[TestArg], str]:
        args = []
        for node in tree.nodes:
            name = node.name
            if name is None or name == '':
                name = self.name_generator.get_name()
            args.append(TestArg(self.render(node), name))
        return args[:-1], args[-1].arg_type
