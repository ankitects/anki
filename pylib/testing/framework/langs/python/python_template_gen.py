from testing.framework.dto.test_suite import TestSuite
from testing.framework.generators.solution_template_gen import SolutionTemplateGenerator
from testing.framework.syntax.utils import trim_indent, to_snake_case


class PythonTemplateGenerator(SolutionTemplateGenerator):
    SOLUTION_TEMPLATE = '''%(comments)s
    
    %(user_types)sdef %(func_name)s(%(arg_declarations)s):
        pass #Add code here
    '''

    def generate_solution_template(self, ts: TestSuite) -> str:
        user_types_src = '\n'.join(ts.user_types) if len(ts.user_types) > 0 else ''
        return trim_indent(self.SOLUTION_TEMPLATE % dict(
            user_types=user_types_src,
            result_type=ts.result_type,
            comments='\n'.join(['    # ' + line for line in ts.description.split('\n')]),
            func_name=to_snake_case(ts.func_name),
            arg_declarations=', '.join([x.arg_name + ': ' + x.arg_type for x in ts.test_args])))

