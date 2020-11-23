from typing import List, Dict

from testing.framework.dto.test_suite import TestSuite
from testing.framework.generators.test_suite_gen import TestSuiteGenerator
from testing.framework.langs.python.python_converter_gen import PythonConverterGenerator
from testing.framework.syntax.syntax_tree import SyntaxTree
from testing.framework.syntax.utils import trim_indent


class PythonTestSuiteGenerator(TestSuiteGenerator):
    IMPORTS = '''
    import datetime
    from testing.python.testing_lib import *'''

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
            expected='',
            result='" + str(result) + "')
        src = trim_indent(self.IMPORTS) + '\n'
        src += trim_indent(solution_src) + '\n'
        src += trim_indent(self.MAIN_FUNCTION_TEMPLATE % dict(
            converters_src=self.converter_generator.generate_initializers(tree),
            function_name=ts.func_name,
            file_path=ts.test_cases_file,
            pass_msg=test_passed_msg,
            fail_msg=test_failed_msg))
        return src

#     def inject_imports(self, solution_src: str, test_suite: TestSuite) -> str:
#         return '''from typing import *
# import datetime
# {}'''.format(solution_src)
#
#     def inject_test_suite_invocation(self,
#                                      solution_src: str,
#                                      test_cases_src: List[str],
#                                      test_suite: TestSuite) -> str:
#         return '''{}
# {}'''.format(solution_src, '\n'.join(test_cases_src))
#
#     def generate_test_case_invocations(self,
#                                        test_suite: TestSuite,
#                                        test_passed_msg: str,
#                                        test_failed_msg: str) -> List[str]:
#         src = []
#         total_count = len(test_suite.test_cases)
#         for index, tc in enumerate(test_suite.test_cases):
#             src.append('''# case {}
# start = datetime.datetime.now()
# result = {}({}) == {}
# end = datetime.datetime.now()
# msg = "{}/{} " + str((end - start).microseconds/1000) + " ms - ";
# if result:
#     msg += "{}"
#     print(msg)
# else:
#     msg += "{}"
#     print(msg)
#     exit(1)'''.format(index + 1, test_suite.func_name, ', '.join(tc.args),
#              tc.result, index + 1, total_count, test_passed_msg, test_failed_msg))
#         return src
#
