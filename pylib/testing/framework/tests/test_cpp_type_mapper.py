import unittest

from testing.framework.cpp.cpp_type_mapper import CppTypeMapper
from testing.framework.syntax.syntax_tree import SyntaxTree
from testing.framework.tests.test_utils import GeneratorTestCase


class CppTypeMapperTests(GeneratorTestCase):
    def setUp(self) -> None:
        self.type_mapper = CppTypeMapper()

    def test_array_of_integers(self):
        tree = SyntaxTree.of(['array(array(int))[a]'])
        args, _ = self.type_mapper.get_args(tree)
        self.assertEqual(1, len(args))
        self.assertEqual('vector<vector<int>>', args[0].type)
        self.assertEqual('a', args[0].name)

    def test_list_of_integer(self):
        tree = SyntaxTree.of(['list(int)[a]'])
        args, _ = self.type_mapper.get_args(tree)
        self.assertEqual(1, len(args))
        self.assertEqual('vector<int>', args[0].type)
        self.assertEqual('a', args[0].name)

    def test_simple_int(self):
        tree = SyntaxTree.of(['int'])
        args, _ = self.type_mapper.get_args(tree)
        self.assertEqual(1, len(args))
        self.assertEqual('int', args[0].type)

    def test_class(self):
        tree = SyntaxTree.of(['object(int[a],int[b])<Edge>[a]'])
        args, type_defs = self.type_mapper.get_args(tree)
        self.assertEqual(1, len(args))
        self.assertEqual('Edge', args[0].type)
        self.assertEqual('a', args[0].name)
        self.assertEqual(1, len(type_defs.keys()))
        self.assertEqual('''\nstruct Edge {\n\tint a;\n\tint b;\n};\n''', type_defs['Edge'])

    def test_object_list(self):
        tree = SyntaxTree.of(['list(object(int[a],int[b])<Edge>)[a]'])
        args, type_defs = self.type_mapper.get_args(tree)
        self.assertEqual(1, len(args))
        self.assertEqual('vector<Edge>', args[0].type)
        self.assertEqual('a', args[0].name)
        self.assertEqual(1, len(type_defs.keys()))
        self.assertEqual('''\nstruct Edge {\n\tint a;\n\tint b;\n};\n''', type_defs['Edge'])

    def test_linked_list(self):
        tree = SyntaxTree.of(['linked_list(int)'])
        args, type_defs = self.type_mapper.get_args(tree)
        self.assertEqual(1, len(args))
        self.assertEqual('shared_ptr<ListNode<int>>', args[0].type)
        self.assertEqual(1, len(type_defs.keys()))
        self.assertEqualsIgnoreWhiteSpaces('''
            template<typename T>
            struct ListNode {
            public:
                T data;
                shared_ptr<ListNode<T>> next;
                ListNode() { }
                ListNode(T data) : data{data}, next{nullptr} { }
                ListNode(T data, shared_ptr<ListNode<T>> next) : data{data}, next{next} { }
            };''', type_defs['linked_list'])

    def test_binary_tree(self):
        tree = SyntaxTree.of(['binary_tree(int)'])
        args, type_defs = self.type_mapper.get_args(tree)
        self.assertEqual(1, len(args))
        self.assertEqual('shared_ptr<BinaryTreeNode<int>>', args[0].type)
        self.assertEqual(1, len(type_defs.keys()))
        self.assertEqualsIgnoreWhiteSpaces('''
            template <typename T>
            struct BinaryTreeNode {
                T data;
                shared_ptr<BinaryTreeNode<T>> left;
                shared_ptr<BinaryTreeNode<T>> right;
                BinaryTreeNode() { } 
                BinaryTreeNode(T data, shared_ptr<BinaryTreeNode<T>> left, shared_ptr<BinaryTreeNode<T>> right) {
                    this->data = data;
                    this->left = left;
                    this->right = right;
                } 
            };''', type_defs['binary_tree'])

