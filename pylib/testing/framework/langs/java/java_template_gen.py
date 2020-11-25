from testing.framework.dto.test_suite import TestSuite
from testing.framework.generators.solution_template_gen import SolutionTemplateGenerator
from testing.framework.syntax.utils import trim_indent, to_camel_case


class JavaTemplateGenerator(SolutionTemplateGenerator):
    SOLUTION_TEMPLATE = '''
    public class Solution {
        %(user_types)spublic %(result_type)s %(func_name)s(%(arg_declarations)s) {
            //Add code here
        }
    }'''

    def generate_solution_template(self, ts: TestSuite) -> str:
        user_types_src = '\n'.join(ts.user_types) if len(ts.user_types) > 0 else ''
        return trim_indent(self.SOLUTION_TEMPLATE % dict(
            user_types=user_types_src,
            result_type=ts.result_type,
            func_name=to_camel_case(ts.func_name),
            arg_declarations=','.join([x.arg_type + ' ' + x.arg_name for x in ts.test_args])
        ))
