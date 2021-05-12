# Copyright: Daveight and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
"""
Test Suite Generator API
"""

import textwrap
from abc import abstractmethod, ABC
from typing import Dict

from testing.framework.type_converter import TypeConverter
from testing.framework.types import TestSuite
from testing.framework.syntax.syntax_tree import SyntaxTree

START_USER_SRC_MARKER = 'begin_user_src'


class TestSuiteConverters:
    """
    This class simplifies work with converters in the test suite's template,
    usually every test suite has 2 converter types: 'input' and 'output'
    - 'input' converters converts json input data to a solution function's arguments
    - 'output' converters converts a solution function's result back to json

    Converters are passed to this class, together with syntax tree
    This class allows to reference argument and result converters separately, using their
    type name "input", "output" and suffixes "args" and "result"
    """

    def __init__(self, input_converter: TypeConverter, output_converter: TypeConverter, tree: SyntaxTree):
        self.all = []
        converters, _ = input_converter.get_converters(tree, self.all)
        self.args = converters[:-1]
        self.result = converters[-1]
        converters, _ = output_converter.get_converters(tree, self.all)
        self.output = converters[-1]


class TestSuiteGenerator(ABC):
    """
    Base class for generating a test suite for a specific language and a test
    """

    def __init__(self, input_converter: TypeConverter, output_converter: TypeConverter, line_comment_char: str = '//'):
        self._line_comment_char = line_comment_char
        self._input_converter = input_converter
        self._output_converter = output_converter

    @abstractmethod
    def get_imports(self) -> str:
        """
        Mandatory list of library imports
        :return imports separated with newlines
        """
        pass

    @abstractmethod
    def get_testing_src(self, ts: TestSuite, converters: TestSuiteConverters, solution_src: str) -> str:
        """
        A testing suite source code, which contains:
           - test case JSON data reading
           - JSON parameters conversion to a specific argument types
           - a solution function invocation
           - storing results as a JSON and sending them to STDOUT

        Result JSON format:
           - expected - JSON value to compare results
           - result - JSON value of result
           - args - input args in JSON format
           - duration - a function's call duration in milliseconds

        :param ts: source test suite
        :param converters: object containing all conversion function definitions
        :param solution_src: a solution source to be tested
        """
        pass

    def generate_test_suite_src(self, ts: TestSuite, tree: SyntaxTree, solution_src: str) -> str:
        """
        This method is used to generate a source code for an input test suite.
        :param ts: source test suite
        :param tree: source syntax tree holding parameter and result types
        :param solution_src: a solution's source code (provided by user)
        :return: a full test suite source code, ready to execute
        """
        solution_src = self._line_comment_char + START_USER_SRC_MARKER + '\n' + solution_src
        test_suite_converters = TestSuiteConverters(self._input_converter, self._output_converter, tree)
        testing_src = self.get_testing_src(ts, test_suite_converters, solution_src)
        return textwrap.dedent(self.get_imports()) + '\n' + testing_src
