import unittest

from testing.framework.python.python_input_converter import PythonInputConverter
from testing.framework.types import ConverterFn
from testing.framework.syntax.syntax_tree import SyntaxTree


class PythonInputConverterTests(unittest.TestCase):

    def setUp(self) -> None:
        self.converter = PythonInputConverter()
        ConverterFn.reset_counter()

    def test_array_of_arrays_of_ints(self):
        tree = SyntaxTree.of(['array(array(int))[a]'])
        _, converters = self.converter.get_converters(tree)
        self.assertEqual(3, len(converters))
        self.assertEqual(ConverterFn('', '\treturn int(value)', '', 'int'), converters[0])
        self.assertEqual(ConverterFn('', '\treturn [converter1(item) for item in value]', '', 'List[int]'),
                         converters[1])
        self.assertEqual(ConverterFn('a', '\treturn [converter2(item) for item in value]', '', 'List[List[int]]'),
                         converters[2])

    def test_object_conversion(self):
        tree = SyntaxTree.of(['object(int[a],int[b])<Edge>[a]'])
        _, converters = self.converter.get_converters(tree)
        self.assertEqual(3, len(converters))
        self.assertEqual(ConverterFn('a', '\treturn int(value)', '', 'int'), converters[0])
        self.assertEqual(ConverterFn('b', '\treturn int(value)', '', 'int'), converters[1])
        self.assertEqual(ConverterFn('a', '\treturn Edge(converter1(value[0]),converter2(value[1]))', 'List', 'Edge'),
                         converters[2])

    def test_obj_nested_list(self):
        tree = SyntaxTree.of(['object(list(int)[a],int[b])<Edge>[a]'])
        arg_converters, converters = self.converter.get_converters(tree)
        self.assertEqual(1, len(arg_converters))
        self.assertEqual(4, len(converters))
        self.assertEqual(ConverterFn('', '\treturn int(value)', '', 'int'), converters[0])
        self.assertEqual(ConverterFn('a', '\treturn [converter1(item) for item in value]', '', 'List[int]'),
                         converters[1])
        self.assertEqual(ConverterFn('b', '\treturn int(value)', '', 'int'), converters[2])
        self.assertEqual(ConverterFn('a', '\treturn Edge(converter2(value[0]),converter3(value[1]))', 'List', 'Edge'),
                         converters[3])

    def test_map(self):
        tree = SyntaxTree.of(['map(string, object(list(int)[a],int[b])<Edge>)[a]'])
        arg_converters, converters = self.converter.get_converters(tree)
        self.assertEqual(1, len(arg_converters))
        self.assertEqual(6, len(converters))
        self.assertEqual(ConverterFn('', '\treturn str(value)', '', 'str'), converters[0])
        self.assertEqual(ConverterFn('', '''\treturn int(value)''', '', 'int'), converters[1])
        self.assertEqual(ConverterFn('a', '\treturn [converter2(item) for item in value]', '', 'List[int]'),
                         converters[2])
        self.assertEqual(ConverterFn('b', '''\treturn int(value)''', '', 'int'), converters[3])
        self.assertEqual(ConverterFn('', '\treturn Edge(converter3(value[0]),converter4(value[1]))', 'List', 'Edge'),
                         converters[4])
        self.assertEqual(ConverterFn('a', '''
            \tresult = {}
            \titerator = iter(value)
            \twhile True:
            \t\ttry:
            \t\t\tk = converter1(next(iterator))
            \t\t\tv = converter5(next(iterator))
            \t\t\tresult[k] = v
            \t\texcept StopIteration:
            \t\t\tbreak
            \treturn result''', 'List', 'Dict[str, Edge]'.strip()), converters[5])

    def test_linked_list(self):
        tree = SyntaxTree.of(['linked_list(string)'])
        arg_converters, converters = self.converter.get_converters(tree)
        self.assertEqual(1, len(arg_converters))
        self.assertEqual(2, len(converters))
        self.assertEqual(ConverterFn('', '\treturn str(value)', '', 'str'), converters[0])
        self.assertEqual(ConverterFn('', '''
            head = ListNode(None)
            node = head
            for item in value:
                nextNode = ListNode(converter1(item))
                node.next = nextNode
                node = nextNode
            return head.next
        ''', '', 'ListNode[str]'), converters[1])
