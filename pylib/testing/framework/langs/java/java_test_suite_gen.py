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
                                     test_suite: TestSuite,
                                     test_summary_msg: str) -> str:
        i = solution_src.rindex('}')
        main_function_src = '''   public static void main(String[] args) {{
      Solution solution = new Solution();
      long start, end;
      String msg;
      boolean result;
{}
      System.out.println("{}");
   }}'''.format('\n'.join([' ' * 6 + x for x in test_cases_src]), test_summary_msg)
        return solution_src[:i] + '\n' + main_function_src + '\n' + solution_src[i:]

    def generate_test_case_invocations(self,
                                       test_suite: TestSuite,
                                       test_passed_msg: str,
                                       test_failed_msg: str) -> List[str]:
        src = []
        total_count = len(test_suite.test_cases)
        for index, tc in enumerate(test_suite.test_cases):
            src.append('''// case {}
                start = System.nanoTime();
                result = verify(solution.{}({}), {});
                end = System.nanoTime();
                msg = "{}/{} " + TimeUnit.MILLISECONDS.convert(end - start, TimeUnit.NANOSECONDS) + " ms - ";
                if (result) {{
                    msg += "{}";
                    System.out.println(msg);
                }} else {{
                    msg += "{}";
                    System.out.println(msg);
                    return;
                }}'''.format(index + 1, test_suite.func_name, ','.join(tc.args),
                             tc.result, index + 1, total_count, test_passed_msg,
                             test_failed_msg))
        return src

