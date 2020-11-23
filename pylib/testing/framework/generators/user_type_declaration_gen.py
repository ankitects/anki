from abc import abstractmethod
from typing import List, Dict

from testing.framework.syntax.syntax_tree import SyntaxTree, SyntaxTreeVisitor


class UserTypeDeclarationGenerator(SyntaxTreeVisitor):

    def get_user_type_declarations(self, tree: SyntaxTree) -> Dict[str, str]:
        user_types = {}
        for node in tree.nodes:
            self.render(node, user_types)
        return user_types
