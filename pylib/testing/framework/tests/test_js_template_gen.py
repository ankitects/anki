import textwrap
import unittest

from testing.framework.js.js_template_gen import JsTemplateGenerator
from testing.framework.types import TestSuite
from testing.framework.syntax.syntax_tree import SyntaxTree


class JsTemplateGeneratorTests(unittest.TestCase):
    def setUp(self) -> None:
        self.generator = JsTemplateGenerator()

    def test_simple_template_generation(self):
        ts = TestSuite()
        ts.fn_name = 'sum'
        ts.description = 'calculate sum of 2 numbers'
        tree = SyntaxTree.of(['int[a]', 'int[b]', 'int'])
        self.assertEqual(textwrap.dedent('''
            /**
            * calculate sum of 2 numbers
            */

            /**
            * @param { number } a
            * @param { number } b
            * @return number
            */

            function sum(a, b) {
                //Add code here
            }
            ''').strip(), self.generator.get_template(tree, ts))

    def test_solution_with_custom_types_generation(self):
        ts = TestSuite()
        ts.fn_name = 'sum'
        ts.description = 'calculate sum of 2 objects'
        tree = SyntaxTree.of(['object(int[val])<TypeA>[a]', 'object(int[val])<TypeB>[b]', 'int'])
        self.assertEqual(textwrap.dedent('''
            /**
            * calculate sum of 2 objects
            */

            class TypeA {
                constructor(val) {
                    this.val = val
                }
            }

            class TypeB {
                constructor(val) {
                    this.val = val
                }
            }

            /**
            * @param { TypeA } a
            * @param { TypeB } b
            * @return number
            */
 
            function sum(a, b) {
                //Add code here
            }''').lstrip(), self.generator.get_template(tree, ts))
