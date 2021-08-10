# Copyright: Daveight and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
"""
Python Output Converter Implementation
"""
from typing import List

from testing.framework.string_utils import render_template
from testing.framework.type_converter import TypeConverter
from testing.framework.types import ConverterFn
from testing.framework.syntax.syntax_tree import SyntaxTree


class PythonOutputConverter(TypeConverter):
    """
    Generates converter functions which convert a type to a result type.
    """

    def visit_array(self, node: SyntaxTree, context):
        """
        Array type has the same output type:
        [1,2,3] -> [1,2,3]
        no conversion is needed

        :param node: source node
        :param context: generation context
        :return: dummy converter fn
        """
        return self.visit_list(node, context)

    def visit_list(self, node: SyntaxTree, context):
        """
        List type has the same output type:
        [1,2,3] -> [1,2,3]
        no conversion is needed

        :param node: source node
        :param context: generation context
        :return: dummy converter fn
        """
        child: ConverterFn = self.render(node.first_child(), context)
        src: str = render_template('\treturn [{{fn}}(item) for item in value]', fn=child.fn_name)
        return ConverterFn(node.name, src, 'List[' + child.ret_type + ']', 'List[' + child.ret_type + ']')

    def visit_map(self, node: SyntaxTree, context):
        """
        Map type is converter to list, keys are followed by values:
        {a: "1", b: "2"} -> ["a", "1", "b", "2"]

        :param node: source node
        :param context: generation context
        :return: converter fn which converts map value to array
        """
        converters: List[ConverterFn] = [self.render(child, context) for child in node.nodes]
        src: str = render_template('''
            \tresult = []
            \tfor k in value:
            \t\tresult.append({{converters[0].fn_name}}(k))
            \t\tresult.append({{converters[1].fn_name}}(value[k]))
            \treturn result''', converters=converters)
        return ConverterFn(node.name, src, 'Dict', 'List')

    def visit_int(self, node: SyntaxTree, context):
        """
        int type has the same output type:
        1 -> 1
        no conversion is needed

        :param node: source node
        :param context: generation context
        :return: dummy converter fn
        """
        return ConverterFn(node.name, '\treturn value', 'int', 'int')

    def visit_long(self, node: SyntaxTree, context):
        """
        long type has the same output type:
        1 -> 1
        no conversion is needed

        :param node: source node
        :param context: generation context
        :return: dummy converter fn
        """
        return ConverterFn(node.name, '\treturn value', 'int', 'int')

    def visit_float(self, node: SyntaxTree, context):
        """
        float type has the same output type:
        1.1 -> 1.1
        no conversion is needed

        :param node: source node
        :param context: generation context
        :return: dummy converter fn
        """
        return ConverterFn(node.name, '\treturn value', 'float', 'float')

    def visit_string(self, node: SyntaxTree, context):
        """
        string type has the same output type:
        "a" -> "a"
        no conversion is needed

        :param node: source node
        :param context: generation context
        :return: dummy converter fn
        """
        return ConverterFn(node.name, '\treturn value', 'str', 'str')

    def visit_bool(self, node: SyntaxTree, context):
        """
        boolean type has the same output type:
        True -> True
        no conversion is needed

        :param node: source node
        :param context: generation context
        :return: dummy converter fn
        """
        return ConverterFn(node.name, '\treturn value', 'bool', 'bool')

    def visit_obj(self, node: SyntaxTree, context):
        """
        object type is converted to list, only field values are put to the array:
        {
            a: 1,
            b: "2"
        } -> [1, "2"]

        :param node: source node
        :param context: generation context
        :return: converter fn
        """
        converters: List[ConverterFn] = [self.render(child, context) for child in node.nodes]
        src: str = render_template('''
            \tresult = []
            {% for converter in converters %}
                \tresult.append({{converter.fn_name}}(value.{{converter.prop_name}}))
            {% endfor %}
            \treturn result''', converters=converters)
        return ConverterFn(node.name, src, node.node_type, 'List')

    def visit_linked_list(self, node: SyntaxTree, context):
        """
        Converts linked-list to a list
        linked_list(string):
        LinkedList<String>() { "a", "b", "c" } -> ["a", "b", "c"]
        """
        child = self.render(node.first_child(), context)
        src = render_template('''
            \tvisited = set([])
            \tresult = []
            \twhile value is not None and value not in visited:
            \t\tresult.append({{child.fn_name}}(value.data))
            \t\tvisited.add(value)
            \t\tvalue = value.next
            \treturn result''', child=child)

        return ConverterFn(node.name, src, 'ListNode[' + child.ret_type + ']', 'List[' + child.ret_type + ']')

    def visit_binary_tree(self, node: SyntaxTree, context):
        """
        Converts binary_tree to a list
        linked_list(string):
        LinkedList<String>() { "a", "b", "c" } -> ["a", "b", "c"]
        """
        child = self.render(node.first_child(), context)
        src = render_template('''
            \tresult = []
            \tqueue = []
            \tvisited = set([])
            \tqueue.append(value)
            \twhile queue:
            \t\tnode = queue.pop(0)
            \t\tif node is not None:
            \t\t\tvisited.add(node)
            \t\t\tresult.append({{child.fn_name}}(node.data))
            \t\telse:
            \t\t\tresult.append(None)
            \t\tif node is not None and not node.left in visited:
            \t\t\tqueue.append(node.left)
            \t\tif node is not None and not node.right in visited:
            \t\t\tqueue.append(node.right)
            \tj = None
            \tfor i in range(len(result) - 1, 0, -1):
            \t\tif result[i] is None:
            \t\t\tj = i
            \t\telse:
            \t\t\tbreak
            \tif j is not None:
            \t\tresult = result[:j]
            \treturn result''', child=child)
        return ConverterFn(node.name, src, 'BinaryTreeNode[' + child.arg_type + ']', 'List[' + child.ret_type + ']')
