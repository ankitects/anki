from testing.framework.dto.test_case import TestCase
from testing.framework.generators.test_case_gen import TestCaseGenerator
from testing.framework.syntax.syntax_tree import SyntaxTree


class PythonTestCaseGenerator(TestCaseGenerator):
    def visit_array(self, node: SyntaxTree, data_item):
        if len(node.nodes) != 1:
            raise Exception('Array cannot contain more than 1 inner-type')
        return '[' + ','.join(self.render(node.first_child(), item) for item in data_item) + ']'

    def visit_list(self, node: SyntaxTree, data_item):
        if len(node.nodes) != 1:
            raise Exception('List cannot contain more than 1 inner-type')
        return '[' + ','.join(self.render(node.nodes[0], item) for item in data_item) + ']'

    def visit_map(self, node: SyntaxTree, data_item):
        if len(node.nodes) != 2:
            raise Exception('Map cannot contain more than 2 sub-nodes')
        entries = []
        for item in data_item:
            if not node.first_child().is_primitive_type():
                raise Exception('Unhashable type as a dict key')
            src = self.render(node.first_child(), item[0])
            src += ':'
            src += self.render(node.second_child(), item[1])
            entries.append(src)
        return '{' + ', '.join(entries) + '}'

    def visit_int(self, node: SyntaxTree, data):
        return 'int(' + str(data) + ')'

    def visit_float(self, node: SyntaxTree, data):
        return 'float(' + str(data) + ')'

    def visit_string(self, node: SyntaxTree, data):
        return 'str("' + str(data) + '")'

    def visit_obj(self, node: SyntaxTree, data_item):
        init_src = []
        for i, child in enumerate(node.nodes):
            init_src.append(self.render(child, data_item[i]))
        return node.node_type + '(' + ','.join(init_src) + ')'
