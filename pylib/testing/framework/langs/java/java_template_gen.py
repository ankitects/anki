from testing.framework.dto.test_suite import TestSuite
from testing.framework.generators.template_gen import SolutionTemplateGenerator
from testing.framework.syntax.utils import trim_indent, to_camel_case


class JavaTemplateGenerator(SolutionTemplateGenerator):
    """
    Produces solution function java template
    """

    SOLUTION_TEMPLATE = '''
/**
%(comments)s
*/
public class Solution {
%(classes)s
\tpublic %(result_type)s %(func_name)s(%(arg_declarations)s) {
\t\t//Add code here
\t}
}'''

    def generate_solution_template(self, ts: TestSuite) -> str:
        """
        Generates java source code template for the input test suite
        :param ts: input test suite
        :return: java source code with the solution template
        """
        classes_src = '\n'.join('\t' + row for src in ts.classes.values() for row in src.split('\n')) \
            if len(ts.classes.values()) > 0 else ''

        func_name = to_camel_case(ts.func_name)

        func_args = ', '.join([x.arg_type + ' ' + x.arg_name for x in ts.test_args])

        comments = '\n'.join(['* ' + line for line in ts.description.split('\n')])

        return trim_indent(self.SOLUTION_TEMPLATE % dict(
            classes=classes_src,
            result_type=ts.result_type,
            func_name=func_name,
            comments=comments,
            arg_declarations=func_args))
