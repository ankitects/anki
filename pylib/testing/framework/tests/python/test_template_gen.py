import unittest

from testing.framework.dto.test_arg import TestArg
from testing.framework.dto.test_suite import TestSuite
from testing.framework.langs.python.python_template_gen import PythonTemplateGenerator


class PythonTemplateGenTests(unittest.TestCase):

    def test_sum_template(self):
        test_suite = TestSuite('sum')
        test_suite.test_args = [TestArg('A', 'a')]
        test_suite.result_type = 'int'
        test_suite.user_types = {'A': '''class A:
   def __init__(a: int):
      self.a = a'''}
        generator = PythonTemplateGenerator()
        result = generator.generate_template_src(test_suite)
        self.assertEqual('''class A:
   def __init__(a: int):
      self.a = a
def sum(a: A) -> int:
   #Add code here''', result)
