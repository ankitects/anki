from typing import List

from testing.framework.dto.test_case import TestCase
from testing.framework.generators.test_case_gen import TestCaseGenerator
from testing.framework.syntax.syntax_tree import SyntaxTree


class JavaTestCaseGenerator(TestCaseGenerator):

    def visit_array(self, node: SyntaxTree, data_array):
        if not isinstance(data_array, list):
            raise Exception('Input data is not of list type')
        if len(node.nodes) != 1:
            raise Exception('Array cannot contain more than 1 inner-type')
        type_src = ''
        if node.parent.is_root() or node.parent.is_container_type():
            type_src += 'new '
        type_src += self.render(node.first_child(), data_array[0])[0] + '[]'
        init_src = '{' + ','.join(self.render(node.first_child(), item)[1] for item in data_array) + '}'
        if node.parent.is_container_type():
            type_src += '[]'
            init_src = '{' + init_src + '}'
        return type_src, init_src

    def visit_list(self, node: SyntaxTree, data_list):
        if not isinstance(data_list, list):
            raise Exception('Input data is not of list type')
        if len(node.nodes) != 1:
            raise Exception('List cannot contain more than 1 inner-type')
        type_src = ''
        if node.parent.is_array_type():
            type_src = 'List'
        init_src = self.render(node.first_child(), data_list)[0]
        init_src += ','.join([self.render(node.first_child(), item)[1] for item in data_list])
        return type_src, 'List.of({})'.format(init_src)

    def visit_map(self, node: SyntaxTree, key_value):
        if not isinstance(key_value, list):
            raise Exception('Input data is not of list type')
        if len(node.nodes) != 2:
            raise Exception('Map cannot contain more than 2 sub-nodes')
        type_src = ''
        if node.parent.is_array_type():
            type_src = 'Map'
        entries = []
        for item in key_value:
            result = self.render(node.first_child(), item[0])
            src = result[0] + result[1]
            src += ','
            result = self.render(node.second_child(), item[1])
            src += result[0] + result[1]
            entries.append('''Map.entry({})'''.format(src))
        init_src = '''Map.ofEntries({})'''.format(','.join(entries))
        return type_src, init_src

    def visit_int(self, node: SyntaxTree, data_item):
        type_src = ''
        if node.parent.is_array_type():
            type_src = 'int'
        init_src = '(int)' + str(data_item)
        return type_src, init_src

    def visit_float(self, node: SyntaxTree, data_item):
        type_src = ''
        if node.parent.is_array_type():
            type_src = 'float'
        init_src = '(float)' + str(data_item)
        return type_src, init_src

    def visit_string(self, node: SyntaxTree, data_item):
        type_src = ''
        if node.parent.is_array_type():
            type_src = 'String'
        init_src = '"' + str(data_item) + '"'
        return type_src, init_src

    def visit_obj(self, node: SyntaxTree, data_item):
        if not isinstance(data_item, list):
            raise Exception('Input data is not of list type')
        type_src = ''
        if node.parent.is_array_type():
            type_src = node.node_type
        init_src = []
        for i, child in enumerate(node.nodes):
            result = self.render(child, data_item[i])
            init_src.append(result[0] + result[1])
        return type_src, 'new ' + node.node_type + '(' + ','.join(init_src) + ')'

