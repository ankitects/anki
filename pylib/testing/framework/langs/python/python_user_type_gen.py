from typing import Dict

from testing.framework.generators.arg_declaration_gen import ArgDeclarationGenerator
from testing.framework.generators.user_type_declaration_gen import UserTypeDeclarationGenerator
from testing.framework.syntax.syntax_tree import SyntaxTree


class PythonUserTypeGenerator(UserTypeDeclarationGenerator):

    TEMPLATE_SRC = '''class {}:
   def __init__(self, {}):
{}'''

    def __init__(self, arg_generator: ArgDeclarationGenerator):
        self.arg_generator = arg_generator

    def visit_array(self, node: SyntaxTree, data):
        return self.arg_generator.visit_array(node, data)

    def visit_list(self, node: SyntaxTree, data):
        return self.arg_generator.visit_array(node, data)

    def visit_map(self, node, data):
        return self.arg_generator.visit_map(node, data)

    def visit_string(self, node, data):
        return self.arg_generator.visit_string(node, data)

    def visit_float(self, node, data):
        return self.arg_generator.visit_float(node, data)

    def visit_int(self, node, data):
        return self.arg_generator.visit_int(node, data)

    def visit_bool(self, node, data):
        return self.arg_generator.visit_bool(node, data)

    def visit_obj(self, node: SyntaxTree, type_registry: Dict[str, str]):
        fields = []
        for child in node.nodes:
            fields.append([self.render(child, type_registry), child.name])

        args_src = ', '.join(field[1] + ': ' + field[0] for field in fields)
        init_src = '\n'.join('      self.' + field[1] + ' = ' + field[1] for field in fields)
        type_registry[node.node_type] = self.TEMPLATE_SRC.format(node.node_type, args_src, init_src)

        return node.node_type
