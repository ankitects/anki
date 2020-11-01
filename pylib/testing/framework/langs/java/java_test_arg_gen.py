from typing import List, Tuple

from testing.framework.dto.test_arg import TestArg
from testing.framework.generators.test_arg_gen import TestArgGenerator
from testing.framework.syntax.syntax_tree import SyntaxTree


class JavaTestArgGenerator(TestArgGenerator):

    def visit_array(self, node: SyntaxTree, data_item):
        if len(node.nodes) != 1:
            raise Exception('Array can have only 1 inner-type')
        return self.render(node.first_child(), data_item) + '[]'

    def visit_list(self, node: SyntaxTree, data_item):
        if len(node.nodes) != 1:
            raise Exception('List can have only 1 inner-type')
        return 'List<' + self.render(node.first_child()) + '>'

    def visit_map(self, node, data):
        if len(node.nodes) != 2:
            raise Exception('Invalid inner-type of map count, must be 2')
        return 'Map<' + self.render(node.first_child()) + ', ' + self.render(node.second_child()) + '>'

    def visit_int(self, node, data_item):
        if node.parent.is_root() or node.parent.is_array_type() or node.parent.is_user_type:
            return 'int'
        elif node.parent.is_container_type():
            return 'Integer'
        else:
            raise Exception('not supported parent type for int')

    def visit_float(self, node, data_item):
        if node.parent.is_array_type() or node.parent.is_user_type:
            return 'float'
        elif node.parent.is_container_type():
            return 'Float'
        else:
            raise Exception('not supported parent type for float')

    def visit_string(self, node, data):
        return 'String'

    def visit_obj(self, node, data):
        return node.node_type
