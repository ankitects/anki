# Copyright: Daveight and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
"""
Python Output Converter Implementation
"""

from testing.framework.string_utils import render_template
from testing.framework.type_converter import TypeConverter
from testing.framework.types import ConverterFn
from testing.framework.syntax.syntax_tree import SyntaxTree


class PythonInputConverter(TypeConverter):
    """
    Generates converter functions which convert an input type to a types which are used in a solution
    """

    def visit_array(self, node: SyntaxTree, context):
        """
        Creates List, for every input element invokes inner type converter and puts it inside list
        list(map(string, string)):
        [["key1", "value1"], ["key2", "value2"]] -> List[Dict[Str, Str]]

        :param node: source node
        :param context: generation context
        :return: converter fn
        """
        return self.visit_list(node, context)

    def visit_list(self, node: SyntaxTree, context):
        """
        Creates List, for every input element invokes inner type converter and puts it inside list
        list(map(string, string)):
        [["key1", "value1"], ["key2", "value2"]] -> List[Dict[Str, Str]]

        :param node: source node
        :param context: generation context
        :return: converter fn
        """
        child: ConverterFn = self.render(node.first_child(), context)
        src: str = render_template('\treturn [{{fn}}(item) for item in value]', fn=child.fn_name)
        return ConverterFn(node.name, src, '', 'List[' + child.ret_type + ']')

    def visit_map(self, node: SyntaxTree, context):
        """
        Creates a dictionary, for every input element invokes inner type converter and puts it inside the dictionary
        map(string, string):
        ["key1", "value1", "key2", "value2"] -> Dict[Str, Str]

        :param node: source node
        :param context: generation context
        :return: converter fn
        """
        converters = [self.render(child, context) for child in node.nodes]
        ret_type = render_template('Dict[{{key_type}}, {{value_type}}]',
                                   key_type=converters[0].ret_type,
                                   value_type=converters[1].ret_type)
        src = render_template('''
            \tresult = {}
            \titerator = iter(value)
            \twhile True:
            \t\ttry:
            \t\t\tk = {{converters[0].fn_name}}(next(iterator))
            \t\t\tv = {{converters[1].fn_name}}(next(iterator))
            \t\t\tresult[k] = v
            \t\texcept StopIteration:
            \t\t\tbreak
            \treturn result''', converters=converters, type_name=node.node_type, ret_type=ret_type)
        return ConverterFn(node.name, src, 'List', ret_type)

    def visit_int(self, node: SyntaxTree, context):
        """
        Converts input element to int

        :param node: source node
        :param context: generation context
        :return: converter fn
        """
        return ConverterFn(node.name, '\treturn int(value)', '', 'int')

    def visit_long(self, node: SyntaxTree, context):
        """
        Converts input element to int

        :param node: source node
        :param context: generation context
        :return: converter fn
        """
        return self.visit_int(node, context)

    def visit_float(self, node: SyntaxTree, context):
        """
        Converts input element to float

        :param node: source node
        :param context: generation context
        :return: converter fn
        """
        return ConverterFn(node.name, '\treturn float(value)', '', 'float')

    def visit_string(self, node: SyntaxTree, context):
        """
        Converts input element to string

        :param node: source node
        :param context: generation context
        :return: converter fn
        """
        return ConverterFn(node.name, '\treturn str(value)', '', 'str')

    def visit_bool(self, node: SyntaxTree, context):
        """
        Converts input element to boolean

        :param node: source node
        :param context: generation context
        :return: converter fn
        """
        return ConverterFn(node.name, '\treturn bool(value)', '', 'bool')

    def visit_obj(self, node: SyntaxTree, context):
        """
        Converts input element to a class instance
        [1, "2", 3] -> new SampleClass(1, "2", 3)

        :param node: source node
        :param context: generation context
        :return: converter fn
        """
        converters = [self.render(child, context) for child in node.nodes]
        arg_list = render_template(
            '{%for c in converters%}{{c.fn_name}}(value[{{loop.index0}}]){%if not loop.last%},{%endif%}{%endfor%}',
            converters=converters)
        src = render_template('\treturn {{type}}({{arg_list}})', arg_list=arg_list, type=node.node_type)
        return ConverterFn(node.name, src, 'List', node.node_type)
