from typing import List

from testing.framework.dto.test_suite import TestSuite
from testing.framework.generators.test_suite_gen import TestSuiteGenerator


class JavaTestSuiteGenerator(TestSuiteGenerator):

    def inject_imports(self, solution_src: str, test_suite: TestSuite) -> str:
        return '''import static test_engine.Verifier.verify;
import java.util.concurrent.TimeUnit;

{}'''.format(solution_src)

    def inject_test_suite_invocation(self,
                                     solution_src: str,
                                     test_cases_src: List[str],
                                     test_suite: TestSuite) -> str:
        i = solution_src.rindex('}')
        main_function_src = '''   public static void main(String[] args) {{
      Solution solution = new Solution();
      long start, end;
      long duration;
      String msg;
      Object result;
      boolean ok;
{}
   }}'''.format('\n'.join([' ' * 6 + x for x in test_cases_src]))
        return solution_src[:i] + '\n' + main_function_src + '\n' + solution_src[i:]

    def generate_test_case_invocations(self,
                                       test_suite: TestSuite,
                                       test_passed_msg: str,
                                       test_failed_msg: str) -> List[str]:
        src = []
        total_count = len(test_suite.test_cases)

        for index, tc in enumerate(test_suite.test_cases):
            pass_msg = test_passed_msg
            pass_msg = pass_msg.replace('$index', str(index + 1))
            pass_msg = pass_msg.replace('$total', str(total_count))
            pass_msg = pass_msg.replace('$duration', '" + duration + "')

            fail_msg = test_failed_msg
            fail_msg = fail_msg.replace('$index', str(index + 1))
            fail_msg = fail_msg.replace('$total', str(total_count))
            fail_msg = fail_msg.replace('$expected', tc.result)
            fail_msg = fail_msg.replace('$result', '" + result + "')

            src.append('''
      start = System.nanoTime();
      result = solution.{}({});
      end = System.nanoTime();
      duration = TimeUnit.MILLISECONDS.convert(end - start, TimeUnit.NANOSECONDS);
      ok = verify(result, {});
      if (ok) {{
         System.out.println("{}");
      }} else {{
         System.out.println("{}");
         return;
      }}
      '''.format(test_suite.func_name,
                 ','.join(tc.args),
                 tc.result,
                 pass_msg,
                 fail_msg))
        return src
