import unittest

from testing.framework.langs.java.java_test_case_gen import JavaTestCaseGenerator
from testing.framework.syntax.syntax_tree import SyntaxTree


class JavaTestCaseGeneratorTest(unittest.TestCase):

    def evaluate_generator(self, type_expression, data_row, expected_arg, expected_result):
        tree = SyntaxTree.of(type_expression)
        generator = JavaTestCaseGenerator()
        test_case = generator.get_test_case(tree, data_row)
        self.assertEqual(len(test_case.args), 1)
        self.assertEqual(test_case.args[0], expected_arg)
        self.assertEqual(test_case.result, expected_result)

    def test_array_of_integers(self):
        self.evaluate_generator(['array(array(int))[a]', 'int'],
                                [[[1, 2, 3]], 1],
                                'new int[][]{{(int)1,(int)2,(int)3}}',
                                '(int)1')

    def test_list_of_integer(self):
        self.evaluate_generator(['list(int)[a]', 'int'],
                                [[1, 2, 3], 1],
                                'List.of((int)1,(int)2,(int)3)',
                                '(int)1')

    def test_array_of_integer(self):
        self.evaluate_generator(['array(int)[a]', 'int'],
                                [[1, 2, 3], 1],
                                'new int[]{(int)1,(int)2,(int)3}',
                                '(int)1')

    def test_list_with_nested_array(self):
        self.evaluate_generator(['list(array(array(int)))[a]', 'int'],
                                [[[[1, 2, 3]]], 1],
                                'List.of(new int[][][]{{{(int)1,(int)2,(int)3}}})',
                                '(int)1')

    def test_array_with_nested_list(self):
        self.evaluate_generator(['array(list(int))[a]', 'int'],
                                [[[1, 2, 3]], 1],
                                'new List[]{List.of((int)1,(int)2,(int)3)}',
                                '(int)1')

    def test_array_of_lists_of_arrays(self):
        self.evaluate_generator(['list(array(list(int)))[a]', 'int'],
                                [[[[1, 2, 3]]], 1],
                                'List.of(new List[][]{{List.of((int)1,(int)2,(int)3)}})',
                                '(int)1')

    def test_obj_simple(self):
        self.evaluate_generator(['object(int[a],int[b])<Edge>[a]', 'int'],
                                [[1, 1], 1],
                                'new Edge((int)1,(int)1)',
                                '(int)1')

    def test_obj_nested_array(self):
        self.evaluate_generator(['object(array(int[a]),int[b])<Edge>[a]', 'int'],
                                [[[1, 1], 1], 1],
                                'new Edge(int[]{(int)1,(int)1},(int)1)',
                                '(int)1')

    def test_obj_nested_list(self):
        self.evaluate_generator(['object(list(int[a]),int[b])<Edge>[a]', 'int'],
                                [[[1, 1], 1], 1],
                                'new Edge(List.of((int)1,(int)1),(int)1)',
                                '(int)1')

    def test_obj_nested_object(self):
        self.evaluate_generator(['object(object(int[a])<Node>[t],int[b])<Edge>[a]', 'int'],
                                [[[1, 1], 1], 1],
                                'new Edge(new Node((int)1),(int)1)',
                                '(int)1')

    def test_list_of_objects(self):
        self.evaluate_generator(['list(object(int[a])<Edge>)[a]', 'int'],
                                [[[1], [2]], 1],
                                'List.of(new Edge((int)1),new Edge((int)2))',
                                '(int)1')

    def test_array_of_objects(self):
        self.evaluate_generator(['array(object(int[a])<Edge>)[a]', 'int'],
                                [[[1], [2]], 1],
                                'new Edge[]{new Edge((int)1),new Edge((int)2)}',
                                '(int)1')

    def test_map(self):
        self.evaluate_generator(['map(int,int)[a]', 'int'],
                                [[[1, 2], [3, 4]], 1],
                                'Map.ofEntries(Map.entry((int)1,(int)2),Map.entry((int)3,(int)4))',
                                '(int)1')

    def test_map_of_arrays(self):
        self.evaluate_generator(['map(array(int),array(int))[a]', 'int'],
                                [[[[1, 2], [2, 3]], [[3, 4], [4, 5]]], 1],
                                'Map.ofEntries(Map.entry(new int[][]{{(int)1,(int)2}},new int[][]{{(int)2,(int)3}}),' +
                                'Map.entry(new int[][]{{(int)3,(int)4}},new int[][]{{(int)4,(int)5}}))',
                                '(int)1')
