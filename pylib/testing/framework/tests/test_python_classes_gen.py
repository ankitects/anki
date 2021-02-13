import unittest

from testing.framework.langs.python.python_class_gen import PythonClassGenerator
from testing.framework.syntax.syntax_tree import SyntaxTree


class PythonClassesGeneratorTests(unittest.TestCase):

    def evaluate_generator(self, type_expression, expected_classes):
        tree = SyntaxTree.of([type_expression])
        generator = PythonClassGenerator()
        classes = {}
        for node in tree.nodes:
            generator.render(node, classes)
        self.assertEqual(classes, expected_classes)

    def test_nested_objects(self):
        self.evaluate_generator(
            'object(object(int[a])<TypeB>[obj_b], int[b])<TypeA>[obj_a]', {'TypeB': '''class TypeB:
    def __init__(self, a: int):
        self.a = a''', 'TypeA': '''class TypeA:
    def __init__(self, obj_b: TypeB, b: int):
        self.obj_b = obj_b
        self.b = b'''})

    def test_list_of_objects(self):
        self.evaluate_generator('list(object(int[start], int[finish])<Event>)', {'Event': '''class Event:
    def __init__(self, start: int, finish: int):
        self.start = start
        self.finish = finish'''})