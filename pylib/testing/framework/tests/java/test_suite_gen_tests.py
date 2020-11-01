import unittest

from testing.framework.dto.test_case import TestCase
from testing.framework.dto.test_suite import TestSuite
from testing.framework.langs.java.java_test_suite_gen import JavaTestSuiteGenerator


class JavaTestSuiteGeneratorTests(unittest.TestCase):

    def test_imports_inections(self):
        test_suite = TestSuite('solution')
        solution_src = '''public class Solution {
  int solution(int a, int b) {
     return a + b;
  }   
}    
'''
        generator = JavaTestSuiteGenerator()
        result = generator.inject_imports(solution_src, test_suite)
        self.assertEquals('''import static test_engine.Verifier.verify;
import java.util.concurrent.TimeUnit;

public class Solution {
  int solution(int a, int b) {
     return a + b;
  }   
}    
''', result)

    def test_suite_invocation_generation(self):
        test_suite = TestSuite('sum')
        solution_src = '''public class Solution {
   int solution(int a, int b) {
      return a + b;
   }
}
'''
        test_cases = ['verify(solution.sum(1, 1), 2);', 'verify(solution.sum(2, 2), 4);']
        summary_message = 'Total tests: 2'
        generator = JavaTestSuiteGenerator()
        result = generator.inject_test_suite_invocation(solution_src, test_cases, test_suite, summary_message)

        self.assertEqual('''public class Solution {
   int solution(int a, int b) {
      return a + b;
   }

   public static void main(String[] args) {
      Solution solution = new Solution();
      long start, end;
      String msg;
      boolean result;
      verify(solution.sum(1, 1), 2);
      verify(solution.sum(2, 2), 4);
      System.out.println("Total tests: 2");
   }
}
''', result)

    def test_case_generation(self):
        test_suite = TestSuite('sum')
        test_suite.test_cases = [TestCase(['(int)1', '(int)1'], '(int)2')]
        generator = JavaTestSuiteGenerator()
        result = generator.generate_test_case_invocations(test_suite, 'test passed', 'test failed')
        self.assertEqual(['''// case 1
                start = System.nanoTime();
                result = verify(solution.sum((int)1,(int)1), (int)2);
                end = System.nanoTime();
                msg = "1/1 " + TimeUnit.MILLISECONDS.convert(end - start, TimeUnit.NANOSECONDS) + " ms - ";
                if (result) {
                    msg += "test passed";
                    System.out.println(msg);
                } else {
                    msg += "test failed";
                    System.out.println(msg);
                    return;
                }'''], result)
