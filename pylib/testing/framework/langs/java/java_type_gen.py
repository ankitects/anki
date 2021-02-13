from testing.framework.syntax.syntax_tree import SyntaxTree, SyntaxTreeVisitor


class JavaTypeGenerator(SyntaxTreeVisitor):
    """
    Generates Java type declarations
    """

    def visit_void(self, node: SyntaxTree, data):
        """
        provides java mapping for "void" node type

        :param node: syntax tree node
        :param data: data item associated with the tree node
        :return: java void type declaration
        """
        return 'void'

    def visit_array(self, node: SyntaxTree, data):
        """
        provides java mapping for "array" node type

        :param node: syntax tree node
        :param data: data item associated with the tree node
        :return: java array-type declaration <child-type>[]
        """
        if len(node.nodes) != 1:
            raise Exception('Array can have only 1 inner-type')
        return self.render(node.first_child(), data) + '[]'

    def visit_list(self, node: SyntaxTree, data):
        """
        provides java mapping for "list" node type

        :param node: syntax tree node
        :param data: data item associated with the tree node
        :return: java list-type declaration List<child-type>
        """
        if len(node.nodes) != 1:
            raise Exception('List can have only 1 inner-type')
        return 'List<' + self.render(node.first_child(), data) + '>'

    def visit_map(self, node, data):
        """
        provides java mapping for "map" node type

        :param node: syntax tree node
        :param data: data item associated with the tree node
        :return: java map-type Map<key-type, value-type>
        """
        if len(node.nodes) != 2:
            raise Exception('Invalid inner-type of map count, must be 2')
        return 'Map<' + self.render(node.first_child(), data) + ', ' + self.render(node.second_child(), data) + '>'

    def visit_bool(self, node, data):
        """
        provides java mapping for "boolean" node type, depending on parent type
        it can be of primitive or reference type

        :param node: syntax tree node
        :param data: data item associated with the tree node
        :return: java boolean type declaration
        """
        if node.parent.is_root() or node.parent.is_array_type() or node.parent.is_user_type:
            return 'boolean'
        elif node.parent.is_container_type():
            return 'Boolean'
        else:
            raise Exception('not supported parent type for int')

    def visit_int(self, node, data):
        """
        provides java mapping for "integer" node type, depending on parent type
        it can be of primitive or reference type

        :param node: syntax tree node
        :param data: data item associated with the tree node
        :return: java integer type declaration
        """
        if node.parent.is_root() or node.parent.is_array_type() or node.parent.is_user_type:
            return 'int'
        elif node.parent.is_container_type():
            return 'Integer'
        else:
            raise Exception('not supported parent type for int')

    def visit_long(self, node, data):
        """
        provides java mapping for "long" node type, depending on parent type
        it can be of primitive or reference type

        :param node: syntax tree node
        :param data: data item associated with the tree node
        :return: java integer type declaration
        """
        if node.parent.is_root() or node.parent.is_array_type() or node.parent.is_user_type:
            return 'long'
        elif node.parent.is_container_type():
            return 'Long'
        else:
            raise Exception('not supported parent type for long')

    def visit_float(self, node, data):
        """
        provides java mapping for "float" node type, depending on parent type
        it can be of primitive or reference type

        :param node: syntax tree node
        :param data: data item associated with the tree node
        :return: java double type declaration
        """
        if node.parent.is_root() or node.parent.is_array_type() or node.parent.is_user_type:
            return 'double'
        elif node.parent.is_container_type():
            return 'Double'
        else:
            raise Exception('not supported parent type for float')

    def visit_string(self, node, data):
        """
        provides java mapping for "String" node type

        :param node: syntax tree node
        :param data: data item associated with the tree node
        :return: java string type declaration
        """
        return 'String'

    def visit_obj(self, node, data):
        """
        provides java mapping for object node type

        :param node: syntax tree node
        :param data: data item associated with the tree node
        :return: java object type declaration
        """
        return node.node_type
