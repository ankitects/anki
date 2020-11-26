from typing import List, Dict

from testing.framework.generators.arg_declaration_gen import ArgDeclarationGenerator
from testing.framework.generators.user_type_declaration_gen import UserTypeDeclarationGenerator
from testing.framework.syntax.syntax_tree import SyntaxTree


class JavaUserTypeGenerator(UserTypeDeclarationGenerator):

    CLASS_TEMPLATE = '''public static class {} {{\n{}\n   public {}({}) {{\n{}\n   }}\n}}'''

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

    def visit_bool(self, node, data):
        return self.arg_generator.visit_bool(node, data)

    def visit_float(self, node, data):
        return self.arg_generator.visit_float(node, data)

    def visit_int(self, node, data):
        return self.arg_generator.visit_int(node, data)

    def visit_obj(self, node: SyntaxTree, registry):
        fields = []
        for child in node.nodes:
            fields.append([self.render(child, registry), child.name])

        fields_src = '\n'.join(' ' * 3 + field[0] + ' ' + field[1] + ';' for field in fields)
        args_src = ', '.join(field[0] + ' ' + field[1] for field in fields)
        fields_init_src = '\n'.join(' ' * 6 + 'this.' + field[1] + ' = ' + field[1] + ';' for field in fields)
        type_src = self.CLASS_TEMPLATE.format(node.node_type, fields_src, node.node_type, args_src, fields_init_src)
        type_name = node.node_type
        registry[type_name] = type_src

        return type_name
