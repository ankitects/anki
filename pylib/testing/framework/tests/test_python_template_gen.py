import unittest

from testing.framework.dto.test_arg import TestArg
from testing.framework.dto.test_suite import TestSuite
from testing.framework.langs.python.python_template_gen import PythonTemplateGenerator


class PythonTemplateGenTests(unittest.TestCase):

    def test_sum_template(self):
        test_suite = TestSuite('sum')
        test_suite.test_args = [TestArg('TypeA', 'a')]
        test_suite.description = 'calc sum'
        test_suite.result_type = 'int'
        test_suite.user_types = {}
        generator = PythonTemplateGenerator()
        result = generator.generate_solution_template(test_suite)
        self.assertEqual('''# calc sum

def sum(a: TypeA):
    #Add code here
    pass

 ''', result)

    def test_sum_template_with_user_type(self):
        test_suite = TestSuite('sum')
        test_suite.test_args = [TestArg('TypeA', 'a')]
        test_suite.description = 'calc sum'
        test_suite.result_type = 'int'
        test_suite.classes = {'TypeA': '''class TypeA:
\tdef __init__(self, a):
\t\tself.a = a'''}
        generator = PythonTemplateGenerator()
        result = generator.generate_solution_template(test_suite)
        self.assertEqual('''# calc sum
class TypeA:
    def __init__(self, a):
        self.a = a

def sum(a: TypeA):
    #Add code here
    pass

 ''', result)
