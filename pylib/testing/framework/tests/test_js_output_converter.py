import unittest

from testing.framework.js.js_output_converter import JsOutputConverter
from testing.framework.types import ConverterFn
from testing.framework.syntax.syntax_tree import SyntaxTree


class JsOutputConverterTests(unittest.TestCase):
    def setUp(self) -> None:
        self.converter = JsOutputConverter()
        ConverterFn.reset_counter()

    def test_array_of_arrays_of_ints(self):
        tree = SyntaxTree.of(['array(array(int))[a]'])
        _, converters = self.converter.get_converters(tree)
        self.assertEqual(3, len(converters))
        self.assertEqual(ConverterFn('', 'return value', ''), converters[0])
        self.assertEqual(ConverterFn('', '''
            var result = []
            for (var i = 0; i < value.length; i++) { result.push(converter1(value[i])) }
            return result''', ''), converters[1])
        self.assertEqual(ConverterFn('a', '''
            var result = []
            for (var i = 0; i < value.length; i++) { result.push(converter2(value[i])) }
            return result''', ''), converters[2])

    def test_object_conversion(self):
        tree = SyntaxTree.of(['object(int[a],int[b])<Edge>[a]'])
        _, converters = self.converter.get_converters(tree)
        self.assertEqual(3, len(converters))
        self.assertEqual(ConverterFn('a', 'return value', ''), converters[0])
        self.assertEqual(ConverterFn('b', 'return value', ''), converters[1])
        self.assertEqual(ConverterFn('a', '''
            var result = []
            result.push(converter1(value.a))
            result.push(converter2(value.b))
            return result''', ''), converters[2])

    def test_obj_nested_list(self):
        tree = SyntaxTree.of(['object(list(int)[a],int[b])<Edge>[a]'])
        arg_converters, converters = self.converter.get_converters(tree)
        self.assertEqual(1, len(arg_converters))
        self.assertEqual(4, len(converters))
        self.assertEqual(ConverterFn('', 'return value', ''), converters[0])
        self.assertEqual(ConverterFn('a', '''
            var result = []
            for (var i = 0; i < value.length; i++) { result.push(converter1(value[i])) }
            return result''', ''), converters[1])
        self.assertEqual(ConverterFn('b', 'return value', ''), converters[2])
        self.assertEqual(ConverterFn('a', '''
            var result = []
            result.push(converter2(value.a))
            result.push(converter3(value.b))
            return result''', ''), converters[3])

    def test_map(self):
        tree = SyntaxTree.of(['map(string, object(list(int)[a],int[b])<Edge>)[a]'])
        arg_converters, converters = self.converter.get_converters(tree)
        self.assertEqual(1, len(arg_converters))
        self.assertEqual(6, len(converters))
        self.assertEqual(ConverterFn('', 'return value', ''), converters[0])
        self.assertEqual(ConverterFn('', 'return value', ''), converters[1])
        self.assertEqual(ConverterFn('a', '''
            var result = []
            for (var i = 0; i < value.length; i++) { result.push(converter2(value[i])) }
            return result''', ''), converters[2])
        self.assertEqual(ConverterFn('b', 'return value', ''), converters[3])
        self.assertEqual(ConverterFn('', '''
            var result = []
            result.push(converter3(value.a))
            result.push(converter4(value.b))
            return result''', ''), converters[4])
        self.assertEqual(ConverterFn('a', '''
            var result = []
            for (var [k, v] of value) {
                result.push(converter1(k));
                result.push(converter5(v));
            }
            return result''', ''), converters[5])

    def test_linked_list(self):
        tree = SyntaxTree.of(['linked_list(int)'])
        arg_converters, converters = self.converter.get_converters(tree)
        self.assertEqual(1, len(arg_converters))
        self.assertEqual(2, len(converters))
        self.assertEqual(ConverterFn('', 'return value', ''), converters[0])
        self.assertEqual(ConverterFn('', '''
            result = []
            n = value
            while (n != null) {
                result.push(converter1(n.data))
                n = n.next
            }
            return result''', ''), converters[1])

    def test_binary_tree(self):
        tree = SyntaxTree.of(['binary_tree(int)'])
        arg_converters, converters = self.converter.get_converters(tree)
        self.assertEqual(1, len(arg_converters))
        self.assertEqual(2, len(converters))
        self.assertEqual(ConverterFn('', 'return value', ''), converters[0])
        self.assertEqual(ConverterFn('', '''
            const result = []
            const queue = []
            const visited = new Set()
            queue.push(value)
            while (queue.length) {
                node = queue.shift()
                if (node) {
                    visited.add(node)
                    result.push(converter1(node.data))
                    if (node.left != null && !visited.has(node.left)) {
                        queue.push(node.left)
                    }
                    if (node.right != null && !visited.has(node.right)) {
                        queue.push(node.right)
                    }
                } else {
                    result.push(null);
                }
            }
            for (let i = result.length - 1; i > 0; i--) {
                if (result[i] == null) {
                    result.pop()
                } else {
                    break
                }
            }
            return result''', ''), converters[1])
