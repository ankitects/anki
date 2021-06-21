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
            ListNode<String> head = new ListNode<>();
            ListNode<String> node = head;
            for (JsonNode n : value) {
                ListNode<String> nextNode = new ListNode<>();
                nextNode.data = converter1(n);
                node.next = nextNode;
                node = nextNode;
            }
            return head.next;
        ''', 'JsonNode', 'ListNode<String>'), converters[1])
