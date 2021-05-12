# Copyright: Daveight and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
"""
Python Template Generator API Implementation
"""

from testing.framework.python.python_type_mapper import PythonTypeMapper
from testing.framework.string_utils import render_template
from testing.framework.template_gen import TemplateGenerator
from testing.framework.types import TestSuite
from testing.framework.syntax.syntax_tree import SyntaxTree
from testing.framework.string_utils import to_snake_case


class PythonTemplateGenerator(TemplateGenerator):
    """
    Generate a solution template in python.
    """

    def __init__(self):
        self.type_mapper = PythonTypeMapper()

    def get_template(self, tree: SyntaxTree, ts: TestSuite) -> str:
        """
        Generate a solution template in python.
        :param tree: source syntax tree
        :param ts: target test sutie
        :return: solution template source
        """
        args, type_defs = self.type_mapper.get_args(tree)
        return render_template('''
            {% for line in description.split('\n') %}# {{line}}\n{% endfor %}
            {% if type_defs|length > 0 %}{% for type_def in type_defs %}{{ type_def }}{% endfor %}{% endif %}
            def {{fn}}({% for a in l %}{{a.name}}: {{a.type}}{% if not loop.last %}, {% endif %}{% endfor %})->{{t}}:
            \t#Add code here
            \tpass
            ''', t=args[-1].type, l=args[:-1], fn=to_snake_case(ts.fn_name), type_defs=type_defs.values(),
                 description=ts.description, retab=True)
