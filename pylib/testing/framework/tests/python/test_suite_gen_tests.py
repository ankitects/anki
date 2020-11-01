import unittest

from testing.framework.dto.test_case import TestCase
from testing.framework.dto.test_suite import TestSuite
from testing.framework.langs.python.python_test_suite_gen import PythonTestSuiteGenerator


class JavaTestSuiteGeneratorTests(unittest.TestCase):

    def test_imports_inections(self):
        test_suite = TestSuite('solution')
        solution_src = '''def solution(a: int, b: int) -> int:
   return a + b'''
        generator = PythonTestSuiteGenerator()
        result = generator.inject_imports(solution_src, test_suite)
        self.assertEqual('''from typing import *
import datetime
def solution(a: int, b: int) -> int:
   return a + b''', result)

    def test_suite_invocation_generation(self):
        test_suite = TestSuite('sum')
        solution_src = '''def solution(a: int, b: int) -> int:
   return a + b'''
        test_cases = ['solution.sum(1, 1) == 2', 'solution.sum(2, 2) == 4']
        summary_message = 'Total tests: 2'
        generator = PythonTestSuiteGenerator()
        result = generator.inject_test_suite_invocation(solution_src, test_cases, test_suite, summary_message)

        self.assertEqual('''def solution(a: int, b: int) -> int:
   return a + b
solution.sum(1, 1) == 2
solution.sum(2, 2) == 4
print("Total tests: 2")''', result)

    def test_case_generation(self):
        test_suite = TestSuite('sum')
        test_suite.test_cases = [TestCase(['int(1)', 'int(1)'], 'int(2)')]
        generator = PythonTestSuiteGenerator()
        result = generator.generate_test_case_invocations(test_suite, 'test passed', 'test failed')
        self.assertEqual(['''# case 1
start = datetime.datetime.now()
result = sum(int(1), int(1)) == int(2)
end = datetime.datetime.now()
msg = "1/1 " + str((end - start).microseconds/1000) + " ms - ";
if result:
    msg += "test passed"
    print(msg)
else:
    msg += "test failed"
    print(msg)
    exit(1)'''], result)
