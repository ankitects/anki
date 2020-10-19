import unittest

from testing.framework.java_codegen import JavaFunctionArgTypeGenerator
from testing.framework.python_codegen import PythonFunctionArgTypeGenerator
from testing.framework.syntax_tree import parse_grammar


class JavaArgGenTests(unittest.TestCase):

    def test_array_of_integers(self):
        syntax_tree = parse_grammar(['array(array(int))[a]'])
        argtype_generator = PythonFunctionArgTypeGenerator()
        args = []
        for node in syntax_tree.nodes:
            args.append(argtype_generator.render(node, syntax_tree))
        self.assertEquals(len(args), 1)
        self.assertEquals('List[List[int]]', args[0])

    def test_list_of_integer(self):
        syntax_tree = parse_grammar(['list(int)[a]'])
        argtype_generator = PythonFunctionArgTypeGenerator()
        args = []
        for node in syntax_tree.nodes:
            args.append(argtype_generator.render(node, syntax_tree))
        self.assertEquals(len(args), 1)
        self.assertEquals('List[int]', args[0])

    def test_simple_int(self):
        syntax_tree = parse_grammar(['int'])
        argtype_generator = PythonFunctionArgTypeGenerator()
        args = []
        for node in syntax_tree.nodes:
            args.append(argtype_generator.render(node, syntax_tree))
        self.assertEquals(len(args), 1)
        self.assertEquals('int', args[0])

    def test_custom_object(self):
        syntax_tree = parse_grammar(['object(int[a],int[b])<Edge>[a]'])
        argtype_generator = JavaFunctionArgTypeGenerator()
        args = []
        for node in syntax_tree.nodes:
            args.append(argtype_generator.render(node, syntax_tree))
        self.assertEquals(len(args), 1)
        self.assertEquals('Edge', args[0])

    def test_object_list(self):
        syntax_tree = parse_grammar(['list(object(int[a],int[b])<Edge>)[a]'])
        argtype_generator = PythonFunctionArgTypeGenerator()
        args = []
        for node in syntax_tree.nodes:
            args.append(argtype_generator.render(node, syntax_tree))
        self.assertEquals(len(args), 1)
        self.assertEquals('List[Edge]', args[0])

    def test_map(self):
        syntax_tree = parse_grammar(['map(int, int)[a]'])
        argtype_generator = PythonFunctionArgTypeGenerator()
        args = []
        for node in syntax_tree.nodes:
            args.append(argtype_generator.render(node, syntax_tree))
        self.assertEquals(len(args), 1)
        self.assertEquals('Dict[int, int]', args[0])
