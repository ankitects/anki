import unittest

from testing.framework.langs.java.java_converter_gen import JavaConverterGenerator
from testing.framework.syntax.syntax_tree import SyntaxTree


class JavaTestCaseGeneratorTest(unittest.TestCase):

    def evaluate_generator(self, type_expression, expected):
        tree = SyntaxTree.of(type_expression)
        generator = JavaConverterGenerator()
        initializer = generator.render(tree.nodes[0])
        self.assertEqual(initializer, expected)

    def test_array_of_integers(self):
        self.evaluate_generator(['array(array(int))[a]'],
                                'new ArrayConverter(new ArrayConverter(new IntegerConverter()))')

    def test_list_of_integer(self):
        self.evaluate_generator(['list(int)[a]'],
                                'new ArrayListConverter(new IntegerConverter())')

    def test_array_of_integer(self):
        self.evaluate_generator(['array(int)[a]'],
                                'new ArrayConverter(new IntegerConverter())')

    def test_list_with_nested_array(self):
        self.evaluate_generator(['list(array(array(int)))[a]'],
                                'new ArrayListConverter(new ArrayConverter(new ArrayConverter(new IntegerConverter()'
                                ')))')

    def test_array_with_nested_list(self):
        self.evaluate_generator(['array(list(int))[a]'],
                                'new ArrayConverter(new ArrayListConverter(new IntegerConverter()))')

    def test_array_of_lists_of_arrays(self):
        self.evaluate_generator(['list(array(list(int)))[a]'],
                                'new ArrayListConverter(new ArrayConverter(new ArrayListConverter(new '
                                'IntegerConverter())))')

    def test_obj_simple(self):
        self.evaluate_generator(['object(int[a],int[b])<Edge>[a]'],
                                'new UserTypeConverter(Arrays.asList(new IntegerConverter(), new IntegerConverter()),'
                                'Edge.class)')

    def test_obj_nested_array(self):
        self.evaluate_generator(['object(array(int[a]),int[b])<Edge>[a]'],
                                'new UserTypeConverter(Arrays.asList(new ArrayConverter(new IntegerConverter()), '
                                'new IntegerConverter()),Edge.class)')

    def test_obj_nested_list(self):
        self.evaluate_generator(['object(list(int[a]),int[b])<Edge>[a]'],
                                'new UserTypeConverter(Arrays.asList(new ArrayListConverter(new IntegerConverter()), '
                                'new IntegerConverter()),Edge.class)')

    def test_obj_nested_object(self):
        self.evaluate_generator(['object(object(int[a])<Node>[t],int[b])<Edge>[a]'],
                                'new UserTypeConverter(Arrays.asList(new UserTypeConverter(Arrays.asList(new '
                                'IntegerConverter()),Node.class), new IntegerConverter()),Edge.class)')

    def test_list_of_objects(self):
        self.evaluate_generator(['list(object(int[a])<Edge>)[a]'],
                                'new ArrayListConverter(new UserTypeConverter(Arrays.asList(new IntegerConverter()),'
                                'Edge.class))')

    def test_array_of_objects(self):
        self.evaluate_generator(['array(object(int[a])<Edge>)[a]'],
                                'new ArrayConverter(new UserTypeConverter(Arrays.asList(new IntegerConverter()),'
                                'Edge.class))')
