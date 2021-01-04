from testing.framework.syntax.syntax_tree import SyntaxTree, SyntaxTreeVisitor


class JavaConverterGenerator(SyntaxTreeVisitor):
    """
    Builds converter classes invocations to cast input parameters to the correct types in java.
    """

    def visit_array(self, node: SyntaxTree, data):
        """
        Generates ArrayConverter initializer, which converts input data to java arrays
        :param node: target syntax tree node
        :param data: corresponding data item
        :return: java ArrayConverter initializer
        """
        return 'new ArrayConverter(' + self.render(node.first_child()) + ')'

    def visit_list(self, node: SyntaxTree, data):
        """
        Generates ArrayListConverter initializer, which converts input data to java ArrayList type
        :param node: target syntax tree node
        :param data: corresponding data item
        :return: java ArrayListConverter initializer
        """
        return 'new ArrayListConverter(' + self.render(node.first_child()) + ')'

    def visit_map(self, node: SyntaxTree, data):
        """
        Generates MapConverter initializer, which converts input data to java HashMap type
        :param node: target syntax tree node
        :param data: corresponding data item
        :return: java MapConverter initializer
        """
        raise Exception('Not implemented')

    def visit_int(self, node: SyntaxTree, data):
        """
        Generates IntegerConverter initializer, which converts input data to java integer type
        :param node: target syntax tree node
        :param data: corresponding data item
        :return: java IntegerConverter initializer
        """
        return 'new IntegerConverter()'

    def visit_float(self, node: SyntaxTree, data):
        """
        Generates DoubleConverter initializer, which converts input data to java double type
        :param node: target syntax tree node
        :param data: corresponding data item
        :return: java DoubleConverter initializer
        """
        return 'new DoubleConverter()'

    def visit_bool(self, node: SyntaxTree, data):
        """
        Generates BoolConverter initializer, which converts input data to java boolean type
        :param node: target syntax tree node
        :param data: corresponding data item
        :return: java BoolConverter initializer
        """
        return 'new BoolConverter()'

    def visit_string(self, node: SyntaxTree, data):
        """
        Generates StringConverter initializer, which converts input data to java string type
        :param node: target syntax tree node
        :param data: corresponding data item
        :return: java StringConverter initializer
        """
        return 'new StringConverter()'

    def visit_obj(self, node: SyntaxTree, data):
        """
        Generates UserTypeConverter initializer, which converts input data to user-specific java type
        :param node: target syntax tree node
        :param data: corresponding data item
        :return: java UserTypeConverter initializer
        """
        return 'new UserTypeConverter(Arrays.asList(' + ', '.join([self.render(node) for node in node.nodes]) + \
               '),' + node.node_type + '.class)'
