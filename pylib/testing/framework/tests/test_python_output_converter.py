import unittest

from testing.framework.python.python_output_converter import PythonOutputConverter
from testing.framework.types import ConverterFn
from testing.framework.syntax.syntax_tree import SyntaxTree


class PythonOutputConverterTests(unittest.TestCase):
    def setUp(self) -> None:
        self.converter = PythonOutputConverter()
        ConverterFn.reset_counter()

    def test_array_of_arrays_of_ints(self):
        tree = SyntaxTree.of(['array(array(int))[a]'])
        _, converters = self.converter.get_converters(tree)
        self.assertEqual(3, len(converters))
        self.assertEqual(ConverterFn('', 'return value', 'int', 'int'), converters[0])
        self.assertEqual(ConverterFn('', '''return [converter1(item) for item in value]''', 'List[int]', 'List[int]'), converters[1])
        self.assertEqual(ConverterFn('a', '''return [converter2(item) for item in value]''',
                                     'List[List[int]]', 'List[List[int]]'), converters[2])

    def test_object_conversion(self):
        tree = SyntaxTree.of(['object(int[a],int[b])<Edge>[a]'])
        _, converters = self.converter.get_converters(tree)
        self.assertEqual(3, len(converters))
        self.assertEqual(ConverterFn('a', 'return value', 'int', 'int'), converters[0])
        self.assertEqual(ConverterFn('b', 'return value', 'int', 'int'), converters[1])
        self.assertEqual(ConverterFn('a', '''
            result = []
            result.append(converter1(value.a))
            result.append(converter2(value.b))
            return result''', 'Edge', 'List'), converters[2])

    def test_obj_nested_list(self):
        tree = SyntaxTree.of(['object(list(int)[a],int[b])<Edge>[a]'])
        arg_converters, converters = self.converter.get_converters(tree)
        self.assertEqual(1, len(arg_converters))
        self.assertEqual(4, len(converters))
        self.assertEqual(ConverterFn('', 'return value', 'int', 'int'), converters[0])
        self.assertEqual(ConverterFn('a', 'return [converter1(item) for item in value]', 'List[int]', 'List[int]'),
                         converters[1])
        self.assertEqual(ConverterFn('b', 'return value', 'int', 'int'), converters[2])
        self.assertEqual(ConverterFn('a', '''
            result = []
            result.append(converter2(value.a))
            result.append(converter3(value.b))
            return result''', 'Edge', 'List'), converters[3])

    def test_map(self):
        tree = SyntaxTree.of(['map(string, object(list(int)[a],int[b])<Edge>)[a]'])
        arg_converters, converters = self.converter.get_converters(tree)
        self.assertEqual(1, len(arg_converters))
        self.assertEqual(6, len(converters))
        self.assertEqual(ConverterFn('', 'return value', 'str', 'str'), converters[0])
        self.assertEqual(ConverterFn('', 'return value', 'int', 'int'), converters[1])
        self.assertEqual(ConverterFn('a', 'return [converter2(item) for item in value]', 'List[int]', 'List[int]'),
                         converters[2])
        self.assertEqual(ConverterFn('b', 'return value', 'int', 'int'), converters[3])
        self.assertEqual(ConverterFn('', '''
            result = []
            result.append(converter3(value.a))
            result.append(converter4(value.b))
            return result''', 'Edge', 'List'), converters[4])
        self.assertEqual(ConverterFn('a', '''
            result = []
            for k in value:
                result.append(converter1(k))
                result.append(converter5(value[k]))
            return result''', 'Dict', 'List'), converters[5])

    def test_linked_list(self):
        tree = SyntaxTree.of(['linked_list(int)'])
        arg_converters, converters = self.converter.get_converters(tree)
        self.assertEqual(1, len(arg_converters))
        self.assertEqual(2, len(converters))
        self.assertEqual(ConverterFn('', 'return value', 'int', 'int'), converters[0])
        self.assertEqual(ConverterFn('', '''
            visited = set([])
            result = []
            while value is not None and value not in visited:
                result.append(converter1(value.data))
                visited.add(value)
                value = value.next
            return result
        ''', 'ListNode[int]', 'List[int]'), converters[1])

    def test_binary_tree(self):
        tree = SyntaxTree.of(['binary_tree(int)'])
        arg_converters, converters = self.converter.get_converters(tree)
        self.assertEqual(1, len(arg_converters))
        self.assertEqual(2, len(converters))
        self.assertEqual(ConverterFn('', 'return value', 'int', 'int'), converters[0])
        self.assertEqual(ConverterFn('', '''
            result = []
            queue = []
            visited = set([])
            queue.append(value)
            while queue:
                node = queue.pop(0)
                if node is not None:
                    visited.add(node)
                    result.append(converter1(node.data))
                else:
                    result.append(None)
                if node is not None and not node.left in visited:
                    queue.append(node.left)
                if node is not None and not node.right in visited:
                    queue.append(node.right)
            j = None
            for i in range(len(result) - 1, 0, -1):
                if result[i] is None:
                    j = i
                else:
                    break
            if j is not None:
                result = result[:j]
            return result
        ''', 'BinaryTreeNode[int]', 'List[int]'), converters[1])
