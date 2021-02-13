import unittest

from testing.framework.langs.python.python_converter_gen import PythonConverterGenerator
from testing.framework.syntax.syntax_tree import SyntaxTree


class PythonTestCaseGeneratorTest(unittest.TestCase):

    def evaluate_generator(self, type_expression, expected):
        tree = SyntaxTree.of(type_expression)
        generator = PythonConverterGenerator()
        initializer = ''.join(generator.render(node) for node in tree.nodes)
        self.assertEqual(initializer, expected)

    def test_array_of_integers(self):
        self.evaluate_generator(['array(array(int))[a]'],
                                'ListConverter(ListConverter(IntegerConverter()))')

    def test_list_of_integer(self):
        self.evaluate_generator(['list(int)[a]'],
                                'ListConverter(IntegerConverter())')

    def test_array_of_integer(self):
        self.evaluate_generator(['array(int)[a]'],
                                'ListConverter(IntegerConverter())')

    def test_list_with_nested_array(self):
        self.evaluate_generator(['list(array(array(int)))[a]'],
                                'ListConverter(ListConverter(ListConverter(IntegerConverter())))')

    def test_array_with_nested_list(self):
        self.evaluate_generator(['array(list(int))[a]'],
                                'ListConverter(ListConverter(IntegerConverter()))')

    def test_array_of_lists_of_arrays(self):
        self.evaluate_generator(['list(array(list(int)))[a]'],
                                'ListConverter(ListConverter(ListConverter(IntegerConverter())))')

    def test_obj_simple(self):
        self.evaluate_generator(['object(int[a],int[b])<Edge>[a]'],
                                'ClassConverter([IntegerConverter(), IntegerConverter()], Edge)')

    def test_obj_nested_array(self):
        self.evaluate_generator(['object(array(int[a]),int[b])<Edge>[a]'],
                                'ClassConverter([ListConverter(IntegerConverter()), IntegerConverter()], Edge)')

    def test_obj_nested_list(self):
        self.evaluate_generator(['object(list(int[a]),int[b])<Edge>[a]'],
                                'ClassConverter([ListConverter(IntegerConverter()), IntegerConverter()], Edge)')

    def test_obj_nested_object(self):
        self.evaluate_generator(['object(object(int[a])<Node>[t],int[b])<Edge>[a]'],
                                'ClassConverter([ClassConverter([IntegerConverter()], Node), IntegerConverter()], Edge)')

    def test_list_of_objects(self):
        self.evaluate_generator(['list(object(int[a])<Edge>)[a]'],
                                'ListConverter(ClassConverter([IntegerConverter()], Edge))')

    def test_array_of_objects(self):
        self.evaluate_generator(['array(object(int[a])<Edge>)[a]'],
                                'ListConverter(ClassConverter([IntegerConverter()], Edge))')

