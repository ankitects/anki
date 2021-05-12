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
        self.assertEqual(ConverterFn('', 'return value', ''), converters[1])
        self.assertEqual(ConverterFn('a', 'return value', ''), converters[2])

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
        self.assertEqual(ConverterFn('a', 'return value', ''), converters[1])
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
        self.assertEqual(ConverterFn('a', 'return value', ''), converters[2])
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
