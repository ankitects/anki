# Copyright: Daveight and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
"""
Java implementation of the Type Mapper API
"""

from testing.framework.string_utils import render_template
from testing.framework.type_mapper import TypeMapper
from testing.framework.syntax.syntax_tree import SyntaxTree


class JavaTypeMapper(TypeMapper):
    """
    Java type mapper.
    """

    def visit_array(self, node: SyntaxTree, context):
        """
        Java mapping for array-type

        :param node: target syntax tree node
        :param context: generation context
        :return: Java array-type declaration
        """
        return self.render(node.first_child(), context) + '[]'

    def visit_list(self, node: SyntaxTree, context):
        """
        Java mapping for list-type

        :param node: target syntax tree node
        :param context: generation context
        :return: Java list-type declaration
        """
        return 'List<' + self.render(node.first_child(), context) + '>'

    def visit_map(self, node: SyntaxTree, context):
        """
        Java mapping for map-type

        :param node: target syntax tree node
        :param context: generation context
        :return: Java map-type declaration
        """
        return 'Map<' + self.render(node.first_child(), context) + ', ' \
               + self.render(node.second_child(), context) + '>'

    def visit_int(self, node: SyntaxTree, context):
        """
        Java mapping for int-type

        :param node: target syntax tree node
        :param context: generation context
        :return: Java int-type declaration
        """
        if node.parent.is_root() or node.parent.is_array_type() or node.parent.user_type:
            return 'int'
        elif node.parent.is_container_type():
            return 'Integer'
        else:
            raise Exception('not supported parent type for int')

    def visit_long(self, node: SyntaxTree, context):
        """
        Java mapping for long-type

        :param node: target syntax tree node
        :param context: generation context
        :return: Java long-type declaration
        """
        if node.parent.is_root() or node.parent.is_array_type() or node.parent.user_type:
            return 'long'
        elif node.parent.is_container_type():
            return 'Long'
        else:
            raise Exception('not supported parent type for long')

    def visit_float(self, node: SyntaxTree, context):
        """
        Java mapping for float-type

        :param node: target syntax tree node
        :param context: generation context
        :return: Java float-type declaration
        """
        if node.parent.is_root() or node.parent.is_array_type() or node.parent.user_type:
            return 'double'
        elif node.parent.is_container_type():
            return 'Double'
        else:
            raise Exception('not supported parent type for float')

    def visit_string(self, node: SyntaxTree, context):
        """
        Java mapping for string-type

        :param node: target syntax tree node
        :param context: generation context
        :return: Java string-type declaration
        """
        return 'String'

    def visit_bool(self, node: SyntaxTree, context):
        """
        Java mapping for string-type

        :param node: target syntax tree node
        :param context: generation context
        :return: Java string-type declaration
        """
        if node.parent.is_root() or node.parent.is_array_type() or node.parent.user_type:
            return 'boolean'
        elif node.parent.is_container_type():
            return 'Boolean'
        else:
            raise Exception('not supported parent type for int')

    def visit_obj(self, node: SyntaxTree, context):
        """
        Java mapping for object-type. Stores type definition to the context

        :param node: target syntax tree node
        :param context: generation context
        :return: Java object-type declaration
        """
        args, _ = self.get_args(node, context)
        if node.node_type not in context:
            context[node.node_type] = render_template('''
                class {{type_name}} {
                    {%for a in args %}\t{{a.type}} {{a.name}};\n{% endfor %}}
                ''', args=args, type_name=node.node_type)
        return node.node_type

    def visit_void(self, node: SyntaxTree, context):
        """
        Java mapping for void-type.
        :param node: target syntax tree node
        :param context: generation context
        :return: Java void-type declaration
        """
        return 'void'

    def visit_linked_list(self, node: SyntaxTree, context):
        """
        Java mapping for linked list type
        :param node: target syntax tree node
        :param context: generation context
        :return: Java linked-list type declaration
        """
        child: SyntaxTree = node.first_child()
        if node.node_type not in context:
            context[node.node_type] = '''
                class ListNode<T> {
                    \tT data;
                    \tListNode<T> next;
 
                    \tpublic ListNode() { }

                    \tpublic ListNode(T data, ListNode<T> next) {
                    \t\tthis.data = data;
                    \t\tthis.next = next;
                    \t}
                }
            '''
        return 'ListNode<' + self.render(child, context) + '>'

    def visit_binary_tree(self, node: SyntaxTree, context):
        """
        Java mapping for binary tree type
        :param node: target syntax tree node
        :param context: generation context
        :return: Java binary-tree type declaration
        """
        child: SyntaxTree = node.first_child()
        if node.node_type not in context:
            context[node.node_type] = '''
                class BinaryTreeNode<T> {
                    \tT data;
                    \tBinaryTreeNode<T> left;
                    \tBinaryTreeNode<T> right;

                    \tpublic BinaryTreeNode() { }
 
                    \tpublic BinaryTreeNode(T data, BinaryTreeNode<T> left, BinaryTreeNode<T> right) {
                    \t\tthis.data = data;
                    \t\tthis.left = left;
                    \t\tthis.right = right;
                    \t}
                }
            '''
        return 'BinaryTreeNode<' + self.render(child, context) + '>'
