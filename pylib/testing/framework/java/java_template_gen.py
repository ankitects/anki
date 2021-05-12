# Copyright: Daveight and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
"""
Java Template Generator API Implementation
"""

from testing.framework.java.java_type_mapper import JavaTypeMapper
from testing.framework.string_utils import render_template
from testing.framework.template_gen import TemplateGenerator
from testing.framework.types import TestSuite
from testing.framework.syntax.syntax_tree import SyntaxTree
from testing.framework.string_utils import to_camel_case


class JavaTemplateGenerator(TemplateGenerator):
    """
    Generate a solution template in Java.
    """

    def __init__(self):
        self.type_mapper = JavaTypeMapper()

    def get_template(self, tree: SyntaxTree, ts: TestSuite) -> str:
        """
        Generate a solution template in Java.

        :param tree: source syntax tree
        :param ts: target test sutie
        :return: solution template source
        """
        args, type_defs = self.type_mapper.get_args(tree)
        return render_template('''
            /**
            {% for line in description.split('\n') %}* {{line}}\n{% endfor %}*/
            {% if type_defs|length > 0%}{% for type_def in type_defs %}{{ type_def }}\n{% endfor %}{%endif%}\
            public class Solution {
            \tpublic {{t}} {{f}}({% for a in p %}{{a.type}} {{a.name}}{% if not loop.last %}, {% endif %}{% endfor %}) {
            \t\t//Add code here
            \t}
            }
            ''', t=args[-1].type, p=args[:-1], f=to_camel_case(ts.fn_name), type_defs=type_defs.values(),
                 description=ts.description, retab=True)