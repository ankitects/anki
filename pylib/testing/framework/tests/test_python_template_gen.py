import textwrap
import unittest

from testing.framework.python.python_template_gen import PythonTemplateGenerator
from testing.framework.types import TestSuite
from testing.framework.syntax.syntax_tree import SyntaxTree


class PythonTemplateGeneratorTests(unittest.TestCase):
    def setUp(self) -> None:
        self.generator = PythonTemplateGenerator()

    def test_simple_template_generation(self):
        ts = TestSuite()
        ts.fn_name = 'sum'
        ts.description = 'calculate sum of 2 numbers'
        tree = SyntaxTree.of(['int[a]', 'int[b]', 'int'])
        self.assertEqual(textwrap.dedent('''
            # calculate sum of 2 numbers

            def sum(a: int, b: int)->int:
                #Add code here
                pass
            ''').lstrip(), self.generator.get_template(tree, ts))

    def test_solution_with_custom_types_generation(self):
        ts = TestSuite()
        ts.fn_name = 'sum'
        ts.description = 'calculate sum of 2 objects'
        tree = SyntaxTree.of(['object(int[val])<TypeA>[a]', 'object(int[val])<TypeB>[b]', 'int'])
        self.assertEqual(textwrap.dedent('''
            # calculate sum of 2 objects

            class TypeA:
                def __init__(self, val: int):
                    self.val = val

            class TypeB:
                def __init__(self, val: int):
                    self.val = val

            def sum(a: TypeA, b: TypeB)->int:
                #Add code here
                pass
            ''').lstrip(), self.generator.get_template(tree, ts))
