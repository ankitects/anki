# Copyright: Daveight and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
"""
C++ Input Converter Implementation
"""

from testing.framework.string_utils import render_template
from testing.framework.type_converter import TypeConverter
from testing.framework.types import ConverterFn
from testing.framework.syntax.syntax_tree import SyntaxTree


class CppInputConverter(TypeConverter):
    """
    Generates a C++ converter functions which convert an input JSON arguments to a solution's typed arguments.
    """

    def visit_obj(self, node: SyntaxTree, context):
        """
        Converts input element to a class instance
        [1, "2", 3] -> SampleClass(1, "2", 3)

        :param node: source node
        :param context: generation context
        :return: converter fn
        """
        converters, _ = self.get_converters(node, context)
        type_name = node.node_type
        src = render_template('''
            \t{{type_name}} obj;
            {% for c in converters %}\tobj.{{c.prop_name}} = {{c.fn_name}}(value[{{loop.index0}}]);{% endfor %}
            \treturn obj;''', type_name=type_name, converters=converters)
        return ConverterFn(node.name, src, 'jute::jValue', type_name)

    def visit_array(self, node: SyntaxTree, context):
        """
        Creates a vector, for every input element invokes inner type converter and puts it inside list
        list(map(string, string)):
        [["key1", "value1"], ["key2", "value2"]] -> Map<String, String>[]

        :param node: source node
        :param context: generation context
        :return: converter fn
        """
        return self.visit_list(node, context)

    def visit_list(self, node: SyntaxTree, context):
        """
        Creates a vector, for every input element invokes inner type converter and puts it inside list
        list(map(string, string)):
        [["key1", "value1"], ["key2", "value2"]] -> Map<String, String>[]

        :param node: source node
        :param context: generation context
        :return: converter fn
        """
        inner_converter: ConverterFn = self.render(node.first_child(), context)
        src = render_template('''
            \tvector<{{converter.ret_type}}> result;
            \tfor (int i = 0; i < value.size(); i++) {
                \t\t{{converter.ret_type}} obj = {{converter.fn_name}}(value[i]);
                \t\tresult.push_back(obj);
            \t}
            \treturn result;''', converter=inner_converter)
        return ConverterFn(node.name, src, 'jute::jValue', 'vector<' + inner_converter.ret_type + '>')

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
        ret_type = render_template('map<{{converters[0].ret_type}}, {{converters[1].ret_type}}>', converters=converters)
        src = render_template('''
            \t{{ret_type}} result;
            \tfor (int i = 0; i < value.size(); i+=2) {
            \t\t{{converters[0].ret_type}} k = {{converters[0].fn_name}}(value[i]);
            \t\t{{converters[1].ret_type}} v = {{converters[1].fn_name}}(value[i + 1]);
            \t\tresult[k] = v;
            \t}
            \treturn result;
        ''', converters=converters, ret_type=ret_type)
        return ConverterFn(node.name, src, 'jute::jValue', ret_type)

    def visit_int(self, node: SyntaxTree, context):
        """
        Converts input element to int

        :param node: source node
        :param context: generation context
        :return: converter fn
        """
        return ConverterFn(node.name, 'return value.as_int();', 'jute::jValue', 'int')

    def visit_long(self, node: SyntaxTree, context):
        """
        Converts input element to long

        :param node: source node
        :param context: generation context
        :return: converter fn
        """
        return ConverterFn(node.name, 'return value.as_long();', 'jute::jValue', 'long int')

    def visit_float(self, node: SyntaxTree, context):
        """
        Converts input element to double

        :param node: source node
        :param context: generation context
        :return: converter fn
        """
        return ConverterFn(node.name, 'return value.as_double();', 'jute::jValue', 'double')

    def visit_string(self, node: SyntaxTree, context):
        """
        Converts input element to String

        :param node: source node
        :param context: generation context
        :return: converter fn
        """
        return ConverterFn(node.name, 'return value.as_string();', 'jute::jValue', 'string')

    def visit_bool(self, node: SyntaxTree, context):
        """
        Converts input element to boolean

        :param node: source node
        :param context: generation context
        :return: converter fn
        """
        return ConverterFn(node.name, 'return value.as_bool();', 'jute::jValue', 'bool')

    def visit_linked_list(self, node: SyntaxTree, context):
        """
        Creates linked-list, for every input element invokes inner type converter and puts it inside linked list
        linked_list(string):
        ["a", "b", "c"] -> LinkedList<String>() { "a", "b", "c" }
        """
        child: ConverterFn = self.render(node.first_child(), context)
        src = render_template('''
            set<int> visited;
            shared_ptr<ListNode<{{child.ret_type}}>> head = nullptr;
            shared_ptr<ListNode<{{child.ret_type}}>> node = nullptr;
            int i = 1;
            while (visited.count(i) == 0 && i <= value.size()) {
            \t{{child.ret_type}} data = {{child.fn_name}}(value[i - 1]);
            \tauto tmp = make_shared<ListNode<{{child.ret_type}}>>(data);
            \tif (head == nullptr) {
            \t\thead = tmp;
            \t\tnode = head;
            \t} else {
            \t\tnode->next = tmp;
            \t\tnode = tmp;
            \t}
            \tif (i >= value.size()) {
            \t\tbreak;
            \t}
            \tvisited.insert(i);
            \ti = 2 * value[i].as_int() + 1;
            }
            if (visited.count(i) != 0 && i < value.size()) {
            \ti = (i / 2);
            \tshared_ptr<ListNode<int>> iter = head;
            \tint j = 0;
            \twhile (i-- != j) {
            \t\titer = iter->next;
            \t}
            \tnode->next = iter;
            }
            return head;
        ''', child=child)
        return ConverterFn(node.name, src, 'jute::jValue', 'shared_ptr<ListNode<' + child.ret_type + '>>')

    def visit_binary_tree(self, node: SyntaxTree, context):
        """
        Creates binary-list, for every input element invokes inner type converter and puts it inside linked list
        linked_list(string):
        ["a", "b", "c"] -> BinaryTree<String>() { "a", left="b", right="c" }
        """
        child: ConverterFn = self.render(node.first_child(), context)
        src = render_template('''
            vector<shared_ptr<BinaryTreeNode<{{child.ret_type}}>>> nodes;
            for (int i = 0; i < value.size(); i++) {
            \tshared_ptr<BinaryTreeNode<{{child.ret_type}}>> node = make_shared<BinaryTreeNode<{{child.ret_type}}>>();
            \tnode->left = nullptr;
            \tnode->right = nullptr;
            \tif (value[i].is_null()) {
            \t\tnode = nullptr;
            \t} else {
            \t\tnode->data = {{child.fn_name}}(value[i]);
            \t}
            \tnodes.push_back(node);
            }
            queue<shared_ptr<BinaryTreeNode<{{child.ret_type}}>>> children;
            for (int i = 0; i < nodes.size(); i++) {
                \tchildren.push(nodes[i]);
            }
            shared_ptr<BinaryTreeNode<{{child.ret_type}}>> root = children.front();
            children.pop();
            for (int i = 0; i < nodes.size(); i++) {
            \tshared_ptr<BinaryTreeNode<{{child.ret_type}}>> node = nodes[i];
            \tif (node != nullptr) {
            \t\tif (!children.empty()) {
            \t\t\tnode->left = children.front();
            \t\t\tchildren.pop();
            \t\t}
            \t\tif (!children.empty()) {
            \t\t\tnode->right = children.front();
            \t\t\tchildren.pop();
            \t\t}
            \t}
            }
            return root;
        ''', child=child)
        return ConverterFn(node.name, src, 'jute::jValue', 'shared_ptr<BinaryTreeNode<' + child.ret_type + '>>')
