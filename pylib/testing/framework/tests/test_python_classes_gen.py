import unittest

from testing.framework.langs.python.python_class_gen import PythonClassGenerator
from testing.framework.syntax.syntax_tree import SyntaxTree


class PythonClassesGeneratorTests(unittest.TestCase):

    def evaluate_generator(self, type_expression, expected_type):
        tree = SyntaxTree.of([type_expression])
        generator = PythonClassGenerator()
        classes = {}
        for node in tree.nodes:
            generator.render(node, classes)
        self.assertEqual(expected_type[0], classes['TypeB'])
        self.assertEqual(expected_type[1], classes['TypeA'])

    def test_nested_objects(self):
        self.evaluate_generator(
            'object(object(int[a])<TypeB>[obj_b], int[b])<TypeA>[obj_a]', ['''class TypeB:
    def __init__(self, a: int):
        self.a = a''', '''class TypeA:
    def __init__(self, obj_b: TypeB, b: int):
        self.obj_b = obj_b
        self.b = b'''])
