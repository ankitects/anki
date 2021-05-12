import textwrap
import unittest

from testing.framework.cpp.cpp_template_gen import CppTemplateGenerator
from testing.framework.types import TestSuite
from testing.framework.syntax.syntax_tree import SyntaxTree


class CppTemplateGeneratorTests(unittest.TestCase):
    def setUp(self) -> None:
        self.generator = CppTemplateGenerator()

    def test_simple_template_generation(self):
        ts = TestSuite()
        ts.fn_name = 'sum'
        ts.description = 'calculate sum of 2 numbers'
        tree = SyntaxTree.of(['int[a]', 'int[b]', 'int'])
        self.assertEqual(textwrap.dedent('''
            /**
            * calculate sum of 2 numbers
            */

            class Solution {
            public:
                int sum(int a, int b) {
                    //Add code here
                }
            };''').lstrip(), self.generator.get_template(tree, ts))

    def test_solution_with_custom_types_generation(self):
        ts = TestSuite()
        ts.fn_name = 'sum'
        ts.description = 'calculate sum of 2 objects'
        tree = SyntaxTree.of(['object(int[val])<TypeA>[a]', 'object(int[val])<TypeB>[b]', 'int'])
        self.assertEqual(textwrap.dedent('''
            /**
            * calculate sum of 2 objects
            */

            struct TypeA {
                int val;
            };

            struct TypeB {
                int val;
            };

            class Solution {
            public:
                int sum(TypeA a, TypeB b) {
                    //Add code here
                }
            };''').lstrip(), self.generator.get_template(tree, ts))
