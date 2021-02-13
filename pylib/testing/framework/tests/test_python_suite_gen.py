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
        result = generator.generate_testing_src(solution_src, test_suite, tree)
        self.assertEqual('''import datetime
from test_case import *
from verifier import *
import json
from converters import *
#begin_user_src
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
    time_diff = (end - start)
    duration = (time_diff.days * 86400000) + (time_diff.seconds * 1000) + (time_diff.microseconds / 1000)
    print(json.dumps({'expected': test_case.expected,
                    'result': result,
                    'args': test_case.args,
                    'duration': duration,
                    'index': i,
                    'test_case_count': len(lines)}, default=lambda obj: obj.__dict__))
    i += 1''', result)
