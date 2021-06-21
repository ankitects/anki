import unittest

from testing.framework.java.java_type_mapper import JavaTypeMapper
from testing.framework.syntax.syntax_tree import SyntaxTree
from testing.framework.tests.test_utils import GeneratorTestCase


class JavaTypeMapperTests(GeneratorTestCase):
    def setUp(self) -> None:
        self.type_mapper = JavaTypeMapper()

    def test_array_of_integers(self):
        tree = SyntaxTree.of(['array(array(int))[a]'])
        args, _ = self.type_mapper.get_args(tree)
        self.assertEqual(1, len(args))
        self.assertEqual('int[][]', args[0].type)
        self.assertEqual('a', args[0].name)

    def test_list_of_integer(self):
        tree = SyntaxTree.of(['list(int)[a]'])
        args, _ = self.type_mapper.get_args(tree)
        self.assertEqual(1, len(args))
        self.assertEqual('List<Integer>', args[0].type)
        self.assertEqual('a', args[0].name)

    def test_simple_int(self):
        tree = SyntaxTree.of(['int'])
        args, _ = self.type_mapper.get_args(tree)
        self.assertEqual(1, len(args))
        self.assertEqual('int', args[0].type)

    def test_class(self):
        tree = SyntaxTree.of(['object(int[a],int[b])<Edge>[a]'])
        args, type_defs = self.type_mapper.get_args(tree)
        self.assertEqual(1, len(args))
        self.assertEqual('Edge', args[0].type)
        self.assertEqual('a', args[0].name)
        self.assertEqual(1, len(type_defs.keys()))
        self.assertEqual('''class Edge {\n\tint a;\n\tint b;\n}\n''', type_defs['Edge'])

    def test_object_list(self):
        tree = SyntaxTree.of(['list(object(int[a],int[b])<Edge>)[a]'])
        args, type_defs = self.type_mapper.get_args(tree)
        self.assertEqual(1, len(args))
        self.assertEqual('List<Edge>', args[0].type)
        self.assertEqual('a', args[0].name)
        self.assertEqual(1, len(type_defs.keys()))
        self.assertEqual('''class Edge {\n\tint a;\n\tint b;\n}\n''', type_defs['Edge'])

    def test_map(self):
        tree = SyntaxTree.of(['map(string, list(object(int[a],int[b])<Edge>))[a]'])
        args, type_defs = self.type_mapper.get_args(tree)
        self.assertEqual(1, len(args))
        self.assertEqual('Map<String, List<Edge>>', args[0].type)
        self.assertEqual('a', args[0].name)
        self.assertEqual(1, len(type_defs.keys()))
        self.assertEqual('''class Edge {\n\tint a;\n\tint b;\n}\n''', type_defs['Edge'])

    def test_linked_list(self):
        tree = SyntaxTree.of(['linked_list(int)'])
        args, type_defs = self.type_mapper.get_args(tree)
        self.assertEqual(1, len(args))
        self.assertEqual('ListNode<Integer>', args[0].type)
        self.assertEqual(1, len(type_defs.keys()))
        self.assertEqualsIgnoreWhiteSpaces('''
            class ListNode<T> {
                public T data;
                public ListNode<T> next;
            }''', type_defs['linked_list'])

