from testing.framework.syntax.syntax_tree import SyntaxTree, SyntaxTreeVisitor


class PythonTypeGenerator(SyntaxTreeVisitor):
    """
    Generates Python type declarations
    """

    def visit_void(self, node: SyntaxTree, data):
        """
        provides python mapping for "void" node type

        :param node: syntax tree node
        :param data: data item associated with the tree node
        :return: python void type declaration
        """
        return ''

    def visit_array(self, node: SyntaxTree, data):
        """
        provides python mapping for "array" node type

        :param node: syntax tree node
        :param data: data item associated with the tree node
        :return: python array-type declaration List[node-type]
        """
        if len(node.nodes) != 1:
            raise Exception('Array can have only 1 inner-type')
        return 'List[' + self.render(node.first_child(), data) + ']'

    def visit_list(self, node: SyntaxTree, data):
        """
        provides python mapping for "list" node type

        :param node: syntax tree node
        :param data: data item associated with the tree node
        :return: java list-type declaration List[child-type]
        """
        if len(node.nodes) != 1:
            raise Exception('List can have only 1 inner-type')
        return 'List[' + self.render(node.first_child(), data) + ']'

    def visit_map(self, node: SyntaxTree, data):
        """
        provides python mapping for "map" node type

        :param node: syntax tree node
        :param data: data item associated with the tree node
        :return: python map-type Dict[key-type, value-type]
        """
        if len(node.nodes) != 2:
            raise Exception('Map inner-types count must be 2')
        return 'Dict[' + self.render(node.first_child()) + ', ' + self.render(node.second_child()) + ']'

    def visit_int(self, node, data):
        """
        provides python mapping for "int" node type

        :param node: syntax tree node
        :param data: data item associated with the tree node
        :return: python integer type declaration
        """
        return 'int'

    def visit_bool(self, node, data):
        """
        provides python mapping for "boolean" node type

        :param node: syntax tree node
        :param data: data item associated with the tree node
        :return: python boolean type declaration
        """
        return 'bool'

    def visit_float(self, node, data):
        """
        provides python mapping for "float" node type

        :param node: syntax tree node
        :param data: data item associated with the tree node
        :return: java double type declaration
        """
        return 'float'

    def visit_string(self, node, data):
        """
        provides python mapping for "String" node type

        :param node: syntax tree node
        :param data: data item associated with the tree node
        :return: python string type declaration
        """
        return 'str'

    def visit_obj(self, node, data):
        """
        provides python mapping for object node type

        :param node: syntax tree node
        :param data: data item associated with the tree node
        :return: python object type declaration
        """
        return node.node_type
