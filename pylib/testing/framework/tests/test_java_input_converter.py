import textwrap
import unittest

from testing.framework.java.java_input_converter import JavaInputConverter
from testing.framework.types import ConverterFn
from testing.framework.syntax.syntax_tree import SyntaxTree


class JavaInputConverterTests(unittest.TestCase):
    def setUp(self) -> None:
        self.converter = JavaInputConverter()
        ConverterFn.reset_counter()

    def test_array_of_arrays_of_ints(self):
        tree = SyntaxTree.of(['array(array(int))[a]'])
        _, converters = self.converter.get_converters(tree)
        self.assertEqual(3, len(converters))
        self.assertEqual(ConverterFn('', '''return value.asInt();''', 'JsonNode', 'int'), converters[0])
        self.assertEqual(ConverterFn('', textwrap.dedent('''
            int result[] = new int[value.size()];
            int i = 0;
            for (JsonNode node : value) {
                result[i++] = converter1(node);
            }
            return result;'''), 'JsonNode', 'int[]'), converters[1])
        self.assertEqual(ConverterFn('a', textwrap.dedent('''
            int[] result[] = new int[value.size()][];
            int i = 0;
            for (JsonNode node : value) {
                result[i++] = converter2(node);
            }
            return result;'''), 'JsonNode', 'int[][]'), converters[2])

    def test_object_conversion(self):
        tree = SyntaxTree.of(['object(int[a],int[b])<Edge>[a]'])
        _, converters = self.converter.get_converters(tree)
        self.assertEqual(3, len(converters))
        self.assertEqual(ConverterFn('a', '''return value.asInt();''', 'JsonNode', 'int'), converters[0])
        self.assertEqual(ConverterFn('b', '''return value.asInt();''', 'JsonNode', 'int'), converters[1])
        self.assertEqual(ConverterFn('a', '''
            Edge result = new Edge();
            result.a = converter1(value.get(0));
            result.b = converter2(value.get(1));

            return result;
        ''', 'JsonNode', 'Edge'), converters[2])

    def test_obj_nested_list(self):
        tree = SyntaxTree.of(['object(list(int)[a],int[b])<Edge>[a]'])
        arg_converters, converters = self.converter.get_converters(tree)
        self.assertEqual(1, len(arg_converters))
        self.assertEqual(4, len(converters))
        self.assertEqual(ConverterFn('', '''return value.asInt();''', 'JsonNode', 'Integer'), converters[0])
        self.assertEqual(ConverterFn('a', '''
            List<Integer> result = new ArrayList<>();
            for (JsonNode node : value) {
                result.add(converter1(node));
            }
            return result;''', 'JsonNode', 'List<Integer>'), converters[1])
        self.assertEqual(ConverterFn('b', '''return value.asInt();''', 'JsonNode', 'int'), converters[2])
        self.assertEqual(ConverterFn('a', '''
            Edge result = new Edge();
            result.a = converter2(value.get(0));
            result.b = converter3(value.get(1));
            return result;
        ''', 'JsonNode', 'Edge'), converters[3])

    def test_map(self):
        tree = SyntaxTree.of(['map(string, object(list(int)[a],int[b])<Edge>)[a]'])
        arg_converters, converters = self.converter.get_converters(tree)
        self.assertEqual(1, len(arg_converters))
        self.assertEqual(6, len(converters))
        self.assertEqual(ConverterFn('', '''return value.asText();''', 'JsonNode', 'String'), converters[0])
        self.assertEqual(ConverterFn('', '''return value.asInt();''', 'JsonNode', 'Integer'), converters[1])
        self.assertEqual(ConverterFn('a', '''
            List<Integer> result = new ArrayList<>();
            for (JsonNode node : value) {
                result.add(converter2(node));
            }
            return result;''', 'JsonNode', 'List<Integer>'), converters[2])
        self.assertEqual(ConverterFn('b', '''return value.asInt();''', 'JsonNode', 'int'), converters[3])
        self.assertEqual(ConverterFn('', '''
            Edge result = new Edge();
            result.a = converter3(value.get(0));
            result.b = converter4(value.get(1));
            return result;
        ''', 'JsonNode', 'Edge'), converters[4])
        self.assertEqual(ConverterFn('a', '''
            Map<String, Edge> result = new HashMap<>();
            Iterator<JsonNode> iterator = value.iterator();
            while (iterator.hasNext()) {
                String key = converter1(iterator.next());
                Edge val = converter5(iterator.next());
                result.put(key, val);
            }
            return result;''', 'JsonNode', 'Map<String, Edge>'), converters[5])

    def test_linked_list(self):
        tree = SyntaxTree.of(['linked_list(string)'])
        arg_converters, converters = self.converter.get_converters(tree)
        self.assertEqual(1, len(arg_converters))
        self.assertEqual(2, len(converters))
        self.assertEqual(ConverterFn('', '''return value.asText();''', 'JsonNode', 'String'), converters[0])
        self.assertEqual(ConverterFn('', '''
            List<ListNode<String>> nodes = new ArrayList<>();
            for (int i = 0; i < value.size(); i += 2) {
                JsonNode n = value.get(i);
                ListNode<String> node = new ListNode<>();
                node.data = converter1(n);
                nodes.add(node);
            }
            for (int i = 1; i < value.size(); i += 2) {
                JsonNode n = value.get(i);
                ListNode<String> node = nodes.get((i - 1) / 2);
                int nextIndex = n.asInt();
                if (nextIndex >= 0) {
                    ListNode<String> nextNode = nodes.get(nextIndex); 
                    node.next = nextNode;
                }
            }
            return nodes.isEmpty() ? null : nodes.get(0);
        ''', 'JsonNode', 'ListNode<String>'), converters[1])

    def test_binary_tree(self):
        tree = SyntaxTree.of(['binary_tree(string)'])
        arg_converters, converters = self.converter.get_converters(tree)
        self.assertEqual(1, len(arg_converters))
        self.assertEqual(2, len(converters))
        self.assertEqual(ConverterFn('', '''return value.asText();''', 'JsonNode', 'String'), converters[0])
        self.assertEqual(ConverterFn('', '''
            List<BinaryTreeNode<String>> nodes = new ArrayList<>();
            for (JsonNode n : value) {
                BinaryTreeNode<String> node = new BinaryTreeNode<>();
                if (n.isNull()) {
                    node = null;
                } else {
                    node.data = converter1(n);
                }
                nodes.add(node);
            }
            Deque<BinaryTreeNode<String>> children = new LinkedList<>(nodes);
            BinaryTreeNode<String> root = children.removeFirst();
            for (BinaryTreeNode<String> node : nodes) {
                if (node != null) {
                    if (!children.isEmpty()) {
                        node.left = children.removeFirst();
                    }
                    if (!children.isEmpty()) {
                        node.right = children.removeFirst();
                    }
                }
            }
            return root;
        ''', 'JsonNode', 'BinaryTreeNode<String>'), converters[1])
