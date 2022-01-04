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


class JsInputConverter(TypeConverter):
    """
    Generates converter functions which convert an input type to a types which are used in a solution
    """

    def visit_array(self, node: SyntaxTree, context):
        """
        Creates array, for every input element invokes inner type converter and puts it inside list
        list(map(string, string)):
        [["key1", "value1"], ["key2", "value2"]] -> [{"key": "value1"}, {"key2": "value2"}]

        :param node: source node
        :param context: generation context
        :return: converter fn
        """
        return self.visit_list(node, context)

    def visit_list(self, node: SyntaxTree, context):
        """
        Creates array, for every input element invokes inner type converter and puts it inside list
        list(map(string, string)):
        [["key1", "value1"], ["key2", "value2"]] -> [{"key": "value1"}, {"key2": "value2"}]

        :param node: source node
        :param context: generation context
        :return: converter fn
        """
        child: ConverterFn = self.render(node.first_child(), context)
        return ConverterFn(node.name, render_template('''
            \tvar result = []
            \tfor (var i = 0; i < value.length; i++) { result.push({{fn}}(value[i])) }
            \treturn result
            ''', fn=child.fn_name), '')

    def visit_map(self, node: SyntaxTree, context):
        """
        Creates a dictionary, for every input element invokes inner type converter and puts it inside the dictionary
        map(string, string):
        ["key1", "value1", "key2", "value2"] -> {"key": "value1", "key2": "value2"}

        :param node: source node
        :param context: generation context
        :return: converter fn
        """
        converters: List[ConverterFn] = [self.render(child, context) for child in node.nodes]
        return ConverterFn(node.name, render_template('''
            \tvar result = new Map()
            \tfor (var i = 0; i < value.length; i += 2) {
            \t\tresult.set({{converters[0].fn_name}}(value[i]), {{converters[1].fn_name}}(value[i+1]))
            \t}
            \treturn result
        ''', converters=converters), '')

    @staticmethod
    def identity(node: SyntaxTree):
        """
        Generates a dummy converter function, which returns argument
        :param node: target node
        :return: value argument
        """
        return ConverterFn(node.name, 'return value', '')

    def visit_int(self, node: SyntaxTree, context):
        """
        1 -> 1
        no conversion is needed

        :param node: source node
        :param context: generation context
        :return: dummy converter fn
        """
        return self.identity(node)

    def visit_long(self, node: SyntaxTree, context):
        """
        1 -> 1
        no conversion is needed

        :param node: source node
        :param context: generation context
        :return: dummy converter fn
        """
        return self.identity(node)

    def visit_float(self, node: SyntaxTree, context):
        """
        1.1 -> 1.1
        no conversion is needed

        :param node: source node
        :param context: generation context
        :return: dummy converter fn
        """
        return self.identity(node)

    def visit_string(self, node: SyntaxTree, context):
        """
        "a" -> "a"
        no conversion is needed

        :param node: source node
        :param context: generation context
        :return: dummy converter fn
        """
        return self.identity(node)

    def visit_bool(self, node: SyntaxTree, context):
        """
        True -> True
        no conversion is needed

        :param node: source node
        :param context: generation context
        :return: dummy converter fn
        """
        return self.identity(node)

    def visit_obj(self, node: SyntaxTree, context):
        """
        Converts input element to an object
        [1, "2", 3] -> {"a": 1, "b": "2", "c": 3}

        :param node: source node
        :param context: generation context
        :return: converter fn
        """
        converters = [self.render(n, context) for n in node.nodes]
        return ConverterFn(node.name, render_template('''
            \tvar result = {}
            {% for converter in converters %}
            \tresult['{{converter.prop_name}}'] = {{converter.fn_name}}(value[{{loop.index0}}])
            {% endfor %}
            \treturn result
            ''', converters=converters), '')

    def visit_linked_list(self, node: SyntaxTree, context):
        """
        Creates linked-list, for every input element invokes inner type converter and puts it inside linked list
        linked_list(string):
        ["a", 1, "b", 2, "c"] -> LinkedListNode("a") => LinkedListNode("b") => LinkedListNode("c")
        """

        child: ConverterFn = self.render(node.first_child(), context)
        src = render_template('''
            const nodes = []
            for (let i = 0; i < value.length; i += 2) {
            \tconst n = value[i]
            \tconst node = new ListNode()
            \tnode.data = {{child.fn_name}}(n)
            \tnodes.push(node)
            }
            for (let i = 1; i < value.length; i += 2) {
            \tconst n = value[i]
            \tconst node = nodes[Math.floor((i - 1) / 2)]
            \tconst nextIndex = n
            \tif (nextIndex >= 0) {
            \t\tconst nextNode = nodes[nextIndex]
            \t\tnode.next = nextNode
            \t}
            }
            return nodes.length == 0 ? null : nodes[0]
        ''', child=child)

        return ConverterFn(node.name, src, '')

    def visit_binary_tree(self, node: SyntaxTree, context):
        """
        Creates binary-tree, for every input element invokes inner type converter and puts it inside binary tree
        binary_tree(string):
        ["a", "b", "c"] -> BinaryTreeNode<String>() { "a", left: "b", right: "c" }
        """
        child: ConverterFn = self.render(node.first_child(), context)
        src = render_template('''
            if (!value) {
            \treturn null;
            }
            const nodes = []
            for (let n of value) {
            \tlet node = new BinaryTreeNode()
            \tif (n == null) {
            \t\tnode = null
            \t} else {
            \t\tnode.data = {{child.fn_name}}(n)
            \t}
            \tnodes.push(node)
            }
            const children = []
            for (n of nodes) {
                children.push(n)
            }
            const root = children.shift()
            for (let node of nodes) {
            \tif (node != null) {
            \t\tif (children.length) {
            \t\t\tnode.left = children.shift()
            \t\t}
            \t\tif (children.length) {
            \t\t\tnode.right = children.shift()
            \t\t}
            \t}
            }
            return root
        ''', child=child)
        return ConverterFn(node.name, src, '')
