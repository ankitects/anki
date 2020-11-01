import unittest

from testing.framework.dto.test_arg import TestArg
from testing.framework.dto.test_suite import TestSuite
from testing.framework.langs.java.java_template_gen import JavaTemplateGenerator


class JavaTemplateGenTests(unittest.TestCase):

    def test_sum_template(self):
        test_suite = TestSuite('sum')
        test_suite.test_args = [TestArg('TypeA', 'a')]
        test_suite.result_type = 'int'
        test_suite.user_types = {'TypeA': '''public static class A {
      int a;
      public A(int a) {
         this.a = a;
      }
   }'''}
        generator = JavaTemplateGenerator()
        result = generator.generate_template_src(test_suite)
        self.assertEquals('''public class Solution {
   public static class A {
      int a;
      public A(int a) {
         this.a = a;
      }
   }
   int sum(TypeA a) {
      //Add code here
   }
}''', result)
