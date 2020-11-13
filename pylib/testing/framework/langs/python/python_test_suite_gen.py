from typing import List

from testing.framework.dto.test_suite import TestSuite
from testing.framework.generators.test_suite_gen import TestSuiteGenerator


class PythonTestSuiteGenerator(TestSuiteGenerator):

    def inject_imports(self, solution_src: str, test_suite: TestSuite) -> str:
        return '''from typing import *
import datetime
{}'''.format(solution_src)

    def inject_test_suite_invocation(self,
                                     solution_src: str,
                                     test_cases_src: List[str],
                                     test_suite: TestSuite) -> str:
        return '''{}
{}'''.format(solution_src, '\n'.join(test_cases_src))

    def generate_test_case_invocations(self,
                                       test_suite: TestSuite,
                                       test_passed_msg: str,
                                       test_failed_msg: str) -> List[str]:
        src = []
        total_count = len(test_suite.test_cases)
        for index, tc in enumerate(test_suite.test_cases):
            src.append('''# case {}
start = datetime.datetime.now()
result = {}({}) == {}
end = datetime.datetime.now()
msg = "{}/{} " + str((end - start).microseconds/1000) + " ms - ";
if result:
    msg += "{}"
    print(msg)
else:
    msg += "{}"
    print(msg)
    exit(1)'''.format(index + 1, test_suite.func_name, ', '.join(tc.args),
             tc.result, index + 1, total_count, test_passed_msg, test_failed_msg))
        return src

