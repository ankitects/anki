# Copyright: Daveight and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
"""
JS Template Generator API Implementation
"""

from testing.framework.js.js_type_mapper import JsTypeMapper
from testing.framework.string_utils import render_template
from testing.framework.template_gen import TemplateGenerator
from testing.framework.types import TestSuite
from testing.framework.syntax.syntax_tree import SyntaxTree


class JsTemplateGenerator(TemplateGenerator):
    """
    Generate a solution template in JS.
    """

    def __init__(self):
        self.type_mapper = JsTypeMapper()

    def get_template(self, tree: SyntaxTree, ts: TestSuite):
        """
        Generate a solution template in JS.
        :param tree: source syntax tree
        :param ts: target test sutie
        :return: solution template source
        """
        args, type_defs = self.type_mapper.get_args(tree)
        return render_template('''
            /**
            {% for line in description.split('\n') %}* {{line}}\n{% endfor %}*/
            {% if type_defs|length > 0 %}
            /**
            {% for type_def in type_defs %}{{type_def}}* \n{% endfor %}*/
            {% endif %}
            /**
            {% for arg in p %}* @param { {{arg.type}} } {{arg.name}}\n{% endfor %}* @return {{ t }}
            */
            
            function {{f}}({% for a in p %}{{a.name}}{% if not loop.last %}, {% endif %}{% endfor %}) {
            \t//Add code here
            }''', t=args[-1].type, p=args[:-1], f=ts.fn_name, type_defs=type_defs.values(),
                               description=ts.description, retab=True)
