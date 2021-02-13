from testing.framework.syntax.syntax_tree import SyntaxTree, SyntaxTreeVisitor


class PythonConverterGenerator(SyntaxTreeVisitor):
    """
    Builds converter classes invocations to cast input parameters to the correct types in java.
    """

    def visit_array(self, node: SyntaxTree, data):
        """
        Generates ListConverter initializer, which converts input data to python list
        :param node: target syntax tree node
        :param data: corresponding data item
        :return: python ListConverter initializer
        """
        return 'ListConverter(' + self.render(node.first_child()) + ')'

    def visit_list(self, node: SyntaxTree, data):
        """
        Generates ListConverter initializer, which converts input data to python list
        :param node: target syntax tree node
        :param data: corresponding data item
        :return: python ListConverter initializer
        """
        return 'ListConverter(' + self.render(node.first_child()) + ')'

    def visit_map(self, node: SyntaxTree, data):
        """
        Generates MapConverter initializer, which converts input data to python dict type
        :param node: target syntax tree node
        :param data: corresponding data item
        :return: python MapConverter initializer
        """
        raise Exception('Not implemented')

    def visit_int(self, node: SyntaxTree, data):
        """
        Generates IntegerConverter initializer, which converts input data to python int type
        :param node: target syntax tree node
        :param data: corresponding data item
        :return: python IntegerConverter initializer
        """
        return 'IntegerConverter()'

    def visit_long(self, node: SyntaxTree, data):
        """
        Generates IntegerConverter initializer, which converts input data to python int type
        :param node: target syntax tree node
        :param data: corresponding data item
        :return: python IntegerConverter initializer
        """
        return 'IntegerConverter()'

    def visit_float(self, node: SyntaxTree, data):
        """
        Generates FloatConverter initializer, which converts input data to python float type
        :param node: target syntax tree node
        :param data: corresponding data item
        :return: java FloatConverter initializer
        """
        return 'FloatConverter()'

    def visit_bool(self, node: SyntaxTree, data):
        """
        Generates BoolConverter initializer, which converts input data to python boolean type
        :param node: target syntax tree node
        :param data: corresponding data item
        :return: python BoolConverter initializer
        """
        return 'BoolConverter()'

    def visit_string(self, node: SyntaxTree, data):
        """
        Generates StringConverter initializer, which converts input data to python string type
        :param node: target syntax tree node
        :param data: corresponding data item
        :return: python StringConverter initializer
        """
        return 'StringConverter()'

    def visit_obj(self, node: SyntaxTree, data):
        """
        Generates UserTypeConverter initializer, which converts input data to user-specific python type
        :param node: target syntax tree node
        :param data: corresponding data item
        :return: python UserTypeConverter initializer
        """
        return 'ClassConverter([' + ', '.join([self.render(node) for node in node.nodes]) + '], ' + \
               node.node_type + ')'

