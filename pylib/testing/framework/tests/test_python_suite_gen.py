import unittest

from testing.framework.dto.test_suite import TestSuite
from testing.framework.langs.python.python_test_suite_gen import PythonTestSuiteGenerator
from testing.framework.syntax.syntax_tree import SyntaxTree


class PythonTestSuiteGeneratorTests(unittest.TestCase):

    def test_solution_generation(self):
        test_suite = TestSuite('solution')
        test_suite.test_case_count = 2
        test_suite.test_cases_file = 'tmp.txt'
        type_expression = ['int[a]', 'int[b]']
        solution_src = '''def solution(a: int, b: int) -> int:
\treturn a + b'''
        tree = SyntaxTree.of(type_expression)
        generator = PythonTestSuiteGenerator()
        result = generator.generate_testing_src(solution_src, test_suite, tree, dict(
            passed_msg='''passed''',
            failed_msg='''failed'''
        ))
        self.assertEqual('''import datetime
from testing_lib import *
def solution(a: int, b: int) -> int:
    return a + b
converters = [IntegerConverter(), IntegerConverter()]
i = 1
file = open('tmp.txt', 'r')
lines = file.readlines()
for line in lines:
    test_case = parse_test_case(converters, line)
    start = datetime.datetime.now()
    result = solution(*test_case.args)
    end = datetime.datetime.now()
    duration = (end - start).microseconds/1000
    if compare(result, test_case.expected):
        print("passed")
    else:
        print("failed")
        break
    i += 1''', result)
