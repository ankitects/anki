import unittest

from testing.framework.cpp.cpp_input_converter import CppInputConverter
from testing.framework.types import ConverterFn
from testing.framework.syntax.syntax_tree import SyntaxTree


class CppInputConverterTests(unittest.TestCase):
    def setUp(self) -> None:
        self.converter = CppInputConverter()
        ConverterFn.reset_counter()

    def test_array_of_arrays_of_ints(self):
        tree = SyntaxTree.of(['array(array(int))[a]'])
        _, converters = self.converter.get_converters(tree)
        self.assertEqual(3, len(converters))
        self.assertEqual(ConverterFn('', '''return value.as_int();''', 'jute::jValue', 'int'), converters[0])
        self.assertEqual(ConverterFn('', '''
            vector<int> result;
            for (int i = 0; i < value.size(); i++) {
              int obj = converter1(value[i]);
              result.push_back(obj);
            }
            return result;''', 'jute::jValue', 'vector<int>'), converters[1])
        self.assertEqual(ConverterFn('a', '''
            vector<vector<int>> result;
            for (int i = 0; i < value.size(); i++) {
              vector<int> obj = converter2(value[i]);
              result.push_back(obj);
            }
            return result;''', 'jute::jValue', 'vector<vector<int>>'), converters[2])

    def test_object_conversion(self):
        tree = SyntaxTree.of(['object(int[a],int[b])<Edge>[a]'])
        _, converters = self.converter.get_converters(tree)
        self.assertEqual(3, len(converters))
        self.assertEqual(ConverterFn('a', '''return value.as_int();''', 'jute::jValue', 'int'), converters[0])
        self.assertEqual(ConverterFn('b', '''return value.as_int();''', 'jute::jValue', 'int'), converters[1])
        self.assertEqual(ConverterFn('a', '''
            Edge obj;
            obj.a = converter1(value[0]);
            obj.b = converter2(value[1]);
            return obj;
        ''', 'jute::jValue', 'Edge'), converters[2])

    def test_obj_nested_list(self):
        tree = SyntaxTree.of(['object(list(int)[a],int[b])<Edge>[a]'])
        arg_converters, converters = self.converter.get_converters(tree)
        self.assertEqual(1, len(arg_converters))
        self.assertEqual(4, len(converters))
        self.assertEqual(ConverterFn('', '''return value.as_int();''', 'jute::jValue', 'int'), converters[0])
        self.assertEqual(ConverterFn('a', '''
            vector<int> result;
            for (int i = 0; i < value.size(); i++) {
              int obj = converter1(value[i]);
              result.push_back(obj);
            }
            return result;''', 'jute::jValue', 'vector<int>'), converters[1])
        self.assertEqual(ConverterFn('b', '''return value.as_int();''', 'jute::jValue', 'int'), converters[2])
        self.assertEqual(ConverterFn('a', '''
            Edge obj;
            obj.a = converter2(value[0]);
            obj.b = converter3(value[1]);
            return obj;
        ''', 'jute::jValue', 'Edge'), converters[3])

    def test_map(self):
        tree = SyntaxTree.of(['map(string, object(list(int)[a],int[b])<Edge>)[a]'])
        arg_converters, converters = self.converter.get_converters(tree)
        self.assertEqual(1, len(arg_converters))
        self.assertEqual(6, len(converters))
        self.assertEqual(ConverterFn('', '''return value.as_string();''', 'jute::jValue', 'string'), converters[0])
        self.assertEqual(ConverterFn('', '''return value.as_int();''', 'jute::jValue', 'int'), converters[1])
        self.assertEqual(ConverterFn('a', '''
            vector<int> result;
            for (int i = 0; i < value.size(); i++) {
              int obj = converter2(value[i]);
              result.push_back(obj);
            }
            return result;''', 'jute::jValue', 'vector<int>'), converters[2])
        self.assertEqual(ConverterFn('b', '''return value.as_int();''', 'jute::jValue', 'int'), converters[3])
        self.assertEqual(ConverterFn('', '''
            Edge obj;
            obj.a = converter3(value[0]);
            obj.b = converter4(value[1]);
            return obj;
        ''', 'jute::jValue', 'Edge'), converters[4])
        self.assertEqual(ConverterFn('a', '''
            map<string, Edge> result;
            for (int i = 0; i < value.size(); i+=2) {
                string k = converter1(value[i]);
                Edge v = converter5(value[i + 1]);
                result[k] = v;
            }
            return result;
        ''', 'jute::jValue', 'map<string, Edge>'), converters[5])

    def test_linked_list(self):
        tree = SyntaxTree.of(['linked_list(string)'])
        arg_converters, converters = self.converter.get_converters(tree)
        self.assertEqual(1, len(arg_converters))
        self.assertEqual(2, len(converters))
        self.assertEqual(ConverterFn('', '''return value.as_string();''', 'jute::jValue', 'string'), converters[0])
        self.assertEqual(ConverterFn('', '''
            set<int> visited;
            shared_ptr<ListNode<string>> head = nullptr;
            shared_ptr<ListNode<string>> node = nullptr;
            int i = 1;
            while (visited.count(i) == 0 && i <= value.size()) {
                string data = converter1(value[i - 1]);
                auto tmp = make_shared<ListNode<string>>(data);
                if (head == nullptr) {
                    head = tmp;
                    node = head;
                } else {
                    node->next = tmp;
                    node = tmp;
                }
                if (i >= value.size()) {
                    break;
                }
                visited.insert(i);
                i = 2 * value[i].as_int() + 1;
            }
            if (visited.count(i) != 0 && i < value.size()) {
                i = (i / 2);
                shared_ptr<ListNode<int>> iter = head;
                int j = 0;
                while (i-- != j) {
                    iter = iter->next;
                }
                node->next = iter;
            }
            return head;
        ''', 'jute::jValue', 'shared_ptr<ListNode<string>>'), converters[1])

    def test_binary_tree(self):
        tree = SyntaxTree.of(['binary_tree(string)'])
        arg_converters, converters = self.converter.get_converters(tree)
        self.assertEqual(1, len(arg_converters))
        self.assertEqual(2, len(converters))
        self.assertEqual(ConverterFn('', '''return value.as_string();''', 'jute::jValue', 'string'), converters[0])
        self.assertEqual(ConverterFn('', '''
            vector<shared_ptr<BinaryTreeNode<string>>> nodes;
            if (value.is_null()) {
            return nullptr;
            }
            for (int i = 0; i < value.size(); i++) {
                shared_ptr<BinaryTreeNode<string>> node = make_shared<BinaryTreeNode<string>>();
                node->left = nullptr;
                node->right = nullptr;
                if (value[i].is_null()) {
                    node = nullptr;
                } else {
                    node->data = converter1(value[i]);
                }
                nodes.push_back(node);
            }
            queue<shared_ptr<BinaryTreeNode<string>>> children;
            for (int i = 0; i < nodes.size(); i++) {
                children.push(nodes[i]);
            }
            shared_ptr<BinaryTreeNode<string>> root = children.front();
            children.pop();
            for (int i = 0; i < nodes.size(); i++) {
                shared_ptr<BinaryTreeNode<string>> node = nodes[i];
                if (node != nullptr) {
                    if (!children.empty()) {
                        node->left = children.front();
                        children.pop();
                    }
                    if (!children.empty()) {
                        node->right = children.front();
                        children.pop();
                    }
                }
            }
            return root;
        ''', 'jute::jValue', 'shared_ptr<BinaryTreeNode<string>>'), converters[1])
