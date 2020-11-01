from testing.framework.dto.test_suite import TestSuite
from testing.framework.generators.template_gen import TemplateGenerator


class JavaTemplateGenerator(TemplateGenerator):
    SOLUTION_TEMPLATE = '''public class Solution {{
{}   {} {}({}) {{
      //Add code here
   }}
}}'''

    def generate_template_src(self, test_suite: TestSuite) -> str:
        args_src = ', '.join([arg.arg_type + ' ' + arg.arg_name for arg in test_suite.test_args])
        types_src = ''
        for type_name in test_suite.user_types:
            if type_name != '':
                types_src += 3 * ' ' + test_suite.user_types[type_name] + '\n'

        return self.SOLUTION_TEMPLATE.format(types_src, test_suite.result_type, test_suite.func_name, args_src)
