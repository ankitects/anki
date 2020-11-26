from testing.framework.generators.arg_declaration_gen import ArgDeclarationGenerator
from testing.framework.syntax.syntax_tree import SyntaxTree


class PythonArgDeclarationGenerator(ArgDeclarationGenerator):
    def visit_array(self, node: SyntaxTree, data_item):
        if len(node.nodes) != 1:
            raise Exception('Array can have only 1 inner-type')
        return 'List[' + self.render(node.first_child(), data_item) + ']'

    def visit_list(self, node: SyntaxTree, data_item):
        if len(node.nodes) != 1:
            raise Exception('List can have only 1 inner-type')
        return 'List[' + self.render(node.first_child(), data_item) + ']'

    def visit_map(self, node: SyntaxTree, data):
        if len(node.nodes) != 2:
            raise Exception('Map inner-types count must be 2')
        return 'Dict[' + self.render(node.first_child()) + ', ' + self.render(node.second_child()) + ']'

    def visit_int(self, node, data):
        return 'int'

    def visit_bool(self, node, data):
        return 'bool'

    def visit_float(self, node, data):
        return 'float'

    def visit_string(self, node, data):
        return 'str'

    def visit_obj(self, node, data):
        return node.node_type
