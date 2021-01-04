import unittest

from testing.framework.dto.test_arg import TestArg
from testing.framework.dto.test_suite import TestSuite
from testing.framework.langs.java.java_template_gen import JavaTemplateGenerator


class JavaTemplateGenTests(unittest.TestCase):

    def test_sum_template(self):
        test_suite = TestSuite('sum')
        test_suite.test_args = [TestArg('TypeA', 'a')]
        test_suite.description = 'calc sum'
        test_suite.result_type = 'int'
        test_suite.user_types = {}
        generator = JavaTemplateGenerator()
        result = generator.generate_solution_template(test_suite)
        self.assertEqual('''/**
* calc sum
*/
public class Solution {

    public int sum(TypeA a) {
        //Add code here
    }
}''', result)

    def test_sum_template_with_user_type(self):
        test_suite = TestSuite('sum')
        test_suite.test_args = [TestArg('TypeA', 'a')]
        test_suite.description = 'calc sum'
        test_suite.result_type = 'int'
        test_suite.classes = {'TypeA': '''public static class TypeA {
\tint a;
\tpublic A(int a) {
\t\tthis.a = a;
\t}
}'''}
        generator = JavaTemplateGenerator()
        result = generator.generate_solution_template(test_suite)
        self.assertEqual('''/**
* calc sum
*/
public class Solution {
    public static class TypeA {
        int a;
        public A(int a) {
            this.a = a;
        }
    }
    public int sum(TypeA a) {
        //Add code here
    }
}''', result)
