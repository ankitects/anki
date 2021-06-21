# Copyright: Daveight and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
"""
JS implementation of the Type Mapper API
"""

from testing.framework.string_utils import render_template
from testing.framework.type_mapper import TypeMapper
from testing.framework.syntax.syntax_tree import SyntaxTree


class JsTypeMapper(TypeMapper):
    """
    Provides type mappings in JS.
    """

    def visit_array(self, node: SyntaxTree, context):
        """
        JS mapping for array-type

        :param node: target syntax tree node
        :param context: generation context
        :return: JS array-type declaration
        """
        return self.render(node.first_child(), context) + '[]'

    def visit_list(self, node: SyntaxTree, context):
        """
        JS mapping for list-type

        :param node: target syntax tree node
        :param context: generation context
        :return: JS list-type declaration
        """
        return self.render(node.first_child(), context) + '[]'

    def visit_map(self, node: SyntaxTree, context):
        """
        JS mapping for map-type

        :param node: target syntax tree node
        :param context: generation context
        :return: JS map-type declaration
        """
        return 'Map.<' + self.render(node.nodes[0], context) + ', ' + self.render(node.nodes[1], context) + '>'

    def visit_int(self, node: SyntaxTree, context):
        """
        JS mapping for int-type

        :param node: target syntax tree node
        :param context: generation context
        :return: JS int-type declaration
        """
        return 'number'

    def visit_long(self, node: SyntaxTree, context):
        """
        JS mapping for long-type

        :param node: target syntax tree node
        :param context: generation context
        :return: JS long-type declaration
        """
        return self.visit_int(node, context)

    def visit_float(self, node: SyntaxTree, context):
        """
        JS mapping for float-type

        :param node: target syntax tree node
        :param context: generation context
        :return: JS float-type declaration
        """
        return self.visit_int(node, context)

    def visit_string(self, node: SyntaxTree, context):
        """
        JS mapping for string-type

        :param node: target syntax tree node
        :param context: generation context
        :return: JS string-type declaration
        """
        return 'string'

    def visit_bool(self, node: SyntaxTree, context):
        """
        JS mapping for string-type

        :param node: target syntax tree node
        :param context: generation context
        :return: JS string-type declaration
        """
        return 'bool'

    def visit_obj(self, node: SyntaxTree, context):
        """
        JS mapping for object-type. Stores type definition to the context

        :param node: target syntax tree node
        :param context: generation context
        :return: JS object-type declaration
        """
        props, _ = self.get_args(node, context)
        if node.node_type not in context:
            context[node.node_type] = render_template('''
                class {{type_name}} {
                    \tconstructor({% for p in props %} {{p.name}}{% if not loop.last %},{% endif %}{% endfor %}) {
                    {% for p in props %}\t\tthis.{{p.name}} = {{p.name}}{% if not loop.last %}\n{% endif %}{% endfor %}
                    \t}
                }
                ''', props=props, type_name=node.node_type)
        return node.node_type

    def visit_linked_list(self, node: SyntaxTree, context):
        """
        JS mapping for linked list type
        :param node: target syntax tree node
        :param context: generation context
        :return: JS linked-list type declaration
        """
        if node.node_type not in context:
            context[node.node_type] = '''
                class ListNode {
                    \tconstructor(data = null) {
                    \t\tthis.data = data
                    \t\tthis.next = null
                    \t}
                }
                '''
        child: SyntaxTree = node.first_child()
        return 'ListNode<' + self.render(child, context) + '>'
