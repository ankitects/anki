import unittest

from testing.framework.langs.python.python_test_arg_gen import PythonTestArgGenerator
from testing.framework.langs.python.python_user_type_gen import PythonUserTypeGenerator
from testing.framework.syntax.syntax_tree import SyntaxTree


class JavaUserTypeGeneratorTests(unittest.TestCase):

    def evaluate_generator(self, type_expression, expected_type):
        tree = SyntaxTree.of([type_expression])
        generator = PythonUserTypeGenerator(PythonTestArgGenerator())
        user_types = generator.get_user_type_definitions(tree)
        self.assertEqual(len(user_types), 2)
        self.assertEqual(expected_type[0], user_types['TypeB'])
        self.assertEqual(expected_type[1], user_types['TypeA'])

    def test_two_classes(self):
        self.evaluate_generator(
            'object(object(list(int)[a])<TypeB>[obj_b], int[b])<TypeA>[obj_a]', ['''class TypeB:
   def __init__(self, a: List[int]):
      self.a = a''', '''class TypeA:
   def __init__(self, obj_b: TypeB, b: int):
      self.obj_b = obj_b
      self.b = b'''])
