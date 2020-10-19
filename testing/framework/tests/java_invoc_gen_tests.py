import unittest

from testing.framework.codegen import generate_function_test_invocations
from testing.framework.java_codegen import JavaInvocationArgsGenerator
from testing.framework.syntax_tree import parse_grammar


class JavaInvocArgsGenTests(unittest.TestCase):

    def test_array_of_integers(self):
        syntax_tree = parse_grammar(['array(array(int))[a]'])
        invocation_gen = JavaInvocationArgsGenerator()
        testing_rows = ['[[1,2,3]]']
        args = generate_function_test_invocations(testing_rows, syntax_tree, invocation_gen)
        self.assertEqual('new int[][]{{(int)1,(int)2,(int)3}}', args[0][0])

    def test_list_of_integer(self):
        syntax_tree = parse_grammar(['list(int)[a]', 'int'])
        invocation_gen = JavaInvocationArgsGenerator()
        testing_rows = ['[1,2,3];1']
        args = generate_function_test_invocations(testing_rows, syntax_tree, invocation_gen)
        self.assertEqual('List.of((int)1,(int)2,(int)3)', args[0][0])
        self.assertEqual('(int)1', args[0][1])

    def test_array_of_integer(self):
        syntax_tree = parse_grammar(['array(int)[a]', 'int'])
        invocation_gen = JavaInvocationArgsGenerator()
        testing_rows = ['[1,2,3];1']
        args = generate_function_test_invocations(testing_rows, syntax_tree, invocation_gen)
        self.assertEqual('new int[]{(int)1,(int)2,(int)3}', args[0][0])
        self.assertEqual('(int)1', args[0][1])

    def test_2d_array_of_integer(self):
        syntax_tree = parse_grammar(['array(array(int))[a]', 'int'])
        invocation_gen = JavaInvocationArgsGenerator()
        testing_rows = ['[[1,2,3]];1']
        args = generate_function_test_invocations(testing_rows, syntax_tree, invocation_gen)
        self.assertEqual('new int[][]{{(int)1,(int)2,(int)3}}', args[0][0])
        self.assertEqual('(int)1', args[0][1])

    def test_list_with_nested_array(self):
        syntax_tree = parse_grammar(['list(array(array(int)))[a]', 'int'])
        invocation_gen = JavaInvocationArgsGenerator()
        testing_rows = ['[[[1,2,3]]];1']
        args = generate_function_test_invocations(testing_rows, syntax_tree, invocation_gen)
        self.assertEqual('List.of(new int[][][]{{{(int)1,(int)2,(int)3}}})', args[0][0])
        self.assertEqual('(int)1', args[0][1])

    def test_array_with_nested_list(self):
        syntax_tree = parse_grammar(['array(list(int))[a]', 'int'])
        invocation_gen = JavaInvocationArgsGenerator()
        testing_rows = ['[[1,2,3]];1']
        args = generate_function_test_invocations(testing_rows, syntax_tree, invocation_gen)
        self.assertEqual('new List[]{List.of((int)1,(int)2,(int)3)}', args[0][0])
        self.assertEqual('(int)1', args[0][1])

    def test_array_of_lists_of_arrays(self):
        syntax_tree = parse_grammar(['list(array(list(int)))[a]', 'int'])
        invocation_gen = JavaInvocationArgsGenerator()
        testing_rows = ['[[[1,2,3]]];1']
        args = generate_function_test_invocations(testing_rows, syntax_tree, invocation_gen)
        self.assertEqual('List.of(new List[][]{{List.of((int)1,(int)2,(int)3)}})', args[0][0])
        self.assertEqual('(int)1', args[0][1])

    def test_obj_simple(self):
        syntax_tree = parse_grammar(['object(int[a],int[b])<Edge>[a]', 'int'])
        invocation_gen = JavaInvocationArgsGenerator()
        testing_rows = ['[1,1];1']
        args = generate_function_test_invocations(testing_rows, syntax_tree, invocation_gen)
        self.assertEqual('new Edge((int)1,(int)1)', args[0][0])
        self.assertEqual('(int)1', args[0][1])

    def test_obj_nested_array(self):
        syntax_tree = parse_grammar(['object(array(int[a]),int[b])<Edge>[a]', 'int'])
        invocation_gen = JavaInvocationArgsGenerator()
        testing_rows = ['[[1,1],1];1']
        args = generate_function_test_invocations(testing_rows, syntax_tree, invocation_gen)
        self.assertEqual('new Edge(int[]{(int)1,(int)1},(int)1)', args[0][0])
        self.assertEqual('(int)1', args[0][1])

    def test_obj_nested_list(self):
        syntax_tree = parse_grammar(['object(list(int[a]),int[b])<Edge>[a]', 'int'])
        invocation_gen = JavaInvocationArgsGenerator()
        testing_rows = ['[[1,1],1];1']
        args = generate_function_test_invocations(testing_rows, syntax_tree, invocation_gen)
        self.assertEqual('new Edge(List.of((int)1,(int)1),(int)1)', args[0][0])
        self.assertEqual('(int)1', args[0][1])

    def test_obj_nested_object(self):
        syntax_tree = parse_grammar(['object(object(int[a])<Node>[t],int[b])<Edge>[a]', 'int'])
        invocation_gen = JavaInvocationArgsGenerator()
        testing_rows = ['[[1,1],1];1']
        args = generate_function_test_invocations(testing_rows, syntax_tree, invocation_gen)
        self.assertEqual('new Edge(new Node((int)1),(int)1)', args[0][0])
        self.assertEqual('(int)1', args[0][1])

    def test_list_of_objects(self):
        syntax_tree = parse_grammar(['list(object(int[a])<Edge>)[a]', 'int'])
        invocation_gen = JavaInvocationArgsGenerator()
        testing_rows = ['[[1],[2]];1']
        args = generate_function_test_invocations(testing_rows, syntax_tree, invocation_gen)
        self.assertEqual('List.of(new Edge((int)1),new Edge((int)2))', args[0][0])
        self.assertEqual('(int)1', args[0][1])

    def test_array_of_objects(self):
        syntax_tree = parse_grammar(['array(object(int[a])<Edge>)[a]', 'int'])
        invocation_gen = JavaInvocationArgsGenerator()
        testing_rows = ['[[1],[2]];1']
        args = generate_function_test_invocations(testing_rows, syntax_tree, invocation_gen)
        self.assertEqual('new Edge[]{new Edge((int)1),new Edge((int)2)}', args[0][0])
        self.assertEqual('(int)1', args[0][1])

    def test_map(self):
        syntax_tree = parse_grammar(['map(int,int)[a]', 'int'])
        invocation_gen = JavaInvocationArgsGenerator()
        testing_rows = ['[[1,2],[3,4]];1']
        args = generate_function_test_invocations(testing_rows, syntax_tree, invocation_gen)
        self.assertEqual('Map.ofEntries(Map.entry((int)1,(int)2),Map.entry((int)3,(int)4))', args[0][0])
        self.assertEqual('(int)1', args[0][1])

    def test_map_of_arrays(self):
        syntax_tree = parse_grammar(['map(array(int),array(int))[a]', 'int'])
        invocation_gen = JavaInvocationArgsGenerator()
        testing_rows = ['[[[1,2],[2,3]],[[3,4],[4,5]]];1']
        args = generate_function_test_invocations(testing_rows, syntax_tree, invocation_gen)
        self.assertEqual('Map.ofEntries(Map.entry(new int[][]{{(int)1,(int)2}},new int[][]{{(int)2,(int)3}}),'
                         'Map.entry(new int[][]{{(int)3,(int)4}},new int[][]{{(int)4,(int)5}}))', args[0][0])
        self.assertEqual('(int)1', args[0][1])
