import unittest

from testing.framework.langs.java.java_type_gen import JavaTypeGenerator
from testing.framework.syntax.syntax_tree import SyntaxTree
from testing.framework.syntax.utils import get_arg_declarations


class JavaTypeGenTests(unittest.TestCase):

    def evaluate_generator(self, type_expression, expected_type, expected_name=''):
        tree = SyntaxTree.of(type_expression)
        generator = JavaTypeGenerator()
        args, result_type = get_arg_declarations(tree, generator)
        self.assertEqual(len(args), 1)
        self.assertEqual(expected_type, args[0].arg_type)
        self.assertEqual(expected_name, args[0].arg_name)

    def test_array_of_integers(self):
        self.evaluate_generator(['array(array(int))[a]', 'int'], 'int[][]', 'a')

    def test_list_of_integer(self):
        self.evaluate_generator(['list(int)[a]', 'int'], 'List<Integer>', 'a')

    def test_simple_int(self):
        self.evaluate_generator(['int', 'int'], 'int', 'var0')

    def test_custom_object(self):
        self.evaluate_generator(['object(int[a],int[b])<Edge>[a]', 'int'], 'Edge', 'a')

    def test_object_list(self):
        self.evaluate_generator(['list(object(int[a],int[b])<Edge>)[a]', 'int'], 'List<Edge>', 'a')

    def test_map(self):
        self.evaluate_generator(['map(int,int)[a]', 'int'], 'Map<Integer, Integer>', 'a')
