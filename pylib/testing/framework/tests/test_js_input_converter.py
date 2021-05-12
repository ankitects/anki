import unittest

from testing.framework.js.js_input_converter import JsInputConverter
from testing.framework.types import ConverterFn
from testing.framework.syntax.syntax_tree import SyntaxTree


class JsInputConverterTests(unittest.TestCase):

    def setUp(self) -> None:
        self.converter = JsInputConverter()
        ConverterFn.reset_counter()

    def test_array_of_arrays_of_ints(self):
        tree = SyntaxTree.of(['array(array(int))[a]'])
        _, converters = self.converter.get_converters(tree)
        self.assertEqual(3, len(converters))
        self.assertEqual(ConverterFn('', '''return value''', ''), converters[0])
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
        self.assertEqual(ConverterFn('a', '''return value''', ''), converters[0])
        self.assertEqual(ConverterFn('b', '''return value''', ''), converters[1])
        self.assertEqual(ConverterFn('a', '''
            var result = {}
            result['a'] = converter1(value[0])
            result['b'] = converter2(value[1])
            return result
        ''', ''), converters[2])

    def test_obj_nested_list(self):
        tree = SyntaxTree.of(['object(list(int)[a],int[b])<Edge>[a]'])
        arg_converters, converters = self.converter.get_converters(tree)
        self.assertEqual(1, len(arg_converters))
        self.assertEqual(4, len(converters))
        self.assertEqual(ConverterFn('', '''return value''', ''), converters[0])
        self.assertEqual(ConverterFn('a', '''
            var result = []
            for (var i = 0; i < value.length; i++) { result.push(converter1(value[i])) }
            return result''', ''), converters[1])
        self.assertEqual(ConverterFn('b', '''return value''', ''), converters[2])
        self.assertEqual(ConverterFn('a', '''
            var result = {}
            result['a'] = converter2(value[0])
            result['b'] = converter3(value[1])
            return result
        ''', ''), converters[3])
