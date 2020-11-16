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
                                       ts: TestSuite,
                                       test_passed_msg_format: str,
                                       test_failed_msg_format: str) -> List[str]:
        src = []
        total_count = len(ts.test_cases)

        for index, tc in enumerate(ts.test_cases):
            test_passed_msg = test_passed_msg_format % dict(
                index=index + 1,
                total=total_count,
                duration='"+duration+"')

            test_failed_msg = test_failed_msg_format % dict(
                index=index + 1,
                total=total_count,
                expected=tc.result,
                result='"+result+"')

            src.append('''
        start = System.nanoTime();
        result = solution.%(func_name)s(%(func_args)s);
        end = System.nanoTime();
        duration = TimeUnit.MILLISECONDS.convert(end - start, TimeUnit.NANOSECONDS);
        ok = verify(result, %(result)s);
        if (ok) {{
           System.out.println("%(pass_msg)s");
        }} else {{
           System.out.println("%(fail_msg)s");
           return;
        }}''' % dict(
                func_name=ts.func_name,
                func_args=','.join(tc.args),
                result=tc.result,
                pass_msg=test_passed_msg,
                fail_msg=test_failed_msg))

        return src
