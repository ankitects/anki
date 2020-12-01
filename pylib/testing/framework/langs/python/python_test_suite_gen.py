from typing import List, Dict

from testing.framework.dto.test_suite import TestSuite
from testing.framework.generators.test_suite_gen import TestSuiteGenerator
from testing.framework.langs.python.python_converter_gen import PythonConverterGenerator
from testing.framework.syntax.syntax_tree import SyntaxTree
from testing.framework.syntax.utils import trim_indent, to_snake_case


class PythonTestSuiteGenerator(TestSuiteGenerator):
    IMPORTS = '''
    import datetime
    from testing_lib import *'''

    MAIN_FUNCTION_TEMPLATE = '''
    converters = [%(converters_src)s]
    i = 0
    file = open('%(file_path)s', 'r')
    lines = file.readlines()
    for line in lines:
        test_case = parse_test_case(converters, line)
        start = datetime.datetime.now()
        result = %(function_name)s(*test_case.args)
        end = datetime.datetime.now()
        duration = (end - start).microseconds/1000
        if result == test_case.expected:
            print("%(pass_msg)s")
        else:
            print("%(fail_msg)s")
            break
        i += 1
    '''

    def __init__(self):
        self.converter_generator = PythonConverterGenerator()

    def generate_testing_src(self, solution_src: str, ts: TestSuite, tree: SyntaxTree, msg: Dict[str, str]) -> str:
        test_passed_msg = msg['passed_msg'] % dict(
            index='" + str(i) + "',
            total="total",
            duration='" + str(duration) + "')
        test_failed_msg = msg['failed_msg'] % dict(
            index='" + str(i) + "',
            total="total",
            expected='" + str(test_case.expected) + "',
            result='" + str(result) + "')
        src = trim_indent(self.IMPORTS) + '\n'
        src += trim_indent(solution_src) + '\n'
        src += trim_indent(self.MAIN_FUNCTION_TEMPLATE % dict(
            converters_src=self.converter_generator.generate_initializers(tree),
            function_name=to_snake_case(ts.func_name),
            file_path=ts.test_cases_file,
            pass_msg=test_passed_msg,
            fail_msg=test_failed_msg))
        return src
