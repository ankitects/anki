from typing import Dict

from testing.framework.dto.test_suite import TestSuite
from testing.framework.generators.test_suite_gen import TestSuiteGenerator
from testing.framework.langs.python.python_converter_gen import PythonConverterGenerator
from testing.framework.syntax.syntax_tree import SyntaxTree
from testing.framework.syntax.utils import trim_indent, to_snake_case


class PythonTestSuiteGenerator(TestSuiteGenerator):
    """
    Generate test suite's source code in python
    """

    IMPORTS = '''
import datetime
from testing_lib import *'''

    MAIN_FUNCTION_TEMPLATE = '''
converters = [%(converters_src)s]
i = 1
file = open('%(file_path)s', 'r')
lines = file.readlines()
for line in lines:
\ttest_case = parse_test_case(converters, line)
\tstart = datetime.datetime.now()
\tresult = %(function_name)s(*test_case.args)
\tend = datetime.datetime.now()
\tduration = (end - start).microseconds/1000
\tif compare(result, test_case.expected):
\t\tprint("%(pass_msg)s")
\telse:
\t\tprint("%(fail_msg)s")
\t\tbreak
\ti += 1'''

    def __init__(self):
        self.converter_generator = PythonConverterGenerator()

    def generate_testing_src(self, solution_src: str, ts: TestSuite, tree: SyntaxTree, messages: Dict[str, str]) -> str:
        """
        Generate test suite's source code in python
        :param solution_src: input user's solution source code
        :param ts: input test suite
        :param tree: input syntax tree
        :param messages: map containing the messages which will be displayed during the testing
        :return: test suite source code in python
        """
        test_passed_msg = messages['passed_msg'] % dict(
            index='" + str(i) + "',
            total=ts.test_case_count,
            duration='" + str(duration) + "')
        test_failed_msg = messages['failed_msg'] % dict(
            index='" + str(i) + "',
            total=ts.test_case_count,
            expected='" + str(test_case.expected) + "',
            result='" + str(result) + "')
        src = trim_indent(self.IMPORTS) + '\n'
        src += trim_indent(solution_src) + '\n'
        converters_src = ', '.join([self.converter_generator.render(node) for node in tree.nodes])
        src += trim_indent(self.MAIN_FUNCTION_TEMPLATE % dict(
            converters_src=converters_src,
            function_name=to_snake_case(ts.func_name),
            file_path=ts.test_cases_file.replace('\\', '\\\\'),
            pass_msg=test_passed_msg,
            fail_msg=test_failed_msg))
        return src
