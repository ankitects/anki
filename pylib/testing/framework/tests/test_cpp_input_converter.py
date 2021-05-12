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
