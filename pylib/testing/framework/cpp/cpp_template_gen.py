# Copyright: Daveight and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
"""
C++ Template Generator API Implementation
"""

from testing.framework.cpp.cpp_type_mapper import CppTypeMapper
from testing.framework.string_utils import render_template
from testing.framework.template_gen import TemplateGenerator
from testing.framework.types import TestSuite
from testing.framework.syntax.syntax_tree import SyntaxTree


class CppTemplateGenerator(TemplateGenerator):
    """
    Generate a solution template in C++.
    """

    def __init__(self):
        self.type_mapper = CppTypeMapper()

    def get_template(self, tree: SyntaxTree, ts: TestSuite):
        """
        Generate a solution template in C++.

        :param tree: source syntax tree
        :param ts: target test sutie
        :return: solution template source
        """
        args, type_defs = self.type_mapper.get_args(tree)
        return render_template('''
            /**
            {% for line in description.split('\n') %}* {{line}}\n{% endfor %}*/
            {% for type_def in type_defs %}{{ type_def }}{% endfor %}
            class Solution {
            public:
            \t{{type}} {{fn}}({% for a in args %}{{a.type}} {{a.name}}{% if not loop.last %}, {% endif %}{% endfor %}) {
            \t\t//Add code here
            \t}
            };''', type=args[-1].type, args=args[:-1], fn=ts.fn_name, type_defs=type_defs.values(),
                  description=ts.description, retab=True)
