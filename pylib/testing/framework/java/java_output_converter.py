# Copyright: Daveight and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
"""
Java Output Converter Implementation
"""

from testing.framework.string_utils import render_template
from testing.framework.type_converter import TypeConverter
from testing.framework.types import ConverterFn
from testing.framework.syntax.syntax_tree import SyntaxTree, is_primitive_type


class JavaOutputConverter(TypeConverter):
    """
    Generates a Java converter functions which convert a solution's result type to an output format.
    """

    def visit_array(self, node: SyntaxTree, context):
        """
        Array type has the same output format:
        [1,2,3] -> [1,2,3]
        no conversion is needed

        :param node: source node
        :param context: generation context
        :return: dummy converter fn
        """
        child = self.render(node.first_child(), context)
        return ConverterFn(node.name, 'return value;', child.ret_type + '[]', child.ret_type + '[]')

    def visit_list(self, node: SyntaxTree, context):
        """
        List type has the same output format:
        [1,2,3] -> [1,2,3]
        no conversion is needed

        :param node: source node
        :param context: generation context
        :return: dummy converter fn
        """
        child = self.render(node.first_child(), context)
        return ConverterFn(node.name, 'return value;', 'List<' + child.ret_type + '>', 'List<' + child.ret_type + '>')

    def visit_map(self, node: SyntaxTree, context):
        """
        Map type is converted to a List, so that keys are followed by values:
        {a: "1", b: "2"} -> ["a", "1", "b", "2"]

        :param node: source node
        :param context: generation context
        :return: converter fn which converts map value to array
        """
        converters = [self.render(child, context) for child in node.nodes]
        src = render_template('''
            \tList<Object> result = new ArrayList<>();
            \tfor (Map.Entry<{{converters[0].arg_type}}, {{converters[1].arg_type}}> entry : value.entrySet()) {
            \t\tresult.add({{converters[0].fn_name}}(entry.getKey()));
            \t\tresult.add({{converters[1].fn_name}}(entry.getValue()));
            \t}
            return result;''', converters=converters)
        arg_type = 'Map<' + converters[0].arg_type + ', ' + converters[1].arg_type + '>'
        return ConverterFn(node.name, src, arg_type, 'List')

    def visit_int(self, node: SyntaxTree, context):
        """
        int type has the same output format:
        1 -> 1
        no conversion is needed

        :param node: source node
        :param context: generation context
        :return: dummy converter fn
        """
        t = 'int' if is_primitive_type(node) else 'Integer'
        return ConverterFn(node.name, 'return value;', t, t)

    def visit_long(self, node: SyntaxTree, context):
        """
        long type has the same output format:
        1 -> 1
        no conversion is needed

        :param node: source node
        :param context: generation context
        :return: dummy converter fn
        """
        t = 'long' if is_primitive_type(node) else 'Long'
        return ConverterFn(node.name, 'return value;', t, t)

    def visit_float(self, node: SyntaxTree, context):
        """
        float type has the same output format:
        1.1 -> 1.1
        no conversion is needed

        :param node: source node
        :param context: generation context
        :return: dummy converter fn
        """
        t = 'double' if is_primitive_type(node) else 'Double'
        return ConverterFn(node.name, 'return value;', t, t)

    def visit_string(self, node: SyntaxTree, context):
        """
        string type has the same output format:
        "a" -> "a"
        no conversion is needed

        :param node: source node
        :param context: generation context
        :return: dummy converter fn
        """
        return ConverterFn(node.name, 'return value;', 'String', 'String')

    def visit_bool(self, node: SyntaxTree, context):
        """
        boolean type has the same output format:
        True -> True
        no conversion is needed

        :param node: source node
        :param context: generation context
        :return: dummy converter fn
        """
        t = 'boolean' if is_primitive_type(node) else 'Boolean'
        return ConverterFn(node.name, 'return value;', t, t)

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
        converters = [self.render(child, context) for child in node.nodes]
        src = render_template('''
            \tList<Object> result = new ArrayList<>();
            {% for converter in converters %}
                \tresult.add({{converter.fn_name}}(value.{{converter.prop_name}}));
            {% endfor %}
            return result;''', converters=converters)
        return ConverterFn(node.name, src, node.node_type, 'List')
