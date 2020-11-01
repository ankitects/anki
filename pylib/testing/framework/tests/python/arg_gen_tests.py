import unittest

from testing.framework.langs.python.python_test_arg_gen import PythonTestArgGenerator
from testing.framework.syntax.syntax_tree import SyntaxTree


class PythonArgGenTests(unittest.TestCase):

    def evaluate_generator(self, type_expression, expected_type, expected_name=''):
        tree = SyntaxTree.of(type_expression)
        generator = PythonTestArgGenerator()
        args, result_type = generator.get_args(tree)
        self.assertEquals(len(args), 1)
        self.assertEquals(expected_type, args[0].arg_type)
        self.assertEquals(expected_name, args[0].arg_name)

    def test_array_of_integers(self):
        self.evaluate_generator(['array(array(int))[a]', 'int'], 'List[List[int]]', 'a')

    def test_list_of_integer(self):
        self.evaluate_generator(['list(int)[a]', 'int'], 'List[int]', 'a')

    def test_simple_int(self):
        self.evaluate_generator(['int', 'int'], 'int')

    def test_custom_object(self):
        self.evaluate_generator(['object(int[a],int[b])<Edge>[a]', 'int'], 'Edge', 'a')

    def test_object_list(self):
        self.evaluate_generator(['list(object(int[a],int[b])<Edge>)[a]', 'int'], 'List[Edge]', 'a')

    def test_map(self):
        self.evaluate_generator(['map(int,int)[a]', 'int'], 'Dict[int, int]', 'a')
