# Copyright: Daveight and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
"""
Java Input Converter Implementation
"""
from typing import List

from testing.framework.string_utils import render_template
from testing.framework.type_converter import TypeConverter
from testing.framework.types import ConverterFn
from testing.framework.syntax.syntax_tree import SyntaxTree, is_primitive_type


def wrap_array_declaration(inner_type: str) -> str:
    """
    generates java array declaration
    :param inner_type: type to be wrapped
    :return: string containing the variable array declaration
    """
    if '[' in inner_type:
        idx = inner_type.index('[')
        initializer = inner_type[:idx] + '[value.size()]' + inner_type[idx:]
    else:
        initializer = inner_type + '[value.size()]'
    return f'{inner_type} result[] = new {initializer};'


class JavaInputConverter(TypeConverter):
    """
    Generates a Java converter functions which convert an input JSON arguments to a solution's typed arguments.
    """

    def visit_array(self, node: SyntaxTree, context):
        """
        Creates array, for every input element invokes inner type converter and puts it inside list
        list(map(string, string)):
        [["key1", "value1"], ["key2", "value2"]] -> Map<String, String>[]

        :param node: source node
        :param context: generation context
        :return: converter fn
        """
        child = self.render(node.first_child(), context)
        array_declaration: str = wrap_array_declaration(child.ret_type)
        src = render_template('''
            \t{{array_declaration}}
            \tint i = 0;
            \tfor (JsonNode node : value) {
            \t\tresult[i++] = {{child.fn_name}}(node);
            \t}
            \treturn result;''', child=child, array_declaration=array_declaration)
        return ConverterFn(node.name, src, 'JsonNode', child.ret_type + '[]')

    def visit_list(self, node: SyntaxTree, context):
        """
        Creates List, for every input element invokes inner type converter and puts it inside list
        list(map(string, string)):
        [["key1", "value1"], ["key2", "value2"]] -> List<Map<String, String>>

        :param node: source node
        :param context: generation context
        :return: converter fn
        """
        child = self.render(node.first_child(), context)
        src = render_template('''
            \tList<{{child.ret_type}}> result = new ArrayList<>();
            \tfor (JsonNode node : value) {
            \t\tresult.add({{child.fn_name}}(node));
            \t}
            \treturn result;''', child=child)
        return ConverterFn(node.name, src, 'JsonNode', 'List<' + child.ret_type + '>')

    def visit_map(self, node: SyntaxTree, context):
        """
        Creates a map, for every input element invokes inner type converter and puts it inside the dictionary
        map(string, string):
        ["key1", "value1", "key2", "value2"] -> Map<String, String>

        :param node: source node
        :param context: generation context
        :return: converter fn
        """
        converters = [self.render(child, context) for child in node.nodes]
        ret_type = render_template('Map<{{converters[0].ret_type}}, {{converters[1].ret_type}}>', converters=converters)
        src = render_template('''
            \t{{ret_type}} result = new HashMap<>();
            \tIterator<JsonNode> iterator = value.iterator();
            \twhile (iterator.hasNext()) {
            \t\t{{converters[0].ret_type}} key = {{converters[0].fn_name}}(iterator.next());
            \t\t{{converters[1].ret_type}} val = {{converters[1].fn_name}}(iterator.next());
            \t\tresult.put(key, val);
            \t}
            \treturn result;''', converters=converters, type_name=node.node_type, ret_type=ret_type)
        return ConverterFn(node.name, src, 'JsonNode', ret_type)

    def visit_int(self, node: SyntaxTree, context):
        """
        Converts input element to int

        :param node: source node
        :param context: generation context
        :return: converter fn
        """
        return ConverterFn(node.name, 'return value.asInt();', 'JsonNode',
                           'int' if is_primitive_type(node) else 'Integer')

    def visit_long(self, node: SyntaxTree, context):
        """
        Converts input element to long

        :param node: source node
        :param context: generation context
        :return: converter fn
        """
        return ConverterFn(node.name, 'return value.asLong();', 'JsonNode',
                           'long' if is_primitive_type(node) else 'Long')

    def visit_float(self, node: SyntaxTree, context):
        """
        Converts input element to double

        :param node: source node
        :param context: generation context
        :return: converter fn
        """
        return ConverterFn(node.name, 'return value.asDouble();', 'JsonNode',
                           'double' if is_primitive_type(node) else 'Double')

    def visit_string(self, node: SyntaxTree, context):
        """
        Converts input element to String

        :param node: source node
        :param context: generation context
        :return: converter fn
        """
        return ConverterFn(node.name, 'return value.asText();', 'JsonNode', 'String')

    def visit_bool(self, node: SyntaxTree, context):
        """
        Converts input element to boolean

        :param node: source node
        :param context: generation context
        :return: converter fn
        """
        return ConverterFn(node.name, 'return value.asBoolean();', 'JsonNode',
                           'boolean' if is_primitive_type(node) else 'Boolean')

    def visit_obj(self, node: SyntaxTree, context):
        """
        Converts input element to a class instance
        [1, "2", 3] -> new SampleClass(1, "2", 3)

        :param node: source node
        :param context: generation context
        :return: converter fn
        """
        converters: List[ConverterFn] = [self.render(child, context) for child in node.nodes]
        src = render_template('''
            {{type_name}} result = new {{type_name}}();
            {% for c in converters %}result.{{c.prop_name}} = {{c.fn_name}}(value.get({{loop.index0}}));\n{% endfor %}
            return result;''', converters=converters, type_name=node.node_type)
        return ConverterFn(node.name, src, 'JsonNode', node.node_type)

    def visit_linked_list(self, node: SyntaxTree, context):
        """
        Creates linked-list, for every input element invokes inner type converter and puts it inside linked list
        linked_list(string):
        ["a", "b", "c"] -> ListNode<String>() { "a", "b", "c" }
        """

        child: ConverterFn = self.render(node.first_child(), context)
        src = render_template('''
            ListNode<{{child.ret_type}}> head = new ListNode<>();
            ListNode<{{child.ret_type}}> node = head;
            \tfor (JsonNode n : value) {
            \t\tListNode<{{child.ret_type}}> nextNode = new ListNode<>();
            \t\tnextNode.data = {{child.fn_name}}(n);
            \t\tnode.next = nextNode;
            \t\tnode = nextNode;
            \t}
            return head.next;
        ''', child=child)
        return ConverterFn(node.name, src, 'JsonNode', 'ListNode<' + child.ret_type + '>')

    def visit_binary_tree(self, node: SyntaxTree, context):
        """
        Creates binary-tree, for every input element invokes inner type converter and puts it inside binary tree
        binary_tree(string):
        ["a", "b", "c"] -> BinaryTreeNode<String>() { "a", left: "b", right: "c" }
        """
        child: ConverterFn = self.render(node.first_child(), context)
        src = render_template('''
            List<BinaryTreeNode<{{child.ret_type}}>> nodes = new ArrayList<>();
            for (JsonNode n : value) {
            \tBinaryTreeNode<{{child.ret_type}}> node = new BinaryTreeNode<>();
            \tif (n.isNull()) {
            \t\tnode = null;
            \t} else {
            \t\tnode.data = {{child.fn_name}}(n);
            \t}
            \tnodes.add(node);
            }
            Deque<BinaryTreeNode<{{child.ret_type}}>> children = new LinkedList<>(nodes);
            BinaryTreeNode<{{child.ret_type}}> root = children.removeFirst();
            for (BinaryTreeNode<{{child.ret_type}}> node : nodes) {
            \tif (node != null) {
            \t\tif (!children.isEmpty()) {
            \t\t\tnode.left = children.removeFirst();
            \t\t}
            \t\tif (!children.isEmpty()) {
            \t\t\tnode.right = children.removeFirst();
            \t\t}
            \t}
            }
            return root;
        ''', child=child)
        return ConverterFn(node.name, src, 'JsonNode', 'BinaryTreeNode<' + child.ret_type + '>')
