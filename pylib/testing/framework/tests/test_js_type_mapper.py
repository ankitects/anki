import textwrap

from testing.framework.js.js_type_mapper import JsTypeMapper
from testing.framework.tests.test_utils import GeneratorTestCase
from testing.framework.syntax.syntax_tree import SyntaxTree


class JsTypeMappingsGeneratorTests(GeneratorTestCase):

    def setUp(self):
        self.type_mapper = JsTypeMapper()

    def test_array_of_integers(self):
        tree = SyntaxTree.of(['array(array(int))[a]'])
        args, _ = self.type_mapper.get_args(tree)
        self.assertEqual(1, len(args))
        self.assertEqual('number[][]', args[0].type)
        self.assertEqual('a', args[0].name)

    def test_list_of_integer(self):
        tree = SyntaxTree.of(['list(int)[a]'])
        args, _ = self.type_mapper.get_args(tree)
        self.assertEqual(1, len(args))
        self.assertEqual('number[]', args[0].type)
        self.assertEqual('a', args[0].name)

    def test_simple_int(self):
        tree = SyntaxTree.of(['int'])
        args, _ = self.type_mapper.get_args(tree)
        self.assertEqual(1, len(args))
        self.assertEqual('number', args[0].type)

    def test_class(self):
        tree = SyntaxTree.of(['object(int[a],int[b])<Edge>[a]'])
        args, type_defs = self.type_mapper.get_args(tree)
        self.assertEqual(1, len(args))
        self.assertEqual('Edge', args[0].type)
        self.assertEqual('a', args[0].name)
        self.assertEqual(1, len(type_defs.keys()))
        self.assertEqualsIgnoreWhiteSpaces('''
            class Edge {
                constructor(a, b) {
                    this.a = a
                    this.b = b
                }
            } 
        ''', type_defs['Edge'].strip())

    def test_object_list(self):
        tree = SyntaxTree.of(['list(object(int[a],int[b])<Edge>)[a]'])
        args, type_defs = self.type_mapper.get_args(tree)
        self.assertEqual(1, len(args))
        self.assertEqual('Edge[]', args[0].type)
        self.assertEqual('a', args[0].name)
        self.assertEqual(1, len(type_defs.keys()))
        self.assertEqualsIgnoreWhiteSpaces('''
            class Edge {
                constructor(a, b) {
                    this.a = a
                    this.b = b
                }
            }
        ''', type_defs['Edge'])

    def test_map(self):
        tree = SyntaxTree.of(['map(string, list(object(int[a],int[b])<Edge>))[a]'])
        args, type_defs = self.type_mapper.get_args(tree)
        self.assertEqual(1, len(args))
        self.assertEqual('Map.<string, Edge[]>', args[0].type)
        self.assertEqual('a', args[0].name)
        self.assertEqual(1, len(type_defs.keys()))
        self.assertEqualsIgnoreWhiteSpaces('''
            class Edge {
                constructor(a, b) {
                    this.a = a
                    this.b = b
                }
            }
        ''', type_defs['Edge'])

    def test_linked_list(self):
        tree = SyntaxTree.of(['linked_list(int)'])
        args, type_defs = self.type_mapper.get_args(tree)
        self.assertEqual(1, len(args))
        self.assertEqual('ListNode<number>', args[0].type)
        self.assertEqual(1, len(type_defs.keys()))
        self.assertEqualsIgnoreWhiteSpaces(textwrap.dedent('''
            class ListNode {
                constructor(data = null) {
                    this.data = data
                    this.next = null
                }
            }
        ''').strip(), type_defs['linked_list'].strip())
