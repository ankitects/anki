from testing.framework.dto.test_suite import TestSuite
from testing.framework.generators.test_suite_gen import TestSuiteGenerator
from testing.framework.langs.python.python_converter_gen import PythonConverterGenerator
from testing.framework.syntax.syntax_tree import SyntaxTree
from testing.framework.syntax.utils import trim_indent, to_snake_case

PYTHON_USER_SRC_START_MARKER = '#begin_user_src\n'


class PythonTestSuiteGenerator(TestSuiteGenerator):
    """
    Generate test suite's source code in python
    """

    IMPORTS = '''
import datetime
from test_case import *
from verifier import *
import json
from converters import *'''

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
\ttime_diff = (end - start)
\tduration = (time_diff.days * 86400000) + (time_diff.seconds * 1000) + (time_diff.microseconds / 1000)
\tprint(json.dumps({'expected': test_case.expected,
                    'result': result,
                    'args': test_case.args,
                    'duration': duration,
                    'index': i,
                    'test_case_count': len(lines)}, default=lambda obj: obj.__dict__))
\ti += 1
'''

    def __init__(self):
        self.converter_generator = PythonConverterGenerator()

    def generate_testing_src(self, solution_src: str, ts: TestSuite, tree: SyntaxTree) -> str:
        """
        Generate test suite's source code in python
        :param solution_src: input user's solution source code
        :param ts: input test suite
        :param tree: input syntax tree
        :return: test suite source code in python
        """
        src = trim_indent(self.IMPORTS) + '\n'
        src += PYTHON_USER_SRC_START_MARKER
        src += trim_indent(solution_src) + '\n'
        converters_src = ', '.join([self.converter_generator.render(node) for node in tree.nodes])
        src += trim_indent(self.MAIN_FUNCTION_TEMPLATE % dict(
            converters_src=converters_src,
            function_name=to_snake_case(ts.func_name),
            file_path=ts.test_cases_file.replace('\\', '\\\\')))
        return src
