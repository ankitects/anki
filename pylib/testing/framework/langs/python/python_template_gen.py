from testing.framework.dto.test_suite import TestSuite
from testing.framework.generators.template_gen import SolutionTemplateGenerator
from testing.framework.syntax.utils import trim_indent, to_snake_case


class PythonTemplateGenerator(SolutionTemplateGenerator):
    """
    Produces solution function python template
    """

    SOLUTION_TEMPLATE = '''%(comments)s
%(classes)s
def %(func_name)s(%(arg_declarations)s):
\tpass #Add code here
'''

    def generate_solution_template(self, ts: TestSuite) -> str:
        """
        Generates python source code template for the input test suite
        :param ts: input test suite
        :return: python source code with the solution template
        """
        classes = '\n'.join(type_src for type_src in ts.classes.values()) \
            if len(ts.classes.values()) > 0 else ''

        comments = '\n'.join(['# ' + line for line in ts.description.split('\n')])

        args = ', '.join([x.arg_name + ': ' + x.arg_type for x in ts.test_args])

        func_name = to_snake_case(ts.func_name)

        return trim_indent(self.SOLUTION_TEMPLATE % dict(
            classes=classes,
            result_type=ts.result_type,
            comments=comments,
            func_name=func_name,
            arg_declarations=args))
