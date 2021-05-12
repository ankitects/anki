import unittest

from testing.framework.java.java_input_converter import JavaInputConverter
from testing.framework.test_suite_gen import TestSuiteConverters
from testing.framework.types import ConverterFn
from testing.framework.syntax.syntax_tree import SyntaxTree


class TestSuiteConvertersTests(unittest.TestCase):

    def setUp(self) -> None:
        ConverterFn.reset_counter()

    def test_simple(self):
        tree = SyntaxTree.of(['array(array(int))[a]'])
        converters = TestSuiteConverters(JavaInputConverter(), JavaInputConverter(), tree)
        self.assertEqual(6, len(converters.all))
        self.assertEqual('converter3', converters.result.fn_name)
