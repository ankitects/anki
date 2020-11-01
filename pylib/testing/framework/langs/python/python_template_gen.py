from testing.framework.dto.test_suite import TestSuite
from testing.framework.generators.template_gen import TemplateGenerator


class PythonTemplateGenerator(TemplateGenerator):
    SOLUTION_TEMPLATE = '''{}def {}({}) -> {}:
   #Add code here'''

    def generate_template_src(self, ts: TestSuite) -> str:
        args_src = ', '.join([arg.arg_name + ': ' + arg.arg_type for arg in ts.test_args])

        user_types = []
        for type_name in ts.user_types:
            if type_name != '':
                user_types.append(ts.user_types[type_name])
        user_types_src = '\n'.join(user_types)
        if len(user_types) > 0:
            user_types_src += '\n'

        return self.SOLUTION_TEMPLATE.format(user_types_src, ts.func_name, args_src, ts.result_type)
